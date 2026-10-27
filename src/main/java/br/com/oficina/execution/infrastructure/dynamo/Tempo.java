package br.com.oficina.execution.infrastructure.dynamo;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Datas como texto de largura fixa (microssegundos, UTC): ordenar os textos é ordenar no tempo, o que o
 * DynamoDB precisa nas chaves de ordenação dos índices. {@link Instant#toString()} corta zeros à direita e
 * não serve para isso.
 */
final class Tempo {

    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    private Tempo() {
    }

    static String formatar(Instant instante) {
        return FORMATO.format(instante);
    }

    static Instant ler(String texto) {
        return texto == null ? null : Instant.parse(texto);
    }
}
