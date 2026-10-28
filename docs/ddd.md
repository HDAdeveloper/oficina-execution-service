# Modelagem de domínio (DDD)

> Os diagramas abaixo descrevem o que existe no código (pacotes `domain` de cada serviço) e o contrato de
> [`eventos.md`](eventos.md). Os limites dos contextos estão justificados no [ADR-001](adr/001-microsservicos-e-limites.md).

> Imagens prontas para o PDF de entrega: [`diagramas/ddd/`](diagramas/ddd/) (um PNG por diagrama).

## 1. Subdomínios e contextos delimitados

| Subdomínio | Tipo | Contexto delimitado (serviço) | Por quê |
|---|---|---|---|
| Gestão da ordem de serviço e coordenação do fluxo | **Core** | **Ordens de Serviço** (OS Service) | É o que diferencia a oficina: ciclo da OS, orçamento, aprovação e a Saga que garante consistência entre contextos |
| Cobrança | Suporte | **Faturamento** (Billing Service) | Regra própria (orçamento, pagamento, estorno), mas sem diferencial competitivo |
| Execução e estoque | Suporte | **Execução** (Execution Service) | Fila de trabalho e reserva de peças; ritmo e banco diferentes (NoSQL) |
| Processamento de pagamento | Genérico | Mercado Pago (externo, atrás de um adaptador) | Comprado pronto; o domínio não depende dele |
| Autenticação por CPF | Genérico | Lambda de autenticação | Papel técnico; não tem regra de negócio própria |

Cada contexto tem **banco próprio** e nenhum lê o banco do outro. O mesmo conceito aparece com significado diferente em cada lado
(a "OS" do Faturamento é só um `osId` ligado a um orçamento; no Execution é uma fila de peças e serviços).

## 2. Mapa de contextos (context map)

```mermaid
flowchart LR
    subgraph core["Core"]
        OS["<b>Ordens de Serviço</b><br/>OS Service<br/>Saga (orquestrador)"]
    end
    subgraph sup["Suporte"]
        BI["<b>Faturamento</b><br/>Billing Service"]
        EX["<b>Execução</b><br/>Execution Service"]
    end
    subgraph ger["Genérico"]
        AU["<b>Autenticação por CPF</b><br/>Lambda"]
        MP["<b>Mercado Pago</b><br/>(externo)"]
    end

    OS -- "comandos<br/>CRIAR_ORCAMENTO, COBRAR_ORCAMENTO,<br/>CANCELAR_ORCAMENTO, ESTORNAR_PAGAMENTO" --> BI
    BI -- "eventos<br/>ORCAMENTO_*, PAGAMENTO_*" --> OS
    OS -- "comandos<br/>RESERVAR_ESTOQUE, LIBERAR_ESTOQUE" --> EX
    EX -- "eventos<br/>ESTOQUE_*, EXECUCAO_*" --> OS
    AU -- "REST: GET /api/clientes/documento/{cpf}<br/>(token de serviço)" --> OS
    BI -- "ACL: PagamentoGateway" --> MP
```

| Relação | Padrão de integração (DDD) | Como aparece no código |
|---|---|---|
| OS Service → Billing / Execution | **Customer/Supplier** com **Published Language** | O OS Service define o envelope e os comandos ([`eventos.md`](eventos.md)); os serviços de apoio se adaptam a ele. Mensagens versionadas (`version`) |
| Billing / Execution → OS Service | **Published Language** (eventos) | Eventos no mesmo envelope, via SNS/SQS; o orquestrador só reage a eles |
| Billing → Mercado Pago | **Anti-Corruption Layer** | Porta `PagamentoGateway`; o adaptador traduz o modelo do Mercado Pago (hoje simulado) para `Pagamento` sem vazar nada no domínio |
| Lambda → OS Service | **Open Host Service** (REST) + cliente conforme | O OS Service expõe a consulta por documento; a Lambda a usa como está e valida o CPF antes |
| Billing ↔ Execution | **Nenhuma** (separate ways) | Não se conhecem; só falam com o orquestrador |

## 3. Agregados por contexto

Em negrito, a raiz do agregado. Objetos de valor (VO) são imutáveis e comparados pelo valor.

### 3.1 Ordens de Serviço (OS Service)

