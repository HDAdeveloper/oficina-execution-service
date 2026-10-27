package br.com.oficina.execution.domain.model;

/**
 * NA_FILA → EM_REPARO → CONCLUIDA, ou NA_FILA/EM_REPARO → FALHOU. A reserva de estoque pode ser
 * desfeita (LIBERADA) a partir de NA_FILA, EM_REPARO ou FALHOU; CONCLUIDA consome as peças de vez.
 */
public enum StatusExecucao {
    NA_FILA, EM_REPARO, CONCLUIDA, FALHOU, LIBERADA;

    /** Ainda ocupa a fila de trabalho da oficina. */
    public boolean naFilaDeTrabalho() {
        return this == NA_FILA || this == EM_REPARO;
    }
}
