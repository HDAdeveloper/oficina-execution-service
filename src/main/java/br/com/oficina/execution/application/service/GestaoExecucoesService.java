package br.com.oficina.execution.application.service;

import br.com.oficina.execution.application.port.in.GestaoExecucoes;
import br.com.oficina.execution.application.port.out.EventoSaga;
import br.com.oficina.execution.application.port.out.ExecucaoStore;
import br.com.oficina.execution.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.execution.domain.model.Execucao;
import br.com.oficina.execution.domain.model.StatusExecucao;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GestaoExecucoesService implements GestaoExecucoes {

    private final ExecucaoStore store;
    private final Clock relogio;

    public GestaoExecucoesService(ExecucaoStore store, Clock relogio) {
        this.store = store;
        this.relogio = relogio;
    }

    @Override
    public Execucao buscar(Long osId) {
        return store.buscarPorOs(osId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Execução não encontrada para a OS " + osId));
    }

    @Override
    public List<Execucao> listarFila() {
        List<Execucao> fila = new ArrayList<>();
        for (StatusExecucao status : StatusExecucao.values()) {
            if (status.naFilaDeTrabalho()) {
                fila.addAll(store.listarPorStatus(status));
            }
        }
        fila.sort(Comparator.comparing(Execucao::getCriadoEm).thenComparing(Execucao::getOsId));
        return fila;
    }

    @Override
    public Execucao iniciar(Long osId) {
        Execucao e = buscar(osId);
        e.iniciar(agora());
        store.atualizar(e, List.of(evento("EXECUCAO_INICIADA", e, "iniciadaEm", e.getIniciadaEm().toString())));
        return e;
    }

    @Override
    public Execucao finalizar(Long osId) {
        Execucao e = buscar(osId);
        e.finalizar(agora());
        store.atualizar(e, List.of(evento("EXECUCAO_FINALIZADA", e, "finalizadaEm", e.getFinalizadaEm().toString())));
        return e;
    }

    @Override
    public Execucao falhar(Long osId, String motivo) {
        Execucao e = buscar(osId);
        e.falhar(motivo, agora());
        store.atualizar(e, List.of(evento("EXECUCAO_FALHOU", e, "motivo", e.getMotivoFalha())));
        return e;
    }

    private static EventoSaga evento(String tipo, Execucao e, String campo, String valor) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(campo, valor);
        return new EventoSaga(tipo, e.getOsId(), e.getSagaId(), e.getCorrelationId(), payload);
    }

    private Instant agora() {
        return Instant.now(relogio);
    }
}
