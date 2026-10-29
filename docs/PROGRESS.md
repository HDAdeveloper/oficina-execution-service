# PROGRESS — acompanhamento por etapa

Legenda: ⬜ não iniciado · 🟨 em andamento · 🟦 entregue pelo Claude, aguardando validação do grupo · ✅ validado pelo grupo

Regra: uma etapa só vira ✅ depois que o grupo roda os comandos da etapa e confirma o resultado.
Cada etapa traz, no fim, a seção **Como rodar e testar**.

## Cronograma (prazo final: 10/11/2026)
| Etapa | Escopo | Janela alvo | Status |
|---|---|---|---|
| 0 | Ambiente do grupo + repositório de trabalho | 21–22/10 | 🟨 |
| 1 | Fundações: contratos de eventos, ADRs, docker-compose com LocalStack e bancos | 22–24/10 | ✅ |
| 2 | OS Service (domínio, API, JWT, Postgres, testes) | 24–28/10 | ✅ |
| 3 | Billing Service (orçamento, pagamento simulado, estorno, testes) | 28–30/10 | ✅ (concluída em 23/10) |
| 4 | Execution Service (DynamoDB, fila, estoque, testes) | 30/10–01/11 | ✅ (concluída em 23/10) |
| 5 | Saga orquestrada + compensações + BDD do fluxo completo | 01–03/11 | ✅ (concluída em 23/10) |
| 6 | Lambda de autenticação por CPF + API Gateway | 03–04/11 | ✅ local (concluída em 23/10); API Gateway na 7a |
| 7 | Dockerfiles, K8s (HPA), Terraform AWS, CI/CD, SonarQube, observabilidade | 04–06/11 | 🟨 Terraform e deploy escritos |
| 8 | READMEs, diagramas, split em repositórios, roteiro do vídeo, PDF | 06–07/11 | ⬜ |
| 9 | Deploy real na AWS, ensaio e gravação do vídeo (grupo) | 07–09/11 | ⬜ |
| — | Entrega no portal | 10/11 | ⬜ |

## Etapa 0 — Ambiente e repositório
Pendente do grupo:
- [x] Docker 29 e Git 2.54 instalados (Windows 11)
- [x] JDK 21 instalado (Temurin 21.0.12)
- [x] Repositório `oficina-workspace` criado (HDAdeveloper) e anexado à sessão do Claude
- [x] Decidido: conta AWS pessoal
- [x] Conta AWS criada; AWS CLI usando o usuário IAM `oficina-dev` (conta 528757785383)
- [ ] MFA no usuário root e alerta de orçamento (US$ 20) — confirmar
- [x] Decidido: New Relic (plano gratuito)
- [ ] Conta New Relic criada e chave de licença guardada (fora do Git)
- [ ] Conta de teste do Mercado Pago criada e Access Token de teste guardado (fora do Git)

### Como rodar e testar (Etapa 0)
```bash
java -version        # esperado: 21.x
mvn -version         # esperado: 3.9+ (ou use o mvnw que o projeto trará)
docker --version && docker compose version
git --version
aws --version        # AWS CLI v2 (necessário a partir da Etapa 7)
terraform -version   # >= 1.6 (necessário a partir da Etapa 7)
```
Envie ao Claude a saída desses comandos.

## Log de decisões e entregas
- 21/10/2026 — Decisões fechadas (ver `CONTEXT.md`): Java 21 + AWS, Saga orquestrada, status `CANCELADA`,
  pagamento simulado, refazer Fases 1–4 do zero já na arquitetura final.
- 21/10/2026 — Criados `CONTEXT.md` e `PROGRESS.md`.
- 22/10/2026 — Repositório `HDAdeveloper/oficina-workspace` anexado; desenvolvimento 100% local; New Relic gratuito; conta AWS pessoal; conta de teste do Mercado Pago em criação. Aguardando a saída dos comandos de verificação do ambiente para iniciar a Etapa 1.
- 22/10/2026 — Ambiente verificado: Docker e Git ok; AWS CLI no usuário `oficina-dev`. Pendências: JDK 21, New Relic, Mercado Pago.
- 22/10/2026 — **Etapa 1 entregue (aguardando validação do grupo):** `docker-compose.yml` (Postgres OS e Billing + LocalStack 4.12 com SNS/SQS/DynamoDB),
  script de init da mensageria, smoke test, `docs/eventos.md` (contrato da Saga) e ADRs 001–005.
  O LocalStack foi fixado na 4.12 porque a imagem atual exige conta e token (ADR-005).
  O Claude não tem Docker no seu ambiente: o init foi testado só com um `awslocal` simulado.
- 22/10/2026 — Decisões: qualidade com SonarQube Cloud (gratuito); pagamento simulado agora e adapter real do Mercado Pago depois, sem mudar o domínio (porta `PagamentoGateway`).

## Etapa 1 — Fundações (✅ smoke test 100% OK em 22/10)

### Como rodar e testar (PowerShell ou Git Bash, na pasta do repositório)
```powershell
git clone https://github.com/HDAdeveloper/oficina-workspace.git   # ou: git pull
cd oficina-workspace
docker compose up -d --wait
docker compose ps
docker compose exec localstack bash -c "cd /scripts && bash smoke-test.sh"
```
Esperado:
- `docker compose up --wait` termina sem erro e `docker compose ps` mostra os 3 containers como `healthy`.
- O smoke test imprime `[OK]` em todas as linhas e termina com `RESULTADO: tudo certo.`

Se algo falhar:
- `docker compose logs localstack` mostra a criação dos recursos (linhas `[init] ...`).
- Erro `\r: command not found` nos scripts: finais de linha do Windows. Rode `git add --renormalize .` e recrie os containers.
- Erro ao baixar a imagem `localstack/localstack:4.12`: confirme com `docker pull localstack/localstack:4.12`; se a tag não existir, informe ao Claude.
- No Git Bash, `bash /scripts/...` falha com `C:/Program Files/Git/scripts/...`: o Git Bash converte caminhos iniciados por `/`. Use o comando acima (com `bash -c "cd /scripts && ..."`).
- Porta ocupada (5433, 5434 ou 4566): feche o programa que a usa ou mude a porta no compose.

Para encerrar: `docker compose down -v`.

Envie ao Claude a saída de `docker compose ps` e do smoke test.
**`docker compose ps` já foi enviado (3 containers healthy). Falta a saída do smoke test para validar a Etapa 1.**

## Etapa 2 — OS Service (✅ concluída em 22/10)
Fatias: **2a** domínio (✅) · **2b** casos de uso e portas (✅) · **2c** persistência (JPA + Flyway, ✅) · **2d** API REST + JWT + Swagger (✅) · **2e** cobertura ≥ 80%, Dockerfile, manifests Kubernetes e pipeline (✅).

### Etapa 2a — Domínio (✅)
Pacote `br.com.oficina.os`. Domínio em Java puro (sem Spring/Lombok): `StatusOS` (com `CANCELADA`), `Documento` (CPF/CNPJ), `Placa`, `Cliente`, `Veiculo`,
`Servico`, `Peca`, itens da OS, `HistoricoStatus` e o agregado `OrdemServico`. As regras de fluxo seguem `docs/eventos.md`.
O Claude compilou o domínio com `javac` e exercitou 46 verificações num harness descartável (todas passaram), mas **não rodou os testes JUnit**.

#### Como rodar e testar (na pasta `os-service`)
O `pom.xml` fica em `os-service/`: rode os comandos **de dentro dessa pasta**.
```powershell
cd C:\Projetos\oficina-workspace\os-service
.\mvnw.cmd -version    # a linha "Java version" precisa mostrar 21.x
.\mvnw.cmd test        # PowerShell/CMD; no Git Bash: ./mvnw test
.\mvnw.cmd verify      # inclui a checagem de cobertura mínima de 80%
```
Se `-version` mostrar Java 25, o Maven está usando o JDK errado (o wrapper lê `JAVA_HOME` antes do PATH):
```powershell
Get-ChildItem "C:\Program Files\Eclipse Adoptium"            # descubra o nome da pasta jdk-21...
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\<pasta-jdk-21>"   # vale para esta janela
.\mvnw.cmd -version
```
Para fixar de vez: Configurações do Windows > "Editar as variáveis de ambiente do sistema" > Variáveis de ambiente > `JAVA_HOME`.
Avisos `WARNING: A restricted method...` / `sun.misc.Unsafe` indicam Java 24+ em uso.
Esperado: `Tests run: ... Failures: 0, Errors: 0` e `BUILD SUCCESS`. Na primeira vez o Maven baixa dependências (alguns minutos).
O relatório de cobertura fica em `os-service/target/site/jacoco/index.html`.
Se falhar, envie ao Claude o trecho com `[ERROR]` (e o nome do teste que falhou).

