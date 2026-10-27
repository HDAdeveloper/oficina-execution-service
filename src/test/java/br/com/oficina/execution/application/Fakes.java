package br.com.oficina.execution.application;

import br.com.oficina.execution.application.port.in.ContextoMensagem;
import br.com.oficina.execution.application.port.out.EstoqueRepository;
import br.com.oficina.execution.application.port.out.EventoSaga;
import br.com.oficina.execution.application.port.out.ExecucaoStore;
import br.com.oficina.execution.application.port.out.GeradorId;
import br.com.oficina.execution.domain.exception.ConflitoException;
import br.com.oficina.execution.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.execution.domain.model.Execucao;
import br.com.oficina.execution.domain.model.ItemEstoque;
import br.com.oficina.execution.domain.model.PecaReservada;
import br.com.oficina.execution.domain.model.StatusExecucao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Implementações em memória das portas de saída, com a mesma semântica atômica do armazém real. */
final class Fakes {

    private Fakes() {
    }

    static class EstoqueMem implements EstoqueRepository {
        final Map<String, ItemEstoque> dados = new LinkedHashMap<>();

        void por(String codigo, int disponivel) {
            dados.put(codigo, new ItemEstoque(codigo, "Peça " + codigo, disponivel));
        }

        int disponivel(String codigo) {
            return dados.get(codigo).disponivel();
        }

        @Override
        public Optional<ItemEstoque> buscar(String codigo) {
            return Optional.ofNullable(dados.get(codigo));
        }

        @Override
        public List<ItemEstoque> buscarVarios(Collection<String> codigos) {
            return codigos.stream().map(dados::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public List<ItemEstoque> listar() {
            return new ArrayList<>(dados.values());
        }

        @Override
        public void criar(ItemEstoque item) {
            if (dados.containsKey(item.codigo())) {
                throw new ConflitoException("Já existe peça " + item.codigo());
            }
            dados.put(item.codigo(), item);
        }

        @Override
        public ItemEstoque repor(String codigo, int quantidade) {
            ItemEstoque atual = dados.get(codigo);
            if (atual == null) {
                throw new RecursoNaoEncontradoException("Peça não encontrada: " + codigo);
            }
            ItemEstoque novo = new ItemEstoque(codigo, atual.nome(), atual.disponivel() + quantidade);
            dados.put(codigo, novo);
            return novo;
        }
    }

    static class StoreMem implements ExecucaoStore {
        final EstoqueMem estoque;
        final Map<Long, Execucao> execucoes = new LinkedHashMap<>();
        final Set<String> processadas = new HashSet<>();
        final List<EventoSaga> eventos = new ArrayList<>();
        /** Se preenchido, a próxima reserva perde a corrida contra outra operação. */
        boolean perderCorrida;

        StoreMem(EstoqueMem estoque) {
            this.estoque = estoque;
        }

        List<String> tipos() {
            return eventos.stream().map(EventoSaga::type).toList();
        }

        EventoSaga ultimo() {
            return eventos.get(eventos.size() - 1);
        }

        private static Execucao copia(Execucao e, long versao) {
            return Execucao.reconstituir(e.getOsId(), e.getSagaId(), e.getCorrelationId(), e.getReservaId(),
                    e.getPecas(), e.getServicos(), e.getStatus(), e.getMotivoFalha(), e.getCriadoEm(),
                    e.getIniciadaEm(), e.getFinalizadaEm(), e.getAtualizadoEm(), versao);
        }

        @Override
        public boolean mensagemJaProcessada(String messageId) {
            return processadas.contains(messageId);
        }

        @Override
        public Optional<Execucao> buscarPorOs(Long osId) {
            Execucao e = execucoes.get(osId);
            return e == null ? Optional.empty() : Optional.of(copia(e, e.getVersao()));
        }

        @Override
        public List<Execucao> listarPorStatus(StatusExecucao status) {
            return execucoes.values().stream().filter(e -> e.getStatus() == status)
                    .map(e -> copia(e, e.getVersao())).toList();
        }

        @Override
        public ResultadoReserva reservar(ContextoMensagem ctx, Execucao nova, List<EventoSaga> eventosSeReservar) {
            Execucao atual = execucoes.get(nova.getOsId());
            if (perderCorrida || (atual != null && atual.getStatus() != StatusExecucao.LIBERADA)) {
                throw new ConflitoException("OS " + nova.getOsId() + " foi reservada por outra operação");
            }
            for (PecaReservada p : nova.getPecas()) {
                ItemEstoque item = estoque.dados.get(p.codigo());
                if (item == null || item.disponivel() < p.quantidade()) {
                    return new ResultadoReserva.EstoqueInsuficiente(); // nada foi alterado
                }
            }
            for (PecaReservada p : nova.getPecas()) {
                ItemEstoque item = estoque.dados.get(p.codigo());
                estoque.dados.put(p.codigo(), new ItemEstoque(p.codigo(), item.nome(), item.disponivel() - p.quantidade()));
            }
            execucoes.put(nova.getOsId(), copia(nova, 1));
            processadas.add(ctx.messageId());
            eventos.addAll(eventosSeReservar);
            return new ResultadoReserva.Reservada();
        }

        @Override
        public void registrarSemMudanca(ContextoMensagem ctx, List<EventoSaga> evs) {
            processadas.add(ctx.messageId());
            eventos.addAll(evs);
        }

        @Override
        public void liberar(ContextoMensagem ctx, Execucao liberada, List<EventoSaga> evs) {
            verificarVersao(liberada);
            for (PecaReservada p : liberada.getPecas()) {
                estoque.repor(p.codigo(), p.quantidade());
            }
            execucoes.put(liberada.getOsId(), copia(liberada, liberada.getVersao() + 1));
            processadas.add(ctx.messageId());
            eventos.addAll(evs);
        }

        @Override
        public void atualizar(Execucao execucao, List<EventoSaga> evs) {
            verificarVersao(execucao);
            execucoes.put(execucao.getOsId(), copia(execucao, execucao.getVersao() + 1));
            eventos.addAll(evs);
        }

        private void verificarVersao(Execucao e) {
            Execucao atual = execucoes.get(e.getOsId());
            if (atual == null || atual.getVersao() != e.getVersao()) {
                throw new ConflitoException("Execução da OS " + e.getOsId() + " foi alterada por outra operação");
            }
        }
    }

    static class IdsSequenciais implements GeradorId {
        private int n;

        @Override
        public String novo() {
            return "res-" + (++n);
        }
    }
}
