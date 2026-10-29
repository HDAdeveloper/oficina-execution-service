# ADR-008 — Estoque permanece no Execution Service; serviço próprio fica como evolução futura

**Status:** Aceito · 29/10/2026

## Contexto
Estoque (quanto existe e o que está reservado) e execução do serviço (o trabalho do mecânico) são assuntos
distintos e, em DDD, poderiam ser contextos separados. Hoje os dois vivem no **Execution Service**
([ADR-001](001-microsservicos-e-limites.md)): o estoque guarda só quantidades; o preço e o nome da peça ficam
no catálogo do OS Service, ligados pelo `codigo`.

Pontos que favorecem separar:
- regras de estoque tendem a crescer de forma independente (reposição, estoque mínimo, vários depósitos);
- escala e ciclo de vida diferentes da execução;
- o cadastro da peça está dividido entre dois serviços (nome/preço no OS, quantidade no Execution), o que obriga
  a manter o mesmo `codigo` nos dois (o script de carga inicial grava nos dois).

## Decisão
**Manter o estoque dentro do Execution Service** e registrar o serviço próprio como **evolução futura**.

Motivos:
- **Custo/prazo.** Um quarto microsserviço exige repositório, Dockerfile, pipeline com Sonar e cobertura de
  80%, manifestos Kubernetes, Terraform (banco, filas, IRSA), documentação e mais ensaio. A entrega é em 10/11/2026.
- **A Saga mudaria.** `RESERVAR_ESTOQUE` e `LIBERAR_ESTOQUE` sairiam do Execution para o novo serviço, com fila,
  tópico, orquestração e compensações próprias (a compensação C libera o estoque depois de uma falha na execução).
  O e2e e o BDD teriam de ser refeitos e revalidados.
- **Não é requisito.** O enunciado pede pelo menos 3 microsserviços; os 3 atuais têm papéis claros
  (core, faturamento, execução).
- **Já está separado por dentro.** No código o estoque tem agregado (`ItemEstoque`), porta de entrada
  (`GestaoEstoque`/`GestaoEstoqueService`), porta de saída (`EstoqueRepository`) e tabela DynamoDB próprios.
  Extrair depois é uma mudança mecânica, sem reescrever regras.

## Consequências
- O Execution Service continua com duas responsabilidades; a fronteira interna (pacotes e portas) precisa ser
  preservada para a extração futura.
- O cadastro da peça continua dividido entre OS Service (nome/preço) e Execution Service (quantidade), ligados
  pelo `codigo`. É uma dívida consciente.
- **Caminho da extração:** (1) novo serviço dono do cadastro e do saldo da peça (o preço também pode ir para ele);
  (2) o OS Service passa a consultar o catálogo por API ou por réplica via eventos; (3) os comandos de estoque
  da Saga passam a ser tratados por ele, mantendo os mesmos contratos de mensagem e as compensações.
