package br.com.oficina.execution.domain.model;

import br.com.oficina.execution.domain.exception.RegraDeNegocioException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Execução de uma OS: nasce junto com a reserva de estoque (entra na fila) e acompanha o reparo.
 * {@code sagaId} e {@code correlationId} vêm do comando de reserva e acompanham os eventos
 * seguintes, mesmo os disparados por chamadas REST, para a Saga continuar rastreável.
 */
public class Execucao {

    private final Long osId;
    private final String sagaId;
    private final String correlationId;
    private final String reservaId;
    private final List<PecaReservada> pecas;
    private final List<String> servicos;
    private StatusExecucao status;
    private String motivoFalha;
    private final Instant criadoEm;
    private Instant iniciadaEm;
    private Instant finalizadaEm;
    private Instant atualizadoEm;
    private final long versao;

    private Execucao(Long osId, String sagaId, String correlationId, String reservaId, List<PecaReservada> pecas,
                     List<String> servicos, StatusExecucao status, String motivoFalha, Instant criadoEm,
                     Instant iniciadaEm, Instant finalizadaEm, Instant atualizadoEm, long versao) {
        this.osId = osId;
        this.sagaId = sagaId;
        this.correlationId = correlationId;
        this.reservaId = reservaId;
        this.pecas = new ArrayList<>(pecas);
        this.servicos = servicos == null ? new ArrayList<>() : new ArrayList<>(servicos);
        this.status = status;
        this.motivoFalha = motivoFalha;
        this.criadoEm = criadoEm;
        this.iniciadaEm = iniciadaEm;
        this.finalizadaEm = finalizadaEm;
        this.atualizadoEm = atualizadoEm;
        this.versao = versao;
    }

    public static Execucao nova(Long osId, String sagaId, String correlationId, String reservaId,
                                List<PecaReservada> pecas, List<String> servicos, Instant agora) {
        if (osId == null) {
            throw new RegraDeNegocioException("OS da execução é obrigatória");
        }
        if (reservaId == null || reservaId.isBlank()) {
            throw new RegraDeNegocioException("Id da reserva é obrigatório");
        }
        if (pecas == null) {
            throw new RegraDeNegocioException("Lista de peças é obrigatória (pode ser vazia)");
        }
        return new Execucao(osId, sagaId, correlationId, reservaId, pecas, servicos, StatusExecucao.NA_FILA, null,
                agora, null, null, agora, 0);
    }

    public static Execucao reconstituir(Long osId, String sagaId, String correlationId, String reservaId,
                                        List<PecaReservada> pecas, List<String> servicos, StatusExecucao status,
                                        String motivoFalha, Instant criadoEm, Instant iniciadaEm,
                                        Instant finalizadaEm, Instant atualizadoEm, long versao) {
        return new Execucao(osId, sagaId, correlationId, reservaId, pecas, servicos, status, motivoFalha, criadoEm,
                iniciadaEm, finalizadaEm, atualizadoEm, versao);
    }

    // ------------------------------------------------------------ transições

    public void iniciar(Instant agora) {
        exigir(status == StatusExecucao.NA_FILA, "iniciada");
        this.iniciadaEm = agora;
        mudar(StatusExecucao.EM_REPARO, agora);
    }

    public void finalizar(Instant agora) {
        exigir(status == StatusExecucao.EM_REPARO, "finalizada");
        this.finalizadaEm = agora;
        mudar(StatusExecucao.CONCLUIDA, agora);
    }

    public void falhar(String motivo, Instant agora) {
        exigir(status.naFilaDeTrabalho(), "marcada como falha");
        this.motivoFalha = motivo == null || motivo.isBlank() ? "Falha na execução" : motivo.trim();
        mudar(StatusExecucao.FALHOU, agora);
    }

    /**
     * Desfaz a reserva. Devolve {@code false} se já estava liberada (chamada repetida, nada a fazer).
     * Uma execução concluída não pode ser liberada: as peças já foram usadas.
     */
    public boolean liberar(Instant agora) {
        if (status == StatusExecucao.LIBERADA) {
            return false;
        }
        if (status == StatusExecucao.CONCLUIDA) {
            throw new RegraDeNegocioException("A execução da OS " + osId + " já foi concluída; as peças foram usadas");
        }
        mudar(StatusExecucao.LIBERADA, agora);
        return true;
    }

    private void exigir(boolean condicao, String acao) {
        if (!condicao) {
            throw new RegraDeNegocioException("A execução da OS " + osId + " está " + status
                    + " e não pode ser " + acao);
        }
    }

    private void mudar(StatusExecucao novo, Instant agora) {
        this.status = novo;
        this.atualizadoEm = agora;
    }

    // ------------------------------------------------------------ leitura

    public Long getOsId() { return osId; }
    public String getSagaId() { return sagaId; }
    public String getCorrelationId() { return correlationId; }
    public String getReservaId() { return reservaId; }
    public List<PecaReservada> getPecas() { return Collections.unmodifiableList(pecas); }
    public List<String> getServicos() { return Collections.unmodifiableList(servicos); }
    public StatusExecucao getStatus() { return status; }
    public String getMotivoFalha() { return motivoFalha; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getIniciadaEm() { return iniciadaEm; }
    public Instant getFinalizadaEm() { return finalizadaEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public long getVersao() { return versao; }
}
