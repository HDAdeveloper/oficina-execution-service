# Guia de gravação: os 4 clipes de tela (passo a passo)

Tudo em **Windows PowerShell 5.1**, a partir de `C:\Projetos\oficina-workspace`. **Um comando por linha.** Se algo não bater com o que está escrito, pare e me mande a tela.
Grave **cada clipe separado** (se errar, regrava só ele). Ferramentas: **Win+Alt+R** (Xbox Game Bar) grava a janela em foco; ou OBS (tela inteira, 1080p).
Regra de ouro: **pause a gravação** antes de digitar qualquer senha ou chave e retome depois. Nunca dê `echo` na senha do admin.

## 0. Preparação (fora do vídeo, ~10 min)

O ambiente da AWS precisa estar no ar (`kubectl -n oficina get pods` com tudo `Running`). Em **cada janela nova** do PowerShell:

```powershell
cd C:\Projetos\oficina-workspace
$env:AWS_PROFILE = "oficina"
$svc = kubectl -n oficina get svc os-service-publico -o json | ConvertFrom-Json
$OS = "http://" + $svc.status.loadBalancer.ingress[0].hostname
$OS
$sec = kubectl -n oficina get secret os-service-secrets -o json | ConvertFrom-Json
$ADMIN = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($sec.data.ADMIN_SENHA))
```

(`$OS` deve mostrar `http://<algo>.elb.amazonaws.com`. Não escreva `$ADMIN` na tela.)

Duas janelas extras, **sempre abertas durante a gravação** (se os pods reiniciarem, feche e repita):

```powershell
kubectl -n oficina port-forward svc/billing-service 8082:80
```
```powershell
kubectl -n oficina port-forward svc/execution-service 8083:80
```

Teste rápido (deve dar `UP` nas três):

```powershell
(Invoke-RestMethod "$OS/actuator/health").status
(Invoke-RestMethod "http://localhost:8082/actuator/health").status
(Invoke-RestMethod "http://localhost:8083/actuator/health").status
```

Se o catálogo não foi carregado: `powershell -ExecutionPolicy Bypass -File scripts\seed\seed-catalogo.ps1 -Os $OS -Execution http://localhost:8083 -Senha $ADMIN`

Abas do navegador, já abertas e logadas, **nesta ordem**: (1) `$OS/saga-monitor.html`, (2) Swagger `$OS/swagger-ui.html`, (3) GitHub (repo `oficina-os-service`), (4) SonarQube Cloud, (5) console AWS (EKS), (6) New Relic (painel **Oficina Tech Service — Operação, Saga e Observabilidade**).
Desligue notificações, aumente a fonte do terminal (Ctrl + `+`) e feche e-mail e chat.

---

## Clipe A: demonstração ponta a ponta (alvo 4:00)

**Fala:** bloco 4 de [`roteiro-narracao.md`](roteiro-narracao.md). Se falar mais devagar, corte as explicações das compensações.

1. Abra a aba **Monitor da Saga** (`$OS/saga-monitor.html`). *Pause a gravação*, entre com `admin` e a senha (para ver a senha, só em outra janela: `$ADMIN` **fora da gravação**), preencha as URLs `http://localhost:8082` (Billing) e `http://localhost:8083` (Execution), *retome*.
2. **Cenário 1 (≈ 1 min):** no seletor, *Pagamento por link* → **Criar OS de demonstração** → aprovar o orçamento escolhendo link → **Simular pagamento** → iniciar e finalizar a execução. Mostre o estado indo até *Concluída*.
3. **Cenário 2 (≈ 35 s):** *Compensação A: saldo insuficiente* → criar → aprovar. A OS volta para o diagnóstico.
4. **Cenário 3 (≈ 50 s):** *Compensação B: sem estoque* → criar → aprovar. Mostre o **estorno** na tabela de mensagens.
5. **Cenário 4 (≈ 50 s):** *Compensação C: execução falha* → criar → aprovar → **Falhar execução**. Mostre a ordem: liberar peças → estornar → cancelar.
6. Feche mostrando a tabela de mensagens (diário da Saga) e, no Swagger do Execution (`http://localhost:8083/swagger-ui.html`), `GET /api/estoque`.

