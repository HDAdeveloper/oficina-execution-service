package br.com.oficina.execution.infrastructure.mensageria;

import br.com.oficina.execution.application.port.in.ComandoExecution.LiberarEstoque;
import br.com.oficina.execution.application.port.in.ComandoExecution.ReservaInvalida;
import br.com.oficina.execution.application.port.in.ComandoExecution.ReservarEstoque;
import br.com.oficina.execution.domain.model.PecaReservada;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComandoMapperTest {

    private final ComandoMapper mapper = new ComandoMapper();
    private final ObjectMapper json = new ObjectMapper();

    private EnvelopeMensagem env(String type, Long osId, String payload) throws Exception {
        JsonNode p = payload == null ? null : json.readTree(payload);
        return new EnvelopeMensagem("m-1", "saga-1", osId, type, 1, "2026-10-23T12:00:00Z", "corr-1", p);
    }

    @Test
    void reservaTraduzPecasEServicos() throws Exception {
        var m = mapper.mapear(env("RESERVAR_ESTOQUE", 42L, """
                {"pecas":[{"codigo":" pec-1 ","quantidade":2},{"codigo":"PEC-2","quantidade":1}],
                 "servicos":["Revisão","Alinhamento"]}"""));

        assertThat(m.contexto().messageId()).isEqualTo("m-1");
        assertThat(m.contexto().sagaId()).isEqualTo("saga-1");
        assertThat(m.contexto().osId()).isEqualTo(42L);
        assertThat(m.contexto().correlationId()).isEqualTo("corr-1");
        assertThat(m.comando()).isEqualTo(new ReservarEstoque(
                List.of(new PecaReservada("PEC-1", 2), new PecaReservada("PEC-2", 1)),
                List.of("Revisão", "Alinhamento")));
    }

    @Test
    void reservaSemPecasNemServicosEhValida() throws Exception {
        var m = mapper.mapear(env("RESERVAR_ESTOQUE", 42L, "{}"));

        assertThat(m.comando()).isEqualTo(new ReservarEstoque(List.of(), List.of()));
    }

    @Test
    void reservaSemPayloadTambemEhValida() throws Exception {
        var m = mapper.mapear(env("RESERVAR_ESTOQUE", 42L, null));

        assertThat(m.comando()).isEqualTo(new ReservarEstoque(List.of(), List.of()));
    }

    @Test
    void quantidadeInvalidaViraReservaInvalida() throws Exception {
        var m = mapper.mapear(env("RESERVAR_ESTOQUE", 42L, """
                {"pecas":[{"codigo":"PEC-1","quantidade":0}]}"""));

        assertThat(m.comando()).isInstanceOfSatisfying(ReservaInvalida.class,
                c -> assertThat(c.motivo()).contains("PEC-1"));
    }

    @Test
    void quantidadeTextualEIgnoradaComoZero() throws Exception {
        var m = mapper.mapear(env("RESERVAR_ESTOQUE", 42L, """
                {"pecas":[{"codigo":"PEC-1","quantidade":"muitas"}]}"""));

        assertThat(m.comando()).isInstanceOf(ReservaInvalida.class);
    }

    @Test
    void codigoVazioViraReservaInvalida() throws Exception {
        var m = mapper.mapear(env("RESERVAR_ESTOQUE", 42L, """
                {"pecas":[{"codigo":"  ","quantidade":1}]}"""));

        assertThat(m.comando()).isInstanceOf(ReservaInvalida.class);
    }

    @Test
    void reservaSemOsViraReservaInvalida() throws Exception {
        var m = mapper.mapear(env("RESERVAR_ESTOQUE", null, "{}"));

        assertThat(m.comando()).isInstanceOf(ReservaInvalida.class);
    }

    @Test
    void liberacaoLeOMotivo() throws Exception {
        assertThat(mapper.mapear(env("LIBERAR_ESTOQUE", 42L, "{\"motivo\":\"pagamento estornado\"}")).comando())
                .isEqualTo(new LiberarEstoque("pagamento estornado"));
        assertThat(mapper.mapear(env("LIBERAR_ESTOQUE", 42L, null)).comando())
                .isEqualTo(new LiberarEstoque(null));
    }

    @Test
    void comandoDesconhecidoEhMensagemInvalida() {
        assertThatThrownBy(() -> mapper.mapear(env("CRIAR_ORCAMENTO", 1L, "{}")))
                .isInstanceOf(MensagemInvalidaException.class).hasMessageContaining("CRIAR_ORCAMENTO");
    }

    @Test
    void semTypeOuSemMessageIdEhMensagemInvalida() {
        assertThatThrownBy(() -> mapper.mapear(null)).isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> mapper.mapear(env(" ", 1L, "{}"))).isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> mapper.mapear(new EnvelopeMensagem(null, "s", 1L, "LIBERAR_ESTOQUE", 1, null, null,
                null))).isInstanceOf(MensagemInvalidaException.class);
    }
}
