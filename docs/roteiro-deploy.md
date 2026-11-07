# Roteiro do dia do deploy (AWS) — passo a passo com tempos

Custo: **≈ US$ 0,35 por hora** enquanto estiver de pé (EKS + 2 nós + 2 RDS + balanceador). Meta: **menos de 3 horas** do
`apply` ao `destroy`. Tudo em **Windows PowerShell 5.1**, a partir de `C:\Projetos\oficina-workspace`.
Nunca cole chaves, tokens ou senhas no chat nem no Git.

## 0. Antes de subir (sem custo) — conferir tudo

| ✔ | Item |
|---|---|
| ☐ | Alerta de orçamento (US$ 20) criado e MFA no usuário root |
| ☐ | Conta New Relic criada e a **INGEST - LICENSE** guardada fora do Git |
| ☐ | `git pull` no workspace; os-service com `.\mvnw.cmd clean verify` verde (já feito) |
| ☐ | Docker Desktop **aberto**; `terraform`, `kubectl` e `aws` instalados (`terraform version`, `kubectl version --client`) |
| ☐ | Vídeo roteirizado (`docs\roteiro-narracao.md`): saiba o que vai mostrar em cada etapa abaixo |
| ☐ | Gravador de tela pronto (OBS ou o do Windows: `Win+Alt+R`) |

```powershell
cd C:\Projetos\oficina-workspace
git pull
$env:AWS_PROFILE = "oficina"        # em TODA janela nova (o "default" e outra conta/sem permissao)
aws sts get-caller-identity          # deve mostrar a conta 528757785383
cd auth-lambda; .\mvnw.cmd clean package -DskipTests; cd ..      # gera o .jar que o Terraform envia para a Lambda
Test-Path auth-lambda\target\auth-lambda-1.0.0-SNAPSHOT.jar      # True
cd infra\terraform
copy terraform.tfvars.example terraform.tfvars                  # so na primeira vez
terraform init
terraform plan -out plano.tfplan                                 # sem custo; revise (~90 a 104 recursos)
cd ..\..
```

## 1. Criar a infraestrutura (≈ 20 min, **o custo começa aqui**)

```powershell
terraform -chdir=infra\terraform apply plano.tfplan
```

- O EKS demora (~12 a 15 min). Um erro transitório do ECR já ocorreu: **repita o `apply`** (sem `-out`) e segue.
- Anote a hora de início para controlar o custo.

## 2. Publicar os serviços (≈ 15 a 20 min)

```powershell
$env:NEW_RELIC_LICENSE_KEY = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR((Read-Host "Licenca New Relic" -AsSecureString)))
.\scripts\deploy\deploy-aws.ps1
```

O script envia as 3 imagens ao ECR, cria segredos e ConfigMaps, aplica os Deployments e espera ficarem prontos. No fim mostra
`OS Service publico em: http://<endereco>`. **Se o `rollout status` estourar o tempo**, não espere: investigue na hora.

```powershell
kubectl -n oficina get pods
kubectl -n oficina describe pod <pod-com-problema>
kubectl -n oficina logs deployment/os-service --tail=80
```

Suspeitas (ensaio de 26/10): o acesso ao SQS/SNS pelo IRSA (corrigido com a dependência `sts`, **ainda não validado na AWS**),
memória do JVM (limite 1Gi), conexão com o RDS, probes de saúde. Se não resolver em ~20 min, **destrua** (passo 7) e investigue sem custo.

## 3. Rotas do API Gateway (≈ 3 min)

```powershell
terraform -chdir=infra\terraform apply -var os_service_url=http://<endereco-do-passo-2>
terraform -chdir=infra\terraform output url_da_api
```

Teste: `GET <url_da_api>/actuator/health` (deve mostrar `UP`) e `POST <url_da_api>/auth/cpf` com um CPF cadastrado.

## 4. Validar o fluxo (≈ 15 min) — **aqui entram o vídeo e o New Relic**

Billing e Execution não são públicos. Para rodar o `saga-e2e` e o painel contra a AWS, abra **dois terminais** com `port-forward`:

```powershell
# terminal A
kubectl -n oficina port-forward svc/billing-service 8082:80
# terminal B
kubectl -n oficina port-forward svc/execution-service 8083:80
```

No terminal principal:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\seed\seed-catalogo.ps1 -Os http://<endereco-os> -Execution http://localhost:8083 -Senha <senha-admin>
powershell -ExecutionPolicy Bypass -File scripts\local\saga-e2e.ps1 -Os http://<endereco-os> -Billing http://localhost:8082 -Execution http://localhost:8083
```

A senha do admin está no segredo do cluster:
`kubectl -n oficina get secret os-service-secrets -o jsonpath='{.data.ADMIN_SENHA}'` (valor em base64; decodifique no PowerShell com
`[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String("<valor>"))`).

O painel `saga-monitor.html` também funciona: abra `http://<endereco-os>/saga-monitor.html` e informe as URLs do Billing e do Execution
como `http://localhost:8082` e `http://localhost:8083` (o navegador acessa pelo `port-forward`; o CORS padrão aceita a origem do OS Service,
ajuste `BILLING_CORS_ORIGENS` / `EXECUTION_CORS_ORIGENS` se o navegador bloquear).

## 5. New Relic (≈ 20 min, enquanto o ambiente está no ar)

1. **APM & Services:** em até ~2 min aparecem `oficina-os-service`, `oficina-billing-service` e `oficina-execution-service`.
2. Importe o dashboard e crie os alertas: `docs\observabilidade\README.md` (troque o Account ID).
3. Rode de novo o `saga-e2e` e confira: tempos por etapa, falhas e compensações, logs em JSON, busca por `correlationId`.
4. Anote os gráficos que ficarem vazios para ajustar a consulta depois (peça no chat).
5. Tire prints do painel e de uma Saga completa (`Distributed tracing`).

## 6. Pipeline com deploy (opcional no mesmo dia, ≈ 15 min)

Só se for gravar o "deploy automatizado com validação de testes" **ao vivo** (o enunciado exige ao menos um serviço):

1. No repo do serviço: **Settings → Secrets and variables → Actions**: secret `AWS_ROLE_ARN` (saída `role_da_pipeline` do Terraform);
   variables `AWS_REGION=us-east-1`, `EKS_CLUSTER_NAME=oficina-eks` e **`DEPLOY_ENABLED=true`**.
2. Abra um PR com uma mudança pequena (ex.: texto do README), espere o check verde e faça o merge: o job `docker` publica a imagem e o `deploy` atualiza o Deployment.
3. Grave: PR verde, Actions (build, testes, Sonar, deploy) e o `kubectl rollout status`.
4. **Depois de gravar, volte `DEPLOY_ENABLED` para vazio** (ou apague a variável).

## 7. Destruir tudo (≈ 15 min) — **não pule**

```powershell
kubectl delete svc os-service-publico -n oficina        # remove o balanceador antes (senao o destroy trava na VPC)
terraform -chdir=infra\terraform destroy
terraform -chdir=infra\terraform state list              # deve vir vazio
```

Se o `destroy` falhar por dependências (VPC/ENI), rode de novo; se persistir, apague o balanceador e as interfaces de rede pelo console e repita.
Depois, **peça no chat "verifique a AWS"**: eu confiro EC2, EKS, RDS, balanceadores, NAT, IPs e volumes em todas as regiões.

## Tempo e custo estimados

| Etapa | Tempo | Acumulado | Custo acumulado (≈) |
|---|---|---|---|
| 1. `apply` | 20 min | 20 min | US$ 0,10 |
| 2. Deploy dos serviços | 20 min | 40 min | US$ 0,25 |
| 3. API Gateway | 3 min | 45 min | US$ 0,30 |
| 4. Validação e vídeo | 15 min | 1 h | US$ 0,35 |
| 5. New Relic | 20 min | 1 h 20 | US$ 0,50 |
| 6. Pipeline (opcional) | 15 min | 1 h 35 | US$ 0,55 |
| 7. `destroy` | 15 min | 1 h 50 | US$ 0,65 |

Com folga para imprevistos, conte **2 a 3 horas (US$ 1 a 2)**. Cada hora a mais custa ≈ US$ 0,35.
