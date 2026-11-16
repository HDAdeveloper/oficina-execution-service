# Como subir o ambiente

Dois caminhos, com os comandos em sequência: **local** (Docker + LocalStack, sem custo e sem conta na AWS) e **nuvem** (AWS real:
Terraform, EKS, RDS, DynamoDB, SNS/SQS, API Gateway e Lambda). O fluxo é o mesmo nos dois; muda só o destino das mensagens e dos bancos.

| | Local | AWS |
|---|---|---|
| Mensageria e DynamoDB | LocalStack (`localhost:4566`) | SNS, SQS e DynamoDB reais |
| Bancos do OS e do Billing | 2 contêineres PostgreSQL (`5433` e `5434`) | 2 instâncias RDS PostgreSQL |
| Serviços | `mvnw spring-boot:run`, portas `8081`, `8082` e `8083` | 3 Deployments no EKS |
| Entrada | `http://localhost:8081` | API Gateway + balanceador do OS Service |
| Pagamento | simulado (ou Mercado Pago, ver [mercado-pago.md](mercado-pago.md)) | simulado |
| E-mail do link de pagamento | SES do LocalStack | não enviado (o link fica no log do Billing) |
| Custo | zero | ≈ US$ 0,35 por hora ligado |

Os comandos são para **Windows PowerShell 5.1**, partindo da raiz do repositório de código (`oficina-workspace`). Nos repositórios
individuais o código de cada serviço está na raiz e os scripts de ambiente ficam no workspace.

---

# Parte A. Ambiente local (LocalStack)

## A.1 Pré-requisitos

| Ferramenta | Verificação |
|---|---|
| Docker Desktop **aberto** (com Compose) | `docker compose version` |
| JDK 21 | `java -version` |
| Git | `git --version` |
| PowerShell 5.1 (ou 7) | `$PSVersionTable.PSVersion` |

O Maven não precisa estar instalado: cada serviço traz o `mvnw`. Portas livres: `5433`, `5434`, `4566`, `8081`, `8082`, `8083`.

## A.2 Passo a passo

**1) Obter o código**

```powershell
git clone https://github.com/HDAdeveloper/oficina-workspace.git
cd oficina-workspace
```

**2) (Opcional) conferir que os testes passam** (~3 a 5 min por serviço, o Docker precisa estar aberto)

```powershell
cd os-service;        .\mvnw.cmd clean verify; cd ..
cd billing-service;   .\mvnw.cmd clean verify; cd ..
cd execution-service; .\mvnw.cmd clean verify; cd ..
```

**3) Subir tudo com um comando**

```powershell
powershell -ExecutionPolicy Bypass -File scripts\local\subir-servicos.ps1 -Seed
```

O script faz, nesta ordem: `docker compose up -d --wait` (2 PostgreSQL + LocalStack), confere que as 6 filas foram criadas (3 filas e 3 DLQ),
libera as portas `8081` a `8083`, abre **uma janela por serviço** (já apontando para o LocalStack e com o e-mail pelo SES local),
espera o `/actuator/health` de cada um e, por causa do `-Seed`, carrega o catálogo (serviços e 20 peças, com estoque).
A primeira execução compila e demora mais. No fim aparece `Ambiente local no ar.`

**4) Conferir**

```powershell
docker compose ps                                  # postgres x2 e localstack "healthy"
curl.exe http://localhost:8081/actuator/health      # {"status":"UP"}  (idem 8082 e 8083)
```

**5) Rodar a Saga ponta a ponta** (6 cenários: caminho feliz, compensações A, B e C, link pago e link vencido)

```powershell
powershell -ExecutionPolicy Bypass -File scripts\local\saga-e2e.ps1
```

Esperado: todas as linhas `[OK]` e `TUDO CERTO: os 6 cenarios passaram.`

**6) Ver os e-mails com o link de pagamento** (SES do LocalStack)

```powershell
powershell -ExecutionPolicy Bypass -File scripts\local\ver-emails.ps1
```

**7) Usar o painel da Saga**

Abra `http://localhost:8081/saga-monitor.html` (usuário `admin`, senha `admin123`). O painel de demonstração cria uma OS no cenário escolhido
e mostra os botões para conduzir cada passo. Swagger de cada serviço: `http://localhost:808x/swagger-ui.html`.

**8) (Opcional) Login por CPF com a Lambda rodando local**

```powershell
cd auth-lambda; .\mvnw.cmd clean package -DskipTests
$env:JWT_SECRET = "dev-only-troque-este-segredo-com-32-bytes-ou-mais"
$env:OS_SERVICE_URL = "http://localhost:8081"
java -cp target\auth-lambda-1.0.0-SNAPSHOT.jar br.com.oficina.auth.LocalServer      # deixe esta janela aberta
```

Em outra janela, na raiz: `.\scripts\local\auth-cpf-e2e.ps1 -Os http://localhost:8081`

