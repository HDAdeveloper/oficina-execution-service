# CONTEXT — Tech Challenge Fase 4 (Oficina Mecânica)

> Este arquivo é a memória do projeto. Quem retomar o trabalho (pessoa ou Claude, em qualquer
> sessão) deve ler este arquivo e o `PROGRESS.md` antes de qualquer coisa.

## Objetivo
Entregar a Fase 4 do Tech Challenge (Pós-Tech SOAT / FIAP) até **10/11/2026**: refatorar a oficina
mecânica para **microsserviços** com **Saga Pattern**, mensageria, testes (80% por serviço, 1 fluxo
BDD), qualidade (SonarQube ou similar), CI/CD independente por serviço e deploy em Kubernetes.
O grupo **não tem** as Fases 1–3 próprias: tudo é feito do zero, já na arquitetura final, cobrindo o
que essas fases pediam (DDD/hexagonal, JWT, autenticação por CPF, API Gateway, observabilidade).

## Decisões fechadas (tomadas em 21/10/2026)
| Tema | Decisão |
|---|---|
| Linguagem / framework | Java 21, Spring Boot 3.x, Maven |
| Arquitetura interna | Hexagonal (ports & adapters), domínio em Java puro (sem Spring/Lombok) |
| Pacotes / coordenadas | groupId `br.com.oficina`; pacote base `br.com.oficina.<servico>` (ex.: `br.com.oficina.os`) |
| Nuvem | **AWS**, conta pessoal (plano gratuito de créditos); região recomendada `us-east-1` |
| Kubernetes | EKS (dev local com kind/docker-compose) |
| API Gateway | AWS API Gateway |
| Autenticação por CPF | AWS Lambda (Java) que valida CPF, consulta o cliente e emite JWT |
| Mensageria | SNS + SQS (LocalStack no desenvolvimento local) |
| Bancos | OS Service: PostgreSQL (RDS) · Billing Service: PostgreSQL (RDS, banco separado) · Execution Service: **DynamoDB** (NoSQL) |
| Saga | **Orquestrada**, com o orquestrador dentro do OS Service (status da OS = estado da Saga) |
| Novo status | `CANCELADA` (além dos 6 do enunciado) para tornar a compensação explícita |
| Pagamento | Port `PagamentoGateway` com adapter **simulado**; adapter real do Mercado Pago ligado quando o grupo tiver o Access Token de teste (conta de teste em criação em 22/10/2026) |
| IaC | Terraform |
| CI/CD | GitHub Actions |
| Observabilidade | **New Relic**, plano gratuito (100 GB/mês de ingestão, 1 usuário completo) |
| Ambiente de desenvolvimento | 100% local (docker-compose + LocalStack) até a Etapa 7; EKS só para deploy real e vídeo |

## Decisões da Etapa 6 e 7 (23/10/2026)
- **Papéis no JWT** (`roles`): ADMIN, CLIENTE (Lambda de CPF) e SERVICE (consulta do cadastro). Ver `docs/adr/007-autenticacao-cpf-lambda.md`.
- **Entrada pública:** API Gateway (HTTP API) → Lambda (`POST /auth/cpf`) e → OS Service (`/api/*`). Billing e Execution não são expostos.
- **Infra AWS** em `infra/terraform/` (estado local, sem NAT, nós do EKS em sub-redes públicas, RDS privados, IRSA por serviço, OIDC para o GitHub). Deploy inicial por `scripts/deploy/deploy-aws.ps1`; as pipelines só atualizam o Deployment.
- **Custo:** ambiente de pé ≈ US$ 0,35/h; subir só para validar e gravar, depois `terraform destroy`.

## Microsserviços
1. **OS Service** — clientes, veículos, catálogo de serviços, ciclo de vida da OS, histórico, orquestrador da Saga.
2. **Billing Service** — orçamento, pagamento (Mercado Pago via port), estorno.
3. **Execution Service** — fila de execução, estoque de peças (reserva/liberação), finalização.

