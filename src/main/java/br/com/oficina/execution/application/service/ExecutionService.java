package br.com.oficina.execution.application.service;

import br.com.oficina.execution.application.port.in.ComandoExecution;
import br.com.oficina.execution.application.port.in.ComandoExecution.LiberarEstoque;
import br.com.oficina.execution.application.port.in.ComandoExecution.ReservaInvalida;
import br.com.oficina.execution.application.port.in.ComandoExecution.ReservarEstoque;
import br.com.oficina.execution.application.port.in.ContextoMensagem;
import br.com.oficina.execution.application.port.in.ProcessadorComandos;
import br.com.oficina.execution.application.port.out.EstoqueRepository;
import br.com.oficina.execution.application.port.out.EventoSaga;
import br.com.oficina.execution.application.port.out.ExecucaoStore;
import br.com.oficina.execution.application.port.out.ExecucaoStore.ResultadoReserva;
import br.com.oficina.execution.application.port.out.GeradorId;
import br.com.oficina.execution.domain.exception.RegraDeNegocioException;
import br.com.oficina.execution.domain.model.Execucao;
import br.com.oficina.execution.domain.model.ItemEstoque;
import br.com.oficina.execution.domain.model.PecaReservada;
import br.com.oficina.execution.domain.model.StatusExecucao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trata os comandos da Saga. Duas camadas de idempotência: pelo {@code messageId} (a mesma mensagem entregue
 * duas vezes) e pela regra de negócio (reservar de novo para a mesma OS não baixa o estoque duas vezes).
 */
