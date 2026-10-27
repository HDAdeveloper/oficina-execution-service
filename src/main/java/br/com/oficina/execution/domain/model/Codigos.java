package br.com.oficina.execution.domain.model;

import br.com.oficina.execution.domain.exception.RegraDeNegocioException;

/** Códigos de peça são comparados sem espaços e em maiúsculas, como no catálogo do OS Service. */
final class Codigos {

    private Codigos() {
    }

    static String normalizar(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new RegraDeNegocioException("Código da peça é obrigatório");
        }
        return codigo.trim().toUpperCase();
    }
}
