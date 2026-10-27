package br.com.oficina.execution.infrastructure.mensageria;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;

public class SnsPublicadorMensagens implements PublicadorMensagens {

    private final SnsClient sns;
    private final String topicoArn;

    public SnsPublicadorMensagens(SnsClient sns, String topicoArn) {
        this.sns = sns;
        this.topicoArn = topicoArn;
    }

    @Override
    public void publicar(String tipo, String envelopeJson) {
        sns.publish(PublishRequest.builder().topicArn(topicoArn).message(envelopeJson)
                .messageAttributes(Map.of("type",
                        MessageAttributeValue.builder().dataType("String").stringValue(tipo).build()))
                .build());
    }
}
