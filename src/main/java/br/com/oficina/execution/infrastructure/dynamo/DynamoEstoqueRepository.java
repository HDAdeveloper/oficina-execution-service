package br.com.oficina.execution.infrastructure.dynamo;

import br.com.oficina.execution.application.port.out.EstoqueRepository;
import br.com.oficina.execution.domain.exception.ConflitoException;
import br.com.oficina.execution.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.execution.domain.model.ItemEstoque;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static br.com.oficina.execution.infrastructure.dynamo.Atributos.chave;
import static br.com.oficina.execution.infrastructure.dynamo.Atributos.n;
import static br.com.oficina.execution.infrastructure.dynamo.Atributos.numero;
import static br.com.oficina.execution.infrastructure.dynamo.Atributos.s;
import static br.com.oficina.execution.infrastructure.dynamo.Atributos.texto;

public class DynamoEstoqueRepository implements EstoqueRepository {

    private final DynamoDbClient dynamo;
    private final String tabela;

    public DynamoEstoqueRepository(DynamoDbClient dynamo, Tabelas tabelas) {
        this.dynamo = dynamo;
        this.tabela = tabelas.estoque();
    }

    @Override
    public Optional<ItemEstoque> buscar(String codigo) {
        var resposta = dynamo.getItem(GetItemRequest.builder().tableName(tabela)
                .key(chave("codigo", s(codigo))).consistentRead(true).build());
        return resposta.hasItem() && !resposta.item().isEmpty()
                ? Optional.of(deItem(resposta.item())) : Optional.empty();
    }

    @Override
    public List<ItemEstoque> buscarVarios(Collection<String> codigos) {
        List<ItemEstoque> itens = new ArrayList<>();
        for (String codigo : new LinkedHashSet<>(codigos)) {
            buscar(codigo).ifPresent(itens::add);
        }
        return itens;
    }

    @Override
    public List<ItemEstoque> listar() {
        List<ItemEstoque> itens = new ArrayList<>();
        dynamo.scanPaginator(ScanRequest.builder().tableName(tabela).consistentRead(true).build())
                .items().forEach(i -> itens.add(deItem(i)));
        return itens;
    }

    @Override
    public void criar(ItemEstoque item) {
        try {
            dynamo.putItem(PutItemRequest.builder().tableName(tabela)
                    .item(Map.of("codigo", s(item.codigo()), "nome", s(item.nome()), "disponivel", n(item.disponivel())))
                    .conditionExpression("attribute_not_exists(codigo)").build());
        } catch (ConditionalCheckFailedException e) {
            throw new ConflitoException("Já existe peça com o código " + item.codigo());
        }
    }

    @Override
    public ItemEstoque repor(String codigo, int quantidade) {
        try {
            var resposta = dynamo.updateItem(UpdateItemRequest.builder().tableName(tabela)
                    .key(chave("codigo", s(codigo)))
                    .updateExpression("ADD disponivel :q")
                    .conditionExpression("attribute_exists(codigo)")
                    .expressionAttributeValues(Map.of(":q", n(quantidade)))
                    .returnValues(ReturnValue.ALL_NEW).build());
            return deItem(resposta.attributes());
        } catch (ConditionalCheckFailedException e) {
            throw new RecursoNaoEncontradoException("Peça não encontrada no estoque: " + codigo);
        }
    }

    private static ItemEstoque deItem(Map<String, AttributeValue> item) {
        return new ItemEstoque(texto(item, "codigo"), texto(item, "nome"), (int) numero(item, "disponivel"));
    }
}
