# Observabilidade (New Relic) — painéis e alertas

O New Relic recebe duas coisas de cada serviço: o que o **agente Java** coleta sozinho (transações, erros,
JVM, logs em JSON com `correlationId`) e os **eventos customizados da Saga** que o OS Service envia:

| Evento | Quando | Atributos principais |
|---|---|---|
| `SagaPasso` | cada linha nova do diário da Saga | `sagaId`, `osId`, `correlationId`, `tipo`, `estadoAtual`, `msDesdePassoAnterior` |
| `SagaTransicao` | cada mudança de etapa | `de`, `para`, `msNaEtapaAnterior` e, no desfecho, `terminal` e `msTotal` |

Sem a licença (`NEW_RELIC_LICENSE_KEY`) esses envios não fazem nada; nada muda no funcionamento do serviço.

## 1. Importar o painel (3 páginas, 19 gráficos)

1. No New Relic: **Dashboards → Import dashboard**.
2. Cole o conteúdo de [`dashboard-newrelic.json`](dashboard-newrelic.json), **depois de trocar o Account ID**.
   O arquivo traz `"accountIds": [0]`. Seu Account ID aparece em **API keys** (e na URL). No PowerShell:

```powershell
$id = 1234567   # seu Account ID
(Get-Content docs\observabilidade\dashboard-newrelic.json -Raw) -replace '"accountIds": \[\s*0\s*\]', "`"accountIds`": [$id]" |
  Set-Content docs\observabilidade\dashboard-newrelic-conta.json -Encoding UTF8
Get-Content docs\observabilidade\dashboard-newrelic-conta.json -Raw | Set-Clipboard
```

3. Cole no importador (o arquivo `-conta.json` é só seu; não vá para o Git).

Os gráficos ficam **vazios até o deploy**: só existem dados depois que os serviços sobem com o agente ligado.

| Página | Exigência da Fase 3 que cobre |
|---|---|
| Ordens de serviço e Saga | volume diário de OS; tempo médio por etapa; falhas e compensações |
| Serviços e recursos | latência; CPU e memória; erros nas integrações; uptime |
| Logs e rastreio | logs estruturados (JSON) com correlação por `correlationId` |

**Sobre o "tempo médio por status":** o painel mostra o tempo em cada **etapa da Saga** (orçamento, aprovação,
pagamento, estoque e **execução**) e o tempo total até o desfecho. O tempo da fase de **diagnóstico** (antes
do orçamento) não faz parte da Saga e não está no painel; cite isso na documentação.

## 2. Alertas (criar no New Relic: Alerts → Alert conditions → NRQL)

| Alerta | Consulta NRQL | Condição |
|---|---|---|
| Falha no processamento de OS | `FROM SagaTransicao SELECT count(*) WHERE para IN ('ORCAMENTO_FALHOU', 'ESTORNO_PENDENTE', 'LIBERACAO_PENDENTE')` | acima de **0** por 5 min (crítico) |
| Erros HTTP | `FROM Transaction SELECT percentage(count(*), WHERE error IS true) WHERE appName LIKE 'oficina-%' FACET appName` | acima de **5%** por 5 min |
| Latência alta | `FROM Transaction SELECT percentile(duration, 95) WHERE appName LIKE 'oficina-%' FACET appName` | acima de **2 s** por 5 min |
| Serviço sem sinal | `FROM Transaction SELECT count(*) WHERE appName LIKE 'oficina-%' FACET appName` | *loss of signal* por 5 min (abrir incidente) |
| Respostas da Saga atrasadas | `FROM SagaPasso SELECT count(*) WHERE tipo = 'REENVIO'` | acima de **3** por 10 min (aviso) |

Crie um **workflow/notificação por e-mail** para o seu endereço (Alerts → Workflows), para o alerta de falha
chegar na caixa de entrada durante a demonstração.

## 3. Como provocar cada painel na demonstração

- **Volume e tempos:** rode o `scripts\local\saga-e2e.ps1` (ou o painel `saga-monitor.html`) apontando para o ambiente da AWS.
- **Falha / alerta:** cenário 3 (sem estoque) ou 4 (execução falhou) gera compensações e aparece em *Falhas e compensações*.
- **Correlação:** copie o `X-Correlation-Id` da resposta (ou o `sagaId` do painel) e cole nos gráficos da página *Logs e rastreio*.

## 4. Kubernetes (CPU/memória dos pods)

O painel usa as métricas de JVM do agente (`apm.service.*`). Para CPU e memória **do cluster** (por pod e por nó),
instale a integração Kubernetes do New Relic (Helm `nri-bundle`) no EKS; ela alimenta `K8sContainerSample` e
`K8sPodSample`. É opcional e consome memória dos nós; decida no dia do deploy.
