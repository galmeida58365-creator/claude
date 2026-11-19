# Baixar TikTok

Baixa vídeos do TikTok **na melhor qualidade disponível e sem marca d'água**. Tem versão para **celular Android** e para **Windows**.

# No celular (Android 10 ou mais novo)

## Instalar

1. Baixe o arquivo **`dist/BaixarTikTok.apk`** no celular e toque nele.
2. Se o celular avisar que não pode instalar apps de fora da Play Store, toque em **Configurações** e ligue **Permitir desta fonte**. Volte e toque em **Instalar**.
3. Se aparecer o aviso do Play Protect, toque em **Mais detalhes → Instalar mesmo assim**. O aviso aparece porque o app não veio da Play Store.

## Usar

- **Pelo TikTok:** no vídeo, toque em **Compartilhar**. Na lista de apps (às vezes em **Mais** ou **Outros**), escolha **Baixar TikTok**. O app abre e o vídeo baixa sozinho.
- **Copiando o link:** no TikTok, toque em **Compartilhar → Copiar link** e abra o Baixar TikTok. Ele percebe o link copiado e baixa.
- **Colando:** cole o link no campo ou toque em **Colar e baixar**.

Os vídeos ficam na **galeria**, na pasta **Movies/TikTok**. Toque num vídeo da lista para assistir.

Para achar o vídeo, o app abre a página dele por dentro, como um navegador. Se o TikTok mostrar uma verificação ou um botão, a página aparece no app: é só resolver que o download continua.

# No computador (Windows)

## Instalar (só na primeira vez)

1. Instale o Python: https://www.python.org/downloads/ → botão amarelo **Download Python**.
   Na primeira tela do instalador, **marque "Add python.exe to PATH"** e clique em **Install Now**.
2. Baixe esta pasta `baixar-tiktok` para o computador (por exemplo, na Área de Trabalho).

## Usar no computador

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

## Gerar o APK de novo

```sh
baixar-tiktok/android/build.sh
```

Mesmo esquema do app Caixa do Mês: baixa as ferramentas, compila e assina com a chave `baixar-tiktok/android/baixar-tiktok.p12`. A chave é criada na primeira vez e não vai para o repositório. Guarde-a: um APK assinado com outra chave não instala por cima do antigo. Aumente `VERSAO_CODIGO` no `build.sh` a cada versão.

---

O programa do computador funciona também em Mac e Linux: `python3 -m pip install -U yt-dlp` e depois `python3 baixar_tiktok.py`.
Usa o [yt-dlp](https://github.com/yt-dlp/yt-dlp) por baixo.