### Etapa 2b — Casos de uso e portas (✅)
Pacote `application`: portas de entrada (`GestaoClientes`, `GestaoVeiculos`, `GestaoServicos`, `GestaoPecas`, `GestaoOrdensServico`),
portas de saída (repositórios, `GeradorNumeroOS`, `SagaCommandPort`) e os serviços `ClienteService`, `VeiculoService`, `ServicoService`,
`PecaService`, `OrdemServicoService`. Continua Java puro, sem Spring.
Regras: abrir OS exige cliente e veículo ativos e do mesmo dono; documento, placa e código de peça duplicados dão conflito (409);
`solicitarOrcamento` e `aprovarOrcamento` só **enviam comandos** da Saga (o status muda quando o orquestrador receber o evento, Etapa 5);
recusar o orçamento volta a OS para `EM_DIAGNOSTICO` e manda `CANCELAR_ORCAMENTO`; cancelamento manual só em `RECEBIDA`/`EM_DIAGNOSTICO`.
Os testes usam repositórios em memória (`Fakes.java`) e um `SagaCommandPort` que grava os comandos enviados.
O Claude compilou tudo com `javac` e rodou um harness descartável com 18 verificações (passaram), mas **não rodou os testes JUnit**.

#### Como rodar e testar (na pasta `os-service`)
```powershell
cd C:\Projetos\oficina-workspace
git pull
cd os-service
.\mvnw.cmd test       # Git Bash: ./mvnw test
```
Esperado: `Failures: 0, Errors: 0` e `BUILD SUCCESS`. Envie a linha `Tests run: N, ...` do final
(testes novos: `CadastrosServiceTest` e `OrdemServicoServiceTest`).

**Validação (22/10, 19:36):** `./mvnw test` → 110 testes, 0 falhas, 0 erros, `BUILD SUCCESS`, 34 classes analisadas pelo JaCoCo. A cobertura ≥ 80% será conferida com `verify` na fatia 2e.

### Etapa 2c — Persistência (✅)
Pacote `infrastructure`: entidades JPA separadas do domínio (`*Entity`, `*Emb`), repositórios Spring Data (`*Jpa`), `PersistenciaMapper`,
um adaptador por porta de saída (`*RepositoryAdapter`), `TransacaoProxy` (cada caso de uso roda em uma transação, sem Spring na camada de aplicação),
`ApplicacaoConfig` (liga serviços e portas), `GeradorNumeroOSPadrao` (`OS-2026-1A2B3C4D`) e `SagaCommandLogger` (provisório: só registra os comandos em log até a Etapa 5).
Banco: migração `V1__criar_schema.sql` (Flyway) é a única dona do esquema; o Hibernate roda com `ddl-auto=validate` e falha se as entidades não baterem com ela.
A OS tem coluna `versao` (bloqueio otimista): duas alterações simultâneas da mesma OS não se sobrescrevem.
Itens e histórico da OS ficam em tabelas filhas (`os_item_servico`, `os_item_peca`, `os_historico`) com ordem preservada.
Limites de tamanho de texto (nome, descrição etc.) são conferidos pelo banco; a API (2d) validará os tamanhos antes e devolverá 400.

**Atenção:** o Claude não consegue baixar dependências nem rodar Docker no seu ambiente, então **nada desta fatia foi compilado ou executado por ele**
(só revisado à mão; a classe `GeradorNumeroOSPadrao` foi compilada com `javac`). Se aparecer erro, é esperado: envie o trecho `[ERROR]` que eu corrijo.

#### Como rodar e testar (na pasta `os-service`)
Os testes de persistência sobem um PostgreSQL de verdade em contêiner (Testcontainers), então **o Docker Desktop precisa estar aberto**.
```powershell
cd C:\Projetos\oficina-workspace
git pull
cd os-service
.\mvnw.cmd test       # Git Bash: ./mvnw test
```
A primeira execução baixa as dependências novas (Spring Data JPA, Flyway, driver Postgres, Testcontainers) e a imagem `postgres:16-alpine`: pode levar alguns minutos.
Esperado: `BUILD SUCCESS` e mais classes de teste: `TransacaoProxyTest`, `GeradorNumeroOSPadraoTest`, `RepositoriosPersistenciaTest`, `FluxoComBancoTest`.
Se falhar, envie o trecho com `[ERROR]` e o nome do teste. Erros comuns: Docker fechado (`Could not find a valid Docker environment`) ou JDK diferente de 21.

**Ocorrência (22/10, Docker 29.4):** 15 testes de banco falharam com `ExceptionInInitializerError` em `PostgresTestBase`. Causa provável: o Docker 29 exige API ≥ 1.44 e o Testcontainers 1.x usa 1.32. Correção: `src/test/resources/docker-java.properties` com `api.version=1.44`.
**2ª rodada (22/10, 20:19):** o Docker passou a funcionar; 129 de 131 testes OK. Os 2 que falharam eram erros dos próprios testes (data de início só existe após `iniciarExecucao`; banco compartilhado entre testes). Corrigidos.
Se ainda falhar, o motivo real está no início de `os-service/target/surefire-reports/br.com.oficina.os.infrastructure.persistence.FluxoComBancoTest.txt` (procure `Caused by`).

**Validação (22/10, 20:28):** `./mvnw test` com Docker 29.4 → 131 testes, 0 falhas, `BUILD SUCCESS` (inclui PostgreSQL real via Testcontainers, Flyway, bloqueio otimista).

### Etapa 2d — API REST, JWT e Swagger (✅)
Pacote `infrastructure.web` (controladores, DTOs com validação, `ApiExceptionHandler`) e `infrastructure.seguranca` (JWT HS256 com Spring Security).
- **Endpoints** (`/api/...`): `auth/login`; `clientes`, `veiculos`, `servicos`, `pecas` (cadastro, consulta, atualização, desativação); `ordens-servico` (abrir, listar, buscar, status, histórico, diagnóstico, adicionar serviço/peça, orçamento, aprovação, recusa, cancelamento, entrega).
- **Erros** no padrão RFC 7807 (`application/problem+json`): 400 dados inválidos (com `erros` por campo), 401 sem token, 404, 409 (duplicidade, transição inválida, alteração concorrente), 422 (regra de negócio), 500 sem vazar detalhes.
- **Segurança:** tudo exige `Authorization: Bearer <token>`, exceto login, Swagger e `/actuator/health`. Usuário e senha do administrador e o segredo do JWT vêm de variáveis de ambiente (`ADMIN_USUARIO`, `ADMIN_SENHA`, `JWT_SECRET`); os padrões do `application.yml` valem **só para desenvolvimento**. O acesso do cliente final por CPF (Lambda + API Gateway) entra na Etapa 6.
- **Orçamento e aprovação** só disparam comandos da Saga (resposta `202 Accepted`); o status muda quando o Billing/Execution responderem (Etapa 5). Por isso, no teste ponta a ponta, aprovar/recusar logo após pedir o orçamento devolve 422.
- **Swagger:** `/swagger-ui.html` (botão *Authorize* com o token). Contrato: `/v3/api-docs`. Coleção Postman: `os-service/docs/postman/oficina-os-service.postman_collection.json`.

**Atenção:** como na 2c, o Claude não compilou nem executou esta fatia (sem acesso ao Maven Central).

#### Como rodar e testar
**1) Testes automáticos** (Docker Desktop aberto):
```powershell
cd C:\Projetos\oficina-workspace
git pull
cd os-service
.\mvnw.cmd test       # Git Bash: ./mvnw test
```
Esperado: `BUILD SUCCESS`. Classes novas: `AuthControllerTest`, `ClienteControllerTest`, `OrdemServicoControllerTest`, `ApiFluxoCompletoTest`.

**2) Subir a API de verdade e testar no Swagger** (outro terminal, na raiz do repositório):
```powershell
docker compose up -d            # sobe os bancos e o LocalStack
cd os-service
.\mvnw.cmd spring-boot:run      # Git Bash: ./mvnw spring-boot:run
```
Quando aparecer `Started OsServiceApplication`, abra http://localhost:8080/swagger-ui.html:
1. `POST /api/auth/login` com `{"usuario":"admin","senha":"admin123"}` e copie o `token`.
2. Clique em **Authorize**, cole o token e confirme.
3. Cadastre cliente, veículo, serviço e peça; abra uma OS e avance (diagnóstico, orçamento). Ou importe a coleção do Postman e rode em ordem.
Se a porta 8080 estiver ocupada (Erro `Port 8080 was already in use`), use outra: PowerShell `$env:PORT = "8081"`; Git Bash `PORT=8081 ./mvnw spring-boot:run` (e ajuste a variável `baseUrl` no Postman).
Se aparecerem `ClassNotFoundException`/`NoClassDefFoundError` em classes que existem, a pasta `target` ficou inconsistente: rode `.\mvnw.cmd clean test`.
Para parar a API: `Ctrl+C`. Saúde: http://localhost:8080/actuator/health.

**Validação (22/10, 21:53):** `./mvnw clean test` → BUILD SUCCESS; API subiu com `spring-boot:run` (porta 8081) e funcionou no Swagger.

