# Checklist de entrega — Tech Challenge Fase 4 (prazo 10/11/2026)

Conferido contra o enunciado da Fase 4 e contra os itens da Fase 3 que a Fase 4 evolui.
Legenda: ✅ pronto · 🟡 parcial / precisa de ação · ⬜ pendente.

## Requisitos obrigatórios (Fase 4)

| Requisito | Estado | Evidência / o que falta |
|---|---|---|
| ≥ 3 microsserviços, cada um com repositório, infra e banco próprios | ✅ | os-service (PostgreSQL), billing-service (PostgreSQL), execution-service (DynamoDB); ADR 001, 004 |
| Um banco SQL e um NoSQL | ✅ | PostgreSQL ×2 + DynamoDB |
| Nenhum serviço acessa o banco de outro | ✅ | só REST + SNS/SQS; ADR 001 |
| REST síncrono + mensageria assíncrona | ✅ | REST nos 3; SNS+SQS com DLQ e outbox; ADR 003 |
| Saga com compensação, documentada e justificada no README | ✅ | orquestrada no OS Service; ADR 002; `docs/eventos.md` · 🟡 conferir se o **README raiz** traz a justificativa |
| Integração com Mercado Pago | ✅ | link, webhook assinado, estorno e expiração validados; `docs/mercado-pago.md` e `docs/evidencias/mercado-pago/` |
| Testes unitários em todos os serviços | ✅ | `./mvnw verify` |
| Pelo menos um fluxo completo com BDD | ✅ | testes de fluxo do os-service (conferir o nome dos cenários antes do vídeo) |
| Cobertura ≥ 80% por serviço | ✅ | regra no `pom.xml` (quebra o build) · 🟡 **atualizar os números** (os de `docs/evidencias/*/cobertura-resumo.txt` são de 23/10; rodar `scripts\cobertura.ps1`) |
| Qualidade no CI (Sonar) | 🟡 | os-service, billing, auth-lambda ok · ⬜ **execution**: criar projeto, renomear branch para `main`, re-run |
| Pipeline independente por serviço (build, testes, qualidade, deploy em K8s) | 🟡 | `ci.yml` nos 4 repos; deploy só com `DEPLOY_ENABLED=true` (⬜ cadastrar `AWS_ROLE_ARN`, `AWS_REGION`, `EKS_CLUSTER_NAME`) |
| `main` com PR obrigatório e checagens | 🟡 | ruleset `proteger-main` importado (repos públicos) · ⬜ conferir nos **5 repos** (`gh api repos/HDAdeveloper/<repo>/rulesets`) · ⬜ ligar **Require status checks** (`build-test`; no infra, `validate`) |
| Dockerfile e manifestos K8s por serviço | ✅ | `Dockerfile` e `k8s/` nos 3 serviços |
| Deploy em Kubernetes | ⬜ | EKS no dia do deploy (07/11) — **o enunciado pede "ambiente Kubernetes", não exige AWS**; kind/minikube também serve |
| Monitoramento e observabilidade da Fase 3 | 🟡 | agente New Relic na imagem, `correlationId`/`sagaId` nos logs, health checks · ⬜ conta e licença New Relic, painel e alertas (ver abaixo) |

## Entregáveis dos repositórios

| Item | Estado | Observação |
|---|---|---|
| Código-fonte | ✅ | |
| Dockerfile + manifestos K8s | ✅ | |
| Pipelines CI/CD | ✅ | corrigidas pelas issues do Sonar |
| Evidências de cobertura no README | 🟡 | `docs/evidencias/<servico>/` existe; ⬜ **linkar a evidência no README** de cada repo com os números novos |
| Documentação da arquitetura do serviço | ✅ | `docs/servico.md` em cada repo |
| **Swagger ou Postman atualizado** | 🟡 | ver "Swagger" abaixo: Swagger roda **na aplicação**; falta o **arquivo versionado** (`docs/openapi.json`) |
| soat-architecture como colaborador (Fase 3 pedia; manter) | ⬜ | convidar nos 5 repos + workspace e **conferir aceite** |

## Vídeo (≤ 15 min, YouTube/Vimeo não listado)

| Cena | Estado |
|---|---|
| Fluxo completo de uma OS pelos serviços (painel `saga-monitor`) | ⬜ gravar |
| Saga e falhas (compensações A, B, C) | ⬜ gravar · 🟡 cenário **ERRO** ainda não validado |
| Deploy automatizado de ao menos um serviço com validação de testes | ⬜ depende do deploy/EKS |
| Monitoramento e rastreamento distribuído (New Relic) | ⬜ depende da licença e do deploy |

