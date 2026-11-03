# Roteiro de narração do vídeo (14 min 30 s)

> Texto para **ler em voz alta**, com marcação de tempo e o que mostrar na tela. O limite do desafio é 15 min; este roteiro fecha em **14:30** para deixar folga.
> Ritmo usado: cerca de 140 palavras por minuto (são ~1.600 palavras de fala; o resto do tempo é demonstração na tela). Se ler mais devagar, corte as frases marcadas com ✂ (são opcionais).
> Itens entre `[colchetes]` são para você preencher ou confirmar antes de gravar.
> Os comandos para preparar cada demonstração estão em [`roteiro-video.md`](roteiro-video.md).

## Linha do tempo

| Tempo | Bloco | Na tela |
|---|---|---|
| 0:00 – 0:50 | 1. Abertura | Slide ou README do repositório |
| 0:50 – 2:20 | 2. Os microsserviços e a arquitetura | Diagrama de arquitetura |
| 2:20 – 4:30 | 3. Saga: estratégia e justificativa | Diagrama da Saga (`docs/eventos.md`) |
| 4:30 – 9:00 | 4. Demonstração ponta a ponta | Monitor da Saga (`/saga-monitor.html`) + Swagger |
| 9:00 – 10:30 | 5. Qualidade: testes, cobertura e BDD | Relatório JaCoCo, Sonar, feature |
| 10:30 – 12:00 | 6. CI/CD por serviço | GitHub Actions |
| 12:00 – 13:30 | 7. Nuvem, Kubernetes e observabilidade | Console AWS, kubectl, New Relic |
| 13:30 – 14:30 | 8. Encerramento | Slide final |

## Antes de gravar
- Ambiente limpo e no ar: `docker compose down -v`, depois `scripts\local\subir-servicos.ps1 -Seed` (três janelas, tudo `UP`, carga do catálogo concluída).
- Terminais com fonte grande, notificações desligadas, nenhum token, chave ou e-mail pessoal visível.
- Abas abertas e na ordem: diagrama, `docs/eventos.md`, **Monitor da Saga** (`http://localhost:8081/saga-monitor.html`, já logado), Swagger dos três serviços (já com o token colocado em **Authorize**), terminal, relatório JaCoCo, SonarQube Cloud, GitHub Actions, console AWS, New Relic.
- Os blocos 4 a 7 dependem de coisas que rodam ao vivo. Grave cada bloco separado e junte na edição: assim um erro não obriga a regravar tudo.

---

## 1. Abertura — 0:00 a 0:50

**Tela:** slide com o nome do projeto e o seu nome com o RM (ou o README do `oficina-workspace`).

**Fala:**

> Olá! Eu sou o Henrique Dellagnesi Azevedo, RM 372407, aluno da turma 15 da Pós-Tech SOAT da FIAP.
>
> Neste vídeo apresento a Fase 4 do Tech Challenge: a minha oficina mecânica, que nas fases anteriores era uma aplicação só, agora dividida em microsserviços.
>
> O desafio pede que cada serviço seja independente, com repositório, banco e pipeline próprios, e que eles conversem por mensagens. O ponto mais delicado é a consistência: como garantir que um pagamento, uma reserva de peças e uma execução de serviço acabem num estado coerente, mesmo quando um passo falha? A resposta é o padrão **Saga**.
>
> Vou mostrar a arquitetura, a estratégia da Saga e por que a escolhi, uma demonstração funcionando, os testes e a cobertura, o CI/CD e a execução na AWS.

---

## 2. Os microsserviços e a arquitetura — 0:50 a 2:20

**Tela:** diagrama de arquitetura (`docs/arquitetura.md`). Aponte cada caixa quando citá-la.

**Fala:**

> São três microsserviços de negócio, uma função Lambda e a infraestrutura como código.
>
> O **OS Service** é o coração do sistema. Cuida de clientes, veículos, catálogo de serviços e peças, e do ciclo de vida da ordem de serviço. O cliente é identificado pelo próprio CPF ou CNPJ. E é ele quem **orquestra a Saga**. Usa banco relacional, PostgreSQL.
>
> O **Billing Service** cuida do orçamento, do pagamento e do estorno. Também usa PostgreSQL, num banco só dele. O pagamento é feito por **link**, no estilo Checkout Pro do Mercado Pago. O gateway fica atrás de uma porta: localmente uso um provedor simulado, e a integração real com o Mercado Pago é um adaptador que se liga por configuração, sem mexer no domínio.
>
> O **Execution Service** guarda o estoque de peças e controla a execução do serviço. Aqui escolhi um banco **NoSQL**, o DynamoDB, porque o acesso é por chave e preciso de transações condicionais para nunca deixar o estoque negativo.
>
> A **Lambda de autenticação** recebe o CPF do cliente, valida os dígitos, consulta o OS Service e devolve um token com o papel de cliente. Ela entra pelo API Gateway.
>
> Os serviços não chamam uns aos outros por REST e nenhum enxerga o banco do outro. A comunicação entre eles é **assíncrona**: o OS Service publica comandos num tópico do SNS, cada serviço recebe os seus pela sua fila SQS, e as respostas voltam como eventos. Depois de três tentativas, a mensagem vai para uma fila de mensagens mortas.
>
> Todos os serviços seguem arquitetura hexagonal, em Java 21 com Spring Boot. Rodam num cluster Kubernetes na AWS, criado por Terraform.

