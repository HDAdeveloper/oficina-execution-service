package br.com.oficina.execution.domain.exception;

/** Violação de uma regra do domínio (dado inválido ou transição não permitida). */
public class RegraDeNegocioException extends RuntimeException {
    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
