package br.com.oficina.execution.infrastructure.mensageria;

/**
 * Mensagem que nunca vai ser entendida (JSON quebrado, tipo desconhecido, campo obrigatório ausente).
 * Não é confirmada na fila: após as tentativas do redrive ela vai para a DLQ, onde a operação a analisa.
 */
public class MensagemInvalidaException extends RuntimeException {
    public MensagemInvalidaException(String mensagem) {
        super(mensagem);
    }

    public MensagemInvalidaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