### Etapa 2e — Cobertura, Docker, Kubernetes e CI (✅)
- **Cobertura:** `./mvnw verify` aplica a regra de 80% de linhas; o grupo rodou em 22/10 e passou. O relatório fica em `os-service/target/site/jacoco/index.html`; onde achar cada relatório, como lê-lo e o que guardar como evidência está em [`docs/cobertura.md`](cobertura.md). Resumo com percentuais: `bash scripts/cobertura.sh os-service --salvar` (ou `scripts\cobertura.ps1`).
- **Dockerfile** (`os-service/Dockerfile`): build em dois estágios, imagem final `eclipse-temurin:21-jre-alpine`, usuário sem root.
- **Kubernetes** (`os-service/k8s/`): `namespace`, `configmap`, `deployment` (2 réplicas, sondas de saúde, requests/limits), `service` e `hpa` (2 a 5 réplicas por CPU). O `Secret` **não** vai no Git: há só um exemplo em `k8s/exemplos/`.
- **Pipeline** (`os-service/.github/workflows/ci.yml`): build + testes + `verify` (cobertura) + Sonar (se `SONAR_TOKEN` existir) + build da imagem; na `main`, com `DEPLOY_ENABLED=true`, publica no ECR e faz deploy no EKS. Enquanto a AWS/Sonar não estiverem configuradas, a pipeline roda só até o build da imagem.
  Segredos e variáveis do GitHub que a Etapa 7 vai pedir: `SONAR_TOKEN`, `AWS_ROLE_ARN`; variáveis `SONAR_ORGANIZATION`, `SONAR_PROJECT_KEY`, `AWS_REGION`, `EKS_CLUSTER_NAME`, `DEPLOY_ENABLED`.
- Os manifestos e a pipeline foram só validados como YAML; **não foram aplicados nem executados** (sem cluster nem GitHub Actions no ambiente do Claude).

#### Como rodar e testar
**1) Imagem Docker local** (Docker Desktop aberto; bancos no ar com `docker compose up -d` na raiz):
```powershell
cd C:\Projetos\oficina-workspace\os-service
docker build -t oficina/os-service:local .
docker run --rm -p 8081:8080 -e DB_URL=jdbc:postgresql://host.docker.internal:5433/oficina_os oficina/os-service:local
```
Abra http://localhost:8081/swagger-ui.html e http://localhost:8081/actuator/health (deve mostrar `UP`). `Ctrl+C` para parar.
Se o build falhar, envie o trecho com `ERROR`.

**2) Pipeline:** só roda depois que este serviço for para um repositório do GitHub (Etapa 8). Até lá, o equivalente local é `.\mvnw.cmd clean verify`.

**Validação (22/10, 22:16):** `./mvnw clean verify` passou (regra de 80%) e a imagem Docker construída e executada localmente respondeu `UP` em `/actuator/health`.

## Etapa 3 — Billing Service (✅ concluída e validada em 23/10: cobertura 90,5% das linhas; `billing-e2e.sh` no LocalStack passou nos 4 cenários)
Fatias: **3a** domínio e casos de uso (✅ validado: 53 testes, `clean verify` com sucesso) · **3b** persistência (JPA + Flyway, outbox, mensagens processadas) (✅ validado, `clean verify` com sucesso em 22/10) · **3c** mensageria SQS/SNS + API de consulta + JWT (✅ validado, `clean verify` com sucesso em 22/10) · **3d** Dockerfile, k8s e CI (✅ imagem Docker validada em 23/10: `/actuator/health` = UP).
Pasta `billing-service/` (pacote `br.com.oficina.billing`), mesma arquitetura hexagonal do OS Service.

### Etapa 3a — Domínio e casos de uso (✅)
- **Domínio:** `Orcamento` (PENDENTE → PAGO → ESTORNADO, ou PENDENTE → CANCELADO), `ItemOrcamento` (valores em 2 casas), `Pagamento` (APROVADO / RECUSADO / ESTORNADO; recusas também ficam registradas).
- **`BillingService`** trata os 4 comandos da Saga com **duas camadas de idempotência**: pelo `messageId` (mensagem entregue duas vezes) e pela regra de negócio (mesmo pedido em mensagens diferentes não cria dois orçamentos nem cobra duas vezes). Respostas e falhas por comando: ver "Comportamento do Billing" em [`eventos.md`](eventos.md).
- **Pagamento atrás de uma porta** (`PagamentoGateway`) com adaptador **simulado** (`PagamentoGatewaySimulado`): o campo `metodo` escolhe o resultado (`APRO`, `FUND`, `OTHE`, `ERRO`, `ERRO_ESTORNO`). O adaptador do Mercado Pago entrará atrás da mesma porta quando houver Access Token de teste.
- Os eventos de resposta saem por `PublicadorEventos`, que na 3b grava numa tabela de **outbox** na mesma transação do estado.
- O Claude compilou tudo com `javac` e rodou um harness descartável com 22 verificações (passaram), mas **não rodou os testes JUnit**.

#### Como rodar e testar (na pasta `billing-service`)
```powershell
cd C:\Projetos\oficina-workspace
git pull
cd billing-service
.\mvnw.cmd -version     # a linha "Java version" precisa mostrar 21.x (ajuste JAVA_HOME como na Etapa 2a)
.\mvnw.cmd clean verify  # Git Bash: ./mvnw clean verify
```
Esperado: `Failures: 0, Errors: 0`, `BUILD SUCCESS` (a regra de 80% de cobertura também é aplicada). Envie a linha `Tests run: N`.
Testes: `OrcamentoTest`, `BillingServiceTest`, `ConsultaOrcamentosServiceTest`, `PagamentoGatewaySimuladoTest`.

### Etapa 3b — Persistência (✅)
- **Banco próprio** (Postgres, `oficina_billing`, porta 5434 no `docker-compose.yml`) com esquema criado pelo **Flyway** (`V1__criar_schema.sql`); o Hibernate só valida (`ddl-auto=validate`).
- Tabelas: `orcamento` + `orcamento_item`, `pagamento`, `mensagem_processada` (idempotência por `messageId`) e `outbox_evento` (eventos gravados **na mesma transação** do estado; o envio ao SNS é a 3c).
- **Travas no próprio banco:** índice único parcial → no máximo 1 orçamento ativo (PENDENTE/PAGO) por OS e 1 pagamento APROVADO por orçamento. Cobrar, cancelar e estornar leem o orçamento com `SELECT ... FOR UPDATE` (`buscarPorIdParaAtualizar`), então comandos simultâneos não passam pela mesma checagem.
- Transações via `TransacaoProxy` (igual ao OS Service); entidades JPA ficam só na infraestrutura.
- **Não foi compilado nem testado pelo Claude** (Spring/JPA/Testcontainers); a validação é sua.

#### Como rodar e testar (na pasta `billing-service`, com o Docker Desktop aberto)
```powershell
cd C:\Projetos\oficina-workspace
git pull
cd billing-service
.\mvnw.cmd clean verify   # Git Bash: ./mvnw clean verify
```
Esperado: `BUILD SUCCESS`, `Failures: 0, Errors: 0`. Novos testes: `RepositoriosPersistenciaTest` (9, banco real) e `FluxoComBancoTest` (5: criar/cobrar, mensagem repetida, recusa, estorno, cancelamento). Envie a linha `Tests run: N` (ou o erro).
Se algum teste de banco falhar com `Failed to load ApplicationContext`, a causa real fica no relatório do surefire; para listar só as causas:
```powershell
Select-String -Path target\surefire-reports\*.txt -Pattern "Caused by" | Select-Object -First 8 | ForEach-Object { $_.Line.Trim() }
```

### Etapa 3c — Mensageria, API de consulta e JWT (✅)
- **Entrada:** `SqsComandoListener` lê a fila `billing-commands` (long polling, thread própria). `ConsumidorComandos` lê o envelope, `ComandoMapper` o converte em comando e o caso de uso processa. A mensagem só é apagada da fila se deu certo; se falhar, volta e, na 3ª vez, vai para a DLQ (`billing-commands-dlq`). Mensagem que nunca será entendida (JSON quebrado, tipo desconhecido, campo obrigatório ausente) segue o mesmo caminho e termina na DLQ.
- **Pedido com item inválido** (quantidade 0, tipo desconhecido, número inválido) **não** vai para a DLQ: o Billing responde `ORCAMENTO_FALHOU` com o motivo, como previsto no contrato.
- **Saída:** `OutboxRelay` lê a outbox a cada 1 s (lote de 50, `FOR UPDATE SKIP LOCKED`, na ordem de criação) e publica no SNS `oficina-saga-events` com o envelope do contrato e o atributo `type`. Se o SNS falhar, o lote para, o erro fica na linha (`tentativas`, `ultimo_erro`) e a próxima rodada tenta de novo. Entrega *at-least-once*: um evento pode sair duas vezes, e o consumidor descarta a repetição pelo `messageId`.
- **API de consulta (somente leitura, JWT):** `GET /api/orcamentos/{id}`, `GET /api/orcamentos/{id}/pagamentos`, `GET /api/ordens/{osId}/orcamentos`. Swagger em `/swagger-ui.html`. O Billing só **valida** o token; quem emite é o OS Service (`/api/auth/login`), com o mesmo `JWT_SECRET`.
- `correlationId` e `sagaId` vão para os logs (MDC) durante o processamento de cada mensagem.
- Mensageria liga/desliga com `billing.mensageria.habilitada` (os testes desligam). SDK da AWS v2; contra o LocalStack basta a variável `AWS_ENDPOINT_URL`.
- **O Claude compilou com `javac` e executou o mapeador e o consumidor com Jackson; o restante (Spring, SQS/SNS, JPA) não foi compilado nem testado por ele.**

