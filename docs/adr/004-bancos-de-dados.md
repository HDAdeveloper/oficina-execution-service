# ADR-004 — PostgreSQL para OS e Billing, DynamoDB para Execution

**Status:** Aceito · 22/10/2026

## Contexto
Os serviços têm perfis de dados diferentes e o enunciado exige ao menos um banco SQL e um NoSQL.

## Decisão
- **OS Service e Billing Service: PostgreSQL** (RDS na AWS). O domínio é relacional (cliente, veículo,
  OS, itens) e financeiro (orçamento, pagamento), com integridade referencial e transações ACID.
- **Execution Service: DynamoDB.** O acesso é por chave (estoque por código de peça, fila por OS) e
  precisa de escrita condicional para reservar estoque sem corrida (`quantidade >= solicitada`).
- Bancos separados: nenhum serviço acessa o banco de outro.

## Consequências
- O Execution Service não faz JOIN nem consultas ad hoc; relatórios que cruzam dados vêm de outros serviços.
- Localmente: duas instâncias de Postgres e DynamoDB no LocalStack. Na AWS: RDS e DynamoDB.
- Custo: o RDS é o item mais caro do plano gratuito; em produção do vídeo usamos instância mínima.
