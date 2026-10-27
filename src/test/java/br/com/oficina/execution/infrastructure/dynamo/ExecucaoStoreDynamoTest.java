package br.com.oficina.execution.infrastructure.dynamo;

import br.com.oficina.execution.application.port.in.ContextoMensagem;
import br.com.oficina.execution.application.port.out.EstoqueRepository;
import br.com.oficina.execution.application.port.out.EventoSaga;
import br.com.oficina.execution.application.port.out.ExecucaoStore;
import br.com.oficina.execution.application.port.out.ExecucaoStore.ResultadoReserva;
import br.com.oficina.execution.domain.exception.ConflitoException;
import br.com.oficina.execution.domain.model.Execucao;
import br.com.oficina.execution.domain.model.ItemEstoque;
import br.com.oficina.execution.domain.model.PecaReservada;
import br.com.oficina.execution.domain.model.StatusExecucao;
import br.com.oficina.execution.infrastructure.dynamo.OutboxDynamo.EventoOutbox;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ExecucaoStoreDynamoTest extends DynamoTestBase {

    private static final Instant T0 = Instant.parse("2026-10-23T12:00:00Z");

    @Autowired ExecucaoStore store;
    @Autowired EstoqueRepository estoque;
    @Autowired OutboxDynamo outbox;

    private static String codigo() {
        return "S" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String peca(int quantidade) {
        String c = codigo();
        estoque.criar(new ItemEstoque(c, "Peça " + c, quantidade));
        return c;
    }

    private int disponivel(String codigo) {
        return estoque.buscar(codigo).orElseThrow().disponivel();
    }

    private static ContextoMensagem ctx(long os) {
        return new ContextoMensagem(UUID.randomUUID().toString(), "saga-" + os, os, "corr-" + os);
    }

    private static Execucao nova(long os, Instant criadoEm, PecaReservada... pecas) {
        return Execucao.nova(os, "saga-" + os, "corr-" + os, UUID.randomUUID().toString(), List.of(pecas),
                List.of("Revisão"), criadoEm);
    }

    private static EventoSaga evento(String tipo, long os) {
        return new EventoSaga(tipo, os, "saga-" + os, "corr-" + os, Map.of("k", "v"));
    }

    private List<String> tiposPendentes(long os) {
        return outbox.listarPendentes(1000).stream().filter(e -> e.osId() != null && e.osId() == os)
                .map(EventoOutbox::tipo).toList();
    }

    // ------------------------------------------------------------ reservar

    @Test
    void reservaBaixaEstoqueGravaExecucaoMensagemEEvento() {
        long os = OS.incrementAndGet();
        String a = peca(10);
        String b = peca(4);
        ContextoMensagem ctx = ctx(os);

        var r = store.reservar(ctx, nova(os, T0, new PecaReservada(a, 3), new PecaReservada(b, 1)),
                List.of(evento("ESTOQUE_RESERVADO", os)));

        assertThat(r).isInstanceOf(ResultadoReserva.Reservada.class);
        assertThat(disponivel(a)).isEqualTo(7);
        assertThat(disponivel(b)).isEqualTo(3);
        Execucao e = store.buscarPorOs(os).orElseThrow();
        assertThat(e.getStatus()).isEqualTo(StatusExecucao.NA_FILA);
        assertThat(e.getPecas()).containsExactly(new PecaReservada(a, 3), new PecaReservada(b, 1));
        assertThat(e.getServicos()).containsExactly("Revisão");
        assertThat(e.getSagaId()).isEqualTo("saga-" + os);
        assertThat(e.getCorrelationId()).isEqualTo("corr-" + os);
        assertThat(e.getCriadoEm()).isEqualTo(T0);
        assertThat(e.getVersao()).isEqualTo(1);
        assertThat(store.mensagemJaProcessada(ctx.messageId())).isTrue();
        assertThat(tiposPendentes(os)).containsExactly("ESTOQUE_RESERVADO");
    }

    @Test
    void faltandoUmaPecaNadaEhAlterado() {
        long os = OS.incrementAndGet();
        String a = peca(5);
        String b = peca(1);
        ContextoMensagem ctx = ctx(os);

        var r = store.reservar(ctx, nova(os, T0, new PecaReservada(a, 2), new PecaReservada(b, 3)),
                List.of(evento("ESTOQUE_RESERVADO", os)));

        assertThat(r).isInstanceOf(ResultadoReserva.EstoqueInsuficiente.class);
        assertThat(disponivel(a)).isEqualTo(5); // a peça que tinha quantidade também ficou intacta
        assertThat(disponivel(b)).isEqualTo(1);
        assertThat(store.buscarPorOs(os)).isEmpty();
        assertThat(store.mensagemJaProcessada(ctx.messageId())).isFalse();
        assertThat(tiposPendentes(os)).isEmpty();
    }

    @Test
    void pecaInexistenteContaComoInsuficiente() {
        long os = OS.incrementAndGet();

        var r = store.reservar(ctx(os), nova(os, T0, new PecaReservada("NAO-EXISTE-" + codigo(), 1)), List.of());

        assertThat(r).isInstanceOf(ResultadoReserva.EstoqueInsuficiente.class);
        assertThat(store.buscarPorOs(os)).isEmpty();
    }

    @Test
    void osSemPecasEntraNaFila() {
        long os = OS.incrementAndGet();

        var r = store.reservar(ctx(os), nova(os, T0), List.of(evento("ESTOQUE_RESERVADO", os)));

        assertThat(r).isInstanceOf(ResultadoReserva.Reservada.class);
        assertThat(store.buscarPorOs(os).orElseThrow().getPecas()).isEmpty();
    }

    @Test
    void mensagemJaRegistradaNaoReservaDeNovo() {
        long os = OS.incrementAndGet();
        String a = peca(10);
        ContextoMensagem ctx = ctx(os);
        store.reservar(ctx, nova(os, T0, new PecaReservada(a, 2)), List.of());

        long outraOs = OS.incrementAndGet();
        assertThatThrownBy(() -> store.reservar(ctx, nova(outraOs, T0, new PecaReservada(a, 2)), List.of()))
                .isInstanceOf(ConflitoException.class);

        assertThat(disponivel(a)).isEqualTo(8);
        assertThat(store.buscarPorOs(outraOs)).isEmpty();
    }

    @Test
    void osJaReservadaSoPodeSerSubstituidaDepoisDeLiberada() {
        long os = OS.incrementAndGet();
        String a = peca(10);
        store.reservar(ctx(os), nova(os, T0, new PecaReservada(a, 2)), List.of());

        assertThatThrownBy(() -> store.reservar(ctx(os), nova(os, T0, new PecaReservada(a, 2)), List.of()))
                .isInstanceOf(ConflitoException.class);
        assertThat(disponivel(a)).isEqualTo(8); // a tentativa rejeitada não baixou nada

        Execucao lida = store.buscarPorOs(os).orElseThrow();
        lida.liberar(T0.plusSeconds(5));
        store.liberar(ctx(os), lida, List.of(evento("ESTOQUE_LIBERADO", os)));
        assertThat(disponivel(a)).isEqualTo(10);

        var r = store.reservar(ctx(os), nova(os, T0.plusSeconds(10), new PecaReservada(a, 1)), List.of());
        assertThat(r).isInstanceOf(ResultadoReserva.Reservada.class);
        assertThat(disponivel(a)).isEqualTo(9);
        assertThat(store.buscarPorOs(os).orElseThrow().getStatus()).isEqualTo(StatusExecucao.NA_FILA);
    }

    @Test
    void duasReservasDaUltimaUnidadeNuncaVencemAsDuas() throws Exception {
        String a = peca(1);
        long os1 = OS.incrementAndGet();
        long os2 = OS.incrementAndGet();
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<ResultadoReserva>> futuros = new ArrayList<>();
            for (long os : new long[]{os1, os2}) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    for (int tentativa = 0; ; tentativa++) {
                        try {
                            return store.reservar(ctx(os), nova(os, T0, new PecaReservada(a, 1)), List.of());
                        } catch (ConflitoException e) {
                            if (tentativa >= 5) {
                                throw e; // conflito transitório do DynamoDB: tenta de novo, como a fila faria
                            }
                        }
                    }
                }));
            }
            largada.countDown();
            int reservadas = 0;
            int insuficientes = 0;
            for (Future<ResultadoReserva> f : futuros) {
                if (f.get() instanceof ResultadoReserva.Reservada) {
                    reservadas++;
                } else {
                    insuficientes++;
                }
            }

            assertThat(reservadas).isEqualTo(1);
            assertThat(insuficientes).isEqualTo(1);
            assertThat(disponivel(a)).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------ liberar / atualizar / registrar

    @Test
    void liberarDevolveEstoqueEIncrementaAVersao() {
        long os = OS.incrementAndGet();
        String a = peca(10);
        store.reservar(ctx(os), nova(os, T0, new PecaReservada(a, 4)), List.of());
        Execucao lida = store.buscarPorOs(os).orElseThrow();
        lida.liberar(T0.plusSeconds(1));
        ContextoMensagem ctx = ctx(os);

        store.liberar(ctx, lida, List.of(evento("ESTOQUE_LIBERADO", os)));

        assertThat(disponivel(a)).isEqualTo(10);
        Execucao depois = store.buscarPorOs(os).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusExecucao.LIBERADA);
        assertThat(depois.getVersao()).isEqualTo(2);
        assertThat(store.mensagemJaProcessada(ctx.messageId())).isTrue();
        assertThat(tiposPendentes(os)).contains("ESTOQUE_LIBERADO");
    }

    @Test
    void liberarComExecucaoDesatualizadaNaoDevolveEstoque() {
        long os = OS.incrementAndGet();
        String a = peca(10);
        store.reservar(ctx(os), nova(os, T0, new PecaReservada(a, 4)), List.of());
        Execucao antiga = store.buscarPorOs(os).orElseThrow();
        Execucao outra = store.buscarPorOs(os).orElseThrow();
        outra.iniciar(T0.plusSeconds(1));
        store.atualizar(outra, List.of()); // mudou a versão no banco

        antiga.liberar(T0.plusSeconds(2));
        ContextoMensagem ctx = ctx(os);
        assertThatThrownBy(() -> store.liberar(ctx, antiga, List.of())).isInstanceOf(ConflitoException.class);

        assertThat(disponivel(a)).isEqualTo(6); // continua reservado
        assertThat(store.mensagemJaProcessada(ctx.messageId())).isFalse();
    }

    @Test
    void atualizarGravaOEstadoEOEventoJuntos() {
        long os = OS.incrementAndGet();
        store.reservar(ctx(os), nova(os, T0), List.of());
        Execucao e = store.buscarPorOs(os).orElseThrow();
        e.iniciar(T0.plusSeconds(30));

        store.atualizar(e, List.of(evento("EXECUCAO_INICIADA", os)));

        Execucao depois = store.buscarPorOs(os).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusExecucao.EM_REPARO);
        assertThat(depois.getIniciadaEm()).isEqualTo(T0.plusSeconds(30));
        assertThat(depois.getVersao()).isEqualTo(2);
        assertThat(tiposPendentes(os)).containsExactly("EXECUCAO_INICIADA");
    }

    @Test
    void atualizarComVersaoAntigaEhRecusadoESemEvento() {
        long os = OS.incrementAndGet();
        store.reservar(ctx(os), nova(os, T0), List.of());
        Execucao primeira = store.buscarPorOs(os).orElseThrow();
        Execucao segunda = store.buscarPorOs(os).orElseThrow();
        primeira.iniciar(T0.plusSeconds(1));
        store.atualizar(primeira, List.of());

        segunda.falhar("outra pessoa", T0.plusSeconds(2));
        assertThatThrownBy(() -> store.atualizar(segunda, List.of(evento("EXECUCAO_FALHOU", os))))
                .isInstanceOf(ConflitoException.class);

        assertThat(store.buscarPorOs(os).orElseThrow().getStatus()).isEqualTo(StatusExecucao.EM_REPARO);
        assertThat(tiposPendentes(os)).doesNotContain("EXECUCAO_FALHOU");
    }

    @Test
    void registrarSemMudancaMarcaAMensagemEGravaOsEventosEmOrdem() {
        long os = OS.incrementAndGet();
        ContextoMensagem ctx = ctx(os);

        store.registrarSemMudanca(ctx, List.of(evento("PRIMEIRO", os), evento("SEGUNDO", os), evento("TERCEIRO", os)));

        assertThat(store.mensagemJaProcessada(ctx.messageId())).isTrue();
        assertThat(tiposPendentes(os)).containsExactly("PRIMEIRO", "SEGUNDO", "TERCEIRO");
        assertThatThrownBy(() -> store.registrarSemMudanca(ctx, List.of(evento("REPETIDO", os))))
                .isInstanceOf(ConflitoException.class);
        assertThat(tiposPendentes(os)).doesNotContain("REPETIDO");
    }

    @Test
    void registrarSemEventosSoMarcaAMensagem() {
        ContextoMensagem ctx = ctx(OS.incrementAndGet());

        store.registrarSemMudanca(ctx, List.of());

        assertThat(store.mensagemJaProcessada(ctx.messageId())).isTrue();
    }

    @Test
    void mensagemDesconhecidaNaoEstaProcessada() {
        assertThat(store.mensagemJaProcessada(UUID.randomUUID().toString())).isFalse();
        assertThat(store.buscarPorOs(-1L)).isEmpty();
    }

    // ------------------------------------------------------------ fila

    @Test
    void listaPorStatusDaMaisAntigaParaAMaisNova() {
        long a = OS.incrementAndGet();
        long b = OS.incrementAndGet();
        long c = OS.incrementAndGet();
        store.reservar(ctx(a), nova(a, T0.plusSeconds(20)), List.of());
        store.reservar(ctx(b), nova(b, T0), List.of());
        store.reservar(ctx(c), nova(c, T0.plusSeconds(10)), List.of());
        Execucao emReparo = store.buscarPorOs(c).orElseThrow();
        emReparo.iniciar(T0.plusSeconds(11));
        store.atualizar(emReparo, List.of());

        List<Long> naFila = store.listarPorStatus(StatusExecucao.NA_FILA).stream().map(Execucao::getOsId)
                .filter(os -> os == a || os == b || os == c).toList();
        List<Long> reparo = store.listarPorStatus(StatusExecucao.EM_REPARO).stream().map(Execucao::getOsId)
                .filter(os -> os == a || os == b || os == c).toList();

        assertThat(naFila).containsExactly(b, a);
        assertThat(reparo).containsExactly(c);
    }
}
