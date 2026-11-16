# Narração por slide (PPT leve, 19 slides)

> Texto para ler em voz alta durante *Gravar Apresentação de Slides*. O mesmo texto está nas **notas do orador** de cada slide. Cerca de 5 min 40 s de fala; os clipes dos blocos 4, 6 e 7 já têm a voz gravada, então nos slides de passagem você só diz a frase curta e passa para o clipe.

## Slide 1 · Abertura

Olá! Eu sou o Henrique Dellagnesi Azevedo, RM 372407, da turma 15 da Pós-Tech SOAT da FIAP. Neste vídeo apresento a Fase 4 do Tech Challenge: a Oficina Tech Service, a minha oficina mecânica, que antes era uma aplicação só e agora é dividida em microsserviços.

## Slide 2 · O desafio

O desafio pede serviços independentes, cada um com repositório, banco e pipeline próprios, conversando por mensagens. O ponto mais delicado é a consistência: como manter pagamento, estoque e execução coerentes quando um passo falha? A resposta é o padrão Saga. Vou mostrar a arquitetura, a Saga, uma demonstração, os testes, o CI/CD e a execução na AWS.

## Slide 3 · Arquitetura

Esta é a arquitetura. O cliente entra pelo API Gateway: o login por CPF vai para uma Lambda, e as demais rotas vão para o OS Service. São três microsserviços, cada um com o seu banco, e eles só se comunicam por mensagens, com SNS e filas SQS. Nenhum serviço lê o banco do outro.

## Slide 4 · Os três serviços

O OS Service cuida de clientes, veículos, catálogo e do ciclo de vida da ordem, e é ele quem orquestra a Saga. Usa PostgreSQL. O Billing Service cuida do orçamento, do pagamento por link e do estorno, também em PostgreSQL. O gateway de pagamento fica atrás de uma porta: nesta demonstração uso um provedor simulado, que permite provocar cada compensação, e a integração real com o Mercado Pago está implementada e validada com uma conta de testes. O Execution Service guarda o estoque e controla a execução. Escolhi o DynamoDB, porque o acesso é por chave e preciso de transações condicionais para o estoque nunca ficar negativo.

## Slide 5 · Mensageria

A comunicação é assíncrona. O OS Service publica comandos num tópico SNS, cada serviço recebe os seus na sua fila SQS, e as respostas voltam como eventos. Depois de três tentativas, a mensagem vai para uma fila de mensagens mortas. Os serviços seguem arquitetura hexagonal, em Java 21 com Spring Boot, e rodam no Kubernetes da AWS, criado com Terraform.

## Slide 6 · Saga orquestrada

Escolhi a Saga orquestrada, com o OS Service como orquestrador. O fluxo tem ordem rígida: orçamento, aprovação, cobrança, reserva de estoque e execução. E, quando algo falha, é preciso desfazer na ordem inversa. Com um único ponto que conhece o estado, fica fácil auditar e depurar; na coreografia, essa lógica ficaria espalhada pelos três serviços. No caminho feliz, o Billing gera o link de pagamento, a Saga espera o pagamento, o Execution reserva as peças e executa, e a ordem é finalizada.

## Slide 7 · Três compensações

Há três compensações, e cada uma desfaz exatamente o que já foi feito. Na A, o pagamento é recusado ou o link vence: só o orçamento foi feito, então ele é cancelado e a ordem volta para o diagnóstico. Na B, falta estoque: o pagamento já foi aprovado, então é estornado e a ordem é cancelada. Na C, a execução falha: as peças voltam ao estoque, o pagamento é estornado e a ordem é cancelada.

## Slide 8 · Garantias

Para isso ser confiável, uso três garantias. Outbox transacional: o estado e a mensagem são gravados na mesma transação, então nenhuma mensagem se perde. Idempotência: a entrega é at-least-once, e processar duas vezes não duplica efeito. E prazos: sem resposta em sessenta segundos, o comando é reenviado e, se as tentativas acabarem, a Saga compensa ou fica pendente para ação manual.

## Slide 9 · Passagem para a demonstração

Agora, a demonstração ponta a ponta, rodando na AWS. [Passe para os clipes do Bloco 4. A voz deles já está gravada.]

## Slide 10 · Testes e cobertura

O desafio exige pelo menos oitenta por cento de cobertura por serviço, e coloquei essa regra no próprio build: abaixo disso o verify falha, local e no pipeline. OS Service 95,2%, Billing 92,0% e Execution 93,5%. Os testes são unitários, de casos de uso e de integração, com PostgreSQL e DynamoDB em contêineres. A qualidade é analisada no SonarQube Cloud, e o BDD, com Cucumber em português, descreve a Saga inteira, com as três compensações e um cenário de mensagem repetida.

## Slide 11 · JaCoCo, OS Service

Relatório do OS Service: 95,2% das linhas e 83,4% dos branches, com 249 testes.

## Slide 12 · JaCoCo, Billing

Billing Service: 92,0% das linhas e 81,2% dos branches, com 167 testes.

## Slide 13 · JaCoCo, Execution

Execution Service: 93,5% das linhas e 82,5% dos branches, com 114 testes.

## Slide 14 · Passagem para o CI/CD

Agora o CI/CD. [Passe para os clipes do Bloco 6. A voz deles já está gravada.]

## Slide 15 · Passagem para a nuvem

Por fim, a nuvem e a observabilidade. [Passe para os clipes do Bloco 7. A voz deles já está gravada.]

## Slide 16 · New Relic, Saga

No New Relic, o painel mostra as ordens abertas, as Sagas iniciadas e o desfecho de cada uma: concluídas, canceladas por compensação e encerradas.

## Slide 17 · New Relic, serviços

Aqui, a latência média e p95 das APIs e as requisições por minuto de cada serviço.

## Slide 18 · New Relic, logs

E aqui, cada Saga tem um identificador de correlação, que permite seguir a mesma operação nos três serviços.

## Slide 19 · Encerramento

Para fechar: construí três microsserviços independentes, com bancos relacional e NoSQL, comunicação assíncrona, uma Saga orquestrada com compensações justificadas, testes acima do mínimo, BDD e pipeline por serviço. Deixei registrado o que decidi não fazer agora: pagamento na retirada, avisos por e-mail além do link de pagamento e estoque como serviço próprio. Os links dos repositórios e as evidências estão no documento de entrega. Obrigado pela atenção!
