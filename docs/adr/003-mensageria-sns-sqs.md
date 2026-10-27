# ADR-003 — Mensageria com SNS + SQS

**Status:** Aceito · 22/10/2026

## Contexto
A comunicação assíncrona entre serviços precisa de entrega confiável, reprocessamento e isolamento
entre produtor e consumidor. O ambiente de produção é a AWS.

## Opções
| Opção | Prós | Contras |
|---|---|---|
| RabbitMQ / Kafka | Recursos ricos | Há que operar o broker no cluster (custo e tempo) |
| **SNS + SQS** | Gerenciado; fan-out com filtro; DLQ nativa; sem servidor para operar | Só existe na AWS |
| SQS direto | Mais simples | Sem fan-out: o orquestrador teria de conhecer cada fila |

## Decisão
**SNS + SQS**: dois tópicos (comandos e eventos), uma fila por consumidor, cada uma com DLQ
(3 tentativas). Comandos usam o atributo `target` para chegar só ao serviço certo. Filas standard,
entrega *at-least-once*, com idempotência por `messageId` nos consumidores.

## Consequências
- Ordem de entrega não é garantida; a Saga não depende dela, porque o orquestrador só avança ao receber a resposta do passo anterior.
- Desenvolvimento local usa o LocalStack (ADR-005). Em produção, o Terraform cria os mesmos recursos.
- Em produção, a política da fila precisa autorizar o tópico SNS a enviar mensagens (o LocalStack não exige isso).