Regras: cada serviço tem repositório, banco, pipeline e manifestos próprios; nenhum serviço acessa o
banco de outro; comunicação síncrona só por REST quando necessário, o resto por eventos.

## Fluxo da Saga (resumo)
abrir OS → diagnóstico → gerar orçamento → aguardar aprovação → pagamento → reservar estoque e executar → finalizar → entregar.

Compensações:
- orçamento recusado → OS volta para `EM_DIAGNOSTICO`;
- pagamento recusado/expirado → orçamento cancelado;
- estoque insuficiente ou falha na execução → estorna pagamento, libera reserva, OS `CANCELADA`.

## Aderência ao enunciado (anotações da revisão de 22/10/2026)
- "Rollback em qualquer etapa": matriz de falhas em `docs/eventos.md`.
- Execution Service "atualiza status durante diagnóstico e reparos": proposta de subestados pendente (ver PROGRESS).
- Rastreamento dos fluxos distribuídos faz parte do vídeo: `correlationId` em todas as mensagens e logs, mais tracing no New Relic.
- Cada repositório final precisa de `main` protegida (PR obrigatório + checagens) e do usuário `soat-architecture` adicionado.

## Estratégia de repositórios
- **Agora:** um único repositório de trabalho (`oficina-workspace`), com uma pasta por componente:
  `os-service/`, `billing-service/`, `execution-service/`, `auth-lambda/`, `infra/`, `docs/`.
- **No fim:** `scripts/split-repos.sh` separa cada pasta em seu próprio repositório (exigência do
  enunciado: um repositório por microsserviço), preservando o histórico.
- O usuário `soat-architecture` precisa ser adicionado a **todos** os repositórios finais.

## Restrições e fatos sobre o ambiente de trabalho do Claude
- A sessão do Claude **não consegue baixar dependências do Maven Central** (bloqueio de rede). Portanto
  o Claude **não compila nem roda os testes Java**. Todo código é entregue sem verificação de
  build; **quem valida é o grupo rodando os comandos de cada etapa** e devolvendo erros/saídas.
- Claude não faz deploy, não cria repositórios, não configura proteção de branch nem segredos.
- O grupo quer **instruções de execução e teste ao final de cada etapa**.
- Custo AWS: EKS cobra pelo control plane; subir só perto da gravação do vídeo e derrubar depois.

## Referências (somente consulta — NÃO copiar código nem documentos)
Repositórios públicos de outro grupo, usados apenas para entender o formato esperado:
`robsonago/oficina`, `robsonago/oficina-infra-k8s`, `robsonago/oficina-infra-db`,
`robsonago/oficina-auth-function`. Armadilha vista neles: segredo do JWT escrito no Terraform —
aqui segredos ficam no AWS Secrets Manager / Secrets do K8s, nunca no Git.

## Entregáveis da Fase 4 (checklist do enunciado)
- [ ] 3+ microsserviços, cada um com repo, infra e banco próprios (≥1 SQL e ≥1 NoSQL)
- [ ] Comunicação: REST síncrono quando necessário + mensageria assíncrona
- [ ] Saga com rollback/compensação, escolha justificada no README
- [ ] Testes unitários em todos os serviços, ≥80% de cobertura por serviço, 1 fluxo completo em BDD
- [ ] Qualidade via SonarQube (ou similar) no CI
- [ ] Pipeline por serviço: build, testes, qualidade, deploy em Kubernetes; `main` protegida com PR
- [ ] Cada repo: Dockerfile, manifestos K8s, pipeline, evidências de cobertura, doc da arquitetura, Swagger/Postman
- [ ] Observabilidade (monitoramento e rastreamento dos fluxos distribuídos)
- [ ] Vídeo ≤15 min: fluxo completo, Saga e falha, deploy automatizado com testes, monitoramento
- [ ] PDF no portal: participantes, links dos repos, link do vídeo, diagrama final, estratégia da Saga, justificativa da divisão