```mermaid
classDiagram
    direction LR
    class OrdemServico {
        <<Aggregate Root>>
        Long id
        String numero
        Long clienteId
        Long veiculoId
        StatusOS status
        String descricaoProblema
        String orcamentoId
        String motivoCancelamento
        abrir()
        iniciarDiagnostico()
        adicionarServico()
        adicionarPeca()
        aguardarAprovacao()
        iniciarExecucao()
        finalizar()
        entregar()
        cancelar()
        valorTotal()
    }
    class ItemServicoOS {
        <<VO>>
        Long servicoId
        int quantidade
        BigDecimal precoUnitario
    }
    class ItemPecaOS {
        <<VO>>
        Long pecaId
        String codigo
        int quantidade
        BigDecimal precoUnitario
    }
    class HistoricoStatus {
        <<VO>>
        StatusOS de
        StatusOS para
        Instant em
        String motivo
    }
    class Saga {
        <<Aggregate Root>>
        String id
        Long osId
        String correlationId
        EstadoSaga estado
        String orcamentoId
        String pagamentoId
        String reservaId
        Instant prazo
        mover()
    }
    class PassoSaga {
        <<VO>>
        Instant em
        String tipo
        String detalhe
    }
    class Cliente {
        <<Aggregate Root>>
        Long id
        String nome
        String email
    }
    class Documento {
        <<VO>>
        String numero
        TipoDocumento tipo
    }
    class Veiculo {
        <<Aggregate Root>>
        Long id
        String marca
        String modelo
        int ano
    }
    class Placa {
        <<VO>>
        String valor
    }
    class Servico {
        <<Aggregate Root>>
        Long id
        String nome
        BigDecimal preco
    }
    class Peca {
        <<Aggregate Root>>
        Long id
        String codigo
        String nome
    }
    OrdemServico "1" *-- "0..*" ItemServicoOS
    OrdemServico "1" *-- "0..*" ItemPecaOS
    OrdemServico "1" *-- "0..*" HistoricoStatus
    Saga "1" *-- "0..*" PassoSaga
    Cliente *-- Documento
    Veiculo *-- Placa
    OrdemServico ..> Cliente : clienteId
    OrdemServico ..> Veiculo : veiculoId
    Saga ..> OrdemServico : osId
    ItemServicoOS ..> Servico : servicoId
    ItemPecaOS ..> Peca : pecaId
```

Regras que o agregado protege (invariantes): a OS só muda de status por transições válidas; só gera orçamento com itens; o valor total
é a soma dos itens; CPF/CNPJ e placa são validados na criação do objeto de valor. Os agregados se referenciam **só por id**
(setas tracejadas). A `Saga` é um agregado separado da OS porque tem ciclo de vida e consistência próprios (timeouts, reenvios).

### 3.2 Faturamento (Billing Service)

```mermaid
classDiagram
    direction LR
    class Orcamento {
        <<Aggregate Root>>
        String id
        Long osId
        Long clienteId
        String emailCliente
        BigDecimal valorTotal
        StatusOrcamento status
        String motivoCancelamento
        criar()
        registrarPagamentoAprovado()
        cancelar()
        estornar()
        temOsMesmosItens()
    }
    class ItemOrcamento {
        <<VO>>
        TipoItem tipo
        String codigo
        String descricao
        int quantidade
        BigDecimal precoUnitario
    }
    class Pagamento {
        <<Aggregate Root>>
        String id
        String orcamentoId
        Long osId
        BigDecimal valor
        String metodo
        StatusPagamento status
        String idExterno
        String motivo
    }
    Orcamento "1" *-- "1..*" ItemOrcamento
    Pagamento ..> Orcamento : orcamentoId
```

Invariantes: só um orçamento pendente pode receber pagamento ou ser cancelado; só um orçamento pago pode ser estornado; um orçamento
pago **não** pode ser cancelado, tem que ser estornado; repetir `cancelar` não faz nada (idempotência).

### 3.3 Execução (Execution Service)

```mermaid
classDiagram
    direction LR
    class Execucao {
        <<Aggregate Root>>
        Long osId
        String sagaId
        String reservaId
        StatusExecucao status
        String motivoFalha
        long versao
        nova()
        iniciar()
        finalizar()
        falhar()
        liberar()
    }
    class PecaReservada {
        <<VO>>
        String codigo
        int quantidade
    }
    class ItemEstoque {
        <<Aggregate Root>>
        String codigo
        String nome
        int disponivel
    }
    Execucao "1" *-- "0..*" PecaReservada
    Execucao ..> ItemEstoque : codigo
```

