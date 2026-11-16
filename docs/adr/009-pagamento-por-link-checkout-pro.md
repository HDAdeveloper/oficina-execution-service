# ADR-009 — Pagamento por link (Mercado Pago Checkout Pro) e Saga que espera o cliente

- **Status:** aceita (31/10/2026)
- **Contexto:** o Billing precisa cobrar de verdade (conta de testes do Mercado Pago). Cobrar com cartão direto exigiria um **token de
  cartão gerado no navegador** (MercadoPago.js) e um frontend que não existe no projeto; além disso, dados de cartão não devem passar pelos nossos serviços.
  O atendimento da oficina também funciona assim na prática: o cliente recebe um **link** e paga quando puder.

## Decisão

1. O Billing usa o **Checkout Pro**: cada `COBRAR_ORCAMENTO` cria uma preferência no Mercado Pago e devolve o link. O cliente paga na página do Mercado Pago.
2. O pagamento passa a ter os estados `AGUARDANDO` e `EXPIRADO`. O resultado chega por **webhook**, que é só um gatilho: o Billing **consulta** o pagamento antes de decidir.
3. A Saga ganha o passo **`AGUARDANDO_PAGAMENTO`**, com prazo igual à validade do link (+60 s). Link vencido = compensação **A** (já existente). O resto da Saga (B e C) não muda.
4. O link chega ao cliente por e-mail (ver [ADR-010](010-link-de-pagamento-por-email-ses.md)).
5. O provedor continua atrás da porta `PagamentoGateway`. O **simulado** segue como padrão (local, CI, testes, vídeo sem internet) e ganhou o método `LINK`, com endpoints de simulação.

## Consequências

- (+) Nenhum dado de cartão passa pelo sistema; não precisa de frontend.
- (+) A compensação por link vencido reaproveita a compensação A: nada de fluxo novo para defender.
- (+) Pagamento tardio ou duplicado é devolvido automaticamente (sem mexer na Saga).
- (−) O webhook precisa de **URL pública HTTPS**. Local: túnel (ngrok/cloudflared). Na AWS: o Billing tem um Service `LoadBalancer` próprio (`billing-service-publico`) e o API Gateway expõe **uma única rota**, `POST /webhooks/mercadopago` (Terraform, variável `billing_service_url`); as demais rotas do Billing não existem no gateway e continuam sob JWT. A rota só é criada quando a variável é informada (apply em duas fases, como o OS Service); ver `docs/mercado-pago.md`.
- (−) Enquanto o link está aberto o cliente não pode recusar o orçamento. Cancelar o link é evolução futura.
- (−) A confiabilidade depende de o Mercado Pago reenviar webhooks (ele reenvia por dias se não receber 200). Por isso o endpoint responde 503 quando o provedor falha e 200 quando o aviso foi entendido.

## Alternativas descartadas

- **Orders API com token de cartão:** exige frontend com MercadoPago.js para gerar o token (uso único, expira em 7 dias).
- **Esperar o pagamento no Billing sem mudar a Saga:** o orquestrador reenviaria `COBRAR_ORCAMENTO` a cada 60 s e acabaria compensando em ~3 min, antes de o cliente conseguir pagar.
