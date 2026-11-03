# Guia de testes com o Monitor da Saga

O monitor (`http://localhost:8081/saga-monitor.html`) serve para **testar à mão** e para **demonstrar** a Saga. Este guia diz como preparar, o que clicar em cada cenário e o que conferir.

## Preparar

```powershell
cd C:\Projetos\oficina-workspace
powershell -ExecutionPolicy Bypass -File scripts\local\subir-servicos.ps1
```

Espere os três serviços subirem (OS 8081, Billing 8082, Execution 8083). Abra a página, entre com `admin` / `admin123` e confira o "v5" ao lado do título (se não aparecer, Ctrl+F5). Os campos **Billing** e **Execution** no topo já vêm com `http://localhost:8082` e `http://localhost:8083`.

Para zerar tudo entre rodadas: `docker compose down -v`, depois subir de novo.

## Como o painel funciona

1. Escolha o **cenário** no seletor.
2. Clique em **Criar OS de demonstração**: ele cria cliente, veículo, serviço, peça, estoque e OS, pede o orçamento e abre a Saga na tela.
3. Os **botões mudam conforme o estado**: aprovar/recusar orçamento, simular pagamento ou vencimento do link, iniciar/finalizar/falhar execução.
4. A **dica** abaixo do seletor diz o que acontece e o que explicar.
5. A tabela de mensagens mostra, em ordem, os comandos (azul) e eventos (verde) entre OS Service, Billing e Execution. Mensagens ignoradas ficam em vermelho.

## Cenários

| Cenário | O que clicar | Estados esperados (final) | Conferir |
|---|---|---|---|
| **Caminho feliz** | Criar → Aprovar (APRO) → Iniciar → Finalizar | `AGUARDANDO_APROVACAO` → `COBRANDO` → `RESERVANDO_ESTOQUE` → `EM_EXECUCAO` → `CONCLUIDA` | Estoque da peça baixou (5 → 3) |
| **Pagamento por link** | Criar → Aprovar (LINK) → **Simular pagamento** → Finalizar | … `AGUARDANDO_PAGAMENTO` (com link e prazo) → `RESERVANDO_ESTOQUE` → `EM_EXECUCAO` → `CONCLUIDA` | Estoque intacto até pagar |
| **Link vencido** (variação) | Cenário do link → **Simular link vencido** | `AGUARDANDO_PAGAMENTO` → `CANCELANDO_ORCAMENTO` → `ENCERRADA` | OS volta a Em Diagnóstico |
| **Compensação A** (FUND) | Criar → Aprovar | `COBRANDO` → `CANCELANDO_ORCAMENTO` → `ENCERRADA` | Pagamento RECUSADO; estoque intacto |
| **Compensação B** (sem estoque) | Criar → Aprovar | `RESERVANDO_ESTOQUE` → `ESTORNANDO` → `CANCELADA` | Pagamento ESTORNADO; estoque intacto |
| **Compensação C** (execução falha) | Criar → Aprovar → **Falhar execução** | `EM_EXECUCAO` → `LIBERANDO_ESTOQUE` → `ESTORNANDO` → `CANCELADA` | Estoque voltou (3 → 5); pagamento ESTORNADO |
| **Provedor fora do ar** (ERRO) | Criar → Aprovar | `COBRANDO` sem avançar; reenvios pelo prazo da Saga | Ainda não validado na prática: observe o terminal do Billing e anote o resultado aqui |

Também dá para testar **Recusar orçamento** (botão ao lado de Aprovar): a OS volta ao diagnóstico e a Saga encerra.

### Conferir o estoque e os pagamentos
Estoque: `GET http://localhost:8083/api/estoque` no Swagger do Execution (porta 8083, com o token do login) lista as peças; as da demonstração têm código `DEMO-...` (a mais recente é a última criada). Pagamentos: `GET http://localhost:8082/api/orcamentos/{orcamentoId}/pagamentos`; o `orcamentoId` vem no Swagger do OS Service, em `GET /api/ordens-servico/{id}/saga` (a página mostra o número da OS na lista).

## Com o Mercado Pago ligado (Billing em modo `mercadopago`)

O método de pagamento do cenário (APRO, FUND, ERRO...) **só vale no provedor simulado**. Com o Mercado Pago o Billing ignora o método e sempre gera um link real, então todo cenário passa por `AGUARDANDO_PAGAMENTO`. Como obter cada resultado:

| Quero | O que fazer |
|---|---|
| Pagamento aprovado | Pagar o link com o comprador de teste e o cartão de titular `APRO` (o webhook avisa; se não vier, `/sincronizar`) |
| Compensação A | **Só pelo vencimento do link**: subir o Billing com `PAGAMENTO_VALIDADE_LINK_MINUTOS=2`, aprovar e não pagar (a Saga encerra logo após a validade, ~2 min: o Billing publica a recusa quando o link vence). Uma recusa no Mercado Pago (cartão `FUND`/`OTHE`) **não** encerra o link, o cliente pode tentar de novo; a recusa imediata só existe no provedor simulado (`FUND`) |
| Compensação B (sem estoque) | Cenário "sem estoque", pagar com `APRO`: o Execution recusa a reserva e o Billing estorna |
| Compensação C | Pagar com `APRO` e clicar em **Falhar execução** (validada em 03/11: pagamento *Reembolsado* no Mercado Pago) |
| Provedor fora do ar | Só existe no simulado |

Os botões "Simular pagamento" e "Simular link vencido" também só funcionam no provedor simulado.

## Problemas comuns

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| "Sessão expirada" | Token venceu | Entrar de novo (topo da página) |
| Falha ao criar a demonstração com erro de rede/CORS | Execution ou Billing fora do ar, ou versão antiga | Reiniciar os serviços (`subir-servicos.ps1`); conferir as portas nos campos do topo |
| Botão "Simular pagamento" não aparece | A Saga não está em `AGUARDANDO_PAGAMENTO` | Escolher o cenário do link **antes** de aprovar (o botão de aprovar mostra o método) |
| Tabela vazia | Página antiga em cache | Ctrl+F5; o título deve mostrar "v5" |
| "Finalizar execução" não faz nada | A execução precisa ser **iniciada** antes (NA_FILA → EM_REPARO → CONCLUIDA); a versão antiga mostrava os dois botões e o erro passava despercebido | Usar a v4: ela mostra o status da execução e só os botões válidos; erros aparecem num aviso vermelho no canto |
| Saga parada em `COBRANDO` sem sair | Billing parado ou fila não criada | Ver o terminal do Billing; reiniciar com `subir-servicos.ps1` |

## Dica para a gravação do vídeo
Faça um ensaio completo dos cenários antes de gravar. Grave cada cenário como um trecho separado e junte na edição. Se o painel falhar na hora, o `scripts\local\saga-e2e.ps1` roda os mesmos cenários e imprime o resultado.
