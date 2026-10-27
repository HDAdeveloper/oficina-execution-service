package br.com.oficina.execution.infrastructure.mensageria;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/** Envelope de toda mensagem da Saga (ver docs/eventos.md). Campos desconhecidos são ignorados. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EnvelopeMensagem(String messageId, String sagaId, Long osId, String type, Integer version,
                               String occurredAt, String correlationId, JsonNode payload) {
}