#### Como rodar e testar (na pasta `billing-service`, com o Docker Desktop aberto)
**1) Testes automatizados**
```powershell
cd C:\Projetos\oficina-workspace
git pull
cd billing-service
.\mvnw.cmd clean verify   # Git Bash: ./mvnw clean verify
```
Esperado: `BUILD SUCCESS`. Novos testes: `ComandoMapperTest`, `ConsumidorComandosTest`, `SqsComandoListenerTest`, `OutboxRelayTest` (banco real) e `OrcamentoControllerTest` (HTTP + JWT).

**2) Ponta a ponta com o LocalStack** (opcional agora, vale para o vídeo). Em um terminal, na raiz do repositório:
```powershell
docker compose up -d
docker compose ps          # espere postgres-billing e localstack "healthy"
```
Em outro terminal, suba o Billing apontando para o LocalStack:
```powershell
cd billing-service
$env:AWS_ENDPOINT_URL = "http://localhost:4566"
.\mvnw.cmd spring-boot:run     # Git Bash: AWS_ENDPOINT_URL=http://localhost:4566 ./mvnw spring-boot:run
```
Espere a linha `Escutando a fila ...billing-commands`. De volta ao primeiro terminal, na raiz:
```powershell
docker compose exec localstack bash -c "cd /scripts && bash billing-e2e.sh"
```
Esperado: todas as linhas `[OK]` e `RESULTADO: Billing respondeu a todos os comandos.` O script cobre criação, pagamento recusado + cancelamento, pagamento aprovado + estorno e pedido inválido.
Para consultar pela API, faça login no OS Service (`/api/auth/login`) e use o token em `http://localhost:8082/swagger-ui.html`.

### Etapa 3d — Dockerfile, Kubernetes e pipeline (✅)
- **`Dockerfile`** em dois estágios (compila com Maven, roda só com JRE 21, usuário sem root, porta 8082), igual ao do OS Service.
- **`k8s/`**: `namespace`, `configmap` (banco, região, fila e ARN do tópico de eventos), `deployment` (2 réplicas, sondas `/actuator/health/*`, sem root), `service` e `hpa`. O segredo (`DB_USER`, `DB_PASSWORD`, `JWT_SECRET`) **não** está no Git: veja `k8s/exemplos/secret.example.yaml`. O acesso ao SQS/SNS será pela role do pod (IRSA), criada no Terraform da Etapa 7. O `EVENTS_TOPIC_ARN` do configmap tem `CONTA_AWS` como espaço reservado até lá.
- **`.github/workflows/ci.yml`** independente do OS Service: `verify` (testes + 80% de cobertura) → Sonar Cloud (se `SONAR_TOKEN` existir) → imagem Docker → deploy no EKS (só na `main` e com `DEPLOY_ENABLED=true`). Evidências de cobertura saem como artefato da execução.
- Segue a regra do enunciado: `main` protegida com PR; a proteção é configurada no GitHub quando o repositório do serviço for criado (Etapa 8).
- **O Claude só validou o YAML; a imagem e o pipeline ainda não rodaram.**

#### Como rodar e testar
**1) Imagem Docker** (Docker Desktop aberto), na raiz do repositório, com o `docker compose up -d` já rodando:
```powershell
cd C:\Projetos\oficina-workspace
git pull
docker build -t billing-service:local billing-service
docker run --rm -p 8082:8082 `
  -e DB_URL=jdbc:postgresql://host.docker.internal:5434/oficina_billing `
  -e AWS_ENDPOINT_URL=http://host.docker.internal:4566 `
  billing-service:local
