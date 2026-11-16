"""Transforma notas/index.html (escrito como página de Artifact, sem <html>/<head>) num documento
completo para o app: doctype, charset, viewport e o mesmo reset básico que o Artifact aplica."""
import sys

CABECA = (
    '<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">'
    '<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">'
    "<style>:root{color-scheme:light;padding-top:env(safe-area-inset-top,0px);"
    "padding-bottom:env(safe-area-inset-bottom,0px)}"
    "body{margin:0;font:14px/1.4 system-ui,-apple-system,sans-serif}"
    "img{max-width:100%}[hidden]{display:none!important}</style></head><body>\n"
)


def main(origem, destino):
    corpo = open(origem, encoding="utf-8").read()
    with open(destino, "w", encoding="utf-8") as f:
        f.write(CABECA + corpo + "\n</body></html>\n")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
