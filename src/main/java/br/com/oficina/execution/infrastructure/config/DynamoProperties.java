package br.com.oficina.execution.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Prefixo {@code execution.dynamo}. */
@ConfigurationProperties(prefix = "execution.dynamo")
public record DynamoProperties(String prefixo, boolean criarTabelas) {
}
