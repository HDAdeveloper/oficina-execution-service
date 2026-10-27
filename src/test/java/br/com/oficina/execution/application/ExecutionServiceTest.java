package br.com.oficina.execution.application;

import br.com.oficina.execution.application.Fakes.EstoqueMem;
import br.com.oficina.execution.application.Fakes.IdsSequenciais;
import br.com.oficina.execution.application.Fakes.StoreMem;
import br.com.oficina.execution.application.port.in.ComandoExecution.LiberarEstoque;
import br.com.oficina.execution.application.port.in.ComandoExecution.ReservaInvalida;
import br.com.oficina.execution.application.port.in.ComandoExecution.ReservarEstoque;
import br.com.oficina.execution.application.port.in.ContextoMensagem;
import br.com.oficina.execution.application.service.ExecutionService;
import br.com.oficina.execution.domain.exception.ConflitoException;
import br.com.oficina.execution.domain.model.PecaReservada;
import br.com.oficina.execution.domain.model.StatusExecucao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionServiceTest {

    private static final Long OS = 42L;

    private EstoqueMem estoque;
    private StoreMem store;
    private ExecutionService service;
    private int mensagens;

    @BeforeEach
    void preparar() {
        estoque = new EstoqueMem();
        estoque.por("FILTRO", 10);
        estoque.por("OLEO", 4);
        store = new StoreMem(estoque);
        Clock relogio = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC);
        service = new ExecutionService(store, estoque, new IdsSequenciais(), relogio);
        mensagens = 0;
    }

    private ContextoMensagem novaMensagem() {
        return new ContextoMensagem("msg-" + (++mensagens), "saga-1", OS, "corr-1");
    }

    private static ReservarEstoque pedido(PecaReservada... pecas) {
        return new ReservarEstoque(List.of(pecas), List.of("Revisão completa"));
    }

    private void reservar2Filtros() {
        service.processar(novaMensagem(), pedido(new PecaReservada("FILTRO", 2)));
    }

    // ------------------------------------------------------------ RESERVAR_ESTOQUE

    @Test
    void reservaBaixaOEstoqueEColocaNaFila() {
        service.processar(novaMensagem(), pedido(new PecaReservada("FILTRO", 2), new PecaReservada("OLEO", 1)));

        assertThat(estoque.disponivel("FILTRO")).isEqualTo(8);
        assertThat(estoque.disponivel("OLEO")).isEqualTo(3);
        var e = store.execucoes.get(OS);
        assertThat(e.getStatus()).isEqualTo(StatusExecucao.NA_FILA);
        assertThat(e.getReservaId()).isEqualTo("res-1");
        assertThat(e.getServicos()).containsExactly("Revisão completa");
        assertThat(store.tipos()).containsExactly("ESTOQUE_RESERVADO");
        assertThat(store.ultimo().payload()).containsEntry("reservaId", "res-1");
        assertThat(store.ultimo().osId()).isEqualTo(OS);
        assertThat(store.ultimo().sagaId()).isEqualTo("saga-1");
        assertThat(store.ultimo().correlationId()).isEqualTo("corr-1");
    }

    @Test
    void mensagemRepetidaEhIgnorada() {
        ContextoMensagem mesma = novaMensagem();
        var cmd = pedido(new PecaReservada("FILTRO", 2));

        service.processar(mesma, cmd);
        service.processar(mesma, cmd);

        assertThat(estoque.disponivel("FILTRO")).isEqualTo(8);
        assertThat(store.tipos()).containsExactly("ESTOQUE_RESERVADO");
    }

    @Test
    void novoPedidoParaAMesmaOsConfirmaSemBaixarDeNovo() {
        reservar2Filtros();

        service.processar(novaMensagem(), pedido(new PecaReservada("FILTRO", 2)));

        assertThat(estoque.disponivel("FILTRO")).isEqualTo(8);
        assertThat(store.tipos()).containsExactly("ESTOQUE_RESERVADO", "ESTOQUE_RESERVADO");
        assertThat(store.ultimo().payload()).containsEntry("reservaId", "res-1");
    }

    @Test
    void estoqueInsuficienteNaoAlteraNadaEListaOsFaltantes() {
        service.processar(novaMensagem(),
                pedido(new PecaReservada("FILTRO", 2), new PecaReservada("OLEO", 9), new PecaReservada("FANTASMA", 1)));

        assertThat(estoque.disponivel("FILTRO")).isEqualTo(10); // a peça suficiente também não foi baixada
        assertThat(estoque.disponivel("OLEO")).isEqualTo(4);
        assertThat(store.execucoes).isEmpty();
        assertThat(store.tipos()).containsExactly("ESTOQUE_INSUFICIENTE");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> faltantes = (List<Map<String, Object>>) store.ultimo().payload().get("faltantes");
        assertThat(faltantes).hasSize(2);
        assertThat(faltantes.get(0)).containsEntry("codigo", "OLEO").containsEntry("solicitado", 9)
                .containsEntry("disponivel", 4);
        assertThat(faltantes.get(1)).containsEntry("codigo", "FANTASMA").containsEntry("disponivel", 0);
    }

    @Test
    void pecasRepetidasNoPedidoSaoSomadas() {
        service.processar(novaMensagem(), pedido(new PecaReservada("FILTRO", 2), new PecaReservada("filtro", 3)));

        assertThat(estoque.disponivel("FILTRO")).isEqualTo(5);
        assertThat(store.execucoes.get(OS).getPecas()).containsExactly(new PecaReservada("FILTRO", 5));
    }

    @Test
    void osSemPecasEntraNaFilaSemMexerNoEstoque() {
        service.processar(novaMensagem(), new ReservarEstoque(List.of(), List.of("Alinhamento")));
        service.processar(new ContextoMensagem("outra", "s", 43L, "c"), new ReservarEstoque(null, null));

        assertThat(store.tipos()).containsExactly("ESTOQUE_RESERVADO", "ESTOQUE_RESERVADO");
        assertThat(estoque.disponivel("FILTRO")).isEqualTo(10);
        assertThat(store.execucoes).hasSize(2);
    }

    @Test
    void pedidoInvalidoRespondeEstoqueInsuficienteComOMotivo() {
        service.processar(novaMensagem(), new ReservaInvalida("Quantidade da peça X deve ser maior que zero"));

        assertThat(store.tipos()).containsExactly("ESTOQUE_INSUFICIENTE");
        assertThat(store.ultimo().payload()).containsEntry("motivo", "Quantidade da peça X deve ser maior que zero");
        assertThat((List<?>) store.ultimo().payload().get("faltantes")).isEmpty();
        assertThat(store.execucoes).isEmpty();
    }

    @Test
    void osSemIdEhRecusada() {
        service.processar(new ContextoMensagem("m", "s", null, "c"), pedido(new PecaReservada("FILTRO", 1)));

        assertThat(store.tipos()).containsExactly("ESTOQUE_INSUFICIENTE");
        assertThat(store.ultimo().payload()).containsEntry("motivo", "OS não informada");
    }

    @Test
    void perderACorridaFazAMensagemVoltarParaAFila() {
        store.perderCorrida = true;
        ContextoMensagem ctx = novaMensagem();

        assertThatThrownBy(() -> service.processar(ctx, pedido(new PecaReservada("FILTRO", 2))))
                .isInstanceOf(ConflitoException.class);

        assertThat(store.mensagemJaProcessada(ctx.messageId())).isFalse(); // será tentada de novo
        assertThat(store.eventos).isEmpty();
    }

    // ------------------------------------------------------------ LIBERAR_ESTOQUE

    @Test
    void liberarDevolveAsPecasETiraDaFila() {
        reservar2Filtros();

        service.processar(novaMensagem(), new LiberarEstoque("falha na execução"));

        assertThat(estoque.disponivel("FILTRO")).isEqualTo(10);
        assertThat(store.execucoes.get(OS).getStatus()).isEqualTo(StatusExecucao.LIBERADA);
        assertThat(store.tipos()).containsExactly("ESTOQUE_RESERVADO", "ESTOQUE_LIBERADO");
        assertThat(store.ultimo().payload()).containsEntry("reservaId", "res-1");
    }

    @Test
    void liberarDepoisDeFalhaDeExecucaoFuncionaEhACompensacaoC() {
        reservar2Filtros();
        var e = store.buscarPorOs(OS).orElseThrow();
        e.iniciar(Instant.parse("2026-10-23T12:05:00Z"));
        e.falhar("motor travou", Instant.parse("2026-10-23T12:10:00Z"));
        store.atualizar(e, List.of());

        service.processar(novaMensagem(), new LiberarEstoque("compensação"));

        assertThat(estoque.disponivel("FILTRO")).isEqualTo(10);
        assertThat(store.execucoes.get(OS).getStatus()).isEqualTo(StatusExecucao.LIBERADA);
    }

    @Test
    void liberarDeNovoResponderDeNovoSemDevolverEmDobro() {
        reservar2Filtros();
        service.processar(novaMensagem(), new LiberarEstoque("x"));

        service.processar(novaMensagem(), new LiberarEstoque("x"));

        assertThat(estoque.disponivel("FILTRO")).isEqualTo(10);
        assertThat(store.tipos()).containsExactly("ESTOQUE_RESERVADO", "ESTOQUE_LIBERADO", "ESTOQUE_LIBERADO");
    }

    @Test
    void liberarOQueNuncaFoiReservadoNaoEhErro() {
        service.processar(novaMensagem(), new LiberarEstoque("x"));
        service.processar(new ContextoMensagem("sem-os", "s", null, "c"), new LiberarEstoque("x"));

        assertThat(store.tipos()).containsExactly("ESTOQUE_LIBERADO", "ESTOQUE_LIBERADO");
        assertThat(store.ultimo().payload()).containsEntry("nadaALiberar", true);
    }

    @Test
    void execucaoConcluidaNaoLiberaNemResponde() {
        reservar2Filtros();
        var e = store.buscarPorOs(OS).orElseThrow();
        e.iniciar(Instant.parse("2026-10-23T12:05:00Z"));
        e.finalizar(Instant.parse("2026-10-23T12:10:00Z"));
        store.atualizar(e, List.of());
        ContextoMensagem ctx = novaMensagem();

        service.processar(ctx, new LiberarEstoque("x"));

        assertThat(estoque.disponivel("FILTRO")).isEqualTo(8); // as peças foram usadas
        assertThat(store.execucoes.get(OS).getStatus()).isEqualTo(StatusExecucao.CONCLUIDA);
        assertThat(store.tipos()).containsExactly("ESTOQUE_RESERVADO");
        assertThat(store.mensagemJaProcessada(ctx.messageId())).isTrue();
    }

    @Test
    void depoisDeLiberadaAOsPodeReservarDeNovoComOutraReserva() {
        reservar2Filtros();
        service.processar(novaMensagem(), new LiberarEstoque("x"));

        service.processar(novaMensagem(), pedido(new PecaReservada("FILTRO", 1)));

        assertThat(estoque.disponivel("FILTRO")).isEqualTo(9);
        var e = store.execucoes.get(OS);
        assertThat(e.getStatus()).isEqualTo(StatusExecucao.NA_FILA);
        assertThat(e.getReservaId()).isEqualTo("res-2");
        assertThat(store.ultimo().payload()).containsEntry("reservaId", "res-2");
    }
}
