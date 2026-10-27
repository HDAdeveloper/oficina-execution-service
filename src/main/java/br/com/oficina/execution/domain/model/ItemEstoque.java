package br.com.oficina.execution.domain.model;

import br.com.oficina.execution.domain.exception.RegraDeNegocioException;

/** Quantidade disponível de uma peça. A reserva já desconta daqui; a liberação devolve. */
public record ItemEstoque(String codigo, String nome, int disponivel) {

    public ItemEstoque {
        codigo = Codigos.normalizar(codigo);
        if (nome == null || nome.isBlank()) {
            throw new RegraDeNegocioException("Nome da peça é obrigatório");
        }
        nome = nome.trim();
        if (disponivel < 0) {
            throw new RegraDeNegocioException("Quantidade em estoque não pode ser negativa");
        }
    }

    /** Forma padrão de um código de peça (sem espaços, maiúsculas), usada nas buscas. */
    public static String normalizarCodigo(String codigo) {
        return Codigos.normalizar(codigo);
    }
}
