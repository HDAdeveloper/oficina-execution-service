package br.com.oficina.execution.infrastructure.mensageria;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Prefixo {@code execution.mensageria}. {@code endpoint} só é preenchido contra o LocalStack. */
@ConfigurationProperties(prefix = "execution.mensageria")
public record MensageriaProperties(boolean habilitada, String regiao, String endpoint, String filaComandos,
                                   String topicoEventosArn, long intervaloRelayMs, int loteRelay) {
}