## PDF do portal

| Item | Estado |
|---|---|
| Nome e identificação dos participantes | ⬜ |
| Links dos repositórios | ⬜ (5: os, billing, execution, auth-lambda, infra) |
| Link do vídeo | ⬜ |
| Diagrama geral da arquitetura (serviços, bancos, comunicação) | 🟡 `docs/arquitetura.md` (Mermaid) · ⬜ exportar imagem |
| Estratégia da Saga e justificativa | ✅ `docs/adr/002`, `docs/eventos.md` (⬜ resumir no PDF) |
| Justificativa da divisão dos microsserviços e tecnologias | ✅ ADR 001, 003, 004 |

## Fase 3 que a Fase 4 evolui (observabilidade e infraestrutura)

| Item da Fase 3 | Estado | Observação |
|---|---|---|
| API Gateway + autenticação por CPF (Lambda, JWT) | ✅ código · ⬜ validar na AWS | ADR 007, `auth-lambda`, Terraform |
| Banco gerenciado (RDS PostgreSQL) com Terraform | ✅ | `infra/terraform/rds.tf` (a Fase 3 pedia repo separado; hoje é um repo `infra` único) |
| Cluster K8s com escalabilidade (HPA) | ✅ | EKS 2 nós + `k8s/hpa.yaml` nos 3 serviços (citar no PDF) |
| Latência, CPU/memória, health checks, uptime | 🟡 | agente New Relic + `/actuator/health` · ⬜ painel |
| **Alertas para falhas no processamento de OS** | ⬜ | criar alerta no New Relic (ex.: erros da Saga / DLQ com mensagens) |
| **Logs estruturados em JSON com correlação** | ✅ | `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` nos ConfigMaps (JSON no cluster) + `correlationId`/`sagaId` no MDC · ⬜ conferir uma linha real no deploy |
| **Dashboards**: volume diário de OS, tempo médio por status, erros de integração | ⬜ | montar no New Relic (NRQL) no dia do deploy |
| Diagrama de componentes e de sequência (autenticação + abertura de OS) | 🟡 | componentes e sequência da Saga existem · ⬜ **sequência de autenticação por CPF + abertura de OS** (falta) |
| RFCs e ADRs | 🟡 | ADRs 001–009 ✅ · ⬜ **RFCs não existem** (nuvem, banco, autenticação): criar `docs/rfc/` |
| Diagrama ER e justificativa do banco | 🟡 | ADR 004 · ⬜ **diagrama ER não existe** (só diagramas de classes do DDD): gerar a partir das migrations |

## Swagger: onde fica e o que entregar

- O Swagger **é gerado pela aplicação** (springdoc): `/swagger-ui.html` e `/v3/api-docs`. Isso prova a API, mas **só com o serviço rodando**.
- O enunciado pede o **Swagger ou a collection Postman "atualizada"** como entregável do repositório, e a Fase 3 pedia **link** no README. Então vale ter o contrato **no repo**:
  1. Subir os 3 serviços e rodar `scripts\local\exportar-openapi.ps1`, que grava `docs/openapi.json` em cada serviço.
  2. Commitar (o README já aponta para o arquivo).
  3. No Postman: *Import → File* com o `openapi.json` gera a coleção. O os-service já tem `docs/postman/…collection.json`.
- Quando houver deploy, acrescentar no README o link do Swagger exposto pelo API Gateway (`/swagger-ui.html`).
- **auth-lambda:** não tem springdoc. Documentar o contrato de `POST /auth/cpf` (entrada, saída, erros) no README ou num `docs/openapi.yaml` curto.

## Ordem sugerida até a entrega

1. **05/11 (hoje):** PRs do infra e do execution; convites `soat-architecture`; Sonar do execution; exportar OpenAPI.
2. **06/11:** atualizar cobertura e README; conta New Relic; alerta de orçamento AWS + MFA root; validar cenário ERRO.
3. **07/11:** deploy (2 fases), `deploy-aws.ps1`, validar via API Gateway, painel e alertas New Relic, **gravar bloco de deploy e monitoramento**, `terraform destroy` e conferir a conta.
4. **08–09/11:** gravar o restante do vídeo, montar o PDF, conferir links e permissões.
5. **10/11:** enviar no portal.
