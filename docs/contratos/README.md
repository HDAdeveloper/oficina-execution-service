# Contratos das APIs

Contratos REST (OpenAPI 3) e coleções Postman dos serviços da Oficina Tech Service, mais o contrato das mensagens trocadas na Saga.
Os arquivos são gerados a partir do código (`scripts/contratos/gerar-contratos.py`) e conferidos pelo Swagger de cada serviço.

| Serviço | OpenAPI (contrato) | Coleção Postman | Swagger no ar (local) |
|---|---|---|---|
| **OS Service** (cadastros, OS, orçamento, login) | [openapi/os-service.json](openapi/os-service.json) | [oficina-os-service](postman/oficina-os-service.postman_collection.json) | `http://localhost:8081/swagger-ui.html` |
| **Billing Service** (orçamento, pagamento por link, simulador, webhook) | [openapi/billing-service.json](openapi/billing-service.json) | [oficina-billing-service](postman/oficina-billing-service.postman_collection.json) | `http://localhost:8082/swagger-ui.html` |
| **Execution Service** (fila de execução, estoque) | [openapi/execution-service.json](openapi/execution-service.json) | [oficina-execution-service](postman/oficina-execution-service.postman_collection.json) | `http://localhost:8083/swagger-ui.html` |
| **Autenticação por CPF** (Lambda via API Gateway) | `POST /auth/cpf` (na coleção) | [oficina-auth-lambda](postman/oficina-auth-lambda.postman_collection.json) | não se aplica |
| Variáveis do ambiente local | | [oficina-local.postman_environment.json](postman/oficina-local.postman_environment.json) | |

## Como usar no Postman

1. **Import → Files** e selecione as quatro coleções e o ambiente `oficina-local.postman_environment.json` (ou arraste a pasta `postman/`).
2. Escolha o ambiente **Oficina (local)** no canto superior direito.
3. Suba o ambiente ([como-subir-o-ambiente.md](../como-subir-o-ambiente.md)).
4. Em cada coleção do Billing e do Execution rode primeiro **0. Login**: ele chama o login do OS Service e guarda o token ADMIN (os três serviços aceitam o mesmo JWT).
5. Na coleção do OS Service, rode **na ordem** (o login guarda o token e os cadastros guardam os ids). Para o caminho feliz: login, cadastros, abrir OS, diagnóstico, serviço e peça, solicitar e aprovar o orçamento.

Também dá para importar direto o `openapi/*.json` (Postman: **Import → File**); as coleções acima já trazem a ordem e as variáveis.

**Na AWS:** troque as variáveis `baseUrl*` pelos endereços (OS Service: balanceador ou `url_da_api`; Billing e Execution, via `kubectl port-forward`, em `localhost`).

## Autenticação

| Papel | Como obter | O que acessa |
|---|---|---|
| `ADMIN` (equipe da oficina) | `POST /api/auth/login` no OS Service | a área administrativa dos três serviços |
| `CLIENTE` | `POST /auth/cpf` (Lambda) | só `GET /api/minhas-ordens` (as OS do próprio CPF) |
| `SERVICE` | token de serviço da Lambda | só `GET /api/clientes/documento/{cpf}` |

Rotas públicas: login, health (`/actuator/health`) e, com o provedor `mercadopago`, o webhook `POST /webhooks/mercadopago` do Billing
(fora do Swagger de propósito; protegido pela assinatura `x-signature`, ver [mercado-pago.md](../mercado-pago.md)).

## Contrato das mensagens (SNS/SQS)

A comunicação entre os serviços é assíncrona e o contrato está em [eventos.md](../eventos.md): comandos
(`CRIAR_ORCAMENTO`, `COBRAR_ORCAMENTO`, `CANCELAR_ORCAMENTO`, `ESTORNAR_PAGAMENTO`, `RESERVAR_ESTOQUE`, `LIBERAR_ESTOQUE`) e eventos
(`ORCAMENTO_CRIADO`, `LINK_PAGAMENTO_GERADO`, `PAGAMENTO_APROVADO`, `PAGAMENTO_RECUSADO`, `ESTOQUE_RESERVADO`, `EXECUCAO_FINALIZADA`, entre outros),
com a topologia SNS/SQS, o envelope, os campos de cada mensagem, o fluxo feliz e as compensações A, B e C.

## Como regenerar

```powershell
# com os 3 serviços no ar (docs/como-subir-o-ambiente.md)
powershell -ExecutionPolicy Bypass -File scripts\local\exportar-openapi.ps1    # atualiza <servico>/docs/openapi.json
python scripts\contratos\gerar-contratos.py                                    # monta docs/contratos/
```
