# Execution Service

Fila de execucao, estoque e reservas. Participa da Saga por mensagens. Parte do Tech Challenge Fase 4 (FIAP SOAT, turma 15). Java 21, Spring Boot 3.4, arquitetura hexagonal.

- **Banco:** DynamoDB (NoSQL). Nenhum outro servico acessa esse banco.
- **Porta:** 8083 (variavel `PORT`)
- **API:** Swagger UI em `/swagger-ui.html`, contrato em `/v3/api-docs`.
- **Autenticacao:** JWT (`Authorization: Bearer ...`); as rotas administrativas exigem papel `ADMIN`.

## Como rodar localmente

Pre-requisitos: JDK 21 e Docker. A infraestrutura local (bancos e LocalStack) vem do repositorio `oficina-workspace`:

```powershell
# no repositorio oficina-workspace
docker compose up -d --wait
# neste repositorio
.\mvnw.cmd spring-boot:run
```

Variaveis de ambiente (todas tem padrao para uso local):

| Variavel | Para que serve | Padrao |
|---|---|---|
| `JWT_SECRET` | segredo HS256 compartilhado | valor de desenvolvimento |
| `EXECUTION_COMMANDS_QUEUE`, `EVENTS_TOPIC_ARN` | fila de comandos e topico de eventos | LocalStack |
| `DYNAMO_PREFIXO`, `DYNAMO_CRIAR_TABELAS` | prefixo das tabelas; criar tabelas na subida (`false` na AWS, o Terraform cria) | `execution_` / `true` |
| `MENSAGERIA_HABILITADA` | liga SNS/SQS | `true` |
| `AWS_ENDPOINT_URL` | aponta para o LocalStack | vazio |

## Como testar

```powershell
.\mvnw.cmd clean verify     # testes + cobertura minima de 80% (relatorio em target\site\jacoco\index.html)
```

Pare o servico antes do `clean`: no Windows a pasta `target` fica bloqueada enquanto ele roda.

## Docker e Kubernetes

```powershell
docker build -t execution-service .
```

Manifestos em `k8s/` (Deployment, Service, ConfigMap, ServiceAccount do IRSA). O ConfigMap tem marcadores
(`CONTA_AWS`, `RDS_*`) trocados pelo script de deploy do `oficina-workspace`, que usa as saidas do Terraform.

## Pipeline (GitHub Actions)

`.github/workflows/ci.yml`: build + testes + **cobertura minima de 80%** (JaCoCo, quebra o build) -> analise no
SonarQube Cloud (com quality gate) -> imagem Docker -> deploy no EKS (so na `main`, com a variavel `DEPLOY_ENABLED=true`).
A `main` e protegida: alteracoes entram por Pull Request e o CI precisa passar.

Configure no repositorio (Settings -> Secrets and variables -> Actions):

| Tipo | Nome | Valor |
|---|---|---|
| Secret | `SONAR_TOKEN` | token do SonarQube Cloud |
| Secret | `AWS_ROLE_ARN` | `terraform output role_da_pipeline` |
| Variable | `SONAR_ORGANIZATION`, `SONAR_PROJECT_KEY` | dados do projeto no SonarQube Cloud |
| Variable | `AWS_REGION`, `EKS_CLUSTER_NAME` | `us-east-1`, `oficina-eks` |
| Variable | `DEPLOY_ENABLED` | `true` so quando o ambiente AWS estiver de pe |

## Observabilidade

Agente Java do New Relic embutido na imagem. Fica desligado ate existir o segredo `newrelic` no cluster
(`NEW_RELIC_LICENSE_KEY`). Health checks em `/actuator/health`, logs com `correlationId`.

## Documentacao

Arquitetura, ADRs, contrato de eventos e Saga: pasta `docs/`. Colecao Postman e Swagger: ver abaixo.

## Documentação

- [Documentação deste serviço](docs/servico.md): responsabilidade, API, mensagens, modelo e testes.
- [Arquitetura](docs/arquitetura.md), [modelagem DDD](docs/ddd.md), [Event Storming](docs/event-storming.md), [eventos e Saga](docs/eventos.md) e [decisões (ADRs)](docs/adr/).
