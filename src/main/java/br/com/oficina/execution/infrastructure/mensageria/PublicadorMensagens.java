package br.com.oficina.execution.infrastructure.mensageria;

/** Saída para o barramento (SNS). Interface própria para o relay da outbox ser testável sem AWS. */
public interface PublicadorMensagens {

    /** Publica o envelope JSON; {@code tipo} vai como atributo da mensagem. Lança exceção se falhar. */
    void publicar(String tipo, String envelopeJson);
}
