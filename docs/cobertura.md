# Cobertura de testes

O enunciado exige **no mínimo 80% de cobertura por serviço**, com evidência. Este documento diz
onde está o relatório, como gerá-lo, como lê-lo e o que guardar como prova.

## A regra e onde ela é aplicada

| O quê | Onde |
|---|---|
| Mínimo de **80% das linhas**, medido no conjunto do serviço | `coverage.minimum` no `pom.xml` de cada serviço |
| Falha do build se ficar abaixo | `./mvnw verify` (fase `verify`, regra do JaCoCo) |
| Mesma checagem em cada Pull Request e na `main` | pipeline do serviço (`.github/workflows/ci.yml`) |

Não baixe o `coverage.minimum` para fazer o build passar: acrescente testes. Linhas que de fato não
fazem sentido testar (ex.: `main` da aplicação) são excluídas **só** da análise do Sonar, de forma
explícita na pipeline, e não do relatório do JaCoCo.

## Onde fica o relatório

| Local | Caminho / acesso | Para quê |
|---|---|---|
| **Na sua máquina (HTML)** | `os-service/target/site/jacoco/index.html` | Navegar por pacote, classe e linha (verde = coberta, vermelho = não coberta) |
| Dados brutos | `os-service/target/site/jacoco/jacoco.csv` e `jacoco.xml` | Scripts e Sonar |
| **No GitHub Actions** | execução da pipeline → seção **Artifacts** → `evidencias-testes-cobertura` (guardado 30 dias) | Prova independente da sua máquina; traz também os relatórios do Surefire |
| **No GitHub Actions (resumo)** | página da execução → **Summary** | Percentual de linhas de cada execução |
| SonarQube Cloud | painel do projeto → *Coverage* (depois de configurar o `SONAR_TOKEN`, Etapa 7) | Histórico e *quality gate* |
| **Evidência versionada** | `docs/evidencias/<servico>/` | O que vai para o PDF e para o vídeo |

A pasta `target/` não vai para o Git (é gerada). Por isso a evidência fica em `docs/evidencias/`.

## Como gerar e abrir

```powershell
cd C:\Projetos\oficina-workspace\os-service
.\mvnw.cmd clean verify        # Git Bash: ./mvnw clean verify  (Docker Desktop aberto)
start target\site\jacoco\index.html        # Git Bash: start target/site/jacoco/index.html
```

O `clean` evita relatório velho misturado com o novo.

## Como ler o relatório

- **Linhas (Line)** é o número que o enunciado cobra e que o build verifica.
- **Branches** mostra os desvios (`if`, `switch`) testados nos dois caminhos; use para achar casos de erro sem teste.
- **Instruções** e **Métodos** ajudam a localizar classes quase sem teste.
- Ordene a tabela pela coluna *Missed* para ver onde falta teste primeiro.

## Resumo em uma linha por serviço (script)

Mostra os percentuais e se o mínimo foi atingido, e pode gravar o texto como evidência:

```powershell
# na raiz do repositório, depois do verify
powershell -ExecutionPolicy Bypass -File scripts\cobertura.ps1 os-service -Salvar
```
```bash
# Git Bash
bash scripts/cobertura.sh os-service --salvar
```

Com `-Salvar` / `--salvar` o texto vai para `docs/evidencias/os-service/cobertura-resumo.txt`, com
data e commit. Faça `git add` desse arquivo e commite junto da entrega.

## O que guardar como evidência (checklist para o PDF e o vídeo)

Para **cada** serviço, em `docs/evidencias/<servico>/`:

- [ ] `cobertura-resumo.txt` (gerado pelo script, com data e commit);
- [ ] print do `index.html` do JaCoCo mostrando o total (`cobertura-<servico>-AAAA-MM-DD.png`);
- [ ] print do `BUILD SUCCESS` do `verify` com a linha `Tests run: N, Failures: 0, Errors: 0`;
- [ ] link da execução da pipeline na `main` com o artefato `evidencias-testes-cobertura`.

Refaça a evidência se o código mudar de forma relevante antes da entrega.

## Situação por serviço

| Serviço | `verify` (mín. 80%) | Linhas | Evidência |
|---|---|---|---|
| OS Service | ✅ passou em 23/10/2026 (`clean verify`, 204 testes, após a Saga) | **94,9%** das linhas (1425 de 1502); branches 81,9% | `docs/evidencias/os-service/` |
| Billing Service | ✅ passou em 23/10/2026 (`clean verify`) | **90,5%** das linhas (590 de 652); branches 83,5% | `docs/evidencias/billing-service/` |
| Execution Service | ✅ passou em 23/10/2026 (`clean verify`) | **93,1%** das linhas (692 de 743); branches 81,8% | `docs/evidencias/execution-service/` |
