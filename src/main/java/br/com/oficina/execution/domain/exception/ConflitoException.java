package br.com.oficina.execution.domain.exception;

/** O recurso já existe ou foi alterado por outra operação ao mesmo tempo. */
public class ConflitoException extends RuntimeException {
    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