```
(Git Bash: troque o acento grave `` ` `` por `\` no fim das linhas.) Espere `Escutando a fila`. Em outro terminal:
```powershell
curl http://localhost:8082/actuator/health      # {"status":"UP"}
docker compose exec localstack bash -c "cd /scripts && bash billing-e2e.sh"
```
**2) Cobertura (evidência para o README do serviço):**
```powershell
cd C:\Projetos\oficina-workspace
powershell -ExecutionPolicy Bypass -File scripts\cobertura.ps1 billing-service -Salvar
```
Envie a saída (linhas totais e porcentagem). Ela também grava `docs/evidencias/billing-service/cobertura-resumo.txt`.
**3) Pipeline:** só roda no GitHub, quando existir o repositório próprio do serviço (Etapa 8). Até lá, `./mvnw clean verify` faz o mesmo que o job de testes.

## Etapa 4 — Execution Service (🟨 em andamento)
Fatias: **4a** domínio e casos de uso (✅ validado: `clean verify` com sucesso em 23/10) · 4b persistência no DynamoDB (✅ validado em 23/10) · 4c mensageria + API REST + JWT · 4d Dockerfile, k8s e CI.
Pasta `execution-service/` (pacote `br.com.oficina.execution`), mesma arquitetura hexagonal. Decisões em [ADR-006](adr/006-execution-dynamodb-transacoes-e-outbox.md).

### Etapa 4a — Domínio e casos de uso (✅)
- **Estoque:** `ItemEstoque` (código normalizado em maiúsculas, quantidade disponível). Reservar baixa, liberar devolve.
- **Execução** (`Execucao`, um registro por OS): `NA_FILA → EM_REPARO → CONCLUIDA`, ou `FALHOU`; `LIBERADA` quando a reserva é desfeita. Resolve a divergência de escopo do dia 22/10: o Execution acompanha o reparo e publica o novo evento `EXECUCAO_INICIADA`.
- **`ExecutionService`** trata `RESERVAR_ESTOQUE` (baixa todas as peças ou nenhuma; pedido repetido não baixa de novo; falta de estoque responde `ESTOQUE_INSUFICIENTE` com os faltantes) e `LIBERAR_ESTOQUE` (compensação; idempotente). Comportamento completo em [`eventos.md`](eventos.md), seção "Comportamento do Execution".
- **`GestaoExecucoesService`** (iniciar/finalizar/falhar e fila) e **`GestaoEstoqueService`** (cadastrar/repor) serão expostos por REST na 4c.
- A atomicidade (estado + estoque + mensagem processada + outbox numa só transação) fica atrás da porta `ExecucaoStore`; a implementação em DynamoDB vem na 4b.
- O Claude compilou com `javac` e rodou um harness descartável (13 verificações, passaram), mas **não rodou os testes JUnit**.

#### Como rodar e testar (na pasta `execution-service`)
```powershell
cd C:\Projetos\oficina-workspace
git pull
cd execution-service
.\mvnw.cmd -version      # a linha "Java version" precisa mostrar 21.x
.\mvnw.cmd clean verify   # Git Bash: ./mvnw clean verify
```
Esperado: `BUILD SUCCESS`, `Failures: 0, Errors: 0` (a regra de 80% de cobertura também é aplicada). Envie a linha `Tests run: N`.
Testes: `DominioTest`, `ExecutionServiceTest`, `GestaoServicesTest`. Não precisa do Docker nesta fatia.

### Etapa 4b — Persistência no DynamoDB (✅ validado: build com sucesso em 23/10)
- **Tabelas** (criadas na subida quando `execution.dynamo.criar-tabelas=true`; no AWS real serão do Terraform): `estoque`, `execucao` (GSI `status-criadoEm-index` para a fila), `mensagem_processada` (TTL) e `outbox` (GSI esparso `pendentes-index` + TTL).
- **Uma transação por comando** (`TransactWriteItems`): baixa de estoque com condição `disponivel >= qtd`, execução (versão otimista), mensagem processada (`attribute_not_exists`) e eventos do outbox. Falta de estoque cancela tudo e vira `EstoqueInsuficiente`; mensagem repetida ou versão antiga vira `ConflitoException`.
- **Outbox** (`OutboxDynamo`): lista pendentes em ordem de gravação, marca publicado, registra falha. O relay para o SNS vem na 4c.
- **Testes** (Testcontainers + LocalStack, precisam do Docker): `EstoqueRepositoryDynamoTest`, `ExecucaoStoreDynamoTest` (inclui duas threads disputando a última unidade), `OutboxDynamoTest`, `FluxoComDynamoTest` (casos de uso reais, compensação C e idempotência).
- O Claude **não consegue compilar** o código do AWS SDK nem rodar Testcontainers; a validação é sua.

#### Como rodar e testar (Docker Desktop aberto)
```powershell
cd C:\Projetos\oficina-workspace
git pull
cd execution-service
.\mvnw.cmd clean verify
```
Esperado: `BUILD SUCCESS`. Envie a linha `Tests run: N` ou o erro. Se aparecer "ApplicationContext failure threshold", rode `Select-String -Path target\surefire-reports\*.txt -Pattern "Caused by" | Select-Object -First 5` e envie a saída.

### Etapa 4c — Mensageria, API REST e JWT (✅ build e ponta a ponta com o LocalStack validados em 23/10)
- **Entrada:** `SqsComandoListener` lê a fila `execution-commands` (long polling, thread própria). A mensagem só é apagada após o tratamento; se falhar, volta e na 3ª vez vai para a DLQ. `ComandoMapper` traduz `RESERVAR_ESTOQUE` e `LIBERAR_ESTOQUE`; pedido com dado inválido (quantidade ≤ 0, código vazio, OS ausente) vira `ReservaInvalida` e é respondido com `ESTOQUE_INSUFICIENTE` + `motivo`.
- **Saída:** `OutboxRelay` publica no SNS `oficina-saga-events` os eventos pendentes do outbox, em ordem; se um envio falha, o lote para (preserva a ordem) e tenta de novo na rodada seguinte.
- **REST** (porta **8083**, JWT HS256 com o mesmo segredo do OS Service; Swagger em `/swagger-ui.html`): `GET/POST /api/estoque`, `GET /api/estoque/{codigo}`, `POST /api/estoque/{codigo}/reposicoes`, `GET /api/execucoes` (fila), `GET /api/execucoes/{osId}`, `POST /api/execucoes/{osId}/iniciar|finalizar|falhar`. Erros em `application/problem+json` (404, 409, 422).
- `execution.mensageria.habilitada=false` desliga SQS/SNS nos testes de persistência.
- Testes novos: `ComandoMapperTest`, `ConsumidorComandosTest`, `SqsComandoListenerTest` (Mockito), `OutboxRelayTest` (Dynamo real no Testcontainers), `ControllersTest` (MockMvc + JWT). O Claude validou o mapeamento com `javac` + harness; o resto (Spring, SDK) é validado por você.

#### Como rodar e testar
**1) Testes automáticos** (Docker Desktop aberto):
```powershell
cd C:\Projetos\oficina-workspace
git pull
cd execution-service
.\mvnw.cmd clean verify
```
Envie a linha `Tests run: N` (ou o erro).

**2) Ponta a ponta com o LocalStack** (3 terminais, na raiz do repositório):
```powershell
docker compose up -d                      # terminal 1: LocalStack + bancos (se ainda não estiver de pé)
cd execution-service
$env:AWS_ENDPOINT_URL="http://localhost:4566"   # terminal 2: sobe o serviço
.\mvnw.cmd spring-boot:run
# terminal 3, na raiz:
docker compose exec localstack bash -c "cd /scripts && bash execution-e2e.sh"
```
Esperado: quatro blocos com `[OK]` e `RESULTADO: Execution respondeu a todos os comandos.` Se algum falhar, mande a saída do script e as últimas linhas do terminal 2.

### Etapa 4d — Dockerfile, Kubernetes, CI e cobertura (🟨 aguardando validação)
- `execution-service/Dockerfile` (multi-estágio, JRE Alpine sem root, porta 8083).
- `execution-service/k8s/`: namespace, configmap (`DYNAMO_CRIAR_TABELAS=false` na AWS), deployment (sondas, limites, 2 réplicas), service, HPA e `exemplos/secret.example.yaml` (só `JWT_SECRET`; o acesso à AWS vem da role do pod).
- `.github/workflows/ci.yml` (copiado do Billing e adaptado): build + testes + 80% de cobertura → Sonar (se houver token) → imagem → deploy no EKS (só na `main` com `DEPLOY_ENABLED=true`). Só roda no GitHub quando o serviço tiver o repositório próprio (Etapa 8).
- `docs/cobertura.md` ganhou a linha do Execution.

#### Como rodar e testar
**1) Cobertura** (Docker Desktop aberto):
```powershell
cd C:\Projetos\oficina-workspace
git pull
cd execution-service
.\mvnw.cmd clean verify
cd ..
powershell -ExecutionPolicy Bypass -File scripts\cobertura.ps1 execution-service -Salvar
```
Envie a saída do script (percentual de linhas). Também rode para o OS Service, que ainda falta: `... cobertura.ps1 os-service -Salvar` (antes, `clean verify` em `os-service`).

**2) Imagem Docker** (com `docker compose up -d` ligado):
```powershell
cd execution-service
docker build -t execution-service:local .
docker run --rm -p 8083:8083 -e AWS_ENDPOINT_URL=http://host.docker.internal:4566 execution-service:local
```
Em outro terminal: `curl http://localhost:8083/actuator/health` deve responder `{"status":"UP"}`. Pare com Ctrl+C.

## Etapa 5 — Orquestrador da Saga no OS Service (🟨 em andamento)

### Etapas 5a–5c — Orquestrador, persistência e mensageria (✅ `clean verify` com 204 testes e cobertura de 94,9% em 23/10)
- **Domínio:** `Saga` (estados, prazo, tentativas, diário de passos), `OrdemServico.registrarOcorrencia`.
- **Aplicação:** `OrquestradorSaga` processa os eventos do Billing e do Execution, avança a Saga, dispara as compensações A/B/C e trata prazos (reenvio idempotente; esgotado, compensa ou marca `*_PENDENTE`). Idempotência por `messageId` (`mensagem_processada`).
- **Infraestrutura:** migração `V2__saga_e_outbox.sql` (`saga_instance`, `saga_passo`, `mensagem_processada`, `outbox_comando`), outbox transacional + `OutboxRelay` (SNS), `SqsEventoListener` (fila `os-events`), `VerificadorDePrazos`, filtro de `X-Correlation-Id`.
- **API:** `POST /api/ordens-servico/{id}/orcamento/aprovacao` aceita corpo opcional `{"metodoPagamento":"APRO|FUND|OTHE|ERRO|ERRO_ESTORNO"}`; novo `GET /api/ordens-servico/{id}/saga` mostra o diário.
- **Testes:** `OrquestradorSagaTest` (22 cenários), `EventoMapperTest`, `ConsumidorEventosTest`, `SqsEventoListenerTest`, `OutboxRelayTest`, `SagaPersistenciaTest`, `SagaComBancoTest`, controller.

### Etapa 5d — Teste ponta a ponta com os três serviços (✅ os 4 cenários passaram em 23/10, 22:18)
`scripts/local/saga-e2e.ps1` percorre 4 cenários com os serviços reais: caminho feliz (APRO → EM_EXECUCAO → iniciar/finalizar → CONCLUIDA), compensação A (FUND), B (estoque insuficiente) e C (execução falhou). Confere o estado da Saga, o status da OS, o pagamento no Billing e o estoque no Execution.

#### Como rodar e testar
Abra **quatro** terminais PowerShell.
```powershell
# Terminal 1 - infraestrutura (na raiz)
cd C:\Projetos\oficina-workspace
git pull
docker compose up -d
docker compose ps        # espere localstack e os dois postgres "healthy"
```
```powershell
# Terminal 2 - OS Service (porta 8080)
cd C:\Projetos\oficina-workspace\os-service
$env:AWS_ENDPOINT_URL = "http://localhost:4566"
.\mvnw.cmd spring-boot:run
```
```powershell
# Terminal 3 - Billing (porta 8082)
cd C:\Projetos\oficina-workspace\billing-service
$env:AWS_ENDPOINT_URL = "http://localhost:4566"
.\mvnw.cmd spring-boot:run
```
```powershell
# Terminal 4 - Execution (porta 8083)
cd C:\Projetos\oficina-workspace\execution-service
$env:AWS_ENDPOINT_URL = "http://localhost:4566"
.\mvnw.cmd spring-boot:run
```
Espere os três subirem (`Started ...Application`). No terminal 1, rode o teste:
```powershell
powershell -ExecutionPolicy Bypass -File scripts\local\saga-e2e.ps1
```
Esperado: todas as linhas `[OK]` e `TUDO CERTO: os 4 cenarios passaram.` Se algo falhar, envie a saída do script e as últimas linhas do log dos três serviços.

### Etapa 5e — BDD (Cucumber) da Saga (✅ `clean verify` com sucesso em 23/10, 22:32)
- Dependências Cucumber 7.20.1 no `os-service/pom.xml` (cucumber-java, cucumber-spring, engine do JUnit Platform, junit-platform-suite).
- `src/test/resources/features/saga_ordem_servico.feature`: 5 cenários em português — caminho feliz (orçamento → aprovação → pagamento → reserva → execução → FINALIZADA), compensações A, B e C, e idempotência de mensagem repetida.
- `src/test/java/.../bdd/`: `SagaSteps` chama a API real (MockMvc, JWT, PostgreSQL via Testcontainers) e entrega ao orquestrador as respostas que Billing e Execution enviariam; `SagaBddTest` roda tudo no `mvnw verify`.
- Relatórios: `os-service/target/cucumber-reports/saga.html` (abrir no navegador; serve de evidência e de cena do vídeo).