**9) Parar**

Feche as três janelas dos serviços (`Ctrl+C`) e:

```powershell
docker compose down        # mantém os dados
docker compose down -v     # apaga bancos e filas (ambiente limpo na próxima subida)
```

## A.3 Sem o script (Linux, macOS ou passo a passo manual)

```bash
docker compose up -d --wait
# um terminal por serviço (as variáveis fazem o serviço falar com o LocalStack, e não com a AWS real)
cd os-service        && AWS_ENDPOINT_URL=http://localhost:4566 EMAIL_PROVEDOR=ses PORT=8081 ./mvnw spring-boot:run
cd billing-service   && AWS_ENDPOINT_URL=http://localhost:4566 EMAIL_PROVEDOR=ses PORT=8082 ./mvnw spring-boot:run
cd execution-service && AWS_ENDPOINT_URL=http://localhost:4566 PORT=8083 ./mvnw spring-boot:run
```

> Um serviço iniciado **sem** `AWS_ENDPOINT_URL` usa a AWS real (o log mostra o aviso `ATENCAO ... AWS REAL`).

## A.4 Problemas comuns

| Sintoma | Causa e solução |
|---|---|
| `porta 8081 em uso` | O script encerra o processo que ocupa a porta; use `-NaoMatar` para só avisar |
| `Faltam filas` | O LocalStack ainda não terminou o script de init. `docker compose logs localstack` |
| Seed falha com `404` na `8080` | O OS Service local roda na `8081`: `scripts\seed\seed-catalogo.ps1 -Os http://localhost:8081 -Execution http://localhost:8083` |
| Login falha | Senha local do admin: `admin123` |
| Cliente sem e-mail é recusado | O e-mail é obrigatório no cadastro (ADR-010). Para uma base antiga, `docker compose down -v` e suba de novo |

---

# Parte B. Ambiente na nuvem (AWS)

Tempo total: 1 h 30 a 2 h; custo: US$ 1 a 2 se destruir no mesmo dia. **Rode o passo B.8 (destruir) ao terminar.**

## B.1 Pré-requisitos

| Item | Verificação |
|---|---|
| Conta AWS com um usuário/perfil administrador e **alerta de orçamento** criado | console AWS → Budgets |
| AWS CLI v2 com o perfil configurado | `aws sts get-caller-identity` |
| Terraform ≥ 1.6 | `terraform version` |
| kubectl | `kubectl version --client` |
| Docker Desktop aberto (builda e envia as imagens) | `docker info` |
| JDK 21 (gera o `.jar` da Lambda) | `java -version` |
| (Opcional) Licença de ingestão do New Relic | só para a observabilidade |

Nunca comite chaves, tokens nem senhas. Os segredos dos serviços são criados pelo script de deploy a partir das saídas do Terraform
(senhas dos bancos e `JWT_SECRET` gerados com `random_password`).

## B.2 Preparar (sem custo)

```powershell
cd oficina-workspace
git pull
$env:AWS_PROFILE = "<seu-perfil>"          # em TODA janela nova
aws sts get-caller-identity                 # confirme a conta

cd auth-lambda; .\mvnw.cmd clean package -DskipTests; cd ..       # gera o .jar enviado à Lambda
Test-Path auth-lambda\target\auth-lambda-1.0.0-SNAPSHOT.jar       # True

cd infra\terraform
copy terraform.tfvars.example terraform.tfvars                    # só na primeira vez; ajuste regiao e github_organizacao
terraform init
terraform plan -out plano.tfplan                                  # revise: ~90 a 100 recursos
cd ..\..
```

## B.3 Criar a infraestrutura (≈ 20 min, **o custo começa aqui**)

```powershell
terraform -chdir=infra\terraform apply plano.tfplan
```

Cria rede (VPC), EKS com 2 nós, 2 RDS PostgreSQL, 4 tabelas DynamoDB, os 2 tópicos SNS e as filas SQS (com DLQ), 3 repositórios ECR,
os papéis IAM (IRSA e da pipeline com OIDC), a Lambda de autenticação e o API Gateway. O EKS leva de 12 a 15 minutos. Se der um erro
transitório (por exemplo no ECR), **repita o `apply`** (agora sem o `-out`).

## B.4 Publicar os serviços no cluster (≈ 15 a 20 min)

```powershell
# opcional: liga o agente New Relic (a licença não vai para o Git)
$env:NEW_RELIC_LICENSE_KEY = Read-Host "Licenca New Relic"
.\scripts\deploy\deploy-aws.ps1
```

O script lê as saídas do Terraform, conecta o `kubectl` ao EKS, constrói e envia as 3 imagens ao ECR, cria namespace, segredos, ConfigMaps
e ServiceAccounts (IRSA), instala o Metrics Server, aplica Deployments, Services e HPAs e espera os pods ficarem prontos.
No fim mostra `OS Service publico em: http://<endereco>`.

