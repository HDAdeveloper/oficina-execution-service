package br.com.oficina.execution.infrastructure.dynamo;

import br.com.oficina.execution.application.port.in.ContextoMensagem;
import br.com.oficina.execution.application.port.out.EventoSaga;
import br.com.oficina.execution.application.port.out.ExecucaoStore;
import br.com.oficina.execution.infrastructure.dynamo.OutboxDynamo.EventoOutbox;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OutboxDynamoTest extends DynamoTestBase {

    @Autowired ExecucaoStore store;
    @Autowired OutboxDynamo outbox;

    private long gravar(String... tipos) {
        long os = OS.incrementAndGet();
        List<EventoSaga> eventos = java.util.Arrays.stream(tipos)
                .map(t -> new EventoSaga(t, os, "saga-" + os, "corr-" + os, Map.of("reservaId", "res-" + os)))
                .toList();
        store.registrarSemMudanca(new ContextoMensagem(UUID.randomUUID().toString(), "saga-" + os, os, "corr-" + os),
                eventos);
        return os;
    }

    private List<EventoOutbox> pendentesDa(long os) {
        return outbox.listarPendentes(1000).stream().filter(e -> e.osId() != null && e.osId() == os).toList();
    }

    @Test
    void eventoGravadoFicaPendenteComEnvelopeCompleto() {
        long os = gravar("ESTOQUE_RESERVADO");

        EventoOutbox e = pendentesDa(os).get(0);

        assertThat(e.tipo()).isEqualTo("ESTOQUE_RESERVADO");
        assertThat(e.sagaId()).isEqualTo("saga-" + os);
        assertThat(e.correlationId()).isEqualTo("corr-" + os);
        assertThat(e.payload()).contains("\"reservaId\"").contains("res-" + os);
        assertThat(e.messageId()).isNotBlank();
        assertThat(e.criadoEm()).isNotNull();
        assertThat(e.tentativas()).isZero();
    }

    @Test
    void pendentesSaemNaOrdemDeGravacao() {
        long a = gravar("A1", "A2");
        long b = gravar("B1");

        List<String> ordem = outbox.listarPendentes(1000).stream()
                .filter(e -> e.osId() != null && (e.osId() == a || e.osId() == b))
                .map(EventoOutbox::tipo).toList();

        assertThat(ordem).containsExactly("A1", "A2", "B1");
    }

    @Test
    void publicadoSaiDaListaDePendentes() {
        long os = gravar("X1", "X2");
        EventoOutbox primeiro = pendentesDa(os).get(0);

        outbox.marcarPublicado(primeiro.messageId());

        assertThat(pendentesDa(os)).extracting(EventoOutbox::tipo).containsExactly("X2");
    }

    @Test
    void falhaFicaRegistradaEOEventoContinuaPendente() {
        long os = gravar("Y1");
        EventoOutbox e = pendentesDa(os).get(0);

        outbox.registrarFalha(e.messageId(), "SNS indisponível");
        outbox.registrarFalha(e.messageId(), "SNS indisponível");

        EventoOutbox depois = pendentesDa(os).get(0);
        assertThat(depois.tentativas()).isEqualTo(2);
    }

    @Test
    void limiteDaListagemEhRespeitado() {
        gravar("L1");
        gravar("L2");

        assertThat(outbox.listarPendentes(1)).hasSize(1);
    }
}
