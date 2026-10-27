# ADR-006 — Execution Service: transações do DynamoDB para estado, idempotência e outbox

**Status:** Aceito · 23/10/2026

## Contexto
O Execution Service guarda o estoque de peças e a fila de execução no DynamoDB (ADR-004). Cada comando da
Saga precisa, ao mesmo tempo, (1) mudar o estoque e a execução, (2) lembrar que a mensagem já foi
tratada e (3) deixar a resposta pronta para o SNS. No OS Service e no Billing isso é uma transação SQL com
tabela de outbox; no DynamoDB não há transação "do serviço", mas há `TransactWriteItems`.

## Decisão
1. **Uma `TransactWriteItems` por comando** reúne: baixa/devolução do estoque (`UpdateItem` condicional),
   a execução (`Put`/`Update` condicional), o registro da mensagem processada (`Put` com
   `attribute_not_exists`) e os eventos de resposta (`Put` na tabela de outbox). Ou tudo é gravado, ou nada.
2. **Reserva sem corrida:** cada peça é baixada com `ConditionExpression: disponivel >= :qtd`. Se uma única
   peça não tem quantidade, a transação inteira é cancelada e nenhuma outra peça é baixada.
3. **Idempotência dupla**, como no Billing: pelo `messageId` (item na própria transação) e pela regra de
   negócio (a OS já tem reserva viva → confirma a mesma `reservaId`, sem nova baixa).
4. **Outbox em tabela própria** com índice esparso de "pendentes". Um relay lê os pendentes, publica no SNS
   `oficina-saga-events` e remove a marca. Entrega *at-least-once*.
5. **Execução vive num item por OS** (chave `osId`), com número de versão para detectar alteração
   concorrente nas chamadas REST. Índice por `status` + data para listar a fila.
6. **Subestados no Execution** (resposta à divergência de escopo levantada em 22/10): `NA_FILA → EM_REPARO →
   CONCLUIDA` (ou `FALHOU`). Novo evento **`EXECUCAO_INICIADA`** quando o reparo começa; o OS Service o
   registra no histórico (Etapa 5). É uma adição ao contrato: consumidores que não o conhecem o ignoram.
7. **Pedido de reserva inválido** (quantidade zero, código vazio) responde `ESTOQUE_INSUFICIENTE` com
   `faltantes` vazio e um campo `motivo`, em vez de ir para a DLQ: não adianta repetir.

## Alternativas consideradas
- *Outbox no Postgres de outro serviço:* viola "banco por serviço".
- *DynamoDB Streams + Lambda para publicar:* mais peças para operar e testar localmente; o relay por consulta
  é mais simples e já é o padrão dos outros serviços.
- *Reserva em duas etapas (ler e depois gravar):* sujeita a corrida entre dois pedidos pela mesma peça.

## Consequências
- Uma transação aceita até 100 itens; uma OS com mais de ~90 peças diferentes não cabe. Aceitável aqui;
  documentado como limite.
- Transações custam o dobro de uma escrita simples (cobrança do DynamoDB); o volume é pequeno.
- Os testes de persistência rodam contra o DynamoDB do LocalStack em contêiner (Testcontainers).
