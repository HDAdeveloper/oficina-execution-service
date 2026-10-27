package br.com.oficina.execution.infrastructure.mensageria;

import br.com.oficina.execution.infrastructure.dynamo.OutboxDynamo;
import br.com.oficina.execution.infrastructure.dynamo.OutboxDynamo.EventoOutbox;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

/**
 * Envia ao SNS os eventos gravados na outbox, na ordem em que foram criados. Se o envio de um evento falha,
 * o lote para ali (preserva a ordem), a falha fica registrada e a próxima rodada tenta de novo.
 * Entrega at-least-once: se o processo cair entre o envio e a marcação, o evento sai duas vezes e o
 * consumidor descarta a repetição pelo messageId.
 */
public class OutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxDynamo outbox;
    private final ObjectMapper json;
    private final PublicadorMensagens publicador;
    private final int lote;

    public OutboxRelay(OutboxDynamo outbox, ObjectMapper json, PublicadorMensagens publicador, int lote) {
        this.outbox = outbox;
        this.json = json;
        this.publicador = publicador;
        this.lote = lote;
    }

    @Scheduled(fixedDelayString = "${execution.mensageria.intervalo-relay-ms:1000}")
    public void executar() {
        try {
            publicarPendentes();
        } catch (RuntimeException e) {
            LOG.error("Erro ao publicar a outbox", e);
        }
    }

    /** Publica um lote de pendentes; devolve quantos saíram. */
    public int publicarPendentes() {
        List<EventoOutbox> pendentes = outbox.listarPendentes(lote);
        int ok = 0;
        for (EventoOutbox e : pendentes) {
            try {
                publicador.publicar(e.tipo(), envelope(e));
            } catch (RuntimeException ex) {
                LOG.warn("Falha ao publicar o evento {} ({}); tentará de novo", e.messageId(), e.tipo(), ex);
                outbox.registrarFalha(e.messageId(), ex.getClass().getSimpleName() + ": " + ex.getMessage());
                break;
            }
            outbox.marcarPublicado(e.messageId());
            ok++;
        }
        return ok;
    }

    String envelope(EventoOutbox e) {
        try {
            ObjectNode no = json.createObjectNode();
            no.put("messageId", e.messageId());
            no.put("sagaId", e.sagaId());
            if (e.osId() == null) {
                no.putNull("osId");
            } else {
                no.put("osId", e.osId());
            }
            no.put("type", e.tipo());
            no.put("version", 1);
            no.put("occurredAt", e.criadoEm().toString());
            no.put("correlationId", e.correlationId());
            no.set("payload", json.readTree(e.payload()));
            return json.writeValueAsString(no);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Payload da outbox inválido (evento " + e.messageId() + ")", ex);
        }
    }
}
