# ADR-010 — Link de pagamento por e-mail (SES) e e-mail obrigatório no cliente

- **Status:** aceita (16/11/2026)
- **Contexto:** pelo ADR-009 o cliente paga por um link. Faltava dizer **como o link chega ao cliente**: ele aparecia
  só na resposta da API e no Monitor da Saga, vistos pelo atendente. Sem um canal até o cliente, a Saga ficava
  esperando um pagamento que o cliente não tinha como fazer.

## Decisão

1. Quando o Billing gera o link (`LINK_PAGAMENTO_GERADO`), ele envia um **e-mail** ao cliente com o valor, o link e a validade.
2. O envio fica atrás de uma porta, `NotificadorCliente`, com dois adaptadores: **`NotificadorSes`** (Amazon SES) e
   **`NotificadorLog`** (padrão, só registra). Escolhe-se por `billing.email.provedor` (`EMAIL_PROVEDOR`: `ses` ou `nenhum`).
3. É de **melhor esforço**: se o envio falhar, a Saga segue e o link continua disponível na API e no Monitor.
   O e-mail só sai quando o link é **criado**, não quando uma cobrança repetida devolve o mesmo link.
4. O e-mail do cliente passa a ser **obrigatório no cadastro e na atualização** (`Cliente.novo` e `Cliente.atualizar`, e `@NotBlank @Email` nos DTOs).
   O construtor do domínio continua aceitando registros antigos sem e-mail, para que eles ainda possam ser lidos.
   O banco **não** ganhou `NOT NULL`, para a migração não quebrar numa base com registros antigos.
5. **Local:** o LocalStack ganhou o serviço `ses` e verifica o remetente `oficina@oficina.local` no init;
   os e-mails enviados ficam em `http://localhost:4566/_aws/ses` (`scripts\local\ver-emails.ps1`).
   **AWS:** o mesmo adaptador funciona, mas o SES exige remetente e destinatário verificados (modo *sandbox*) e a permissão `ses:SendEmail`
   no papel do Billing. Isso **não está no Terraform**; na demonstração na AWS o provedor ficou em `nenhum`.

## Consequências

- (+) O cliente recebe o link e consegue pagar; a Saga deixa de depender de alguém repassar o link.
- (+) O mesmo código roda no LocalStack e na AWS; o provedor troca por configuração, sem mexer no domínio.
- (+) Falha de e-mail não derruba o pagamento nem a Saga.
- (−) E-mail não é entrega garantida: o cliente pode não receber (caixa de spam, endereço errado). O link segue disponível ao atendente.
- (−) Na AWS real é preciso verificar remetente e destinatário no SES e sair do *sandbox* para enviar a qualquer endereço.
- (−) Clientes cadastrados antes desta decisão podem estar sem e-mail; para eles o envio é pulado e o log avisa.

## Fora do escopo (evolução)

Avisos de orçamento pronto, ordem finalizada e cancelada por e-mail ou SMS: seriam consumidores dos eventos da Saga que já existem.