Invariantes: a baixa de estoque só acontece se `disponivel >= quantidade` (condição na `TransactWriteItems` do DynamoDB); uma execução
concluída **não** pode ser liberada (as peças foram usadas); `liberar` repetido não faz nada; a `versao` evita escrita concorrente.
O estoque e a execução são agregados distintos, mas alterados na mesma transação do DynamoDB ([ADR-006](adr/006-execution-dynamodb-transacoes-e-outbox.md)).

## 4. Ciclos de vida (estados dos agregados)

### 4.1 Ordem de Serviço

```mermaid
stateDiagram-v2
    [*] --> RECEBIDA : abrir
    RECEBIDA --> EM_DIAGNOSTICO : iniciarDiagnostico
    EM_DIAGNOSTICO --> AGUARDANDO_APROVACAO : ORCAMENTO_CRIADO
    AGUARDANDO_APROVACAO --> EM_EXECUCAO : ESTOQUE_RESERVADO
    AGUARDANDO_APROVACAO --> EM_DIAGNOSTICO : recusa / PAGAMENTO_RECUSADO
    EM_EXECUCAO --> FINALIZADA : EXECUCAO_FINALIZADA
    FINALIZADA --> ENTREGUE : entregar
    RECEBIDA --> CANCELADA
    EM_DIAGNOSTICO --> CANCELADA
    AGUARDANDO_APROVACAO --> CANCELADA
    EM_EXECUCAO --> CANCELADA : compensação (B/C)
    ENTREGUE --> [*]
    CANCELADA --> [*]
```

### 4.2 Saga (orquestrada pelo OS Service)

```mermaid
stateDiagram-v2
    [*] --> ORCAMENTO_SOLICITADO
    ORCAMENTO_SOLICITADO --> AGUARDANDO_APROVACAO : ORCAMENTO_CRIADO
    ORCAMENTO_SOLICITADO --> ORCAMENTO_FALHOU : ORCAMENTO_FALHOU / timeout
    AGUARDANDO_APROVACAO --> COBRANDO : cliente aprova
    AGUARDANDO_APROVACAO --> CANCELANDO_ORCAMENTO : cliente recusa
    COBRANDO --> RESERVANDO_ESTOQUE : PAGAMENTO_APROVADO
    COBRANDO --> CANCELANDO_ORCAMENTO : PAGAMENTO_RECUSADO (compensação A)
    RESERVANDO_ESTOQUE --> EM_EXECUCAO : ESTOQUE_RESERVADO
    RESERVANDO_ESTOQUE --> ESTORNANDO : ESTOQUE_INSUFICIENTE (compensação B)
    EM_EXECUCAO --> CONCLUIDA : EXECUCAO_FINALIZADA
    EM_EXECUCAO --> LIBERANDO_ESTOQUE : EXECUCAO_FALHOU (compensação C)
    LIBERANDO_ESTOQUE --> ESTORNANDO : ESTOQUE_LIBERADO
    LIBERANDO_ESTOQUE --> LIBERACAO_PENDENTE : esgotou reenvios
    CANCELANDO_ORCAMENTO --> ENCERRADA : ORCAMENTO_CANCELADO
    ESTORNANDO --> CANCELADA : PAGAMENTO_ESTORNADO
    ESTORNANDO --> ESTORNO_PENDENTE : esgotou reenvios
    ESTORNO_PENDENTE --> CANCELADA : PAGAMENTO_ESTORNADO (ação manual)
    LIBERACAO_PENDENTE --> ESTORNANDO : ESTOQUE_LIBERADO (ação manual)
    CONCLUIDA --> [*]
    CANCELADA --> [*]
    ENCERRADA --> [*]
    ORCAMENTO_FALHOU --> [*]
```

Estados `*_PENDENTE` sinalizam que a compensação não terminou sozinha e **precisa de ação humana** (a mensagem foi para a DLQ).

### 4.3 Orçamento e Execução

```mermaid
stateDiagram-v2
    direction LR
    [*] --> PENDENTE
    PENDENTE --> PAGO : pagamento aprovado
    PENDENTE --> CANCELADO : cancelar
    PAGO --> ESTORNADO : estornar
    CANCELADO --> [*]
    ESTORNADO --> [*]
```

