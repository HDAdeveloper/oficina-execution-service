package br.com.oficina.execution.infrastructure.dynamo;

import br.com.oficina.execution.domain.model.Execucao;
import br.com.oficina.execution.domain.model.PecaReservada;
import br.com.oficina.execution.domain.model.StatusExecucao;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static br.com.oficina.execution.infrastructure.dynamo.Atributos.n;
import static br.com.oficina.execution.infrastructure.dynamo.Atributos.numero;
import static br.com.oficina.execution.infrastructure.dynamo.Atributos.s;
import static br.com.oficina.execution.infrastructure.dynamo.Atributos.texto;

/** Converte a {@link Execucao} do domínio de e para o item do DynamoDB. */
final class ExecucaoMapper {

    private ExecucaoMapper() {
    }

    /** {@code versao} é o número gravado (a versão lida + 1 numa atualização). */
    static Map<String, AttributeValue> paraItem(Execucao e, long versao) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("osId", n(e.getOsId()));
        item.put("reservaId", s(e.getReservaId()));
        item.put("status", s(e.getStatus().name()));
        item.put("criadoEm", s(Tempo.formatar(e.getCriadoEm())));
        item.put("atualizadoEm", s(Tempo.formatar(e.getAtualizadoEm())));
        item.put("versao", n(versao));
        colocar(item, "sagaId", e.getSagaId());
        colocar(item, "correlationId", e.getCorrelationId());
        colocar(item, "motivoFalha", e.getMotivoFalha());
        if (e.getIniciadaEm() != null) {
            item.put("iniciadaEm", s(Tempo.formatar(e.getIniciadaEm())));
        }
        if (e.getFinalizadaEm() != null) {
            item.put("finalizadaEm", s(Tempo.formatar(e.getFinalizadaEm())));
        }
        List<AttributeValue> pecas = new ArrayList<>();
        for (PecaReservada p : e.getPecas()) {
            pecas.add(AttributeValue.builder().m(Map.of("codigo", s(p.codigo()), "quantidade", n(p.quantidade())))
                    .build());
        }
        item.put("pecas", AttributeValue.builder().l(pecas).build());
        item.put("servicos", AttributeValue.builder().l(e.getServicos().stream().map(Atributos::s).toList()).build());
        return item;
    }

    static Execucao deItem(Map<String, AttributeValue> item) {
        List<PecaReservada> pecas = new ArrayList<>();
        for (AttributeValue v : item.get("pecas").l()) {
            pecas.add(new PecaReservada(texto(v.m(), "codigo"), (int) numero(v.m(), "quantidade")));
        }
        List<String> servicos = item.get("servicos").l().stream().map(AttributeValue::s).toList();
        return Execucao.reconstituir(Long.valueOf(numero(item, "osId")), texto(item, "sagaId"), texto(item, "correlationId"),
                texto(item, "reservaId"), pecas, servicos, StatusExecucao.valueOf(texto(item, "status")),
                texto(item, "motivoFalha"), Tempo.ler(texto(item, "criadoEm")), Tempo.ler(texto(item, "iniciadaEm")),
                Tempo.ler(texto(item, "finalizadaEm")), Tempo.ler(texto(item, "atualizadoEm")),
                numero(item, "versao"));
    }

    private static void colocar(Map<String, AttributeValue> item, String chave, String valor) {
        if (valor != null) {
            item.put(chave, s(valor));
        }
    }
}
