package br.com.oficina.execution.application;

import br.com.oficina.execution.application.Fakes.EstoqueMem;
import br.com.oficina.execution.application.Fakes.StoreMem;
import br.com.oficina.execution.application.service.GestaoEstoqueService;
import br.com.oficina.execution.application.service.GestaoExecucoesService;
import br.com.oficina.execution.domain.exception.ConflitoException;
import br.com.oficina.execution.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.execution.domain.exception.RegraDeNegocioException;
import br.com.oficina.execution.domain.model.Execucao;
import br.com.oficina.execution.domain.model.ItemEstoque;
import br.com.oficina.execution.domain.model.PecaReservada;
import br.com.oficina.execution.domain.model.StatusExecucao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GestaoServicesTest {

    private static final Instant T0 = Instant.parse("2026-10-23T12:00:00Z");

    private EstoqueMem estoque;
    private StoreMem store;
    private GestaoExecucoesService execucoes;
    private GestaoEstoqueService gestaoEstoque;

    @BeforeEach
    void preparar() {
        estoque = new EstoqueMem();
        store = new StoreMem(estoque);
        Clock relogio = Clock.fixed(T0.plusSeconds(300), ZoneOffset.UTC);
        execucoes = new GestaoExecucoesService(store, relogio);
        gestaoEstoque = new GestaoEstoqueService(estoque);
    }

    private void naFila(long os, Instant criadaEm) {
        store.execucoes.put(os, Execucao.reconstituir(os, "saga-" + os, "corr-" + os, "res-" + os,
                List.of(new PecaReservada("P1", 1)), List.of(), StatusExecucao.NA_FILA, null, criadaEm, null, null,
                criadaEm, 1));
    }

    // ------------------------------------------------------------ execuções

    @Test
    void iniciarMudaOEstadoEPublicaOEventoComASagaOriginal() {
        naFila(7, T0);

        Execucao e = execucoes.iniciar(7L);

        assertThat(e.getStatus()).isEqualTo(StatusExecucao.EM_REPARO);
        assertThat(store.execucoes.get(7L).getStatus()).isEqualTo(StatusExecucao.EM_REPARO);
        assertThat(store.tipos()).containsExactly("EXECUCAO_INICIADA");
        assertThat(store.ultimo().sagaId()).isEqualTo("saga-7");
        assertThat(store.ultimo().correlationId()).isEqualTo("corr-7");
        assertThat(store.ultimo().osId()).isEqualTo(7L);
        assertThat(store.ultimo().payload()).containsEntry("iniciadaEm", "2026-10-23T12:05:00Z");
    }

    @Test
    void finalizarPublicaExecucaoFinalizada() {
        naFila(7, T0);
        execucoes.iniciar(7L);

        Execucao e = execucoes.finalizar(7L);

        assertThat(e.getStatus()).isEqualTo(StatusExecucao.CONCLUIDA);
        assertThat(store.tipos()).containsExactly("EXECUCAO_INICIADA", "EXECUCAO_FINALIZADA");
        assertThat(store.ultimo().payload()).containsEntry("finalizadaEm", "2026-10-23T12:05:00Z");
    }

    @Test
    void falharPublicaExecucaoFalhouComOMotivo() {
        naFila(7, T0);

        Execucao e = execucoes.falhar(7L, "peça com defeito");

        assertThat(e.getStatus()).isEqualTo(StatusExecucao.FALHOU);
        assertThat(store.tipos()).containsExactly("EXECUCAO_FALHOU");
        assertThat(store.ultimo().payload()).containsEntry("motivo", "peça com defeito");
    }

    @Test
    void transicaoInvalidaNaoPublicaNada() {
        naFila(7, T0);

        assertThatThrownBy(() -> execucoes.finalizar(7L)).isInstanceOf(RegraDeNegocioException.class);

        assertThat(store.eventos).isEmpty();
        assertThat(store.execucoes.get(7L).getStatus()).isEqualTo(StatusExecucao.NA_FILA);
    }

    @Test
    void execucaoInexistenteDa404() {
        assertThatThrownBy(() -> execucoes.buscar(99L)).isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("99");
        assertThatThrownBy(() -> execucoes.iniciar(99L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void alteracaoConcorrenteEhDetectada() {
        naFila(7, T0);
        Execucao lida = execucoes.buscar(7L);
        execucoes.iniciar(7L); // outra pessoa mexeu primeiro

        lida.iniciar(T0);
        assertThatThrownBy(() -> store.atualizar(lida, List.of())).isInstanceOf(ConflitoException.class);
    }

    @Test
    void filaTrazSoOQueEstaEmTrabalhoDoMaisAntigoParaOMaisNovo() {
        naFila(3, T0.plusSeconds(30));
        naFila(1, T0);
        naFila(2, T0.plusSeconds(10));
        execucoes.iniciar(2L);
        naFila(4, T0.plusSeconds(5));
        store.execucoes.put(4L, Execucao.reconstituir(4L, "s", "c", "r", List.of(), List.of(),
                StatusExecucao.CONCLUIDA, null, T0, T0, T0, T0, 1));

        List<Long> fila = execucoes.listarFila().stream().map(Execucao::getOsId).toList();

        assertThat(fila).containsExactly(1L, 2L, 3L);
    }

    // ------------------------------------------------------------ estoque

    @Test
    void cadastraPecaComCodigoNormalizado() {
        var item = gestaoEstoque.cadastrar(" pec-1 ", "Filtro de óleo", 12);

        assertThat(item.codigo()).isEqualTo("PEC-1");
        assertThat(gestaoEstoque.buscar("pec-1").disponivel()).isEqualTo(12);
    }

    @Test
    void naoCadastraDuasVezesOMesmoCodigo() {
        gestaoEstoque.cadastrar("P1", "Filtro", 1);

        assertThatThrownBy(() -> gestaoEstoque.cadastrar("p1", "Outro", 5)).isInstanceOf(ConflitoException.class);
    }

    @Test
    void reporSomaAoDisponivel() {
        gestaoEstoque.cadastrar("P1", "Filtro", 5);

        assertThat(gestaoEstoque.repor("p1", 3).disponivel()).isEqualTo(8);
    }

    @Test
    void reposicaoInvalidaOuDePecaInexistenteEhRecusada() {
        gestaoEstoque.cadastrar("P1", "Filtro", 5);

        assertThatThrownBy(() -> gestaoEstoque.repor("P1", 0)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> gestaoEstoque.repor("NAO", 1)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> gestaoEstoque.buscar("NAO")).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void listaOrdenadaPorCodigo() {
        gestaoEstoque.cadastrar("B", "Segunda", 1);
        gestaoEstoque.cadastrar("A", "Primeira", 1);

        assertThat(gestaoEstoque.listar()).extracting(ItemEstoque::codigo).containsExactly("A", "B");
    }
}
