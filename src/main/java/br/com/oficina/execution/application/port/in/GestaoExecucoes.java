package br.com.oficina.execution.application.port.in;

import br.com.oficina.execution.domain.model.Execucao;

import java.util.List;

/** Acompanhamento do reparo pela equipe da oficina (via REST). Cada mudança de estado gera um evento. */
public interface GestaoExecucoes {

    Execucao buscar(Long osId);

    /** Fila de trabalho: execuções NA_FILA e EM_REPARO, da mais antiga para a mais nova. */
    List<Execucao> listarFila();

    /** NA_FILA → EM_REPARO; publica {@code EXECUCAO_INICIADA}. */
    Execucao iniciar(Long osId);

    /** EM_REPARO → CONCLUIDA; publica {@code EXECUCAO_FINALIZADA}. */
    Execucao finalizar(Long osId);

    /** NA_FILA/EM_REPARO → FALHOU; publica {@code EXECUCAO_FALHOU} (o orquestrador compensa). */
    Execucao falhar(Long osId, String motivo);
}
