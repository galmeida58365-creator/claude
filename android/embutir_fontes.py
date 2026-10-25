"""Copia a página para os assets do app com as fontes do Google embutidas, para funcionar sem internet."""
import os
import re
import sys
import urllib.request

UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"


def baixar(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read()


def main(origem, destino):
    html = open(origem, encoding="utf-8").read()
    link = re.search(r'<link rel="stylesheet" href="(https://fonts\.googleapis\.com/[^"]+)">', html)
    if link:
        try:
            css = baixar(link.group(1).replace("&amp;", "&")).decode("utf-8")
            # Só os blocos latin e latin-ext (português).
            blocos = re.findall(r"/\* (latin(?:-ext)?) \*/\s*(@font-face \{.*?\})", css, re.S)
            os.makedirs(os.path.join(destino, "fontes"), exist_ok=True)
            saida = []
            for i, (_, bloco) in enumerate(blocos):
                url = re.search(r"url\((https://[^)]+)\)", bloco).group(1)
                nome = f"fontes/f{i}.woff2"
                with open(os.path.join(destino, nome), "wb") as f:
                    f.write(baixar(url))
                saida.append(bloco.replace(url, nome))
            html = re.sub(r'<link rel="preconnect"[^>]*>\n', "", html)
            html = html.replace(link.group(0), "<style>\n" + "\n".join(saida) + "\n</style>")
            print(f"{len(saida)} arquivos de fonte embutidos")
        except Exception as e:  # sem internet: o app usa as fontes do sistema
            print(f"Fontes não embutidas ({e}); o app usará as fontes do sistema")
    with open(os.path.join(destino, "index.html"), "w", encoding="utf-8") as f:
        f.write(html)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
