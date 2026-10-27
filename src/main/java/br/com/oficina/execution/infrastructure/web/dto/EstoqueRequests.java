package br.com.oficina.execution.infrastructure.web.dto;

/** Corpos das chamadas de estoque. */
public final class EstoqueRequests {

    private EstoqueRequests() {
    }

    public record NovaPeca(String codigo, String nome, int quantidade) {
    }

    public record Reposicao(int quantidade) {
    }
}
