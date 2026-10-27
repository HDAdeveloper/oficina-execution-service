package br.com.oficina.execution.infrastructure.web.dto;

import br.com.oficina.execution.domain.model.Execucao;

import java.time.Instant;
import java.util.List;

public record ExecucaoResponse(Long osId, String sagaId, String reservaId, String status, String motivoFalha,
                               List<Peca> pecas, List<String> servicos, Instant criadoEm, Instant iniciadaEm,
                               Instant finalizadaEm, Instant atualizadoEm) {

    public record Peca(String codigo, int quantidade) {
    }

    public static ExecucaoResponse de(Execucao e) {
        return new ExecucaoResponse(e.getOsId(), e.getSagaId(), e.getReservaId(), e.getStatus().name(),
                e.getMotivoFalha(), e.getPecas().stream().map(p -> new Peca(p.codigo(), p.quantidade())).toList(),
                e.getServicos(), e.getCriadoEm(), e.getIniciadaEm(), e.getFinalizadaEm(), e.getAtualizadoEm());
    }
}
