package br.com.oficina.execution.application.port.out;

import java.util.Map;

/** Evento de resposta para o orquestrador. O envelope (messageId, data...) é montado ao publicar. */
public record EventoSaga(String type, Long osId, String sagaId, String correlationId,
                         Map<String, Object> payload) {
}
