# Roteiro do vídeo (até 15 min) — documento vivo

> **Texto de narração completo, com tempos: [`roteiro-narracao.md`](roteiro-narracao.md).** Este arquivo continua sendo o guia técnico (comandos de cada demonstração).

> Atualizado a cada etapa. Os blocos marcados **(pronto)** já têm comandos testados; os **(pendente)** serão preenchidos nas próximas etapas.
> Dica geral: ensaie uma vez sem gravar, deixe os terminais já abertos nas pastas certas, fonte grande (Ctrl + `+` no terminal) e feche notificações.

## Preparação antes de gravar (5 min, fora do vídeo)
1. Docker Desktop aberto. Atalho para o passo 1 e os terminais dos serviços: `powershell -ExecutionPolicy Bypass -File scripts\local\subir-servicos.ps1` (sobe Docker, filas e os 3 serviços; com `-Seed` já faz a carga do passo 6). Ou à mão: `docker compose up -d` na raiz; `docker compose ps` com tudo `healthy`.
2. Cinco abas de terminal nomeadas: **raiz**, **os-service**, **billing-service**, **execution-service**, **comandos**.
3. Navegador com abas: GitHub (repos), Swagger de cada serviço, New Relic, SonarQube Cloud, console AWS (EKS/RDS/DynamoDB).
4. Zere o estado: `docker compose down -v && docker compose up -d` (banco e filas limpos).
5. Gravador: OBS ou Win+G; 1080p; microfone testado.
6. **Carga inicial** (depois que OS e Execution estiverem no ar): `powershell -ExecutionPolicy Bypass -File scripts\seed\seed-catalogo.ps1` cadastra 10 serviços e 20 peças nos dois serviços. Para mostrar a compensação de estoque, use a peça `KIT-CORREIA` (estoque 0). Detalhes em `scripts/seed/README.md`.

7. **Monitor da Saga** (painel de demonstração): com os serviços no ar, abra `http://localhost:8081/saga-monitor.html`, entre com `admin` / `admin123` e deixe a aba pronta. Guia de uso e de testes: [`guia-testes-painel.md`](guia-testes-painel.md).

## Divisão sugerida do tempo
| Min | Bloco | Status |
|---|---|---|
| 0:00–1:30 | Abertura: problema, grupo, visão dos 3 microsserviços | pendente |
| 1:30–3:30 | Arquitetura: diagrama, bancos (2 Postgres + DynamoDB), SNS/SQS, API Gateway, EKS | pendente (diagrama na Etapa 8) |
| 3:30–6:30 | **Saga** (estratégia orquestrada, justificativa, compensações) com o diagrama de `docs/eventos.md` | pendente |
| 6:30–10:30 | **Demonstração ponta a ponta** (fluxo feliz + compensações, pelo monitor da Saga) | pronto (ver abaixo) |
| 10:30–12:30 | CI/CD: pipeline por serviço, Sonar, cobertura ≥ 80%, branch protegida com PR | pendente |
| 12:30–14:00 | Observabilidade (New Relic) e deploy no Kubernetes | pendente |
| 14:00–15:00 | BDD (um fluxo completo), encerramento | pendente |

## Demonstração por serviço

### Billing Service (pronto)
Mostrar: o serviço responde aos comandos da Saga pela mensageria.
1. Terminal **billing-service**: `.\mvnw.cmd spring-boot:run` (esperar `Started BillingServiceApplication` e `Escutando a fila`).
2. Terminal **comandos** (raiz): `docker compose exec localstack bash -c "cd /scripts && bash billing-e2e.sh"`.
3. Falar durante a saída: (1) orçamento criado, (2) pagamento recusado e compensação de cancelamento, (3) pagamento aprovado e estorno, (4) pedido inválido vira `ORCAMENTO_FALHOU`.
4. Abrir `http://localhost:8082/swagger-ui.html` e mostrar os endpoints de consulta (exigem JWT).
5. Evidência de cobertura: `docs/evidencias/billing-service/` (90,5% de linhas).

### Execution Service (pronto, ponta a ponta pendente de validação)
Mostrar: fila de execução, estoque no DynamoDB e a compensação que devolve as peças.
1. Terminal **execution-service**: `$env:AWS_ENDPOINT_URL="http://localhost:4566"` e `.\mvnw.cmd spring-boot:run` (esperar `Escutando a fila ...execution-commands`).
2. Terminal **comandos**: `docker compose exec localstack bash -c "cd /scripts && bash execution-e2e.sh"`.
3. Falar: (1) reserva baixa 5→3, (2) estoque insuficiente não baixa nada (tudo ou nada, transação do DynamoDB), (3) `LIBERAR_ESTOQUE` devolve 3→5 (compensação), (4) pedido inválido responde com motivo.
4. Swagger em `http://localhost:8083/swagger-ui.html`: listar estoque, ver a fila, iniciar/finalizar uma execução (precisa de token; o OS Service emite, ver Etapa 5).
5. Mostrar a tabela no DynamoDB: `docker compose exec localstack awslocal dynamodb scan --table-name execution_estoque`.

