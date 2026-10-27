package br.com.oficina.execution.domain.model;

import br.com.oficina.execution.domain.exception.RegraDeNegocioException;

/** Peça e quantidade pedidas para uma OS. */
public record PecaReservada(String codigo, int quantidade) {

    public PecaReservada {
        codigo = Codigos.normalizar(codigo);
        if (quantidade <= 0) {
            throw new RegraDeNegocioException("Quantidade da peça " + codigo + " deve ser maior que zero");
        }
    }
}
