package br.com.oficina.execution.infrastructure.dynamo;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

/** Atalhos para montar valores do DynamoDB. */
final class Atributos {

    private Atributos() {
    }

    static AttributeValue s(String valor) {
        return AttributeValue.builder().s(valor).build();
    }

    static AttributeValue n(long valor) {
        return AttributeValue.builder().n(Long.toString(valor)).build();
    }

    static Map<String, AttributeValue> chave(String atributo, AttributeValue valor) {
        return Map.of(atributo, valor);
    }

    static String texto(Map<String, AttributeValue> item, String atributo) {
        AttributeValue v = item.get(atributo);
        return v == null || v.s() == null ? null : v.s();
    }

    static long numero(Map<String, AttributeValue> item, String atributo) {
        AttributeValue v = item.get(atributo);
        return v == null || v.n() == null ? 0 : Long.parseLong(v.n());
    }
}
