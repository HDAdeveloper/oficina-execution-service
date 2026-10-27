package br.com.oficina.execution.infrastructure.mensageria;

import br.com.oficina.execution.application.port.in.ComandoExecution;
import br.com.oficina.execution.application.port.in.ComandoExecution.LiberarEstoque;
import br.com.oficina.execution.application.port.in.ComandoExecution.ReservaInvalida;
import br.com.oficina.execution.application.port.in.ComandoExecution.ReservarEstoque;
import br.com.oficina.execution.application.port.in.ContextoMensagem;
import br.com.oficina.execution.domain.exception.RegraDeNegocioException;
import br.com.oficina.execution.domain.model.PecaReservada;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Traduz o envelope da mensagem no comando da aplicação. */
public class ComandoMapper {

    public record Mapeado(ContextoMensagem contexto, ComandoExecution comando) {
    }

    public Mapeado mapear(EnvelopeMensagem env) {
        if (env == null || vazio(env.type())) {
            throw new MensagemInvalidaException("Mensagem sem o campo type");
        }
        if (vazio(env.messageId())) {
            throw new MensagemInvalidaException("Mensagem " + env.type() + " sem messageId");
        }
        JsonNode p = env.payload();
        ComandoExecution comando = switch (env.type()) {
            case "RESERVAR_ESTOQUE" -> reservar(env, p);
            case "LIBERAR_ESTOQUE" -> new LiberarEstoque(texto(p, "motivo"));
            default -> throw new MensagemInvalidaException("Comando desconhecido para o Execution: " + env.type());
        };
        return new Mapeado(new ContextoMensagem(env.messageId(), env.sagaId(), env.osId(), env.correlationId()),
                comando);
    }

    private ComandoExecution reservar(EnvelopeMensagem env, JsonNode p) {
        if (env.osId() == null) {
            return new ReservaInvalida("Pedido de reserva sem a OS");
        }
        try {
            List<PecaReservada> pecas = new ArrayList<>();
            JsonNode lista = p == null ? null : p.get("pecas");
            if (lista != null && lista.isArray()) {
                for (JsonNode i : lista) {
                    JsonNode q = i.get("quantidade");
                    int quantidade = q != null && q.canConvertToInt() ? q.asInt() : 0;
                    pecas.add(new PecaReservada(texto(i, "codigo"), quantidade));
                }
            }
            List<String> servicos = new ArrayList<>();
            JsonNode lis = p == null ? null : p.get("servicos");
            if (lis != null && lis.isArray()) {
                lis.forEach(s -> servicos.add(s.asText()));
            }
            return new ReservarEstoque(pecas, servicos);
        } catch (RegraDeNegocioException e) {
            // Dado inválido nunca passa na próxima tentativa: responde ESTOQUE_INSUFICIENTE com o motivo
            return new ReservaInvalida(e.getMessage());
        }
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode n = no == null ? null : no.get(campo);
        return n == null || n.isNull() ? null : n.asText();
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
