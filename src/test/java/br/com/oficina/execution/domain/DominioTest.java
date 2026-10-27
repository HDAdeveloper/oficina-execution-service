package br.com.oficina.execution.domain;

import br.com.oficina.execution.domain.exception.RegraDeNegocioException;
import br.com.oficina.execution.domain.model.Execucao;
import br.com.oficina.execution.domain.model.ItemEstoque;
import br.com.oficina.execution.domain.model.PecaReservada;
import br.com.oficina.execution.domain.model.StatusExecucao;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DominioTest {

    private static final Instant T0 = Instant.parse("2026-10-23T12:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);

    private static Execucao nova() {
        return Execucao.nova(42L, "saga-1", "corr-1", "res-1", List.of(new PecaReservada("p1", 2)),
                List.of("Revisão"), T0);
    }

    // ------------------------------------------------------------ peça e estoque

    @Test
    void codigoDaPecaEhNormalizado() {
        assertThat(new PecaReservada("  pec-1 ", 3).codigo()).isEqualTo("PEC-1");
        assertThat(new ItemEstoque("pec-1", " Filtro ", 5).nome()).isEqualTo("Filtro");
        assertThat(ItemEstoque.normalizarCodigo(" abc ")).isEqualTo("ABC");
    }

    @Test
    void dadosInvalidosSaoRecusados() {
        assertThatThrownBy(() -> new PecaReservada(" ", 1)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> new PecaReservada(null, 1)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> new PecaReservada("P1", 0)).hasMessageContaining("maior que zero");
        assertThatThrownBy(() -> new ItemEstoque("P1", "  ", 1)).hasMessageContaining("Nome");
        assertThatThrownBy(() -> new ItemEstoque("P1", null, 1)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> new ItemEstoque("P1", "Filtro", -1)).hasMessageContaining("negativa");
    }

    // ------------------------------------------------------------ execução

    @Test
    void nasceNaFila() {
        Execucao e = nova();

        assertThat(e.getStatus()).isEqualTo(StatusExecucao.NA_FILA);
        assertThat(e.getStatus().naFilaDeTrabalho()).isTrue();
        assertThat(e.getVersao()).isZero();
        assertThat(e.getPecas()).containsExactly(new PecaReservada("P1", 2));
        assertThat(e.getServicos()).containsExactly("Revisão");
        assertThat(e.getSagaId()).isEqualTo("saga-1");
        assertThat(e.getCorrelationId()).isEqualTo("corr-1");
        assertThat(e.getReservaId()).isEqualTo("res-1");
    }

    @Test
    void criacaoExigeDadosMinimos() {
        assertThatThrownBy(() -> Execucao.nova(null, "s", "c", "r", List.of(), null, T0))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> Execucao.nova(1L, "s", "c", " ", List.of(), null, T0))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> Execucao.nova(1L, "s", "c", "r", null, null, T0))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThat(Execucao.nova(1L, "s", "c", "r", List.of(), null, T0).getServicos()).isEmpty();
    }

    @Test
    void caminhoFeliz() {
        Execucao e = nova();

        e.iniciar(T1);
        assertThat(e.getStatus()).isEqualTo(StatusExecucao.EM_REPARO);
        assertThat(e.getIniciadaEm()).isEqualTo(T1);

        e.finalizar(T1.plusSeconds(60));
        assertThat(e.getStatus()).isEqualTo(StatusExecucao.CONCLUIDA);
        assertThat(e.getFinalizadaEm()).isEqualTo(T1.plusSeconds(60));
        assertThat(e.getStatus().naFilaDeTrabalho()).isFalse();
    }

    @Test
    void transicoesForaDeOrdemSaoRecusadas() {
        Execucao e = nova();
        assertThatThrownBy(() -> e.finalizar(T1)).hasMessageContaining("NA_FILA");

        e.iniciar(T1);
        assertThatThrownBy(() -> e.iniciar(T1)).hasMessageContaining("EM_REPARO");

        e.finalizar(T1);
        assertThatThrownBy(() -> e.falhar("x", T1)).hasMessageContaining("CONCLUIDA");
    }

    @Test
    void falhaGuardaOMotivoOuUmPadrao() {
        Execucao a = nova();
        a.falhar("  motor travou ", T1);
        assertThat(a.getStatus()).isEqualTo(StatusExecucao.FALHOU);
        assertThat(a.getMotivoFalha()).isEqualTo("motor travou");

        Execucao b = nova();
        b.iniciar(T1);
        b.falhar(" ", T1);
        assertThat(b.getMotivoFalha()).isEqualTo("Falha na execução");
    }

    @Test
    void liberarFuncionaDeQualquerEstadoAntesDaConclusaoEEhIdempotente() {
        Execucao naFila = nova();
        assertThat(naFila.liberar(T1)).isTrue();
        assertThat(naFila.getStatus()).isEqualTo(StatusExecucao.LIBERADA);
        assertThat(naFila.liberar(T1)).isFalse();

        Execucao falhou = nova();
        falhou.falhar("x", T1);
        assertThat(falhou.liberar(T1)).isTrue();
    }

    @Test
    void execucaoConcluidaNaoPodeSerLiberada() {
        Execucao e = nova();
        e.iniciar(T1);
        e.finalizar(T1);

        assertThatThrownBy(() -> e.liberar(T1)).hasMessageContaining("concluída");
    }

    @Test
    void listasSaoSomenteLeitura() {
        Execucao e = nova();
        assertThatThrownBy(() -> e.getPecas().add(new PecaReservada("X", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> e.getServicos().add("X")).isInstanceOf(UnsupportedOperationException.class);
    }
}
