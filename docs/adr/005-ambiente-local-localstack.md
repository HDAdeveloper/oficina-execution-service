# ADR-005 — Ambiente local com docker-compose e LocalStack fixado em 4.12

**Status:** Aceito · 22/10/2026

## Contexto
Para não gastar crédito da AWS durante o desenvolvimento, SNS, SQS e DynamoDB rodam localmente no
LocalStack. Desde 23/03/2026 a edição Community foi encerrada: a imagem `latest` exige conta e token
de autenticação (há plano gratuito para uso não comercial).

## Decisão
Fixar `localstack/localstack:4.12`, a última série Community, que não exige token. A imagem é
configurável por `LOCALSTACK_IMAGE` no `.env`; quem preferir a mais nova cria conta gratuita e
informa `LOCALSTACK_AUTH_TOKEN`.

## Consequências
- A versão 4.12 não recebe correções de segurança. Aceitável porque roda só na máquina do
  desenvolvedor, sem exposição externa.
- Divergências do comportamento real da AWS são possíveis (ex.: políticas de fila não são validadas).
  Por isso o fluxo completo também é testado na AWS real antes do vídeo (Etapa 9).
- O CI também usa a 4.12, sem segredo.
