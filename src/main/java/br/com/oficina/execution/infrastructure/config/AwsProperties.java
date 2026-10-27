package br.com.oficina.execution.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Prefixo {@code execution.aws}. {@code endpoint} só é preenchido contra o LocalStack. */
@ConfigurationProperties(prefix = "execution.aws")
public record AwsProperties(String regiao, String endpoint) {

    public boolean temEndpointProprio() {
        return endpoint != null && !endpoint.isBlank();
    }
}
