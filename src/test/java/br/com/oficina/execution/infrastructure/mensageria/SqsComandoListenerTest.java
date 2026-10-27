package br.com.oficina.execution.infrastructure.mensageria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SqsComandoListenerTest {

    private static final String FILA = "http://localhost:4566/000000000000/execution-commands";

    private final SqsClient sqs = mock(SqsClient.class);

    private static Message mensagem(String recibo, String corpo) {
        return Message.builder().messageId("sqs-" + recibo).receiptHandle(recibo).body(corpo).build();
    }

    private static final String VALIDA = """
            {"messageId":"m-1","sagaId":"s","osId":1,"type":"LIBERAR_ESTOQUE","version":1,
             "payload":{"motivo":"x"}}""";

    @Test
    void confirmaSoAsMensagensTratadasComSucesso() {
        var tratadas = new java.util.ArrayList<String>();
        var consumidor = new ConsumidorComandos(new ObjectMapper(), new ComandoMapper(),
                (ctx, cmd) -> tratadas.add(ctx.messageId()));
        var listener = new SqsComandoListener(sqs, FILA, consumidor);

        int confirmadas = listener.processarLote(List.of(mensagem("r1", VALIDA), mensagem("r2", "lixo")));

        assertThat(confirmadas).isEqualTo(1);
        assertThat(tratadas).containsExactly("m-1");
        ArgumentCaptor<DeleteMessageRequest> apagada = ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(sqs).deleteMessage(apagada.capture());
        assertThat(apagada.getValue().receiptHandle()).isEqualTo("r1");
        assertThat(apagada.getValue().queueUrl()).isEqualTo(FILA);
    }

    @Test
    void mensagemQueFalhaNaoEApagadaDaFila() {
        var consumidor = new ConsumidorComandos(new ObjectMapper(), new ComandoMapper(),
                (ctx, cmd) -> { throw new IllegalStateException("provedor fora"); });
        var listener = new SqsComandoListener(sqs, FILA, consumidor);

        assertThat(listener.processarLote(List.of(mensagem("r1", VALIDA)))).isZero();

        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void cicloDeVidaIniciaEPara() {
        var consumidor = new ConsumidorComandos(new ObjectMapper(), new ComandoMapper(), (ctx, cmd) -> { });
        var listener = new SqsComandoListener(sqs, FILA, consumidor);
        assertThat(listener.isRunning()).isFalse();

        listener.start();
        assertThat(listener.isRunning()).isTrue();

        listener.stop();
        assertThat(listener.isRunning()).isFalse();
    }
}