---

## 3. Saga: estratégia e justificativa — 2:20 a 4:30

**Tela:** diagrama de estados e fluxo da Saga (`docs/eventos.md` e `docs/ddd.md`). Percorra o caminho feliz da esquerda para a direita e depois as três compensações.

**Fala:**

> Escolhi a Saga **orquestrada**, e o orquestrador é o OS Service.
>
> Por quê? Porque o meu fluxo tem ordem rígida: primeiro o orçamento, depois a aprovação do cliente, a cobrança, a reserva de estoque e só então a execução. E, quando algo dá errado, é preciso desfazer os passos **na ordem inversa**. Com um único ponto que conhece o estado da Saga, fica fácil saber em que passo estamos, auditar o que aconteceu e depurar. Na coreografia, essa lógica ficaria espalhada pelos três serviços.
>
> O caminho feliz: o mecânico pede o orçamento e o Billing o cria. O cliente aprova e o Billing gera o **link de pagamento**. Enquanto o cliente não paga, a Saga fica em *aguardando pagamento*, com prazo igual à validade do link. Pago o link, o Execution reserva as peças e executa. No fim, a ordem é finalizada.
>
> E há **três compensações**, cada uma desfazendo exatamente o que já foi feito.
>
> Na **compensação A**, o pagamento é recusado, ou o link vence sem pagamento. Nada além do orçamento foi feito, então ele é cancelado e a ordem volta para o diagnóstico, para o cliente tentar de novo.
>
> Na **compensação B**, não há estoque. O pagamento já tinha sido aprovado, então é **estornado**, e a ordem é cancelada.
>
> Na **compensação C**, a execução falha. Primeiro as peças voltam ao estoque, depois o pagamento é estornado, e a ordem é cancelada.
>
> Para isso ser confiável, uso três garantias. A primeira é o **outbox transacional**: cada serviço grava o seu estado e a mensagem a enviar na mesma transação, então nenhuma mensagem se perde. A segunda é a **idempotência**: a entrega é *at-least-once*, então toda mensagem tem um identificador e processá-la duas vezes não duplica efeito. A terceira são os **prazos**: se um serviço não responde em sessenta segundos, o comando é reenviado; esgotadas as tentativas, a Saga encerra com a compensação ou fica marcada como pendente para ação manual, como um estorno que não concluiu.

---

## 4. Demonstração ponta a ponta — 4:30 a 9:00

### 4.1 Ambiente e monitor — 4:30 a 5:15

**Tela:** terminal com a saída do `subir-servicos.ps1` (três serviços `UP`) por alguns segundos; depois o **Monitor da Saga** (`http://localhost:8081/saga-monitor.html`).

**Fala:**

> Aqui está tudo rodando localmente: dois PostgreSQL e o LocalStack, que simula SNS, SQS e DynamoDB, mais os três serviços.
>
> Esta página é o **monitor da Saga**. À esquerda, as últimas Sagas. No centro, a **máquina de estados**: o estado atual em destaque e o caminho já percorrido. Embaixo, as mensagens trocadas entre o OS Service, o Billing e o Execution, em ordem. Tudo atualiza sozinho, em tempo real.
>
> Em cima, o painel de demonstração: escolho um cenário e crio uma ordem de serviço com um clique. Ele cadastra cliente, veículo, peça e estoque e deixa o orçamento pronto.

**Ações:** mostrar rapidamente o seletor de cenários (sem abrir o Swagger). ✂ Se quiser mostrar o catálogo, `GET /api/pecas` no Swagger (10 s).

### 4.2 As situações da Saga — 5:15 a 8:30

**Tela:** o monitor. Para cada cenário: escolher no seletor → **Criar OS de demonstração** → clicar nos botões que aparecem. A dica abaixo do seletor lembra o que dizer. Alternativa de reserva se o painel falhar: `scripts\local\saga-e2e.ps1` (roda os mesmos cenários e imprime o resultado).

