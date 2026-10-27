package br.com.oficina.execution.infrastructure.dynamo;

import br.com.oficina.execution.application.port.in.ComandoExecution.LiberarEstoque;
import br.com.oficina.execution.application.port.in.ComandoExecution.ReservarEstoque;
import br.com.oficina.execution.application.port.in.ContextoMensagem;
import br.com.oficina.execution.application.port.in.GestaoEstoque;
import br.com.oficina.execution.application.port.in.GestaoExecucoes;
import br.com.oficina.execution.application.port.in.ProcessadorComandos;
import br.com.oficina.execution.domain.model.PecaReservada;
import br.com.oficina.execution.domain.model.StatusExecucao;
import br.com.oficina.execution.infrastructure.dynamo.OutboxDynamo.EventoOutbox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Casos de uso reais + DynamoDB real: estado, estoque e eventos conferidos juntos. */
@SpringBootTest
class FluxoComDynamoTest extends DynamoTestBase {

    @Autowired ProcessadorComandos processador;
    @Autowired GestaoExecucoes execucoes;
    @Autowired GestaoEstoque gestaoEstoque;
    @Autowired OutboxDynamo outbox;
    @Autowired ObjectMapper json;

    private static ContextoMensagem ctx(long os) {
        return new ContextoMensagem(UUID.randomUUID().toString(), "saga-" + os, os, "corr-" + os);
    }

    private String peca(int quantidade) {
        String c = "F" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        gestaoEstoque.cadastrar(c, "Peça " + c, quantidade);
        return c;
    }

    private List<EventoOutbox> eventos(long os) {
        return outbox.listarPendentes(1000).stream().filter(e -> e.osId() != null && e.osId() == os).toList();
    }

    private List<String> tipos(long os) {
        return eventos(os).stream().map(EventoOutbox::tipo).toList();
    }

    @Test
    void fluxoFelizReservaIniciaEFinaliza() {
        long os = OS.incrementAndGet();
        String p = peca(5);

        processador.processar(ctx(os), new ReservarEstoque(List.of(new PecaReservada(p, 2)), List.of("Revisão")));
        execucoes.iniciar(os);
        execucoes.finalizar(os);

        assertThat(gestaoEstoque.buscar(p).disponivel()).isEqualTo(3);
        assertThat(execucoes.buscar(os).getStatus()).isEqualTo(StatusExecucao.CONCLUIDA);
        assertThat(tipos(os)).containsExactly("ESTOQUE_RESERVADO", "EXECUCAO_INICIADA", "EXECUCAO_FINALIZADA");
        assertThat(eventos(os)).allSatisfy(e -> assertThat(e.sagaId()).isEqualTo("saga-" + os));
    }

    @Test
    void mensagemRepetidaNaoGeraNovoEventoNemNovaBaixa() {
        long os = OS.incrementAndGet();
        String p = peca(5);
        ContextoMensagem mesma = ctx(os);
        var cmd = new ReservarEstoque(List.of(new PecaReservada(p, 2)), List.of());

        processador.processar(mesma, cmd);
        processador.processar(mesma, cmd);

        assertThat(gestaoEstoque.buscar(p).disponivel()).isEqualTo(3);
        assertThat(tipos(os)).containsExactly("ESTOQUE_RESERVADO");
    }

    @Test
    void estoqueInsuficienteInformaOsFaltantes() throws Exception {
        long os = OS.incrementAndGet();
        String p = peca(1);

        processador.processar(ctx(os), new ReservarEstoque(List.of(new PecaReservada(p, 3)), List.of()));

        assertThat(tipos(os)).containsExactly("ESTOQUE_INSUFICIENTE");
        JsonNode payload = json.readTree(eventos(os).get(0).payload());
        assertThat(payload.get("faltantes").get(0).get("codigo").asText()).isEqualTo(p);
        assertThat(payload.get("faltantes").get(0).get("solicitado").asInt()).isEqualTo(3);
        assertThat(payload.get("faltantes").get(0).get("disponivel").asInt()).isEqualTo(1);
        assertThat(gestaoEstoque.buscar(p).disponivel()).isEqualTo(1);
    }

    @Test
    void compensacaoCDevolveOEstoqueDepoisDaFalhaNaExecucao() {
        long os = OS.incrementAndGet();
        String p = peca(5);
        processador.processar(ctx(os), new ReservarEstoque(List.of(new PecaReservada(p, 2)), List.of()));
        execucoes.iniciar(os);
        execucoes.falhar(os, "motor travou");

        processador.processar(ctx(os), new LiberarEstoque("compensação"));

        assertThat(gestaoEstoque.buscar(p).disponivel()).isEqualTo(5);
        assertThat(execucoes.buscar(os).getStatus()).isEqualTo(StatusExecucao.LIBERADA);
        assertThat(tipos(os)).containsExactly("ESTOQUE_RESERVADO", "EXECUCAO_INICIADA", "EXECUCAO_FALHOU",
                "ESTOQUE_LIBERADO");
    }

    @Test
    void liberarDeNovoResponderDeNovoSemDevolverEmDobro() {
        long os = OS.incrementAndGet();
        String p = peca(5);
        processador.processar(ctx(os), new ReservarEstoque(List.of(new PecaReservada(p, 2)), List.of()));

        processador.processar(ctx(os), new LiberarEstoque("x"));
        processador.processar(ctx(os), new LiberarEstoque("x"));

        assertThat(gestaoEstoque.buscar(p).disponivel()).isEqualTo(5);
        assertThat(tipos(os)).containsExactly("ESTOQUE_RESERVADO", "ESTOQUE_LIBERADO", "ESTOQUE_LIBERADO");
    }

    @Test
    void execucaoConcluidaNaoELiberada() {
        long os = OS.incrementAndGet();
        String p = peca(5);
        processador.processar(ctx(os), new ReservarEstoque(List.of(new PecaReservada(p, 2)), List.of()));
        execucoes.iniciar(os);
        execucoes.finalizar(os);

        processador.processar(ctx(os), new LiberarEstoque("x"));

        assertThat(gestaoEstoque.buscar(p).disponivel()).isEqualTo(3);
        assertThat(execucoes.buscar(os).getStatus()).isEqualTo(StatusExecucao.CONCLUIDA);
        assertThat(tipos(os)).doesNotContain("ESTOQUE_LIBERADO");
    }

    @Test
    void liberarOQueNuncaFoiReservadoResponde() throws Exception {
        long os = OS.incrementAndGet();

        processador.processar(ctx(os), new LiberarEstoque("x"));

        assertThat(tipos(os)).containsExactly("ESTOQUE_LIBERADO");
        assertThat(json.readTree(eventos(os).get(0).payload()).get("nadaALiberar").asBoolean()).isTrue();
    }

    @Test
    void filaTrazSoOQueEstaEmTrabalho() {
        long a = OS.incrementAndGet();
        long b = OS.incrementAndGet();
        processador.processar(ctx(a), new ReservarEstoque(List.of(), List.of("Alinhamento")));
        processador.processar(ctx(b), new ReservarEstoque(List.of(), List.of("Balanceamento")));
        execucoes.iniciar(b);
        execucoes.finalizar(b);

        List<Long> fila = execucoes.listarFila().stream().map(e -> e.getOsId()).toList();

        assertThat(fila).contains(a).doesNotContain(b);
    }
}
