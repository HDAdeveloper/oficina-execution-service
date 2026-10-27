# ADR-007 — Autenticação do cliente por CPF: Lambda, JWT com papéis e consulta por REST

**Status:** Aceito · 24/10/2026

## Contexto
O enunciado herda das fases anteriores o acesso do cliente por CPF, com API Gateway e Lambda. Até a Etapa 5
todo JWT válido valia como administrador. Um token de cliente final não pode abrir a área administrativa,
e a Lambda não pode ler o banco do OS Service (cada serviço é dono do seu banco).

## Decisão
1. **Papéis na claim `roles` do JWT (HS256, segredo compartilhado `JWT_SECRET`):**
   `ADMIN` (login do OS Service), `CLIENTE` (emitido pela Lambda), `SERVICE` (token de 60 s para a Lambda).
2. **Autorização por papel nos três serviços.** OS Service: `CLIENTE` → só `GET /api/minhas-ordens`;
   `SERVICE` → só `GET /api/clientes/documento/{cpf}`; o restante exige `ADMIN`. Billing e Execution exigem `ADMIN`.
3. **Lambda Java 21** (`auth-lambda`): valida os dígitos do CPF, consulta o cliente pelo REST do OS Service,
   exige cliente ativo e emite o JWT de 1 hora. Sem Spring: só JDK + Jackson, para iniciar rápido.
4. **Respostas:** 400 CPF inválido; 401 genérico quando o CPF é válido mas não há cliente ativo (não revela
   se o CPF existe); 502 se o OS Service estiver fora.
5. **Área do cliente:** `GET /api/minhas-ordens` devolve as OS do CPF que está no `sub` do token. O cliente
   nunca informa o CPF na consulta, então não consegue ver OS de outra pessoa.

## Consequências
- Um segredo compartilhado (HS256) simplifica o desenvolvimento e o prazo. Na AWS ele vem do Secrets Manager
  (variável do pod e da Lambda). Evolução natural: RS256 com chave pública, sem segredo nos consumidores.
- A Lambda depende da disponibilidade do OS Service; o tempo limite de 5 s e o 502 deixam isso explícito.
- Fica mais um artefato com pipeline próprio (CI/CD em `auth-lambda/.github/workflows`).
