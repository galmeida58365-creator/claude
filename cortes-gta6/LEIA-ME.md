# Cortes GTA 6: reações e gameplay

Programa que roda no seu PC (Windows) e faz tudo sozinho:

1. **Procura** no YouTube vídeos de youtubers reagindo aos trailers de GTA 6 (ou jogando, depois do lançamento).
2. **Acha os melhores momentos** de cada reação usando:
   - o gráfico de "mais repetidos" do YouTube (as partes que o público mais voltou para ver);
   - os picos de volume (quando o youtuber grita ou surta).
3. **Corta** cada momento (~40 s) no formato vertical 1080x1920, com fundo desfocado, título em cima ("FULANO REAGINDO / AO TRAILER DE GTA 6"), **legenda automática** palavra por palavra e crédito ao criador embaixo.
4. **Monta um painel** (`cortes/PAINEL.html`) com cada vídeo, título, descrição do YouTube, legenda do TikTok com botão de copiar, sugestão de dia e horário para postar e caixinhas de "já postei".

## Dois modos (troca sozinho no lançamento)

| Quando | Modo | O que corta |
|---|---|---|
| Até 18/11/2026 | **trailer** | Youtubers reagindo aos trailers |
| A partir de 19/11/2026 (lançamento) | **gameplay** | Youtubers jogando: risadas, gritos, surtos, os momentos mais repetidos |

Você não precisa fazer nada: o `rodar.bat` vê a data e escolhe o modo. Se o lançamento mudar, altere `data_lancamento` no `config.json`. Para forçar um modo, troque `"modo": "auto"` por `"trailer"` ou `"gameplay"`.

No modo gameplay os vídeos são lives longas (até 12 h). Para não baixar gigas, o programa baixa **só o áudio**, acha os momentos e depois baixa **só os trechos** que viram corte.


## Nome da conta (YouTube e TikTok)

**Vice Cortes**, arroba **@vicecortes**

- Lembra Vice City, a cidade de GTA 6, sem usar a marca "GTA" no nome (a Rockstar não tem como reclamar e ninguém confunde com um canal oficial).
- Depois do lançamento, o canal continua fazendo sentido com cortes de gameplay.

Se o arroba já estiver em uso, na ordem: **@vicecortesbr**, **@cortesdeleonida** (Leonida é o estado de GTA 6), **@vicereage**.
Se trocar, mude `nome_da_conta` e `arroba` no `config.json`.

## Primeira vez (uns 15 minutos)

1. Baixe o **`cortes-gta6.zip`** que o Claude mandou na conversa, clique com o botão direito > **Extrair tudo** e abra a pasta **`cortes-gta6`**.
   (Ou pelo GitHub: branch `ccr-df95a07f-nh1hoa` > **Code > Download ZIP**.)
2. Dois cliques em **`instalar.bat`**. Ele instala Python, ffmpeg e Deno e as bibliotecas.
   Se ele pedir, feche a janela e rode o `instalar.bat` de novo.
3. Dois cliques em **`rodar.bat`**. Na primeira vez demora mais, porque baixa o modelo de legenda (~500 MB).
4. Quando terminar, o **painel** abre sozinho no navegador.

## Uso no dia a dia

| Quero... | Faço |
|---|---|
| Gerar cortes novos | `rodar.bat` |
| Cortar um vídeo específico (ex.: a reação de um youtuber grande) | `cortar-link.bat` e cole o link |
| Rodar sozinho todo dia às 10h | `agendar-diario.bat` (uma vez) |
| Parar o agendamento | `desagendar.bat` |
| Ver os cortes | `cortes/PAINEL.html` |

O programa lembra quais vídeos já cortou e não repete.

## Como postar

**YouTube Shorts** (pelo PC): YouTube Studio > **Criar > Enviar vídeos**. Dá para enviar vários de uma vez. Para cada um, cole título e descrição do painel e, em **Visibilidade**, use **Programar** com o horário sugerido.

**TikTok**: pelo PC em **tiktok.com/upload** (também dá para programar), ou mande os vídeos para o celular e poste pelo app. Cole a legenda do TikTok do painel.

