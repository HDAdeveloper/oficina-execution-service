package br.com.oficina.execution.infrastructure.mensageria;

import br.com.oficina.execution.application.port.in.ContextoMensagem;
import br.com.oficina.execution.application.port.out.EventoSaga;
import br.com.oficina.execution.application.port.out.ExecucaoStore;
import br.com.oficina.execution.infrastructure.dynamo.DynamoTestBase;
import br.com.oficina.execution.infrastructure.dynamo.OutboxDynamo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OutboxRelayTest extends DynamoTestBase {

    @Autowired ExecucaoStore store;
    @Autowired OutboxDynamo outbox;
    @Autowired ObjectMapper json;

    /** Registra o que seria enviado ao SNS; pode ser instruído a falhar. */
    static class PublicadorFalso implements PublicadorMensagens {
        final List<String> tipos = new ArrayList<>();
        final List<String> corpos = new ArrayList<>();
        boolean falhar;

        @Override
        public void publicar(String tipo, String envelopeJson) {
            if (falhar) {
                throw new IllegalStateException("SNS fora do ar");
            }
            tipos.add(tipo);
            corpos.add(envelopeJson);
        }
    }

    @BeforeEach
    void drenar() {
        // eventos pendentes de outros testes não podem atrapalhar a contagem
        new OutboxRelay(outbox, json, new PublicadorFalso(), 1000).publicarPendentes();
    }

    private long gravar(String... tipos) {
        long os = OS.incrementAndGet();
        List<EventoSaga> eventos = java.util.Arrays.stream(tipos)
                .map(t -> new EventoSaga(t, os, "saga-" + os, "corr-" + os, Map.of("reservaId", "res-" + os)))
                .toList();
        store.registrarSemMudanca(new ContextoMensagem(UUID.randomUUID().toString(), "saga-" + os, os, "corr-" + os),
                eventos);
        return os;
    }

    @Test
    void publicaNaOrdemEMarcaComoPublicado() throws Exception {
        long os = gravar("ESTOQUE_RESERVADO", "EXECUCAO_INICIADA");
        var publicador = new PublicadorFalso();

        int enviados = new OutboxRelay(outbox, json, publicador, 100).publicarPendentes();

        assertThat(enviados).isEqualTo(2);
        assertThat(publicador.tipos).containsExactly("ESTOQUE_RESERVADO", "EXECUCAO_INICIADA");
        JsonNode primeiro = json.readTree(publicador.corpos.get(0));
        assertThat(primeiro.get("osId").asLong()).isEqualTo(os);
        assertThat(primeiro.get("sagaId").asText()).isEqualTo("saga-" + os);
        assertThat(primeiro.get("correlationId").asText()).isEqualTo("corr-" + os);
        assertThat(primeiro.get("type").asText()).isEqualTo("ESTOQUE_RESERVADO");
        assertThat(primeiro.get("version").asInt()).isEqualTo(1);
        assertThat(primeiro.get("messageId").asText()).isNotBlank();
        assertThat(primeiro.get("occurredAt").asText()).isNotBlank();
        assertThat(primeiro.get("payload").get("reservaId").asText()).isEqualTo("res-" + os);
        assertThat(outbox.listarPendentes(100)).isEmpty();
    }

    @Test
    void eventoJaPublicadoNaoSaiDeNovo() {
        gravar("A");
        var publicador = new PublicadorFalso();
        var relay = new OutboxRelay(outbox, json, publicador, 100);
        relay.publicarPendentes();

        assertThat(relay.publicarPendentes()).isZero();
        assertThat(publicador.tipos).hasSize(1);
    }

    @Test
    void falhaNoEnvioParaOLoteERegistraATentativa() {
        long os = gravar("A", "B");
        var publicador = new PublicadorFalso();
        publicador.falhar = true;
        var relay = new OutboxRelay(outbox, json, publicador, 100);

        assertThat(relay.publicarPendentes()).isZero();

        var pendentes = outbox.listarPendentes(100);
        assertThat(pendentes).extracting(e -> e.tipo()).containsExactly("A", "B");
        assertThat(pendentes.get(0).tentativas()).isEqualTo(1);
        assertThat(pendentes.get(1).tentativas()).isZero(); // o lote parou no primeiro: a ordem é preservada

        publicador.falhar = false;
        assertThat(relay.publicarPendentes()).isEqualTo(2);
        assertThat(publicador.tipos).containsExactly("A", "B");
        assertThat(os).isPositive();
    }

    @Test
    void executarNaoPropagaErroDoPublicador() {
        gravar("A");
        var publicador = new PublicadorFalso();
        publicador.falhar = true;

        new OutboxRelay(outbox, json, publicador, 100).executar(); // só registra; o agendador não pode morrer

        assertThat(outbox.listarPendentes(100)).hasSize(1);
    }

    @Test
    void respeitaOTamanhoDoLote() {
        gravar("A", "B", "C");
        var publicador = new PublicadorFalso();
        var relay = new OutboxRelay(outbox, json, publicador, 2);

        assertThat(relay.publicarPendentes()).isEqualTo(2);
        assertThat(relay.publicarPendentes()).isEqualTo(1);
    }
}