#### Como rodar e testar
```powershell
cd C:\Projetos\oficina-workspace
git pull
cd os-service
.\mvnw.cmd clean verify
```
(Docker Desktop aberto.) Esperado: `BUILD SUCCESS` com os 5 cenários do Cucumber somados aos 204 testes anteriores. Depois abra `target\cucumber-reports\saga.html`. Só os cenários: `.\mvnw.cmd test -Dtest=SagaBddTest`.

## Etapa 6 — Autenticação por CPF (Lambda) e papéis de acesso (✅ validada em 23/10: os 4 projetos com `clean verify` e o ponta a ponta do CPF)

### Decisões (ADR em `docs/adr/007-autenticacao-cpf-lambda.md`)
- **Papéis no JWT** (claim `roles`): `ADMIN` (login do OS Service), `CLIENTE` (emitido pela Lambda de CPF) e `SERVICE` (token de 1 minuto que a Lambda usa para consultar o cadastro). Os três serviços leem o papel; Billing e Execution exigem `ADMIN`.
- **OS Service:** `CLIENTE` só acessa `GET /api/minhas-ordens` (as OS do CPF do token, `sub`); `SERVICE` só `GET /api/clientes/documento/{cpf}`; o resto é `ADMIN`.
- **Lambda `auth-lambda/`** (Java 21, só JDK + Jackson): `POST /auth/cpf` com `{"cpf":"..."}` → valida os dígitos do CPF (400), consulta o cliente **pelo REST do OS Service** (nunca pelo banco dele), exige cliente ativo (senão 401 genérico, sem revelar se o CPF existe) e devolve o JWT (1 h) assinado com o mesmo `JWT_SECRET`. Se o OS Service estiver fora, responde 502.
- Na AWS: API Gateway → Lambda (Etapa 7, Terraform). Localmente a mesma Lambda roda como servidor HTTP (`LocalServer`, porta 8090).

### Etapa 6a — Papéis nos serviços (✅ `clean verify` OK nos 3 serviços)
`SecurityConfig` dos três serviços, `MinhasOrdensController`, `OrdemServicoRepository.buscarPorCliente`, `SegurancaPorPapelTest`; testes de controller passaram a usar `ROLE_ADMIN`.
### Etapa 6b — Lambda de CPF (✅ `clean verify` OK e `auth-cpf-e2e.ps1` passou em 23/10, 22:56)
Testes: CPF, JWT (assinatura conferida), caso de uso, gateway REST (servidor HTTP falso) e handler. CI próprio em `auth-lambda/.github/workflows/ci.yml` (verify + 80% + Sonar + deploy com `aws lambda update-function-code`).

#### Como rodar e testar
**1) Testes dos quatro projetos** (Docker Desktop aberto; pare qualquer serviço rodando antes):
```powershell
cd C:\Projetos\oficina-workspace
git pull
cd os-service;        .\mvnw.cmd clean verify; cd ..
cd billing-service;   .\mvnw.cmd clean verify; cd ..
cd execution-service; .\mvnw.cmd clean verify; cd ..
cd auth-lambda;       .\mvnw.cmd clean verify; cd ..
```
Envie a linha `Tests run: N` de cada um (ou os erros).

**2) Ponta a ponta do CPF** (OS Service no ar na 8081; use 8080 se estiver livre):
```powershell
# Terminal A (OS Service)
cd os-service; $env:AWS_ENDPOINT_URL="http://localhost:4566"; $env:PORT="8081"; .\mvnw.cmd spring-boot:run
# Terminal B (Lambda local, porta 8090)
cd auth-lambda
$env:JWT_SECRET="dev-only-troque-este-segredo-com-32-bytes-ou-mais"
$env:OS_SERVICE_URL="http://localhost:8081"
java -cp target\auth-lambda-1.0.0-SNAPSHOT.jar br.com.oficina.auth.LocalServer
# Terminal C (teste)
cd C:\Projetos\oficina-workspace
Set-ExecutionPolicy -Scope Process Bypass -Force
.\scripts\local\auth-cpf-e2e.ps1 -Os http://localhost:8081
```
Esperado: todas as linhas `[OK]` e `TUDO CERTO`.

## Etapa 7 — AWS: Terraform, EKS, CI/CD e observabilidade (🟨 em andamento)

### Etapa 7a — Terraform e deploy (🟨 `terraform plan` validado em 25/10: 104 recursos, 0 erros; falta o `apply` real e o teste do script de deploy)
**O que o Terraform (`infra/terraform/`) cria:** VPC (2 zonas, sem NAT para economizar), EKS 1.33 com 2 nós `t3.medium`, 3 repositórios ECR, 2 RDS PostgreSQL 16 `db.t4g.micro` (um por serviço, em sub-redes privadas), 4 tabelas DynamoDB do Execution, 2 tópicos SNS + 3 filas SQS com DLQ (espelho do LocalStack), roles IAM por serviço (IRSA, mínimo necessário), role da pipeline do GitHub (OIDC, só a branch `main`), Lambda `oficina-auth-cpf` e API Gateway (HTTP API).
**Segredos:** senhas dos bancos e `JWT_SECRET` são gerados pelo Terraform (nunca vão ao Git; ficam no estado local, que o `.gitignore` ignora) e viram Secrets do Kubernetes pelo script de deploy.
**Exposição:** só o OS Service tem balanceador público; o API Gateway aponta para ele (`/api/*`, Swagger, saúde) e para a Lambda (`POST /auth/cpf`). Billing e Execution não são expostos.
**Pipelines:** a partir de agora o deploy de cada serviço só aplica o `deployment.yaml` com a nova imagem; ConfigMap, ServiceAccount e Secrets são criados uma vez por `scripts/deploy/deploy-aws.ps1`.

**Custo aproximado enquanto estiver de pé:** EKS US$ 0,10/h + 2 nós (~US$ 0,084/h cada) + 2 RDS (~US$ 0,03/h cada) + balanceador (~US$ 0,03/h) ≈ **US$ 0,35 por hora (~US$ 8 por dia)**. Por isso: subir só para validar e gravar, e rodar `terraform destroy` logo depois.

#### Como rodar e testar
**Parte 1 — sem custo (valida a sintaxe e mostra o que seria criado):**
```powershell
cd C:\Projetos\oficina-workspace
git pull
terraform version                      # precisa do Terraform >= 1.6 (https://developer.hashicorp.com/terraform/install)
aws sts get-caller-identity            # confirma o usuario oficina-dev (conta 528757785383)
cd auth-lambda; .\mvnw.cmd clean verify; cd ..     # gera target\auth-lambda-1.0.0-SNAPSHOT.jar
cd infra\terraform
terraform init
terraform validate
terraform plan -out plano.tfplan
```
Esperado: `Success! The configuration is valid.` e um plano com cerca de 90 recursos a criar. Envie a saída (ou os erros). **Não aplique ainda.**

**Parte 2 — criar de verdade (só quando formos gravar/validar; custa):**
```powershell
terraform apply plano.tfplan           # ~15 a 20 min (o EKS demora)
cd ..\..
.\scripts\deploy\deploy-aws.ps1        # imagens, segredos, manifestos; ao fim mostra a URL do OS Service
terraform -chdir=infra\terraform apply -var os_service_url=http://<URL-mostrada>   # cria as rotas do API Gateway
```
Teste: `terraform -chdir=infra\terraform output url_da_api`, depois `POST <url>/auth/cpf` e `GET <url>/actuator/health`.
**Destruir tudo (para parar a cobrança):**
```powershell
kubectl delete svc os-service-publico -n oficina     # remove o balanceador antes (senao o destroy trava na VPC)
terraform -chdir=infra\terraform destroy
```

## Revisão do plano — 22/10/2026 (fim do 1º dia)

**Cronograma:** dentro do plano e adiantado. Etapas 0–1 prontas (falta só o smoke test e as contas); a Etapa 2 começou 2 dias antes da janela.

**Aderência ao enunciado da Fase 4** (relido em 22/10):
| Exigência | Situação |
|---|---|
| ≥ 3 microsserviços, cada um com repo, infra e banco próprios | Planejado (ADR-001). Cada repo final terá Dockerfile, `k8s/` e pipeline; ver decisão pendente sobre Terraform por serviço |
| ≥ 1 SQL e ≥ 1 NoSQL; nenhum serviço acessa o banco de outro | Planejado (ADR-004) |
| REST síncrono + mensageria assíncrona | Mensageria desenhada e testável (Etapa 1). REST entre serviços só se necessário |
| Saga com rollback "em qualquer etapa" e justificativa no README | Orquestrada (ADR-002); matriz de falhas por etapa adicionada a `docs/eventos.md` hoje |
| Testes unitários, ≥ 80% por serviço, 1 fluxo completo em BDD | Cobertura imposta no `pom.xml`; BDD na Etapa 5 |
| SonarQube ou similar no CI | **SonarQube Cloud (plano gratuito)**, decidido em 22/10 |
| Pipeline por serviço (build, testes, qualidade, deploy em K8s); `main` protegida com PR | Etapa 7; proteção da `main` é tarefa do grupo nos repos finais |
| Observabilidade da Fase 3 (New Relic) e **rastreamento dos fluxos distribuídos** | `correlationId` já está no envelope; falta o plano de tracing no New Relic (ver riscos) |
| Swagger ou Postman por serviço, evidências de cobertura no README | Planejado (Etapas 2d/2e) |
| Vídeo ≤ 15 min e PDF no portal | Etapas 8–9 |