**Cenário 1 — pagamento por link (≈ 1 min).** Escolher *Pagamento por link*.

> Vou começar pelo caminho feliz, com pagamento por link. A ordem foi criada e o Billing já devolveu o orçamento: a Saga espera a aprovação do cliente. Aprovo, escolhendo o link como forma de pagamento.
>
> O Billing gerou o link e a Saga ficou em **aguardando pagamento**. Nada foi reservado ainda: o estoque só é tocado depois de pagar. Agora simulo o cliente pagando o link. [clicar em *Simular pagamento*] O pagamento foi aprovado, o Execution reservou as peças e a ordem entrou em execução. Inicio e finalizo a execução, e a Saga conclui.

**Cenário 2 — compensação A (≈ 35 s).** Escolher *Compensação A: saldo insuficiente*.

> O pagamento é recusado por saldo insuficiente. O OS Service manda cancelar o orçamento e a ordem **volta para o diagnóstico**. Nada de estoque foi mexido.

✂ Variação: no cenário do link, em vez de pagar, clicar em *Simular link vencido*: o link vence e acontece a mesma compensação A.

**Cenário 3 — compensação B (≈ 50 s).** Escolher *Compensação B: sem estoque*.

> Aqui o pagamento passa, mas a peça pedida não tem estoque. O Execution responde estoque insuficiente. Repare que nada foi baixado: a reserva é tudo ou nada, numa transação do DynamoDB. O OS Service manda **estornar** o pagamento e cancela a ordem.

**Cenário 4 — compensação C (≈ 50 s).** Escolher *Compensação C: execução falha*.

> O estoque foi reservado e a ordem está em execução. Agora a execução falha. [clicar em *Falhar execução*] O OS Service manda primeiro **liberar o estoque** e depois **estornar** o pagamento. Veja a ordem na tabela de mensagens: liberar peças, estornar dinheiro, cancelar.

### 4.3 O diário da Saga e as consultas — 8:30 a 9:00

**Tela:** a tabela de mensagens da última Saga (monitor); depois Swagger do Execution com `GET /api/estoque`.

**Fala:**

> O OS Service guarda o **diário** de cada Saga: cada comando enviado, cada evento recebido e cada mudança de estado, com horário. É isso que o monitor está mostrando, e é o que torna a auditoria simples.
>
> E, no Execution Service, o estoque mostra o resultado: a peça devolvida na compensação voltou à quantidade original.

---

## 5. Qualidade: testes, cobertura e BDD — 9:00 a 10:30

**Tela:** relatório HTML do JaCoCo (`target/site/jacoco/index.html`) de um serviço; depois o painel do SonarQube Cloud; por fim o arquivo `saga_ordem_servico.feature`.

**Fala:**

> O desafio exige pelo menos **oitenta por cento de cobertura de testes por serviço**. Coloquei essa regra no próprio build: se a cobertura de linhas cair abaixo de oitenta, o `verify` do Maven falha, localmente e no pipeline. Aqui está o relatório do JaCoCo: [CONFIRME NO RELATÓRIO: OS Service __%, Billing __%, Execution __%]. Todos acima do mínimo.
>
> Os testes são de vários níveis: unitários no domínio, testes dos casos de uso com repositórios em memória, e testes de integração com **PostgreSQL e DynamoDB (via LocalStack) rodando em contêineres**, incluindo a Saga inteira persistida.
>
> A análise de qualidade roda no **SonarQube Cloud**. [CONFIRME: aqui está o painel, sem vulnerabilidades abertas.] Quando o Sonar apontou a desativação do CSRF, em vez de silenciar, documentei o motivo: a API é *stateless*, usa token no cabeçalho e não cookie de sessão.
>
> E há o **BDD**, com Cucumber, escrito em português. Este arquivo descreve o fluxo completo da Saga: o caminho feliz, as três compensações e um cenário de **mensagem repetida que não duplica efeitos**. Os cenários são executados nos testes e leem como uma especificação do negócio.

---

## 6. CI/CD por serviço — 10:30 a 12:00

**Tela:** página *Actions* do repositório `oficina-os-service`, uma execução verde com os jobs abertos. Depois, rapidamente, o arquivo `.github/workflows/ci.yml`.

**Fala:**

