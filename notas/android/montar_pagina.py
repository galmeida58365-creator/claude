"""Prepara notas/index.html para ser usada fora do repositório.

Uso: python3 montar_pagina.py origem.html destino.html [boletim.json] [--sem-cabeca]

- boletim.json (opcional): matérias e notas que já vêm prontas no app. É um arquivo particular,
  que não vai para o repositório. Formato:
  {"versao": "...", "materias": [{"nome": "...", "minimo": 60,
    "avals": [{"nome": "1º trimestre", "vale": 30, "nota": 18}, ...]}]}
- Sem --sem-cabeca, a página (escrita como Artifact, sem <html>/<head>) vira um documento completo
  para o app: doctype, charset, viewport e o mesmo reset básico que o Artifact aplica."""
import json
import sys

CABECA = (
    '<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">'
    '<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">'
    "<style>:root{color-scheme:light;padding-top:env(safe-area-inset-top,0px);"
    "padding-bottom:env(safe-area-inset-bottom,0px)}"
    "body{margin:0;font:14px/1.4 system-ui,-apple-system,sans-serif}"
    "img{max-width:100%}[hidden]{display:none!important}</style></head><body>\n"
)
MARCA = "<!-- NOTAS_INICIAIS -->"


def main(args):
    sem_cabeca = "--sem-cabeca" in args
    args = [a for a in args if a != "--sem-cabeca"]
    origem, destino = args[0], args[1]
    corpo = open(origem, encoding="utf-8").read()
    if len(args) > 2:
        dados = json.load(open(args[2], encoding="utf-8"))
        texto = json.dumps(dados, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
        assert MARCA in corpo, "marca NOTAS_INICIAIS não encontrada na página"
        corpo = corpo.replace(MARCA, "<script>window.NOTAS_INICIAIS=" + texto + ";</script>", 1)
        print(f"{len(dados['materias'])} matérias do boletim incluídas")
    with open(destino, "w", encoding="utf-8") as f:
        f.write(corpo if sem_cabeca else CABECA + corpo + "\n</body></html>\n")


if __name__ == "__main__":
    main(sys.argv[1:])