public class ExecutionService implements ProcessadorComandos {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutionService.class);

    private final ExecucaoStore store;
    private final EstoqueRepository estoque;
    private final GeradorId ids;
    private final Clock relogio;

    public ExecutionService(ExecucaoStore store, EstoqueRepository estoque, GeradorId ids, Clock relogio) {
        this.store = store;
        this.estoque = estoque;
        this.ids = ids;
        this.relogio = relogio;
    }

    @Override
    public void processar(ContextoMensagem ctx, ComandoExecution comando) {
        if (store.mensagemJaProcessada(ctx.messageId())) {
            LOG.info("Mensagem {} já processada; ignorando (entrega repetida)", ctx.messageId());
            return;
        }
        switch (comando) {
            case ReservarEstoque c -> reservar(ctx, c);
            case LiberarEstoque c -> liberar(ctx, c);
            case ReservaInvalida c -> rejeitar(ctx, c.motivo());
        }
    }

    // ------------------------------------------------------------ RESERVAR_ESTOQUE

    private void reservar(ContextoMensagem ctx, ReservarEstoque cmd) {
        if (ctx.osId() == null) {
            rejeitar(ctx, "OS não informada");
            return;
        }
        Execucao existente = store.buscarPorOs(ctx.osId()).orElse(null);
        if (existente != null && existente.getStatus() != StatusExecucao.LIBERADA) {
            // Pedido repetido (outra mensagem, mesma OS): confirma a reserva que já existe, sem baixar de novo
            LOG.info("OS {} já tem reserva {}; confirmando sem nova baixa", ctx.osId(), existente.getReservaId());
            store.registrarSemMudanca(ctx, List.of(reservado(existente)));
            return;
        }

        List<PecaReservada> pecas = agrupar(cmd.pecas());
        Execucao nova = Execucao.nova(ctx.osId(), ctx.sagaId(), ctx.correlationId(), ids.novo(), pecas,
                cmd.servicos(), agora());

        ResultadoReserva resultado = store.reservar(ctx, nova, List.of(reservado(nova)));
        switch (resultado) {
            case ResultadoReserva.Reservada r -> LOG.info("OS {}: estoque reservado ({})", ctx.osId(), nova.getReservaId());
            case ResultadoReserva.EstoqueInsuficiente r -> {
                List<Map<String, Object>> faltantes = faltantes(pecas);
                LOG.warn("OS {}: estoque insuficiente {}", ctx.osId(), faltantes);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("faltantes", faltantes);
                store.registrarSemMudanca(ctx, List.of(evento("ESTOQUE_INSUFICIENTE", ctx, payload)));
            }
        }
    }

    private void rejeitar(ContextoMensagem ctx, String motivo) {
        LOG.warn("Reserva da OS {} recusada: {}", ctx.osId(), motivo);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("faltantes", List.of());
        payload.put("motivo", motivo);
        store.registrarSemMudanca(ctx, List.of(evento("ESTOQUE_INSUFICIENTE", ctx, payload)));
    }

    /** Peças repetidas no pedido viram uma linha só (o armazém altera cada peça uma vez por operação). */
    private static List<PecaReservada> agrupar(List<PecaReservada> pecas) {
        Map<String, Integer> soma = new LinkedHashMap<>();
        if (pecas != null) {
            for (PecaReservada p : pecas) {
                soma.merge(p.codigo(), p.quantidade(), Integer::sum);
            }
        }
        List<PecaReservada> agrupadas = new ArrayList<>();
        soma.forEach((codigo, qtd) -> agrupadas.add(new PecaReservada(codigo, qtd)));
        return agrupadas;
    }

    private List<Map<String, Object>> faltantes(List<PecaReservada> pecas) {
        Map<String, Integer> disponiveis = new LinkedHashMap<>();
        for (ItemEstoque item : estoque.buscarVarios(pecas.stream().map(PecaReservada::codigo).toList())) {
            disponiveis.put(item.codigo(), item.disponivel());
        }
        List<Map<String, Object>> faltantes = new ArrayList<>();
        for (PecaReservada p : pecas) {
            int disponivel = disponiveis.getOrDefault(p.codigo(), 0);
            if (disponivel < p.quantidade()) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("codigo", p.codigo());
                f.put("solicitado", p.quantidade());
                f.put("disponivel", disponivel);
                faltantes.add(f);
            }
        }
        return faltantes;
    }

    // ------------------------------------------------------------ LIBERAR_ESTOQUE

    private void liberar(ContextoMensagem ctx, LiberarEstoque cmd) {
        Execucao execucao = ctx.osId() == null ? null : store.buscarPorOs(ctx.osId()).orElse(null);
        if (execucao == null) {
            // Nada foi reservado para esta OS: nada a desfazer, e a compensação segue
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("nadaALiberar", true);
            store.registrarSemMudanca(ctx, List.of(evento("ESTOQUE_LIBERADO", ctx, payload)));
            return;
        }
        boolean mudou;
        try {
            mudou = execucao.liberar(agora());
        } catch (RegraDeNegocioException e) {
            // Execução concluída: as peças foram usadas. O orquestrador deve estornar sem liberar.
            LOG.error("LIBERAR_ESTOQUE da OS {} recusado: {}", ctx.osId(), e.getMessage());
            store.registrarSemMudanca(ctx, List.of());
            return;
        }
        if (!mudou) {
            store.registrarSemMudanca(ctx, List.of(liberado(execucao))); // repetição: responde de novo
            return;
        }
        LOG.info("OS {}: reserva {} liberada ({})", ctx.osId(), execucao.getReservaId(), cmd.motivo());
        store.liberar(ctx, execucao, List.of(liberado(execucao)));
    }

    // ------------------------------------------------------------ eventos

    private static EventoSaga reservado(Execucao e) {
        return new EventoSaga("ESTOQUE_RESERVADO", e.getOsId(), e.getSagaId(), e.getCorrelationId(),
                Map.of("reservaId", e.getReservaId()));
    }

    private static EventoSaga liberado(Execucao e) {
        return new EventoSaga("ESTOQUE_LIBERADO", e.getOsId(), e.getSagaId(), e.getCorrelationId(),
                Map.of("reservaId", e.getReservaId()));
    }

    private static EventoSaga evento(String tipo, ContextoMensagem ctx, Map<String, Object> payload) {
        return new EventoSaga(tipo, ctx.osId(), ctx.sagaId(), ctx.correlationId(), payload);
    }

    private Instant agora() {
        return Instant.now(relogio);
    }
}
