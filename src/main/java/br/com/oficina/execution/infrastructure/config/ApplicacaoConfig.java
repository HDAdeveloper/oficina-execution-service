package br.com.oficina.execution.infrastructure.config;

import br.com.oficina.execution.application.port.in.GestaoEstoque;
import br.com.oficina.execution.application.port.in.GestaoExecucoes;
import br.com.oficina.execution.application.port.in.ProcessadorComandos;
import br.com.oficina.execution.application.port.out.EstoqueRepository;
import br.com.oficina.execution.application.port.out.ExecucaoStore;
import br.com.oficina.execution.application.port.out.GeradorId;
import br.com.oficina.execution.application.service.ExecutionService;
import br.com.oficina.execution.application.service.GestaoEstoqueService;
import br.com.oficina.execution.application.service.GestaoExecucoesService;
import br.com.oficina.execution.infrastructure.dynamo.CriadorDeTabelas;
import br.com.oficina.execution.infrastructure.dynamo.DynamoEstoqueRepository;
import br.com.oficina.execution.infrastructure.dynamo.DynamoExecucaoStore;
import br.com.oficina.execution.infrastructure.dynamo.OutboxDynamo;
import br.com.oficina.execution.infrastructure.dynamo.Tabelas;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.time.Clock;

/** Liga os casos de uso (Java puro) às portas de saída, implementadas sobre o DynamoDB. */
@Configuration
@EnableConfigurationProperties({AwsProperties.class, DynamoProperties.class})
public class ApplicacaoConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock relogio() {
        return Clock.systemUTC();
    }

    @Bean
    public GeradorId geradorId() {
        return new GeradorIdUuid();
    }

    @Bean(destroyMethod = "close")
    public DynamoDbClient dynamoDbClient(AwsProperties p) {
        AvisoAwsReal.verificar(p.endpoint());
        var b = DynamoDbClient.builder().region(Region.of(p.regiao()));
        if (p.temEndpointProprio()) {
            // LocalStack: endpoint próprio e credenciais fictícias. Na AWS vale a cadeia padrão de credenciais.
            b.endpointOverride(URI.create(p.endpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
        }
        return b.build();
    }

    @Bean
    public Tabelas tabelas(DynamoProperties p) {
        return Tabelas.comPrefixo(p.prefixo());
    }

    /** Só no desenvolvimento/testes; na AWS o Terraform cria as tabelas. */
    @Bean(initMethod = "garantirTabelas")
    @ConditionalOnProperty(name = "execution.dynamo.criar-tabelas", havingValue = "true")
    public CriadorDeTabelas criadorDeTabelas(DynamoDbClient dynamo, Tabelas tabelas) {
        return new CriadorDeTabelas(dynamo, tabelas);
    }

    @Bean
    public EstoqueRepository estoqueRepository(DynamoDbClient dynamo, Tabelas tabelas) {
        return new DynamoEstoqueRepository(dynamo, tabelas);
    }

    @Bean
    public ExecucaoStore execucaoStore(DynamoDbClient dynamo, Tabelas tabelas, ObjectMapper json, GeradorId ids,
                                       Clock relogio) {
        return new DynamoExecucaoStore(dynamo, tabelas, json, ids, relogio);
    }

    @Bean
    public OutboxDynamo outboxDynamo(DynamoDbClient dynamo, Tabelas tabelas, Clock relogio) {
        return new OutboxDynamo(dynamo, tabelas, relogio);
    }

    @Bean
    public ProcessadorComandos processadorComandos(ExecucaoStore store, EstoqueRepository estoque, GeradorId ids,
                                                   Clock relogio) {
        return new ExecutionService(store, estoque, ids, relogio);
    }

    @Bean
    public GestaoExecucoes gestaoExecucoes(ExecucaoStore store, Clock relogio) {
        return new GestaoExecucoesService(store, relogio);
    }

    @Bean
    public GestaoEstoque gestaoEstoque(EstoqueRepository estoque) {
        return new GestaoEstoqueService(estoque);
    }
}
