# Kubernetes (EKS)

Como o cluster e os manifestos foram definidos e por quê. Os manifestos ficam em `<serviço>/k8s/` e o cluster em `infra/terraform/eks.tf`. Para subir tudo, veja [`como-subir-o-ambiente.md`](como-subir-o-ambiente.md).

## 1. Cluster (Terraform)

| Item | Definição | Motivo |
|---|---|---|
| Serviço | Amazon EKS (módulo `terraform-aws-modules/eks`), Kubernetes 1.33 | Plano de controle gerenciado; suporta IRSA |
| Nós | 1 node group gerenciado, 2 × `t3.medium` (máximo 3) | Cabe os 3 serviços com folga e custa pouco |
| Rede | VPC com 2 subnets públicas, **sem NAT gateway** | O NAT cobra por hora; a economia foi uma escolha consciente |
| Acesso | Endpoint público; admin para quem aplica o Terraform e para a role da pipeline (access entries) | A pipeline faz o deploy sem chaves fixas |
| Identidade dos pods | **IRSA**: cada serviço assume uma role IAM própria (`iam.tf`) | Acesso a SNS/SQS e DynamoDB sem chave de acesso no pod |

Valores ajustáveis em `variables.tf`: `versao_kubernetes`, `tipo_no`, `quantidade_nos`.

## 2. Manifestos (os mesmos três arquivos de base nos 3 serviços)

Todos no namespace `oficina`.

| Arquivo | O que define |
|---|---|
| `namespace.yaml` | Namespace `oficina` |
| `serviceaccount.yaml` | Conta do serviço, com a anotação `eks.amazonaws.com/role-arn` (IRSA); o script de deploy preenche a role |
| `configmap.yaml` | Configuração não sensível: endpoint do RDS, ARNs de SNS/SQS, prazos da Saga, New Relic |
| `deployment.yaml` | 2 réplicas, `RollingUpdate` com `maxSurge: 1` e `maxUnavailable: 0`, recursos, probes e segurança |
| `hpa.yaml` | Escala de 2 a 5 réplicas com meta de 70% de CPU |
| `service.yaml` | `ClusterIP` (acesso interno) |
| `service-publico.yaml` | `LoadBalancer` público; existe só no OS Service e, opcionalmente, no Billing (webhook) |

### Deployment

- **Imagem:** vem do ECR. A pipeline troca o marcador `IMAGEM_AQUI` pela imagem do commit.
- **Recursos:** requests de 250m de CPU e 512Mi de memória; limites de 1 CPU e 1Gi.
- **Probes** (Actuator): `startupProbe` tolera até 3 minutos de início do Spring (36 tentativas de 5 s); `readinessProbe` tira o pod do balanceamento quando não está pronto; `livenessProbe` reinicia o pod travado.
- **Segurança do container:** `runAsNonRoot` e `allowPrivilegeEscalation: false`.
- **Atualização sem queda:** `maxUnavailable: 0` mantém as réplicas antigas até as novas ficarem prontas.

### Configuração e segredos

- O ConfigMap traz marcadores (`RDS_OS_ENDPOINT`, `CONTA_AWS`) que o `scripts/deploy/deploy-aws.ps1` troca pelas saídas do Terraform.
- Senhas e chaves ficam em `Secret` (`<serviço>-secrets`), criado pelo script de deploy e **nunca no Git**.
- O Secret `newrelic` é opcional: sem ele o agente fica desligado.
- O diretório `k8s/exemplos/` traz modelos dos segredos.

## 3. Exposição

| Serviço | Acesso externo |
|---|---|
| OS Service | `LoadBalancer` (`os-service-publico`); o API Gateway e a Lambda de CPF chamam este endereço |
| Billing | Nenhum. Conversa só por SNS/SQS. Com `deploy-aws.ps1 -ExporWebhook` ganha um `LoadBalancer` só para o webhook do Mercado Pago |
| Execution | Nenhum. Conversa só por SNS/SQS |

A entrada pública da API é o API Gateway (HTTP API), que encaminha para o OS Service. Detalhes em [`arquitetura.md`](arquitetura.md).

## 4. Deploy

1. O Terraform cria EKS, RDS, DynamoDB, SNS/SQS, ECR, API Gateway e Lambda.
2. O `deploy-aws.ps1` configura o `kubectl`, cria o namespace e os Secrets, substitui os marcadores e aplica os manifestos; depois espera o rollout.
3. A pipeline repete o passo 2 a cada merge na `main`, depois de build, testes e análise no Sonar.

## 5. Trade-offs

- **Subnets públicas sem NAT:** mais barato, mas os nós têm IP público. Em produção, usar subnets privadas com NAT ou VPC endpoints.
- **Endpoint do cluster público:** simplifica a pipeline; em produção, restringir por CIDR.
- **2 nós fixos:** suficiente para a demonstração. O HPA escala os pods, não os nós; para escalar nós, usar Cluster Autoscaler ou Karpenter.
- **Um cluster para os 3 serviços:** reduz custo; o isolamento é por namespace e por role IAM de cada serviço.
