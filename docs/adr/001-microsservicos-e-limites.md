# ADR-001 — Divisão em três microsserviços

**Status:** Aceito · 22/10/2026

## Contexto
O enunciado da Fase 4 pede ao menos três microsserviços, cada um com banco e repositório próprios,
sendo ao menos um SQL e um NoSQL. A divisão precisa seguir fronteiras de negócio, não camadas técnicas.

## Decisão
| Serviço | Dono de | Banco |
|---|---|---|
| **OS Service** | clientes, veículos, catálogo de serviços e peças (nome e preço), ciclo de vida da OS, orquestração da Saga | PostgreSQL |
| **Billing Service** | orçamento, pagamento, estorno, integração com o Mercado Pago | PostgreSQL (instância separada) |
| **Execution Service** | fila de execução, estoque (quantidades), reservas | DynamoDB |

O catálogo de peças (preço) fica no OS Service, que monta o orçamento. A **quantidade em estoque**
é do Execution Service, que é quem reserva e baixa.

## Consequências
- Nenhum serviço lê o banco de outro. Dados de outro serviço chegam por mensagem ou REST.
- O OS Service concentra mais responsabilidade por ser também o orquestrador (ADR-002).
- Consultas que cruzam serviços (ex.: OS com seu pagamento) exigem chamada REST ou dados replicados nos eventos.