**Plano B** se o monitor travar (rode no terminal, mostra os 6 cenários com ✔):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\local\saga-e2e.ps1 -Os $OS -Billing http://localhost:8082 -Execution http://localhost:8083 -Senha $ADMIN
```

(A senha vai na linha de comando e **aparece na tela**: use isso só com a gravação pausada durante a digitação, ou limpe o terminal com `cls` antes de retomar.)

---

## Clipe B: testes, cobertura e BDD (alvo 1:20)

**Fala:** bloco 5 do roteiro. **Antes de gravar** (leva alguns minutos), para ter números de hoje:

```powershell
cd C:\Projetos\oficina-workspace\os-service
.\mvnw.cmd clean verify
cd ..
powershell -ExecutionPolicy Bypass -File scripts\cobertura.ps1 os-service
```

Repita com `billing-service` e `execution-service` (anote os 3 percentuais e **troque os `__%` na fala**). Docker Desktop precisa estar aberto (testes de integração).

**Gravando:**

1. Terminal: `powershell -ExecutionPolicy Bypass -File scripts\cobertura.ps1 os-service` (mostra a linha com o percentual).
2. `start os-service\target\site\jacoco\index.html` (relatório JaCoCo, 10 s).
3. Aba do **SonarQube Cloud**: painel do projeto, sem vulnerabilidades abertas (confira antes).
4. Editor: abra `os-service\src\test\resources\features\saga_ordem_servico.feature` e role pelos cenários (caminho feliz, compensações, mensagem repetida).

---

## Clipe C: CI/CD e deploy automatizado (alvo 1:20)

O enunciado exige mostrar o **deploy automatizado de ao menos um serviço, com validação dos testes**. Prepare **antes** de gravar:

1. Pegue o ARN do papel: `terraform -chdir=infra\terraform output role_da_pipeline`
2. No GitHub, repo **oficina-os-service** → *Settings* → *Secrets and variables* → *Actions*:
   - Secret `AWS_ROLE_ARN` = o valor do passo 1.
   - Variables: `AWS_REGION` = `us-east-1`, `EKS_CLUSTER_NAME` = `oficina-eks`, `DEPLOY_ENABLED` = `true`.
3. Crie uma mudança pequena em um branch e abra um Pull Request (ex.: uma linha no README).

**Gravando:**

1. Mostre o **Pull Request** com os checks rodando e ficando verdes (`build-test`).
2. Faça o **merge**. Abra a aba **Actions** e mostre a execução da `main` passando por: build e testes → Sonar → imagem Docker → ECR → **deploy**.
3. No terminal, mostre o resultado do deploy:

```powershell
kubectl -n oficina rollout status deployment/os-service
kubectl -n oficina get pods
```

4. Mostre rapidamente *Settings → Rules* (a `main` protegida, PR obrigatório).

**Depois de gravar**: volte `DEPLOY_ENABLED` para vazio (ou apague a variável).
Se a pipeline falhar na gravação, **corte e regrave** o clipe; não improvise na hora. Em caso de erro, copie a mensagem do job e me mande.

---

## Clipe D: nuvem, Kubernetes e observabilidade (alvo 1:20)

**Fala:** bloco 7 do roteiro. Grave **com o ambiente no ar e logo depois de rodar uma Saga** (Clipe A), para o New Relic ter dados frescos.

1. Terminal:

```powershell
kubectl get nodes
kubectl -n oficina get pods
kubectl -n oficina get svc
terraform -chdir=infra\terraform state list | Measure-Object
```

2. Console AWS (aba pronta): EKS → cluster `oficina-eks`; RDS (2 bancos); DynamoDB; SNS/SQS (filas e DLQ). 5 a 10 s em cada.
3. API Gateway (rota pública):

```powershell
$api = terraform -chdir=infra\terraform output -raw url_da_api
(Invoke-RestMethod "$api/actuator/health").status
```

4. **New Relic** (painel Oficina Tech Service): página *Ordens de serviço e Saga* (tabela **Últimas Sagas**), *Serviços e recursos*, *Logs e rastreio* (logs da Saga mais recente com o `correlationId`). Depois *APM & Services → Distributed tracing* (se aparecer o trace entre serviços, mostre).
5. **Alertas:** *Alerts → Alert conditions* (as 5 condições) e, se quiser, provoque o alerta de falha: rode no Monitor da Saga o cenário **Compensação B**; em alguns minutos chega o e-mail em rickd3ll@gmail.com (mostre a caixa de entrada só se não tiver nada pessoal visível).

---

## Depois de gravar

1. **Destrua a AWS** (cada hora custa ≈ US$ 0,30):

```powershell
kubectl delete svc os-service-publico -n oficina --ignore-not-found
terraform -chdir=infra\terraform destroy
terraform -chdir=infra\terraform state list
```

   O último comando deve vir vazio. Depois me peça "verifique a AWS".
2. Monte o vídeo conforme [`video/narracao-marcada.md`](video/narracao-marcada.md) (≤ 15 min), suba no YouTube como **não listado** e teste o link numa janela anônima.
3. Monte o PDF: participantes, links dos 5 repositórios, link do vídeo, diagrama de arquitetura (use o slide `docs/video/slides/03_arquitetura.png`) e o resumo da estratégia da Saga (ADR 002).
