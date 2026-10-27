package br.com.oficina.execution.application.port.in;

import br.com.oficina.execution.domain.model.PecaReservada;

import java.util.List;

/** Comandos da Saga endereçados ao Execution Service. */
public sealed interface ComandoExecution {

    /** Reserva as peças e coloca a OS na fila de execução. */
    record ReservarEstoque(List<PecaReservada> pecas, List<String> servicos) implements ComandoExecution {
    }

    /** Desfaz a reserva e tira a OS da fila (compensação). */
    record LiberarEstoque(String motivo) implements ComandoExecution {
    }

    /**
     * Pedido de reserva com dados que nunca serão válidos (quantidade zero, código vazio...).
     * Não adianta tentar de novo: responde {@code ESTOQUE_INSUFICIENTE} com o motivo.
     */
    record ReservaInvalida(String motivo) implements ComandoExecution {
    }
}
