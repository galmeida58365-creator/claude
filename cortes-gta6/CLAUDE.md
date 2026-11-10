# Cortes GTA 6: instruções para o Claude

Esta pasta gera cortes verticais (YouTube Shorts / TikTok) de youtubers com GTA 6: reações aos trailers (modo `trailer`) e, a partir do lançamento em 19/11/2026, momentos engraçados de gameplay (modo `gameplay`). `"modo": "auto"` troca sozinho pela `data_lancamento`; `--modo` força um modo. Conta: "Vice Cortes" (@vicereage no YouTube). O dono fala português; responda em português, de forma simples.

## Rodar

Windows (venv criado pelo `instalar.bat`):

```
venv\Scripts\python -m pip install -q -U "yt-dlp[default]"
venv\Scripts\python cortes.py tudo                 # busca vídeos novos, corta, gera o painel
venv\Scripts\python cortes.py link URL [URL ...]   # corta vídeos específicos
venv\Scripts\python cortes.py arquivo X.mp4 --canal "Nome" [--url URL]
venv\Scripts\python cortes.py buscar               # só mostra o que encontraria
venv\Scripts\python cortes.py painel               # refaz cortes/PAINEL.html
venv\Scripts\python cortes.py limpar [--dias N]     # apaga cortes antigos (0 = todos)
```

Sem venv: `python cortes.py ...` (precisa de `pip install -r requisitos.txt` e do ffmpeg no PATH).

## Como funciona

- `config.json`: ajustes gerais em cima; buscas, tamanho do corte, textos e hashtags de cada modo em `modos.trailer` / `modos.gameplay`. Mude aqui em vez de mudar o código.
- Vídeos acima de `baixar_so_trechos_acima_de_min` (lives): baixa só o áudio, acha os momentos e baixa só os trechos.
- O dono tem pouco espaço em disco: cortes são apagados `apagar_cortes_depois_de_dias` depois de criados (a cada rodada) e downloads logo após cortar. Não desligue isso sem ele pedir.
- `estado.json`: vídeos já processados e cortes gerados (evita repetir). Não apague sem pedir.
- Saída: `cortes/AAAA-MM-DD/<canal>_<id>_<n>/corte.mp4` + `titulo.txt`, `descricao_youtube.txt`, `legenda_tiktok.txt`; painel em `cortes/PAINEL.html`.
- Momentos: "mais repetidos" do YouTube (`heatmap` do yt-dlp) + picos de volume; legendas com faster-whisper.

## Regras

- Postar é manual (YouTube Studio e tiktok.com/upload). Uploads pela API do YouTube de projetos não verificados ficam presos como privados.
- Sempre manter o crédito ao criador original na tela e na descrição.
- Se o dono disser que um youtuber pediu para tirar os cortes, coloque o canal em `canais_bloqueados`.
- Quando o YouTube bloquear downloads, primeiro atualize o yt-dlp; depois sugira `cookies_do_navegador: "firefox"`.
