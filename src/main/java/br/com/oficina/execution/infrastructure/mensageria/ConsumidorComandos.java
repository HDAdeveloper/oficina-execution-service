package br.com.oficina.execution.infrastructure.mensageria;

import br.com.oficina.execution.application.port.in.ProcessadorComandos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Trata o corpo de uma mensagem da fila: lê o envelope, converte no comando e chama o caso de uso.
 * Se lançar exceção, a mensagem NÃO deve ser confirmada na fila (volta e, depois de 3 vezes, vai para a DLQ).
 */
public class ConsumidorComandos {

    private static final Logger LOG = LoggerFactory.getLogger(ConsumidorComandos.class);

    private final ObjectMapper json;
    private final ComandoMapper mapper;
    private final ProcessadorComandos processador;

    public ConsumidorComandos(ObjectMapper json, ComandoMapper mapper, ProcessadorComandos processador) {
        this.json = json;
        this.mapper = mapper;
        this.processador = processador;
    }

    public void tratar(String corpo) {
        EnvelopeMensagem envelope;
        try {
            envelope = json.readValue(corpo, EnvelopeMensagem.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new MensagemInvalidaException("Corpo da mensagem não é um envelope JSON válido", e);
        }
        ComandoMapper.Mapeado mapeado = mapper.mapear(envelope);
        // correlationId nos logs de tudo que acontece no processamento desta mensagem
        MDC.put("correlationId", String.valueOf(envelope.correlationId()));
        MDC.put("sagaId", String.valueOf(envelope.sagaId()));
        try {
            LOG.info("Comando {} recebido (messageId={}, osId={})", envelope.type(), envelope.messageId(),
                    envelope.osId());
            processador.processar(mapeado.contexto(), mapeado.comando());
        } finally {
            MDC.remove("correlationId");
            MDC.remove("sagaId");
        }
    }
}
