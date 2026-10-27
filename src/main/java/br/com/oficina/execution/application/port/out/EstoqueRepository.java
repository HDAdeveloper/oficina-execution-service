package br.com.oficina.execution.application.port.out;

import br.com.oficina.execution.domain.model.ItemEstoque;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Leitura e administração do estoque. A reserva/liberação passa por {@link ExecucaoStore}. */
public interface EstoqueRepository {

    Optional<ItemEstoque> buscar(String codigo);

    List<ItemEstoque> buscarVarios(Collection<String> codigos);

    List<ItemEstoque> listar();

    /** Cria a peça; lança {@code ConflitoException} se o código já existe. */
    void criar(ItemEstoque item);

    /** Soma atomicamente ao disponível e devolve o item atualizado; lança {@code RecursoNaoEncontradoException}. */
    ItemEstoque repor(String codigo, int quantidade);
}
