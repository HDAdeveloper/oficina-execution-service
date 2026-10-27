package br.com.oficina.execution.infrastructure.mensageria;

import br.com.oficina.execution.application.port.in.ComandoExecution;
import br.com.oficina.execution.application.port.in.ComandoExecution.LiberarEstoque;
import br.com.oficina.execution.application.port.in.ContextoMensagem;
import br.com.oficina.execution.application.port.in.ProcessadorComandos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsumidorComandosTest {

    private final List<ContextoMensagem> contextos = new ArrayList<>();
    private final List<ComandoExecution> comandos = new ArrayList<>();
    private RuntimeException falha;

    private final ProcessadorComandos processador = (ctx, cmd) -> {
        if (falha != null) {
            throw falha;
        }
        contextos.add(ctx);
        comandos.add(cmd);
    };

    private final ConsumidorComandos consumidor =
            new ConsumidorComandos(new ObjectMapper(), new ComandoMapper(), processador);

    private static final String CORPO = """
            {"messageId":"m-9","sagaId":"s-9","osId":5,"type":"LIBERAR_ESTOQUE","version":1,
             "occurredAt":"2026-11-03T14:05:00Z","correlationId":"c-9",
             "payload":{"motivo":"teste"},"campoNovo":"ignorado"}""";

    @Test
    void leOEnvelopeEChamaOCasoDeUso() {
        consumidor.tratar(CORPO);

        assertThat(contextos).singleElement().satisfies(c -> {
            assertThat(c.messageId()).isEqualTo("m-9");
            assertThat(c.osId()).isEqualTo(5L);
        });
        assertThat(comandos).singleElement().isEqualTo(new LiberarEstoque("teste"));
    }

    @Test
    void corpoQueNaoEJsonEMensagemInvalida() {
        assertThatThrownBy(() -> consumidor.tratar("isto não é json"))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThat(comandos).isEmpty();
    }

    @Test
    void erroDoCasoDeUsoSobeParaAMensagemNaoSerConfirmada() {
        falha = new IllegalStateException("banco fora");

        assertThatThrownBy(() -> consumidor.tratar(CORPO)).hasMessage("banco fora");
    }
}
