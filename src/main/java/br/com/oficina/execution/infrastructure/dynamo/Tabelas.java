package br.com.oficina.execution.infrastructure.dynamo;

/** Nomes das tabelas do Execution Service (com o prefixo configurado). */
public record Tabelas(String estoque, String execucao, String mensagemProcessada, String outbox) {

    public static final String INDICE_STATUS = "status-criadoEm-index";
    public static final String INDICE_PENDENTES = "pendentes-index";

    public static Tabelas comPrefixo(String prefixo) {
        String p = prefixo == null ? "" : prefixo;
        return new Tabelas(p + "estoque", p + "execucao", p + "mensagem_processada", p + "outbox");
    }
}
