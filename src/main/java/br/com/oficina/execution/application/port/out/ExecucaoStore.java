package br.com.oficina.execution.application.port.out;

import br.com.oficina.execution.application.port.in.ContextoMensagem;
import br.com.oficina.execution.domain.model.Execucao;
import br.com.oficina.execution.domain.model.StatusExecucao;

import java.util.List;
import java.util.Optional;

/**
 * Armazém das execuções e do estoque reservado. Cada operação de escrita é <b>atômica</b>: o novo estado,
 * a baixa/devolução do estoque, a marca de mensagem processada e os eventos de resposta (outbox) são
 * gravados juntos ou nenhum deles.
 */
public interface ExecucaoStore {

    sealed interface ResultadoReserva {
        record Reservada() implements ResultadoReserva {
        }

        /** Alguma peça não tinha quantidade suficiente (ou não existe); nada foi alterado. */
        record EstoqueInsuficiente() implements ResultadoReserva {
        }
    }

    boolean mensagemJaProcessada(String messageId);

    Optional<Execucao> buscarPorOs(Long osId);

    List<Execucao> listarPorStatus(StatusExecucao status);

    /**
     * Baixa o estoque de todas as peças e grava a execução na fila, só se houver quantidade para tudo.
     * Substitui uma execução anterior da mesma OS apenas se ela estiver LIBERADA.
     * Lança {@code ConflitoException} se outra operação ganhou a corrida (a mensagem volta à fila).
     */
    ResultadoReserva reservar(ContextoMensagem ctx, Execucao nova, List<EventoSaga> eventosSeReservar);

    /** Só marca a mensagem como processada e grava os eventos (respostas sem mudança de estado). */
    void registrarSemMudanca(ContextoMensagem ctx, List<EventoSaga> eventos);

    /** Grava a execução como LIBERADA e devolve as peças ao estoque. */
    void liberar(ContextoMensagem ctx, Execucao liberada, List<EventoSaga> eventos);

    /**
     * Grava a mudança de estado feita por chamada REST (sem mensagem de origem). Usa o número de versão da
     * execução lida para detectar alteração concorrente: lança {@code ConflitoException}.
     */
    void atualizar(Execucao execucao, List<EventoSaga> eventos);
}
