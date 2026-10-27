package br.com.oficina.execution.infrastructure.config;

import br.com.oficina.execution.application.port.out.GeradorId;

import java.util.UUID;

public class GeradorIdUuid implements GeradorId {
    @Override
    public String novo() {
        return UUID.randomUUID().toString();
    }
}
