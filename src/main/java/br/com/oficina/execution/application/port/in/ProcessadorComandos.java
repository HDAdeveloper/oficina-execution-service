package br.com.oficina.execution.application.port.in;

/** Entrada dos comandos da Saga (chamada pelo consumidor da fila). */
public interface ProcessadorComandos {

    /**
     * Trata o comando e grava, de forma atômica, o novo estado, a marca de "mensagem já processada" e os
     * eventos de resposta. Entrega repetida (mesmo {@code messageId}) é ignorada. Se lançar exceção, a mensagem
     * não deve ser confirmada na fila.
     */
    void processar(ContextoMensagem contexto, ComandoExecution comando);
}