> Cada serviço tem o **seu próprio repositório e o seu próprio pipeline**, no GitHub Actions. Um serviço pode ser alterado e implantado sem tocar nos outros.
>
> O pipeline tem etapas encadeadas. Primeiro, **build e testes com a regra de cobertura**; o relatório fica guardado como evidência. Depois, a **análise de qualidade** no SonarQube. Em seguida, o **build da imagem Docker**. Na `main`, a imagem é publicada no ECR e o **deploy** é feito no Kubernetes, com espera pelo término do rollout.
>
> Não guardo chave de acesso da AWS no GitHub: o pipeline se autentica por **OIDC**, assumindo um papel temporário, restrito aos meus repositórios.
>
> A branch `main` é **protegida**: toda mudança entra por Pull Request, e o pipeline precisa passar antes do merge. [CONFIRME: mostre as regras da branch em *Settings*.]
>
> A mesma ideia vale para a Lambda de autenticação e para a infraestrutura, cada uma no seu repositório.

---

## 7. Nuvem, Kubernetes e observabilidade — 12:00 a 13:30

> ⚠ Este bloco só pode ser gravado no **dia do deploy real** (`terraform apply` em duas fases, depois `deploy-aws.ps1`). Faça a gravação em seguida e rode `terraform destroy` no fim. Se o deploy não estiver pronto, grave o trecho de **7.1** mostrando os arquivos do Terraform e diga explicitamente que a execução na AWS está em [data].

### 7.1 Infraestrutura como código e Kubernetes — 12:00 a 12:50

**Tela:** pasta `infra/terraform` no editor; depois `kubectl get pods` e o console da AWS (EKS, RDS, DynamoDB, SNS/SQS).

**Fala:**

> Toda a infraestrutura é criada com **Terraform**: a rede, o cluster EKS com dois nós, os dois bancos PostgreSQL no RDS, as tabelas do DynamoDB, os tópicos e filas, os repositórios de imagem, o API Gateway e a Lambda.
>
> Aqui no cluster estão os três serviços rodando como pods, cada um com o seu *Deployment*, o seu *Service* e a sua configuração. As permissões dos pods à AWS são dadas por **IRSA**: cada serviço assume um papel com só as permissões de que precisa, como consumir a sua fila e publicar nos tópicos.

### 7.2 Fluxo pela API Gateway — 12:50 a 13:10

**Tela:** chamada a `POST /auth/cpf` na URL do API Gateway e depois a uma rota do OS Service com o token.

**Fala:**

> Na AWS, o acesso é pelo API Gateway: o login por CPF chega na Lambda e as demais rotas, ao OS Service no cluster.

### 7.3 Observabilidade — 13:10 a 13:30

**Tela:** painel do New Relic (serviços, transações, logs).

**Fala:**

> Para observabilidade, os serviços levam o agente do **New Relic**, que mostra as transações, a latência e os erros de cada um, e as mensagens da Saga carregam um **identificador de correlação**, que permite seguir a mesma operação de um serviço ao outro. Cada serviço também expõe o *health check* usado pelo Kubernetes.

---

## 8. Encerramento — 13:30 a 14:30

**Tela:** slide final com os links dos repositórios e a lista de evoluções.

**Fala:**

> Para fechar: construí três microsserviços independentes, com bancos relacional e NoSQL, comunicação assíncrona, uma Saga orquestrada com compensações justificadas, testes acima do mínimo, BDD e pipeline por serviço.
>
> Também tomei decisões conscientes sobre o que **não** fazer agora, e deixei registrado: **pagamento na retirada** do veículo, que mudaria a ordem da Saga; **aviso por e-mail ao cliente**, que seria um consumidor dos eventos que já existem; e **separar o estoque** num serviço próprio, o que faz sentido no futuro, mas custaria um quarto serviço e mudaria a Saga. As justificativas estão na documentação.
>
> Todos os repositórios, a documentação de arquitetura, os diagramas de DDD e de Event Storming, as coleções do Postman e a evidência de cobertura estão nos links do documento de entrega. Obrigado pela atenção!

---

## Se o tempo estourar
Corte nesta ordem, até caber em 14:30:
1. A explicação de cada compensação no bloco 4.2 (deixe só a frase de abertura e mostre o diário).
2. O trecho do Sonar sobre o CSRF (bloco 5).
3. O bloco 7.2.

## Checklist de gravação
- [ ] Percentuais de cobertura conferidos no relatório de hoje (os de 23/10 eram OS 94,9% e Billing 90,5%, antes das últimas mudanças).
- [ ] Painel do Sonar sem vulnerabilidades abertas.
- [ ] Branch `main` protegida e `soat-architecture` convidado nos repositórios (confirme as telas que vai mostrar).
- [ ] Bloco 7 gravado no dia do deploy, com `terraform destroy` logo depois.
- [ ] Duração final do arquivo ≤ 15:00 e link aberto sem login.