Ritmo bom para começar: **3 por dia** em cada rede (12h, 18h e 21h, já sugeridos no painel). Em semana de lançamento dá para subir para 4 ou 5.

## ⚠️ Direitos autorais: leia antes de postar

Os vídeos de reação são dos youtubers. Repostar sem permissão pode gerar **reivindicação (Content ID), strike** ou desmonetização por "conteúdo reutilizado". Três strikes derrubam o canal. Para reduzir o risco:

- **Prefira quem libera cortes.** Muitos streamers e youtubers deixam qualquer um cortar e até pedem isso (vários têm "programa de cortes"). Peça por DM ou e-mail quando der. Depois coloque esses canais em `canais_permitidos` no `config.json` e mude `somente_canais_permitidos` para `true`: assim o programa só corta deles.
- **Já vem configurado só com alanzoka, BRKsEDU e Davy Jones** (`somente_canais_permitidos: true`). Para incluir outro youtuber, adicione o nome do canal em `canais_permitidos` (maiúscula, espaço e acento não importam). Para cortar de qualquer canal, mude para `false`.
- **Sempre dê crédito** (o programa já põe o nome na tela e o link na descrição).
- **Se um youtuber pedir para tirar, tire na hora** e coloque o nome dele em `canais_bloqueados`.
- Para monetizar no YouTube, cortes puros têm risco de reprovação. Ajuda adicionar algo seu: um comentário, uma reação sua ou uma compilação de vários youtubers no mesmo momento do trailer.

## Ajustes (`config.json`)

Abra com o Bloco de Notas.

| Campo | O que faz |
|---|---|
| `modo` / `data_lancamento` | `auto` troca de trailer para gameplay na data |
| `canais_permitidos` | Youtubers que ele corta (com `somente_canais_permitidos: true`) |
| `qualidade_maxima` | 1080 ou 720 (720 baixa mais rápido) |

Dentro de `"modos"` cada modo (`trailer` e `gameplay`) tem os próprios ajustes:

| Campo | O que faz |
|---|---|
| `buscas` | O que o programa pesquisa no YouTube |
| `cortes_por_video` | Quantos cortes tira de cada vídeo |
| `segundos_antes_do_pico` / `segundos_depois_do_pico` | Tamanho do corte (trailer 40 s, gameplay 45 s) |
| `pular_inicio_seg` | Ignora o começo (no gameplay, 5 min de "boa noite chat") |
| `titulo_linha1` / `titulo_linha2` | Texto em cima do vídeo (`{canal}` vira o nome do youtuber) |
| `titulos`, `hashtags_youtube`, `hashtags_tiktok` | Textos das postagens |

Ajustes gerais (fora de `"modos"`):

| Campo | O que faz |
|---|---|
| `videos_por_rodada` | Quantos vídeos novos ele corta por vez |
| `zoom_do_video` | 1.0 = vídeo inteiro; 1.25 = maior, cortando as laterais |
| `legendas` / `modelo_whisper` | Liga a legenda automática; `small` é bom, `base` é mais rápido, `medium` é mais preciso |
| `cookies_do_navegador` | Se o YouTube bloquear ("confirme que não é um robô"), coloque `"firefox"` |

## Problemas comuns

- **"Sign in to confirm you're not a bot"**: rode de novo mais tarde, ou use `"cookies_do_navegador": "firefox"` (precisa estar logado no YouTube no Firefox).
- **Erro ao baixar algum vídeo**: o `rodar.bat` atualiza o yt-dlp sozinho; tente de novo no dia seguinte.
- **Legenda errada**: troque `modelo_whisper` para `medium` (mais lento, mais preciso).
- **Corte começou no momento errado**: ajuste `segundos_antes_do_pico`, ou use o `cortar-link.bat` com o vídeo que você escolheu.

## Usando o Claude Code no seu PC

Se tiver o Claude Code instalado, abra um terminal nesta pasta, rode `claude` e diga, por exemplo, *"roda os cortes"* ou *"corta esse vídeo: LINK"*. O arquivo `CLAUDE.md` daqui explica para ele como usar o programa.
