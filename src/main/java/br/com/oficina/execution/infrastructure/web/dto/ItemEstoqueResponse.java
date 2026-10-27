package br.com.oficina.execution.infrastructure.web.dto;

import br.com.oficina.execution.domain.model.ItemEstoque;

public record ItemEstoqueResponse(String codigo, String nome, int disponivel) {
    public static ItemEstoqueResponse de(ItemEstoque i) {
        return new ItemEstoqueResponse(i.codigo(), i.nome(), i.disponivel());
    }
}
