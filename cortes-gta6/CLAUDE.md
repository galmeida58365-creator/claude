# Cortes GTA 6: instruções para o Claude

Esta pasta gera cortes verticais (YouTube Shorts / TikTok) de youtubers reagindo aos trailers de GTA 6. Conta: "Vice Cortes" (@vicecortes). O dono fala português; responda em português, de forma simples.

## Rodar

Windows (venv criado pelo `instalar.bat`):

```
venv\Scripts\python -m pip install -q -U "yt-dlp[default]"
venv\Scripts\python cortes.py tudo                 # busca vídeos novos, corta, gera o painel
venv\Scripts\python cortes.py link URL [URL ...]   # corta vídeos específicos
venv\Scripts\python cortes.py arquivo X.mp4 --canal "Nome" [--url URL]
venv\Scripts\python cortes.py buscar               # só mostra o que encontraria
venv\Scripts\python cortes.py painel               # refaz cortes/PAINEL.html
```

Sem venv: `python cortes.py ...` (precisa de `pip install -r requisitos.txt` e do ffmpeg no PATH).

## Como funciona

- `config.json`: buscas, filtros, tamanho do corte, textos e hashtags. Mude aqui em vez de mudar o código.
- `estado.json`: vídeos já processados e cortes gerados (evita repetir). Não apague sem pedir.
- Saída: `cortes/AAAA-MM-DD/<canal>_<id>_<n>/corte.mp4` + `titulo.txt`, `descricao_youtube.txt`, `legenda_tiktok.txt`; painel em `cortes/PAINEL.html`.
- Momentos: "mais repetidos" do YouTube (`heatmap` do yt-dlp) + picos de volume; legendas com faster-whisper.

## Regras

- Postar é manual (YouTube Studio e tiktok.com/upload). Uploads pela API do YouTube de projetos não verificados ficam presos como privados.
- Sempre manter o crédito ao criador original na tela e na descrição.
- Se o dono disser que um youtuber pediu para tirar os cortes, coloque o canal em `canais_bloqueados`.
- Quando o YouTube bloquear downloads, primeiro atualize o yt-dlp; depois sugira `cookies_do_navegador: "firefox"`.
