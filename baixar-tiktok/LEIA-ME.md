# Baixar TikTok

Programa para Windows que baixa vídeos do TikTok **na melhor qualidade disponível e sem marca d'água**. Você cola o link e ele baixa sozinho.

## Instalar (só na primeira vez)

1. Instale o Python: https://www.python.org/downloads/ → botão amarelo **Download Python**.
   Na primeira tela do instalador, **marque "Add python.exe to PATH"** e clique em **Install Now**.
2. Baixe esta pasta `baixar-tiktok` para o computador (por exemplo, na Área de Trabalho).

## Usar

1. Dê dois cliques em **`Abrir-Baixar-TikTok.bat`**. Ele atualiza o que precisa e abre o programa.
2. No TikTok (site ou app), toque em **Compartilhar → Copiar link**.
3. Pronto: com a opção *"Baixar sozinho quando eu copiar um link do TikTok"* ligada, o vídeo começa a baixar na hora. Também dá para colar o link no campo (Ctrl+V) ou clicar em **Colar e baixar**.

Os vídeos vão para `Downloads\TikTok` (troque em **Trocar pasta**). Clique duas vezes num vídeo da lista para assistir.

Dá para copiar vários links seguidos: eles entram numa fila e baixam um de cada vez. O mesmo vídeo não é baixado duas vezes.

## O que ele aceita

- Links de vídeo: `tiktok.com/@perfil/video/...`, links curtos `vm.tiktok.com/...`, `vt.tiktok.com/...` e `tiktok.com/t/...`.
- **Não baixa** perfis inteiros, hashtags, músicas nem posts de fotos (de propósito: só vídeos).

## Qualidade

Ele pega a maior resolução que o TikTok oferece para o vídeo (normalmente 1080p) e descarta a versão com marca d'água. Na mesma resolução, prefere o formato H.264, que abre em qualquer player do Windows. A qualidade final depende de como o vídeo foi postado: o programa não melhora um vídeo que foi enviado em baixa resolução.

## Se der erro

- **"O TikTok mudou alguma coisa"**: feche o programa e abra de novo pelo `.bat`, que atualiza o yt-dlp. Se continuar, espere um ou dois dias: o yt-dlp costuma ser corrigido rápido.
- **"Vídeo privado"** ou **"pediu login"**: o vídeo não é público, então não dá para baixar.
- **"Sem conexão"**: confira a internet.

## Uso responsável

Baixe vídeos para assistir offline ou guardar o que é seu. Para repostar o vídeo de outra pessoa, peça autorização e dê o crédito: os vídeos têm direitos autorais de quem criou.

---

Funciona também em Mac e Linux: `python3 -m pip install -U yt-dlp` e depois `python3 baixar_tiktok.py`.
Usa o [yt-dlp](https://github.com/yt-dlp/yt-dlp) por baixo.
