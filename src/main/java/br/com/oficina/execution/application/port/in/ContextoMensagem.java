package br.com.oficina.execution.application.port.in;

/** Metadados do envelope da mensagem, repassados às respostas para a Saga continuar rastreável. */
public record ContextoMensagem(String messageId, String sagaId, Long osId, String correlationId) {
}
