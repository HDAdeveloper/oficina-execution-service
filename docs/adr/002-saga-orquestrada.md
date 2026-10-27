# ADR-002 — Saga orquestrada, com o orquestrador no OS Service

**Status:** Aceito · 22/10/2026

## Contexto
O fluxo abre OS, gera orçamento, cobra, reserva estoque e executa, atravessando três bancos. Não há
transação distribuída: cada passo confirma localmente e, se um passo posterior falhar, os anteriores
precisam ser desfeitos por operações de compensação.

## Opções
| Opção | Prós | Contras |
|---|---|---|
| **Coreografia** (serviços reagem a eventos uns dos outros) | Sem ponto central; acoplamento baixo | O fluxo fica espalhado em três códigos; difícil de entender, depurar e explicar |
| **Orquestração** (um coordenador envia comandos e decide o próximo passo) | Fluxo em um só lugar; compensação explícita; fácil de testar e de mostrar | O coordenador vira ponto de atenção |

## Decisão
**Orquestração.** O orquestrador fica dentro do OS Service, porque o status da OS já é o estado do
fluxo e evita um quarto serviço. O estado detalhado fica na tabela `saga_instance`.

## Consequências
- Os contratos de comandos e eventos estão em `docs/eventos.md`.
- Billing e Execution não conhecem o fluxo completo: só executam comandos e respondem com eventos.
- Se o OS Service estiver fora do ar, a Saga pausa e retoma ao voltar (as filas guardam as respostas).
- Compensações: recusa de pagamento, estoque insuficiente e falha na execução (ver `docs/eventos.md`).
