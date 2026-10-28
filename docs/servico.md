# Execution Service — documentação do serviço

## Responsabilidade

Contexto **Execução** (subdomínio de suporte). Mantém o **estoque de peças** e a **fila de execução** das OS. Reserva as peças quando a Saga pede,
inicia, finaliza ou marca a falha do trabalho e devolve a reserva na compensação.

- **Banco:** **DynamoDB** (NoSQL), escolhido por ter acesso simples por chave e transações atômicas entre itens ([ADR-006](adr/006-execution-dynamodb-transacoes-e-outbox.md)).
- **Tabelas** (prefixo `execution_`): `estoque`, `execucao` (índice por status), `mensagem_processada` (TTL), `outbox` (índice esparso de pendentes).
- **Porta:** 8083. Swagger UI em `/swagger-ui.html`. Local: LocalStack cria as tabelas; na AWS o Terraform cria (`DYNAMO_CRIAR_TABELAS=false`).

## API REST

Todas exigem JWT com papel `ADMIN`.

| Rota | Descrição |
|---|---|
| `GET /api/estoque`, `GET /api/estoque/{codigo}` | Consulta o estoque |
| `POST /api/estoque` | Cadastra uma peça |
| `POST /api/estoque/{codigo}/reposicoes` | Repõe estoque |
| `GET /api/execucoes`, `GET /api/execucoes/{osId}` | Fila e detalhe da execução |
| `POST /api/execucoes/{osId}/iniciar` | Inicia o reparo (`EXECUCAO_INICIADA`) |
| `POST /api/execucoes/{osId}/finalizar` | Conclui (`EXECUCAO_FINALIZADA`) |
| `POST /api/execucoes/{osId}/falhar` | Registra falha (`EXECUCAO_FALHOU`) |
| `/actuator/health` | Saúde (público) |

## Mensageria

| Direção | Mensagem |
|---|---|
| Consome (fila `execution-commands`) | `RESERVAR_ESTOQUE`, `LIBERAR_ESTOQUE` |
| Publica (tópico `oficina-saga-events`) | `ESTOQUE_RESERVADO`, `ESTOQUE_INSUFICIENTE`, `ESTOQUE_LIBERADO`, `EXECUCAO_INICIADA`, `EXECUCAO_FINALIZADA`, `EXECUCAO_FALHOU` |

## Consistência no DynamoDB

Cada comando vira **uma única `TransactWriteItems`**: baixa de estoque com condição `disponivel >= quantidade`, criação da execução com versão otimista,
marca de mensagem processada e eventos da outbox, tudo junto ou nada. Isso garante que duas OS nunca reservem a mesma peça e que repetir a mensagem não duplica a reserva.

## Modelo e regras

Agregados: `Execucao` (com `PecaReservada`) e `ItemEstoque` (ver [`ddd.md`](ddd.md)). Execução: `NA_FILA → EM_REPARO → CONCLUIDA`, com `FALHOU` e `LIBERADA`.
Uma execução concluída **não** pode ser liberada, porque as peças já foram usadas.

## Estrutura do código

`domain/` · `application/` (portas, `ExecucaoStore`) · `infrastructure/` (web, DynamoDB, mensageria, segurança).

## Testes e qualidade

- Testes unitários e de integração por camada (JUnit 5, Mockito); `domain` e `application` testam-se sem Spring.
- Cobertura mínima de **80% das linhas** aplicada no build (JaCoCo em `mvnw verify`) e em cada Pull Request.
- Análise estática no SonarQube Cloud pela pipeline. Evidências de cobertura: [`evidencias/`](evidencias/).
- Como rodar: `.\mvnw.cmd clean verify` (relatório em `target\site\jacoco\index.html`).

## Documentação compartilhada do projeto

| Documento | Conteúdo |
|---|---|
| [`arquitetura.md`](arquitetura.md) | Visão geral, hexagonal, dados, Saga, mensageria, segurança, CI/CD |
| [`ddd.md`](ddd.md) | Contextos, agregados, estados e linguagem ubíqua (imagens em [`diagramas/ddd/`](diagramas/ddd/)) |
| [`eventos.md`](eventos.md) | Comandos, eventos, envelope, fluxo feliz e compensações da Saga |
| [`adr/`](adr/) | Decisões de arquitetura e justificativas |