**Divergência de escopo a decidir:** o enunciado sugere que o Execution Service também "atualiza o status durante diagnóstico e reparos".
Hoje o diagnóstico fica no OS Service e o Execution só entra a partir de `EM_EXECUCAO`. Proposta: o Execution ganha subestados próprios
(`NA_FILA`, `EM_REPARO`, `CONCLUIDA`) e publica `EXECUCAO_INICIADA`, que o OS Service registra no histórico.

**Riscos, em ordem:**
1. **Etapa 7 é a mais apertada** (3 dias para EKS, RDS, DynamoDB, SNS/SQS, API Gateway, Lambda, 3 pipelines, qualidade e New Relic). Mitigação: Dockerfile, `k8s/` e pipeline de cada serviço saem junto com o serviço (2e, 3, 4); fazer um teste curto de criação do EKS por volta de 03/11 para descobrir restrições do plano gratuito da AWS cedo.
2. **Rastreamento distribuído**: exige propagar `correlationId` nos logs, agente do New Relic nos 3 serviços e teste cedo com mensagens SNS/SQS.
3. **Mercado Pago real**: sem Access Token não há teste. Webhook precisa de URL pública (no EKS) ou usar consulta do pagamento (polling). Hoje só o adapter simulado está previsto.
4. **Validação depende do grupo**: o Claude não roda Maven nem Docker. Cada atraso na validação empurra a próxima fatia.
5. **BDD**: o fluxo completo entre os 3 serviços reais é pesado. Proposta: BDD no nível do orquestrador, com portas em memória, mais um script de ponta a ponta para o vídeo.

**Decisões pendentes (levar para a próxima sessão):**
- [x] Qualidade: SonarQube Cloud, plano gratuito (repositórios públicos ilimitados; privados até 50 mil linhas). Decidido em 22/10.
- [x] Mercado Pago: só o adapter simulado por enquanto; o real entra depois, atrás da mesma porta, quando houver Access Token de teste. Decidido em 22/10.
- [x] Execution com subestados e `EXECUCAO_INICIADA`: **sim**, decidido em 23/10 (ADR-006). _Se o grupo preferir o contrário, é só não expor os endpoints de iniciar._
- [ ] Terraform: um repo `oficina-infra` compartilhado (EKS, SNS/SQS, API Gateway) + recursos de banco em cada repo de serviço?
- [ ] Estilo do BDD (nível do orquestrador, com portas em memória)?

## Próxima sessão — começar por aqui (atualizado em 26/10/2026, 15:40)

**Ensaio do deploy real (25–26/10):** `terraform apply` criou os 104 recursos (um erro transitório do ECR foi resolvido repetindo o `apply`). O `deploy-aws.ps1` passou do login no ECR (corrigido: PowerShell 5.1 não aceita senha por `|`), enviou as 3 imagens e aplicou os manifestos, mas o **OS Service não ficou pronto em 7 minutos** (`rollout status` com timeout). A causa ainda **não foi investigada** (faltam `kubectl get pods`, `describe pod` e `logs`). O usuário encerrou a sessão e deveria ter rodado `terraform destroy` — **confirmar no console da AWS que nada ficou cobrando** (EKS, RDS, Load Balancers, EC2).

**Próximos passos:**
1. Confirmar o `destroy` (`terraform state list` deve vir vazio; console da AWS sem EKS/RDS/ELB).
2. Antes de recriar (custa ~US$ 0,35/h), tentar achar a causa do OS Service sem gastar: suspeitas = memória do JVM (limite 768Mi), conexão com o RDS, credenciais do SQS/SNS (IRSA), probes de saúde. Ao recriar, rodar `kubectl -n oficina logs deployment/os-service` e `describe pod`.
3. Recriar (`terraform apply`, `deploy-aws.ps1`), corrigir, validar o fluxo pela API Gateway e destruir.
4. Em paralelo (sem custo): New Relic, SonarQube Cloud nos pipelines, repositórios separados + `soat-architecture`, READMEs finais, diagramas, roteiro do vídeo e PDF (Etapas 7b/8).

**Lembretes do ambiente do usuário:** `$env:AWS_PROFILE = "oficina"` em toda janela (o `default` é o `amplify-dev`, sem permissão); PowerShell 5.1; porta 8080 ocupada pelo Apache (OS Service local na 8081); terminal do VS Code precisa reiniciar depois de instalar programas.

## Diagnóstico do ensaio na AWS (26/10)
- Pods em CrashLoopBackOff nos 3 serviços: o banco (Flyway) funcionou; falhava o acesso ao SQS (`QueueDoesNotExist`).
- Causa: faltava `software.amazon.awssdk:sts` no classpath, então o SDK ignorava o token do IRSA e usava a role do nó (sem permissão).
- Correção: dependência `sts` nos poms de os/billing/execution. `mvnw verify` passou nos três (26/10). Falta validar na AWS no próximo deploy.
- Ambiente destruído em 26/10 e conferido (EKS, RDS, ELB, EC2, NAT, EBS, EIP e VPC: nada ativo na conta 528757785383, us-east-1).

## Etapa 7b — observabilidade, repositórios e READMEs (26/10)
**O que foi feito (sem custo na AWS):**
- **New Relic:** o agente Java entra na imagem (Dockerfile dos 3 serviços, download no build; se falhar, a imagem segue sem agente). Só é ligado se o segredo `newrelic` existir. ConfigMaps ganharam `NEW_RELIC_APP_NAME` etc.; o `deploy-aws.ps1` cria o segredo quando `$env:NEW_RELIC_LICENSE_KEY` está definida. Limite de memória dos pods subiu para 1Gi e `MaxRAMPercentage` caiu para 65 (o agente consome memória extra).
- **SonarQube Cloud:** já estava nos `ci.yml` (roda se existir `SONAR_TOKEN`, com quality gate). Falta só criar a conta/projetos e cadastrar secrets e variáveis (tabela nos READMEs).
- **READMEs** de os-service, billing-service, execution-service, auth-lambda e infra (como rodar, testar, variáveis, pipeline).
- **`scripts/repos/separar-repos.ps1`:** monta uma pasta por repositório (módulo + `docs/`), faz o commit inicial e, com `-Publicar`, cria os repos no GitHub, convida `soat-architecture` e tenta proteger a `main`.

**Como testar o que mudou (local, sem AWS):**
1. `git pull`; em cada serviço `.\mvnw.cmd clean verify` (nada mudou no Java, deve continuar verde).
2. `docker build -t os-service os-service` (e billing, execution). O build deve passar; sem a licença o agente não liga. Se aparecer "Agente New Relic indisponivel no build", a imagem funciona mesmo assim.
3. `.\scripts\repos\separar-repos.ps1` (sem `-Publicar`): confira as 5 pastas em `..\oficina-repos`.

**Próximos passos:**
1. Usuário: criar conta New Relic (guardar a licença, nunca no Git) e conta/organização no SonarQube Cloud (token).
2. Rodar `separar-repos.ps1 -Publicar` (precisa do `gh auth login`); cadastrar secrets/variáveis em cada repo; proteger `main`.
3. Diagramas finais, `docs/roteiro-video.md`, PDF de entrega.
4. Dia do deploy real: `terraform apply` (2 fases), `deploy-aws.ps1`, validar pela API Gateway, painel New Relic, gravar o vídeo, `terraform destroy`.

## Repositórios publicados (26/10) e pendências de entrega
- Criados e populados na `main`: `oficina-os-service`, `oficina-billing-service`, `oficina-execution-service`, `oficina-auth-lambda`, `oficina-infra` (conta HDAdeveloper, privados). `oficina-workspace` segue como repositório central (docs, scripts, docker-compose).
- **Adiado de propósito para perto da entrega (10/11):** convidar o usuário `soat-architecture` como colaborador nos 5 repositórios (e no workspace); proteger a `main` com PR; cadastrar secrets/variáveis das pipelines (README de cada repo); conferir se o convite foi aceito. **Cobrar o usuário até 05/11.**
- Pendente do usuário: conta New Relic (licença), conta SonarQube Cloud (token), alerta de orçamento US$ 20 e MFA da conta root.