```powershell
kubectl -n oficina get pods                        # os 3 servicos "Running" e "1/1"
kubectl -n oficina logs deployment/os-service --tail=50
```

Billing e Execution **não são públicos** (só o OS Service tem balanceador). Para expor só o webhook do Mercado Pago, veja [mercado-pago.md](mercado-pago.md) (seção 6) e rode o script com `-ExporWebhook`.

## B.5 Rotas do API Gateway (≈ 3 min)

Segunda aplicação do Terraform, agora com o endereço do OS Service:

```powershell
terraform -chdir=infra\terraform apply -var os_service_url=http://<endereco-do-passo-B.4>
terraform -chdir=infra\terraform output url_da_api
```

Teste:

```powershell
curl.exe <url_da_api>/actuator/health                                   # {"status":"UP"}
curl.exe -X POST <url_da_api>/auth/cpf -H "Content-Type: application/json" -d "{\"cpf\":\"<cpf-cadastrado>\"}"   # JWT do cliente
```

## B.6 Validar a Saga na AWS (≈ 15 min)

Billing e Execution são internos; para os scripts os alcançarem, abra dois `port-forward` (um por terminal) e deixe-os abertos:

```powershell
kubectl -n oficina port-forward svc/billing-service 8082:80        # terminal A
kubectl -n oficina port-forward svc/execution-service 8083:80      # terminal B
```

Senha do admin (gerada no deploy) e execução dos testes:

```powershell
$b64 = kubectl -n oficina get secret os-service-secrets -o jsonpath='{.data.ADMIN_SENHA}'
$senha = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($b64))
$os = "http://<endereco-do-passo-B.4>"

powershell -ExecutionPolicy Bypass -File scripts\seed\seed-catalogo.ps1 -Os $os -Execution http://localhost:8083 -Senha $senha
powershell -ExecutionPolicy Bypass -File scripts\local\saga-e2e.ps1 -Os $os -Billing http://localhost:8082 -Execution http://localhost:8083 -Senha $senha
```

Esperado: `TUDO CERTO: os 6 cenarios passaram.` O painel também funciona em `$os/saga-monitor.html`.

## B.7 Observabilidade (opcional, ≈ 20 min)

Com a licença do New Relic configurada no passo B.4, os três serviços aparecem em **APM & Services** em até 2 minutos. Para importar o painel
e criar os alertas, siga [observabilidade/README.md](observabilidade/README.md).

## B.8 Destruir tudo (≈ 15 min) — **não pule**

```powershell
kubectl delete svc os-service-publico -n oficina               # remove o balanceador antes (senão o destroy trava na VPC)
kubectl delete svc billing-service-publico -n oficina --ignore-not-found
terraform -chdir=infra\terraform destroy
terraform -chdir=infra\terraform state list                    # deve vir vazio
```

Se o `destroy` falhar por dependências de rede (VPC/ENI), rode-o de novo; se persistir, apague o balanceador e as interfaces de rede
pelo console e repita.

## B.9 Pipeline com deploy automático (opcional)

Cada serviço tem o seu repositório e o seu pipeline (GitHub Actions: build, testes, cobertura mínima de 80%, Sonar, imagem no ECR e deploy
com `kubectl rollout`). O deploy só roda quando o repositório tem a variável `DEPLOY_ENABLED=true`. Em **Settings → Secrets and variables → Actions**:

| Tipo | Nome | Valor |
|---|---|---|
| Secret | `AWS_ROLE_ARN` | saída `role_da_pipeline` do Terraform |
| Variable | `AWS_REGION` | `us-east-1` |
| Variable | `EKS_CLUSTER_NAME` | `oficina-eks` |
| Variable | `DEPLOY_ENABLED` | `true` (apague depois de usar, para não tentar implantar com o cluster destruído) |

O acesso à AWS usa **OIDC**: não há chave de acesso salva no GitHub; a pipeline assume um papel temporário restrito aos repositórios da organização.

## B.10 Problemas comuns

| Sintoma | Causa e solução |
|---|---|
| `rollout status` estoura o tempo | `kubectl -n oficina describe pod <pod>` e `logs`; verifique a memória do JVM (limite 1Gi), o acesso ao RDS e as probes |
| `docker login` no ECR recusado (400) | Use o script (ele passa a senha como argumento, que evita a quebra de linha do PowerShell 5.1) |
| Pods sem acesso ao SQS/SNS | Confira os papéis IRSA: `kubectl -n oficina describe sa <servico>` deve mostrar a anotação `eks.amazonaws.com/role-arn` |
| `terraform destroy` trava | Apague os balanceadores (passo B.8) e tente de novo |
| Conta errada | `aws sts get-caller-identity` e confira `$env:AWS_PROFILE` |
