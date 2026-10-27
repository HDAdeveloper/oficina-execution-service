package br.com.oficina.execution.infrastructure.dynamo;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Um LocalStack (DynamoDB) em contêiner, compartilhado pelos testes de persistência. Precisa do Docker rodando.
 * Cada teste usa códigos de peça e números de OS próprios, então não depende de tabelas vazias.
 */
public abstract class DynamoTestBase {

    protected static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.12"))
                    .withServices(LocalStackContainer.Service.DYNAMODB);

    /** Números de OS únicos entre todos os testes (as tabelas são compartilhadas). */
    protected static final AtomicLong OS = new AtomicLong(System.currentTimeMillis());

    static {
        LOCALSTACK.start();
    }

    @DynamicPropertySource
    protected static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("execution.aws.endpoint", () -> LOCALSTACK.getEndpoint().toString());
        registro.add("execution.aws.regiao", LOCALSTACK::getRegion);
        registro.add("execution.dynamo.criar-tabelas", () -> "true");
        // Fila e SNS só nos testes de mensageria; aqui o foco é o DynamoDB
        registro.add("execution.mensageria.habilitada", () -> "false");
    }
}
