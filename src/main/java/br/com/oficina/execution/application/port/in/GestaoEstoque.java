package br.com.oficina.execution.application.port.in;

import br.com.oficina.execution.domain.model.ItemEstoque;

import java.util.List;

/** Cadastro e reposição do estoque (uso do almoxarifado, via REST). */
public interface GestaoEstoque {

    ItemEstoque cadastrar(String codigo, String nome, int quantidadeInicial);

    /** Soma {@code quantidade} (maior que zero) ao disponível. */
    ItemEstoque repor(String codigo, int quantidade);

    ItemEstoque buscar(String codigo);

    List<ItemEstoque> listar();
}
