package br.com.oficina.execution.infrastructure.dynamo;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static br.com.oficina.execution.infrastructure.dynamo.Atributos.chave;
import static br.com.oficina.execution.infrastructure.dynamo.Atributos.n;
import static br.com.oficina.execution.infrastructure.dynamo.Atributos.numero;
import static br.com.oficina.execution.infrastructure.dynamo.Atributos.s;
import static br.com.oficina.execution.infrastructure.dynamo.Atributos.texto;

/**
 * Leitura da outbox para o relay: lista os eventos ainda não publicados (índice esparso, na ordem em que foram
 * gravados) e marca cada um como publicado ou como falho. Os eventos são gravados pelo
 * {@link DynamoExecucaoStore}, dentro da transação do comando.
 */
public class OutboxDynamo {

    /** Depois de publicado, o registro fica este tempo (para consulta/diagnóstico) e então expira. */
    private static final long RETENCAO_SEGUNDOS = 7L * 24 * 3600;

    public record EventoOutbox(String messageId, String tipo, Long osId, String sagaId, String correlationId,
                               String payload, Instant criadoEm, int tentativas) {
    }

    private final DynamoDbClient dynamo;
    private final String tabela;
    private final Clock relogio;

    public OutboxDynamo(DynamoDbClient dynamo, Tabelas tabelas, Clock relogio) {
        this.dynamo = dynamo;
        this.tabela = tabelas.outbox();
        this.relogio = relogio;
    }

    public List<EventoOutbox> listarPendentes(int limite) {
        var resposta = dynamo.query(QueryRequest.builder().tableName(tabela).indexName(Tabelas.INDICE_PENDENTES)
                .keyConditionExpression("pendente = :p")
                .expressionAttributeValues(Map.of(":p", s("S")))
                .scanIndexForward(true).limit(limite).build());
        return resposta.items().stream().map(OutboxDynamo::deItem).toList();
    }

    public void marcarPublicado(String messageId) {
        Instant agora = Instant.now(relogio);
        dynamo.updateItem(UpdateItemRequest.builder().tableName(tabela).key(chave("messageId", s(messageId)))
                .updateExpression("REMOVE pendente SET publicadoEm = :t, expiraEm = :e")
                .expressionAttributeValues(Map.of(":t", s(Tempo.formatar(agora)),
                        ":e", n(agora.getEpochSecond() + RETENCAO_SEGUNDOS))).build());
    }

    public void registrarFalha(String messageId, String erro) {
        String curto = erro == null ? "" : erro.substring(0, Math.min(erro.length(), 500));
        dynamo.updateItem(UpdateItemRequest.builder().tableName(tabela).key(chave("messageId", s(messageId)))
                .updateExpression("ADD tentativas :um SET ultimoErro = :e")
                .expressionAttributeValues(Map.of(":um", n(1), ":e", s(curto))).build());
    }

    private static EventoOutbox deItem(Map<String, AttributeValue> item) {
        AttributeValue os = item.get("osId");
        return new EventoOutbox(texto(item, "messageId"), texto(item, "tipo"),
                os == null || os.n() == null ? null : Long.valueOf(os.n()), texto(item, "sagaId"),
                texto(item, "correlationId"), texto(item, "payload"), Tempo.ler(texto(item, "criadoEm")),
                (int) numero(item, "tentativas"));
    }
}