## Documentação de domínio (28/10)
- Feitos e publicados nos 6 repositórios: `docs/ddd.md` (contextos, agregados, estados, glossário), `docs/event-storming.md` e PNGs em `docs/diagramas/`. Cada repositório tem também `docs/servico.md`.
- Event Storming visual (quadro de post-its, 3 pranchas) criado como artefato privado no claude.ai, aprovado pelo usuário. Para o PDF: exportar cada prancha como imagem pelo editor e, se o avaliador for ver o quadro online, compartilhar pelo menu Share.
- Decisão registrada: pagamento na retirada do veículo fica como evolução futura (fluxo atual mantido).
- Pendente do usuário: token do SonarQube Cloud nos secrets dos repos, licença New Relic (só no dia do deploy), alerta de orçamento e MFA da AWS; convite do `soat-architecture` e proteção da `main` perto da entrega (cobrar até 05/11).
- Próximos passos: roteiro do vídeo (`docs/roteiro-video.md`), esqueleto do PDF de entrega, deploy real e gravação (apply → deploy → vídeo → destroy).

## Carga inicial de serviços e peças (28/10)
- `scripts/seed/seed-catalogo.ps1` + `catalogo.json`: 10 serviços básicos e 20 peças; grava o catálogo no OS Service e o estoque no Execution Service (mesmo `codigo`). Idempotente. `KIT-CORREIA` (estoque 0) e `BAT-60AH` (1 un.) servem para demonstrar a compensação B.
- **Como testar (local):** suba o docker compose, OS (8080/8081) e Execution (8083); rode `.\scripts\seed\seed-catalogo.ps1` (com `-Os http://localhost:8081` se for o caso). Esperado: `TUDO CERTO`; rode de novo e tudo deve sair `[existe]`. Confira em `GET /api/pecas` e `GET /api/estoque`.
- Não foi executado por mim (sem PowerShell nem serviços no meu ambiente): validar na máquina do usuário.

## Subida local em um comando e aviso de AWS real (29/10)
- **Causa dos erros de hoje:** OS e Execution iniciados sem `AWS_ENDPOINT_URL` falaram com a AWS real (perfil `oficina`/`amplify-dev`): `QueueDoesNotExist` no OS e `dynamodb:CreateTable` negado no Execution. O seed do OS Service passou; o estoque (Execution) ficou pendente.
- **`scripts/local/subir-servicos.ps1`** (`-OsPort`, `-Seed`): `docker compose up -d --wait`, confere as 6 filas e as portas livres, abre uma janela por serviço com `AWS_ENDPOINT_URL` definido e sem `AWS_PROFILE`, espera o health e, com `-Seed`, roda a carga do catálogo.
- **`AvisoAwsReal`** nos 3 serviços (`infrastructure/config`): WARN destacado na partida se `AWS_ENDPOINT_URL` estiver vazio fora de Kubernetes/Lambda (detecta `KUBERNETES_SERVICE_HOST`, `AWS_WEB_IDENTITY_TOKEN_FILE`, `AWS_EXECUTION_ENV`). Testes unitários em `AvisoAwsRealTest`. Não bloqueia a partida.
- **Como testar:** (1) `.\mvnw.cmd clean verify` em cada serviço (testes novos + cobertura >= 80%); (2) feche os serviços e rode `scripts\local\subir-servicos.ps1 -Seed`: três janelas, health UP, seed com `TUDO CERTO`; (3) para ver o aviso, abra um serviço sem a variável e confira o bloco "ATENCAO ... AWS REAL" no log (use um terminal sem credenciais para não criar nada na conta).
- Não executado por mim (sem PowerShell/Maven no meu ambiente): só compilei e exercitei a classe do aviso isoladamente. Validar na máquina do usuário.

## Correção do seed: duplicados por array JSON no PowerShell 5.1 (29/10)
- **Sintoma (rodada com `subir-servicos.ps1 -Seed`):** os 10 serviços saíram `[criado]` de novo (duplicados), as peças deram `409` e o estoque falhou com "índice de matriz avaliado como nulo".
- **Causa:** no Windows PowerShell 5.1, `Invoke-RestMethod` entrega um array JSON como um único objeto. A checagem "já existe" (`GET /api/servicos`, `/api/pecas`, `/api/estoque`) nunca achava nada, e a lista vazia do estoque virava um item sem `codigo`.
- **Correção:** `Chama` em `seed-catalogo.ps1` desenrola a resposta (`foreach ($item in $r) { $item }`).
- **Limpeza necessária no ambiente local:** os serviços duplicados já gravados no Postgres do OS. Resetar com os serviços fechados: `docker compose down -v` e depois `subir-servicos.ps1 -Seed`.
- **Como testar:** após o reset, `-Seed` termina em `TUDO CERTO`; rodar o seed de novo mostra tudo `[existe]` e `GET /api/servicos` retorna 10 itens.

## CPF/CNPJ como identificador do cliente (29/10)
- **Decisão (usuário):** o id do cliente é o próprio documento (CPF ou CNPJ, só dígitos), sem id numérico gerado.
- **OS Service:** `Cliente` perde o componente `id` (o `id()` devolve `documento().numero()`); `cliente.documento` vira a chave primária; `Veiculo.clienteId`, `OrdemServico.clienteId` e `ClienteRepository.buscarPorId` passam a `String` (o repositório não tem mais `buscarPorDocumento`). API: `GET/PUT/DELETE /api/clientes/{id}` aceita CPF/CNPJ com ou sem pontuação; `VeiculoRequest.clienteId` e `GET /api/veiculos?clienteId=` recebem o documento; `ClienteResponse.id` é texto. A rota `GET /api/clientes/documento/{doc}` continua (usada pela Lambda).
- **Migration V3 (OS):** converte os dados existentes (ids antigos viram o documento nas tabelas `veiculo` e `ordem_servico`, FKs e índices recriados). **Billing V2:** `orcamento.cliente_id` vira `VARCHAR(14)`.
- **Mensagens da Saga:** `CRIAR_ORCAMENTO.clienteId` agora é texto (o Billing lê com `asText`, então ainda aceita número antigo). **Lambda:** o claim `clienteId` do token e o `Cliente` do gateway são texto.
- **Salvar o mesmo CPF duas vezes** passa a atualizar o cliente no repositório; quem impede o duplicado é o `ClienteService` (409). A corrida entre duas inserções simultâneas ainda é barrada pela chave primária.
- **Como testar:** (1) `.\mvnw.cmd clean verify` em os-service, billing-service e `mvn verify` em auth-lambda (Testcontainers sobe o Postgres e roda a V3/V2); (2) com o ambiente local no ar: `POST /api/clientes` devolve `"id": "11144477735"`; `GET /api/clientes/111.444.777-35` acha o mesmo; cadastre um veículo com `"clienteId": "11144477735"`; `scripts\local\saga-e2e.ps1` e `auth-cpf-e2e.ps1` continuam verdes. Em banco local antigo a V3 converte os dados; se preferir, `docker compose down -v`.
- Não compilei nem rodei os testes aqui (sem acesso ao Maven): validar na máquina do usuário.

## Reativação de cliente (29/10)
- `DELETE /api/clientes/{id}` continua sendo exclusão lógica (`ativo = false`, histórico preservado). Novo **`POST /api/clientes/{id}/reativacao`** (só ADMIN) volta o cliente a ativo; é idempotente e devolve o cliente (200), ou 404 se o CPF não existe.
- `POST /api/clientes` com o CPF de um cliente inativo continua dando 409, agora com a mensagem "(está inativo: use a reativação)".
- Domínio: `Cliente.reativar()`; porta `GestaoClientes.reativar`; testes em `CatalogoTest`, `CadastrosServiceTest`, `ClienteControllerTest` (200, 404, 403 para CLIENTE) e `ApiFluxoCompletoTest` (DELETE → 409 no cadastro → reativação → ativo). Postman: requisição "Reativar cliente".
- **Como testar:** `.\mvnw.cmd clean verify` no os-service; no Swagger: `DELETE /api/clientes/{cpf}`, `GET /api/clientes` (some da lista), `POST /api/clientes/{cpf}/reativacao` (volta, `ativo: true`). Não executado por mim (sem Maven aqui).

## Aviso ao cliente: evolução futura (29/10)
- Conferido: não há envio de e-mail/SMS/push em nenhum serviço (o `emailCliente` só viaja no `CRIAR_ORCAMENTO` e vira o pagador no gateway). Decisão do usuário: **fica para o futuro**. Registrado em `docs/event-storming.md` (pontos de atenção) com o caminho sugerido: consumidor dos eventos da Saga, via SNS→e-mail ou serviço de notificações com SES.

## Estoque como microsserviço: evolução futura (29/10)
- Avaliado separar o estoque do Execution Service. Decisão do usuário: **manter e registrar como evolução futura**. Justificativa (custo/prazo, mudança da Saga, não é requisito, já separado por pacotes e portas) e caminho da extração em `docs/adr/008-estoque-no-execution-service-evolucao-futura.md`; também em `docs/event-storming.md` (pontos de atenção).
