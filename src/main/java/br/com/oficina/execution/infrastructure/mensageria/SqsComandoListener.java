package br.com.oficina.execution.infrastructure.mensageria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

/**
 * Lê a fila de comandos com long polling numa thread própria. A mensagem só é apagada da fila depois de
 * tratada com sucesso; em caso de erro ela reaparece após o visibility timeout e, na terceira falha,
 * o redrive da fila a envia para a DLQ.
 */
public class SqsComandoListener implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(SqsComandoListener.class);

    private final SqsClient sqs;
    private final String filaUrl;
    private final ConsumidorComandos consumidor;
    private volatile boolean ativo;
    private Thread thread;

    public SqsComandoListener(SqsClient sqs, String filaUrl, ConsumidorComandos consumidor) {
        this.sqs = sqs;
        this.filaUrl = filaUrl;
        this.consumidor = consumidor;
    }

    /** Trata um lote já recebido; devolve quantas mensagens foram confirmadas. */
    int processarLote(List<Message> mensagens) {
        int confirmadas = 0;
        for (Message m : mensagens) {
            try {
                consumidor.tratar(m.body());
                sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(filaUrl)
                        .receiptHandle(m.receiptHandle()).build());
                confirmadas++;
            } catch (RuntimeException e) {
                LOG.error("Falha ao tratar a mensagem {}; ela volta para a fila (DLQ após 3 tentativas)",
                        m.messageId(), e);
            }
        }
        return confirmadas;
    }

    private void repetir() {
        while (ativo) {
            try {
                var resposta = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(filaUrl)
                        .maxNumberOfMessages(10).waitTimeSeconds(20).build());
                processarLote(resposta.messages());
            } catch (RuntimeException e) {
                if (!ativo) {
                    return;
                }
                LOG.error("Erro ao ler a fila de comandos; nova tentativa em 2s", e);
                dormir(2000);
            }
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void start() {
        ativo = true;
        thread = new Thread(this::repetir, "execution-sqs-listener");
        thread.setDaemon(true);
        thread.start();
        LOG.info("Escutando a fila {}", filaUrl);
    }

    @Override
    public void stop() {
        ativo = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return ativo;
    }
}