```mermaid
stateDiagram-v2
    direction LR
    [*] --> NA_FILA : reserva criada
    NA_FILA --> EM_REPARO : iniciar
    EM_REPARO --> CONCLUIDA : finalizar
    NA_FILA --> FALHOU : falhar
    EM_REPARO --> FALHOU : falhar
    NA_FILA --> LIBERADA : liberar
    EM_REPARO --> LIBERADA : liberar
    FALHOU --> LIBERADA : liberar
    CONCLUIDA --> [*]
    LIBERADA --> [*]
```

## 5. Comandos e eventos de domínio

Quem **decide** (comando) e quem **informa o que aconteceu** (evento). O orquestrador é o único que envia comandos.

```mermaid
flowchart LR
    subgraph OS["Ordens de Serviço"]
        direction TB
        a1["Atendente abre a OS e gera o orçamento"]
        a2["Cliente aprova o orçamento"]
    end
    subgraph BI["Faturamento"]
        direction TB
        b1["Orçamento criado"]
        b2["Pagamento aprovado / recusado"]
        b3["Orçamento cancelado"]
        b4["Pagamento estornado"]
    end
    subgraph EX["Execução"]
        direction TB
        e1["Estoque reservado / insuficiente"]
        e2["Execução iniciada / finalizada / falhou"]
        e3["Estoque liberado"]
    end
    a1 -->|CRIAR_ORCAMENTO| b1
    a2 -->|COBRAR_ORCAMENTO| b2
    b2 -->|"PAGAMENTO_APROVADO → RESERVAR_ESTOQUE"| e1
    b2 -->|"PAGAMENTO_RECUSADO → CANCELAR_ORCAMENTO"| b3
    e1 -->|"ESTOQUE_INSUFICIENTE → ESTORNAR_PAGAMENTO"| b4
    e2 -->|"EXECUCAO_FALHOU → LIBERAR_ESTOQUE"| e3
    e3 -->|"ESTOQUE_LIBERADO → ESTORNAR_PAGAMENTO"| b4
```

A tabela completa (payloads e reação do orquestrador) está em [`eventos.md`](eventos.md).

## 6. Linguagem ubíqua (glossário)

| Termo | Significado no domínio |
|---|---|
| **Ordem de Serviço (OS)** | Pedido de reparo de um veículo de um cliente, com serviços e peças; vive no contexto Ordens de Serviço |
| **Orçamento** | Proposta de valor de uma OS; vive no Faturamento. Pode ser pendente, pago, cancelado ou estornado |
| **Aprovação** | Decisão do cliente de aceitar o orçamento; dispara a cobrança |
| **Pagamento / Estorno** | Cobrança do orçamento e devolução do valor quando a OS é compensada |
| **Reserva de estoque** | Peças separadas para uma OS; só existe no contexto Execução |
| **Execução** | Trabalho da oficina sobre uma OS (na fila, em reparo, concluída, falhou, liberada) |
| **Saga** | Fluxo distribuído que une cobrança, reserva e execução, com **compensações** quando um passo falha |
| **Compensação** | Ação que desfaz um passo já feito (cancelar orçamento, estornar pagamento, liberar estoque) |
| **Comando** | Pedido do orquestrador para um serviço fazer algo (`COBRAR_ORCAMENTO`) |
| **Evento** | Fato que já aconteceu, publicado por um serviço (`PAGAMENTO_APROVADO`) |
| **Outbox** | Tabela em que o evento é gravado junto com o estado; um *relay* o envia depois, para nada se perder |
| **Idempotência** | Processar a mesma mensagem duas vezes tem o mesmo efeito de processar uma |
| **DLQ** | Fila de mensagens que falharam 3 vezes; exige análise humana |

## 7. Como isso aparece no código

| Conceito DDD | Onde |
|---|---|
| Agregados, objetos de valor, regras | `domain/model` de cada serviço, em Java puro |
| Exceções de negócio | `domain/exception` |
| Casos de uso (serviços de aplicação) e portas | `application/service`, `application/port/in`, `application/port/out` |
| Repositórios, mensageria, REST, ACL | `infrastructure/*` (adaptadores das portas) |
| Orquestrador da Saga | `OrquestradorSaga` (OS Service) |
