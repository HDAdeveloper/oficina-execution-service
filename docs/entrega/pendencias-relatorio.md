# Pendências do relatório de entrega (revisão)

Rascunho em `relatorio-entrega.md`; PDF de revisão gerado por `python scripts/entrega/gerar-pdf.py`.
Este arquivo **não** vai para o PDF.

## Campos a preencher (aparecem como **[PREENCHER]** / **[ATUALIZAR]**)

| Item | Depende de |
|---|---|
| Link do repositório de documentação, se for público | Decisão: publicar o workspace ou só os 5 repos |
| Cobertura e número de testes dos 3 serviços (seção 10.2) | `scripts\cobertura.ps1` e `mvnw clean verify` (**medir de novo**: OS e Billing ganharam testes do e-mail) |

## O que um avaliador de arquitetura pode cobrar e ainda não está no relatório

1. **RFCs** (nuvem, banco, autenticação): a Fase 3 pedia; hoje só há ADRs. Criar `docs/rfc/` e citar na seção 6.
2. **Rastreamento distribuído** no New Relic: confirmar que aparece no painel; se não, remover a menção ou explicar o limite.
3. **Prova de deploy**: link de uma execução verde do pipeline e print do `kubectl get pods` (evidência da AWS de 08/11).
4. **Status checks obrigatórios** na `main` dos 5 repositórios (os repositórios são públicos; o convite `soat-architecture` expirou e foi dispensado).
5. **Sonar do Execution Service** (projeto ainda não criado).
6. **Cenário ERRO** (provedor indisponível) validado no monitor.
7. **Nome novo** (Oficina Tech Service) propagado aos 5 repositórios.
8. Conferir se o **README raiz** traz a justificativa da Saga (o enunciado pede no README).
9. Decidir se o PDF ganha as **imagens do Event Storming e do DDD** (`docs/diagramas/`) como anexo.
