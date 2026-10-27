package br.com.oficina.execution.application.service;

import br.com.oficina.execution.application.port.in.GestaoEstoque;
import br.com.oficina.execution.application.port.out.EstoqueRepository;
import br.com.oficina.execution.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.execution.domain.exception.RegraDeNegocioException;
import br.com.oficina.execution.domain.model.ItemEstoque;

import java.util.Comparator;
import java.util.List;

public class GestaoEstoqueService implements GestaoEstoque {

    private final EstoqueRepository estoque;

    public GestaoEstoqueService(EstoqueRepository estoque) {
        this.estoque = estoque;
    }

    @Override
    public ItemEstoque cadastrar(String codigo, String nome, int quantidadeInicial) {
        ItemEstoque item = new ItemEstoque(codigo, nome, quantidadeInicial);
        estoque.criar(item); // ConflitoException se o código já existe
        return item;
    }

    @Override
    public ItemEstoque repor(String codigo, int quantidade) {
        if (quantidade <= 0) {
            throw new RegraDeNegocioException("A quantidade a repor deve ser maior que zero");
        }
        return estoque.repor(ItemEstoque.normalizarCodigo(codigo), quantidade);
    }

    @Override
    public ItemEstoque buscar(String codigo) {
        String normalizado = ItemEstoque.normalizarCodigo(codigo);
        return estoque.buscar(normalizado)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Peça não encontrada no estoque: " + normalizado));
    }

    @Override
    public List<ItemEstoque> listar() {
        return estoque.listar().stream().sorted(Comparator.comparing(ItemEstoque::codigo)).toList();
    }
}
