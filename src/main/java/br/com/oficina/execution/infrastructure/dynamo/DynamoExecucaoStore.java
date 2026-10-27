package br.com.oficina.execution.infrastructure.dynamo;

import br.com.oficina.execution.application.port.in.ContextoMensagem;
import br.com.oficina.execution.application.port.out.EventoSaga;
import br.com.oficina.execution.application.port.out.ExecucaoStore;
import br.com.oficina.execution.application.port.out.GeradorId;
import br.com.oficina.execution.domain.exception.ConflitoException;
import br.com.oficina.execution.domain.model.Execucao;
import br.com.oficina.execution.domain.model.PecaReservada;
import br.com.oficina.execution.domain.model.StatusExecucao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static br.com.oficina.execution.infrastructure.dynamo.Atributos.chave;
import static br.com.oficina.execution.infrastructure.dynamo.Atributos.n;
import static br.com.oficina.execution.infrastructure.dynamo.Atributos.s;

/**
 * Cada operação de escrita é <b>uma</b> {@code TransactWriteItems}: estado da execução, baixa/devolução do
 * estoque, marca de mensagem processada e eventos da outbox entram juntos ou não entram (ver ADR-006).
 * A baixa de estoque tem condição {@code disponivel >= quantidade}, então duas reservas simultâneas pela
 * última unidade nunca vencem as duas.
 */
public class DynamoExecucaoStore implements ExecucaoStore {

    private static final String CONDICAO_FALHOU = "ConditionalCheckFailed";
    /** Mensagens processadas são lembradas por este tempo (a fila só reentrega por alguns dias). */
    private static final long RETENCAO_MENSAGEM_SEGUNDOS = 30L * 24 * 3600;

    private final DynamoDbClient dynamo;
    private final Tabelas tabelas;
    private final ObjectMapper json;
    private final GeradorId ids;
    private final Clock relogio;

    public DynamoExecucaoStore(DynamoDbClient dynamo, Tabelas tabelas, ObjectMapper json, GeradorId ids, Clock relogio) {
        this.dynamo = dynamo;
        this.tabelas = tabelas;
        this.json = json;
        this.ids = ids;
        this.relogio = relogio;
    }

    // ------------------------------------------------------------ leitura

    @Override
    public boolean mensagemJaProcessada(String messageId) {
        var r = dynamo.getItem(GetItemRequest.builder().tableName(tabelas.mensagemProcessada())
                .key(chave("messageId", s(messageId))).consistentRead(true).build());
        return r.hasItem() && !r.item().isEmpty();
    }

    @Override
    public Optional<Execucao> buscarPorOs(Long osId) {
        var r = dynamo.getItem(GetItemRequest.builder().tableName(tabelas.execucao())
                .key(chave("osId", n(osId))).consistentRead(true).build());
        return r.hasItem() && !r.item().isEmpty() ? Optional.of(ExecucaoMapper.deItem(r.item())) : Optional.empty();
    }

    @Override
    public List<Execucao> listarPorStatus(StatusExecucao status) {
        List<Execucao> lista = new ArrayList<>();
        dynamo.queryPaginator(QueryRequest.builder().tableName(tabelas.execucao()).indexName(Tabelas.INDICE_STATUS)
                .keyConditionExpression("#s = :s").expressionAttributeNames(Map.of("#s", "status"))
                .expressionAttributeValues(Map.of(":s", s(status.name()))).scanIndexForward(true).build())
                .items().forEach(i -> lista.add(ExecucaoMapper.deItem(i)));
        return lista;
    }

    // ------------------------------------------------------------ escrita atômica

    @Override
    public ResultadoReserva reservar(ContextoMensagem ctx, Execucao nova, List<EventoSaga> eventosSeReservar) {
        List<TransactWriteItem> itens = new ArrayList<>();
        itens.add(registroDaMensagem(ctx.messageId()));
        // Uma OS só tem uma execução; só pode ser substituída se a anterior foi liberada
        itens.add(TransactWriteItem.builder().put(Put.builder().tableName(tabelas.execucao())
                .item(ExecucaoMapper.paraItem(nova, 1))
                .conditionExpression("attribute_not_exists(osId) OR #s = :liberada")
                .expressionAttributeNames(Map.of("#s", "status"))
                .expressionAttributeValues(Map.of(":liberada", s(StatusExecucao.LIBERADA.name()))).build()).build());
        int primeiroEstoque = itens.size();
        for (PecaReservada p : nova.getPecas()) {
            itens.add(TransactWriteItem.builder().update(Update.builder().tableName(tabelas.estoque())
                    .key(chave("codigo", s(p.codigo())))
                    .updateExpression("SET disponivel = disponivel - :q")
                    .conditionExpression("attribute_exists(codigo) AND disponivel >= :q")
                    .expressionAttributeValues(Map.of(":q", n(p.quantidade()))).build()).build());
        }
        int ultimoEstoque = itens.size();
        itens.addAll(outbox(eventosSeReservar));

        try {
            executar(itens);
            return new ResultadoReserva.Reservada();
        } catch (TransactionCanceledException e) {
            List<CancellationReason> motivos = e.cancellationReasons();
            if (falhou(motivos, 0)) {
                throw new ConflitoException("A mensagem " + ctx.messageId() + " já está sendo tratada");
            }
            if (falhou(motivos, 1)) {
                throw new ConflitoException("A OS " + nova.getOsId() + " foi reservada por outra operação");
            }
            for (int i = primeiroEstoque; i < ultimoEstoque; i++) {
                if (falhou(motivos, i)) {
                    return new ResultadoReserva.EstoqueInsuficiente(); // nada foi alterado
                }
            }
            throw conflitoTransitorio(e);
        }
    }

