package br.com.oficina.execution.infrastructure.dynamo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveSpecification;
import software.amazon.awssdk.services.dynamodb.model.UpdateTimeToLiveRequest;

import java.util.List;

/**
 * Cria as tabelas que faltam (idempotente). Usado no desenvolvimento local e nos testes; na AWS as tabelas são
 * criadas pelo Terraform com este mesmo desenho.
 */
public class CriadorDeTabelas {

    private static final Logger LOG = LoggerFactory.getLogger(CriadorDeTabelas.class);

    private final DynamoDbClient dynamo;
    private final Tabelas tabelas;

    public CriadorDeTabelas(DynamoDbClient dynamo, Tabelas tabelas) {
        this.dynamo = dynamo;
        this.tabelas = tabelas;
    }

    public void garantirTabelas() {
        // estoque: uma linha por peça
        criar(tabelas.estoque(), List.of(def("codigo", ScalarAttributeType.S)),
                List.of(chave("codigo", KeyType.HASH)), List.of());

        // execução: uma linha por OS; o índice lista a fila por status, da mais antiga para a mais nova
        criar(tabelas.execucao(),
                List.of(def("osId", ScalarAttributeType.N), def("status", ScalarAttributeType.S),
                        def("criadoEm", ScalarAttributeType.S)),
                List.of(chave("osId", KeyType.HASH)),
                List.of(indice(Tabelas.INDICE_STATUS, "status", "criadoEm")));

        // mensagens já tratadas (idempotência); expiram sozinhas
        criar(tabelas.mensagemProcessada(), List.of(def("messageId", ScalarAttributeType.S)),
                List.of(chave("messageId", KeyType.HASH)), List.of());
        expirar(tabelas.mensagemProcessada());

        // outbox: o índice esparso só contém os eventos ainda não publicados (atributo "pendente")
        criar(tabelas.outbox(),
                List.of(def("messageId", ScalarAttributeType.S), def("pendente", ScalarAttributeType.S),
                        def("ordem", ScalarAttributeType.S)),
                List.of(chave("messageId", KeyType.HASH)),
                List.of(indice(Tabelas.INDICE_PENDENTES, "pendente", "ordem")));
        expirar(tabelas.outbox());
    }

    private void criar(String nome, List<AttributeDefinition> atributos, List<KeySchemaElement> chave,
                       List<GlobalSecondaryIndex> indices) {
        try {
            dynamo.describeTable(DescribeTableRequest.builder().tableName(nome).build());
            return;
        } catch (ResourceNotFoundException e) {
            LOG.info("Criando a tabela {}", nome);
        }
        var req = CreateTableRequest.builder().tableName(nome).attributeDefinitions(atributos).keySchema(chave)
                .billingMode(BillingMode.PAY_PER_REQUEST);
        if (!indices.isEmpty()) {
            req.globalSecondaryIndexes(indices);
        }
        dynamo.createTable(req.build());
        dynamo.waiter().waitUntilTableExists(DescribeTableRequest.builder().tableName(nome).build());
    }

    private void expirar(String tabela) {
        try {
            dynamo.updateTimeToLive(UpdateTimeToLiveRequest.builder().tableName(tabela)
                    .timeToLiveSpecification(TimeToLiveSpecification.builder().enabled(true)
                            .attributeName("expiraEm").build()).build());
        } catch (RuntimeException e) {
            // já estava ligado, ou o emulador não suporta: não impede o serviço de funcionar
            LOG.debug("TTL da tabela {} não alterado: {}", tabela, e.getMessage());
        }
    }

    private static AttributeDefinition def(String nome, ScalarAttributeType tipo) {
        return AttributeDefinition.builder().attributeName(nome).attributeType(tipo).build();
    }

    private static KeySchemaElement chave(String nome, KeyType tipo) {
        return KeySchemaElement.builder().attributeName(nome).keyType(tipo).build();
    }

    private static GlobalSecondaryIndex indice(String nome, String hash, String range) {
        return GlobalSecondaryIndex.builder().indexName(nome)
                .keySchema(chave(hash, KeyType.HASH), chave(range, KeyType.RANGE))
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build()).build();
    }
}
