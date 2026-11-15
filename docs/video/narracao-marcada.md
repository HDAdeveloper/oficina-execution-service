# Vídeo de slides: marcações para trocar a narração

O vídeo `video-slides-sem-audio.mp4` (6 min 23 s) tem os slides já cronometrados para a narração sintética.
Se você for gravar a **sua voz** no lugar, leia o texto de [`../roteiro-narracao.md`](../roteiro-narracao.md) e **troque de slide quando disser a frase-chave** da tabela.
Monte com `powershell -ExecutionPolicy Bypass -File montar-video.ps1` (voz sintética) ou `... -MinhaVoz minha-voz.mp3` (a sua, uma faixa só do mesmo tamanho; veja a última seção).

## Linha do tempo do vídeo de slides

| Início | Fim | Slide (arquivo em `slides/`) | Frase-chave que entra no slide | Voz |
|---|---|---|---|---|
| 0:00 | 0:14 | `01_capa` | "Olá! Eu sou o Henrique…" | sintética (faixa 1) |
| 0:14 | 1:04 | `02_desafio` | "O desafio pede que cada serviço seja independente…" | faixa 1 |
| 1:04 | 1:31 | `03_arquitetura` | "São três microsserviços de negócio…" | faixa 2 |
| 1:31 | 2:39 | `04_servicos` | "O Billing Service cuida do orçamento…" | faixa 2 |
| 2:39 | 3:18 | `05_mensageria` | "Os serviços não chamam uns aos outros por REST…" | faixa 2 |
| 3:18 | 4:24 | `06_saga_orquestrada` | "Escolhi a Saga orquestrada…" | faixa 3 |
| 4:24 | 5:00 | `07_compensacoes` | "E há três compensações…" | faixa 3 |
| 5:00 | 5:40 | `08_garantias` | "Para isso ser confiável, uso três garantias…" | faixa 3 |
| 5:40 | 5:46 | `09_demo` | cartão do bloco 4 (silencioso) | sem voz |
| 5:46 | 5:52 | `10_qualidade` | cartão do bloco 5 | sem voz |
| 5:52 | 5:58 | `11_cicd` | cartão do bloco 6 | sem voz |
| 5:58 | 6:04 | `12_nuvem` | cartão do bloco 7 | sem voz |
| 6:04 | 6:24 | `13_final` | "Para fechar: construí três microsserviços…" | **sem voz** (acabou o crédito); leia o bloco 8 do roteiro |

Os tempos dos slides 3 a 8 são aproximados (a voz foi gerada em 3 faixas e o corte entre slides é por proporção do texto). Se um corte
ficar adiantado ou atrasado, ajuste no editor arrastando a borda do slide.

## Montagem final (≤ 15 min), na ordem

| Ordem | Parte | Duração alvo | Fonte |
|---|---|---|---|
| 1 | Abertura, Arquitetura e Saga | 5:40 | `video-slides-com-voz.mp4` (corte em 5:40) |
| 2 | Demonstração ponta a ponta | 4:00 | **Clipe A** (você grava) |
| 3 | Qualidade: testes, cobertura e BDD | 1:20 | **Clipe B** (você grava) |
| 4 | CI/CD e deploy automatizado | 1:20 | **Clipe C** (você grava) |
| 5 | Nuvem, Kubernetes e observabilidade | 1:20 | **Clipe D** (você grava) |
| 6 | Encerramento | 0:30 | slide `13_final` + bloco 8 do roteiro (leia ou grave por cima) |
| | **Total** | **≈ 14:10** | |

Editor sugerido: **Clipchamp** (já vem no Windows 11; Win → "Clipchamp"). Importe o vídeo de slides e os 4 clipes, arraste na ordem,
**Exportar → 1080p**. Como o vídeo de slides traz os cartões de bloco no fim, **corte o vídeo em 5:40** antes de colocar os clipes.

## Usar a sua voz (ou trocar um trecho)

1. Grave a narração dos slides 1 a 8 (do "Olá!" ao fim das garantias) numa faixa só, lendo o texto dos blocos 1, 2 e 3 de `roteiro-narracao.md`.
2. Para o encaixe automático, a faixa deveria ter 5:40. Se ficar diferente, **não use** `-MinhaVoz`: no Clipchamp, solte o vídeo de slides e o seu áudio na linha do tempo e arraste as bordas dos slides até casar com as frases-chave da tabela.
3. Para trocar só a faixa de áudio da sintética no Clipchamp: clique no áudio do vídeo → **Separar áudio** → apague → solte a sua gravação.