    @Override
    public void registrarSemMudanca(ContextoMensagem ctx, List<EventoSaga> eventos) {
        List<TransactWriteItem> itens = new ArrayList<>();
        itens.add(registroDaMensagem(ctx.messageId()));
        itens.addAll(outbox(eventos));
        try {
            executar(itens);
        } catch (TransactionCanceledException e) {
            throw falhou(e.cancellationReasons(), 0)
                    ? new ConflitoException("A mensagem " + ctx.messageId() + " já está sendo tratada")
                    : conflitoTransitorio(e);
        }
    }

    @Override
    public void liberar(ContextoMensagem ctx, Execucao liberada, List<EventoSaga> eventos) {
        List<TransactWriteItem> itens = new ArrayList<>();
        itens.add(registroDaMensagem(ctx.messageId()));
        itens.add(gravarExecucao(liberada));
        for (PecaReservada p : liberada.getPecas()) {
            itens.add(TransactWriteItem.builder().update(Update.builder().tableName(tabelas.estoque())
                    .key(chave("codigo", s(p.codigo())))
                    .updateExpression("ADD disponivel :q")
                    .conditionExpression("attribute_exists(codigo)")
                    .expressionAttributeValues(Map.of(":q", n(p.quantidade()))).build()).build());
        }
        itens.addAll(outbox(eventos));
        try {
            executar(itens);
        } catch (TransactionCanceledException e) {
            throw new ConflitoException("A liberação da OS " + liberada.getOsId()
                    + " conflitou com outra operação: " + codigos(e));
        }
    }

    @Override
    public void atualizar(Execucao execucao, List<EventoSaga> eventos) {
        List<TransactWriteItem> itens = new ArrayList<>();
        itens.add(gravarExecucao(execucao));
        itens.addAll(outbox(eventos));
        try {
            executar(itens);
        } catch (TransactionCanceledException e) {
            throw new ConflitoException("A execução da OS " + execucao.getOsId()
                    + " foi alterada por outra operação; consulte-a de novo");
        }
    }

    // ------------------------------------------------------------ apoio

    /** Grava a execução com versão + 1, só se a versão no banco ainda é a que foi lida. */
    private TransactWriteItem gravarExecucao(Execucao e) {
        return TransactWriteItem.builder().put(Put.builder().tableName(tabelas.execucao())
                .item(ExecucaoMapper.paraItem(e, e.getVersao() + 1))
                .conditionExpression("#v = :esperada")
                .expressionAttributeNames(Map.of("#v", "versao"))
                .expressionAttributeValues(Map.of(":esperada", n(e.getVersao()))).build()).build();
    }

    private TransactWriteItem registroDaMensagem(String messageId) {
        Instant agora = Instant.now(relogio);
        return TransactWriteItem.builder().put(Put.builder().tableName(tabelas.mensagemProcessada())
                .item(Map.of("messageId", s(messageId), "processadaEm", s(Tempo.formatar(agora)),
                        "expiraEm", n(agora.getEpochSecond() + RETENCAO_MENSAGEM_SEGUNDOS)))
                .conditionExpression("attribute_not_exists(messageId)").build()).build();
    }

    private List<TransactWriteItem> outbox(List<EventoSaga> eventos) {
        List<TransactWriteItem> itens = new ArrayList<>();
        String criadoEm = Tempo.formatar(Instant.now(relogio));
        int indice = 0;
        for (EventoSaga ev : eventos) {
            Map<String, AttributeValue> item = new java.util.HashMap<>();
            item.put("messageId", s(ids.novo()));
            item.put("tipo", s(ev.type()));
            item.put("payload", s(serializar(ev)));
            item.put("criadoEm", s(criadoEm));
            // Desempata eventos gravados no mesmo instante, mantendo a ordem da lista
            item.put("ordem", s(criadoEm + "-" + String.format("%03d", indice++)));
            item.put("pendente", s("S"));
            if (ev.osId() != null) {
                item.put("osId", n(ev.osId()));
            }
            if (ev.sagaId() != null) {
                item.put("sagaId", s(ev.sagaId()));
            }
            if (ev.correlationId() != null) {
                item.put("correlationId", s(ev.correlationId()));
            }
            itens.add(TransactWriteItem.builder().put(Put.builder().tableName(tabelas.outbox()).item(item)
                    .conditionExpression("attribute_not_exists(messageId)").build()).build());
        }
        return itens;
    }

    private String serializar(EventoSaga ev) {
        try {
            return json.writeValueAsString(ev.payload());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Payload do evento " + ev.type() + " inválido", e);
        }
    }

    private void executar(List<TransactWriteItem> itens) {
        dynamo.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(itens).build());
    }

    private static boolean falhou(List<CancellationReason> motivos, int indice) {
        return motivos != null && motivos.size() > indice && CONDICAO_FALHOU.equals(motivos.get(indice).code());
    }

    private static List<String> codigos(TransactionCanceledException e) {
        return e.cancellationReasons() == null ? List.of()
                : e.cancellationReasons().stream().map(CancellationReason::code).toList();
    }

    /** Conflito de transações simultâneas ou limite de capacidade: a mensagem volta à fila e é tentada de novo. */
    private static ConflitoException conflitoTransitorio(TransactionCanceledException e) {
        return new ConflitoException("Transação cancelada pelo DynamoDB (tentará de novo): " + codigos(e));
    }
}