### OS Service + Saga completa (pronto — Etapa 5)
Mostrar: os três serviços conversando só por mensagens e as compensações.
1. Raiz: `scripts\local\subir-servicos.ps1` faz tudo isto sozinho. À mão: `docker compose up -d`. Três terminais, cada um com `$env:AWS_ENDPOINT_URL="http://localhost:4566"` e `.\mvnw.cmd spring-boot:run` em `os-service`, `billing-service` e `execution-service`. (Se a porta 8080 estiver ocupada, `$env:PORT="8081"` no OS Service e passe `-Os http://localhost:8081` ao script.)
2. Terminal **comandos** (raiz): `.\scripts\local\saga-e2e.ps1` (com `Set-ExecutionPolicy -Scope Process Bypass -Force` antes).
3. Falar durante a saída: (1) caminho feliz: orçamento → aprovação → pagamento → reserva de estoque → execução → CONCLUIDA; (2) compensação A: pagamento recusado (método FUND), o orçamento é cancelado e a OS volta a EM_DIAGNOSTICO; (3) compensação B: estoque insuficiente, o pagamento é estornado e a OS fica CANCELADA; (4) compensação C: a execução falha, o estoque volta, o pagamento é estornado e a OS fica CANCELADA. O script imprime o diário da Saga do último cenário.
4. Swagger do OS Service: `POST /api/auth/login`, depois `GET /api/ordens-servico/{id}/saga` para mostrar o diário (comandos, eventos, estados).
5. Evidência de cobertura: `docs/evidencias/os-service/` (94,9% de linhas).

### Monitor da Saga e painel de demonstração (pronto)
Mostrar: a máquina de estados e as mensagens entre os serviços em tempo real, conduzindo cada cenário com botões. É o que o bloco 4 do [`roteiro-narracao.md`](roteiro-narracao.md) usa; o `saga-e2e.ps1` fica como alternativa de reserva.
1. Abrir `http://localhost:8081/saga-monitor.html` (login `admin` / `admin123`).
2. Escolher o cenário no seletor, clicar em **Criar OS de demonstração**, depois nos botões que forem aparecendo (aprovar orçamento, simular pagamento do link, falhar execução...).
3. Falar olhando para três áreas: estado atual e caminho percorrido (diagrama), tabela de mensagens (OS Service ↔ Billing ↔ Execution) e lista das últimas Sagas.
4. Passo a passo de cada cenário, resultado esperado e problemas comuns: [`guia-testes-painel.md`](guia-testes-painel.md).

### Autenticação por CPF (pronto localmente — Etapa 6); API Gateway na AWS (pendente — Etapa 7)
Mostrar: o cliente entra só com o CPF e enxerga apenas as próprias OS.
1. Terminais: OS Service (`$env:PORT="8081"` se a 8080 estiver ocupada) e Lambda local (`java -cp target\auth-lambda-1.0.0-SNAPSHOT.jar br.com.oficina.auth.LocalServer`, com `JWT_SECRET` e `OS_SERVICE_URL`).
2. Terminal **comandos**: `.\scripts\local\auth-cpf-e2e.ps1 -Os http://localhost:8081`.
3. Falar: o CPF é validado (400 se inválido), a Lambda consulta o cadastro pelo REST do OS Service (nunca pelo banco), e devolve 401 genérico se não houver cliente ativo; o token de cliente (papel CLIENTE) só abre `/api/minhas-ordens`, a área administrativa responde 403.
4. Na AWS (Etapa 7): mostrar o API Gateway chamando a Lambda e a função no console.
### Infra, CI/CD, Sonar e New Relic (pendente — Etapa 7)
### BDD (pendente — Etapa 5)

## Frases-chave para falar (justificativa da Saga)
- Escolhemos Saga **orquestrada** porque o fluxo tem ordem rígida e compensações em ordem inversa; um ponto único (OS Service) conhece o estado e facilita auditar e depurar.
- Cada serviço grava estado + evento na mesma transação (**outbox**), então nunca perde mensagem; consumidores são **idempotentes** (messageId), porque a entrega é at-least-once.
- Cada serviço tem seu banco e ninguém acessa o banco do outro.

## Checklist final antes de enviar
- [ ] Vídeo ≤ 15 min, link acessível sem login.
- [ ] Áudio claro, tela legível, nenhum segredo (tokens, chaves, e-mails pessoais) visível.
- [ ] Usuário `soat-architecture` adicionado aos repositórios.
