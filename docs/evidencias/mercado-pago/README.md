# Evidências do teste real com o Mercado Pago (conta de teste, 03/11/2026)

| Arquivo | O que mostra |
|---|---|
| `estorno-compensacao-c-saga.png` | Monitor da Saga (OS 21): link gerado, pagamento aprovado, estoque reservado, execução falhou, `LIBERAR_ESTOQUE`, `ESTORNAR` → `PagamentoEstornado`, Saga `CANCELADA` |
| `estorno-painel-mercado-pago-reembolsado.png` | Painel *Pagamentos de teste* do Mercado Pago: o pagamento de R$ 200,00 da mesma OS aparece como **Reembolsado** |
| `link-vencido-compensacao-a-saga.png` | Monitor da Saga (OS 23) com `PAGAMENTO_VALIDADE_LINK_MINUTOS=2`: máquina de estados e mensagens; link vence, `PagamentoRecusado`, `CANCELAR_ORCAMENTO`, Saga `ENCERRADA` ("Link de pagamento expirou sem pagamento") |
| `recusa-fundos-insuficientes-painel-mercado-pago.png` | Painel *Pagamentos de teste*: tentativa com cartão de titular `FUND`, status **Fundos insuficientes** (20:11). A recusa fica no Mercado Pago, mas o **link continua aberto**: a Saga só compensa quando o link vence |

São dados de uma conta de **teste**. Não há token nem assinatura secreta nas imagens. Contexto e resultados: [`../../mercado-pago.md`](../../mercado-pago.md), seção 5.
