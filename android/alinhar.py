"""Equivalente ao zipalign: alinha em 4 bytes os arquivos guardados sem compressão no APK.
Uso: python3 alinhar.py entrada.apk saida.apk   |   python3 alinhar.py --conferir arquivo.apk"""
import struct
import sys
import zipfile


def deslocamento_dados(f, info):
    f.seek(info.header_offset)
    cab = f.read(30)
    n, e = struct.unpack("<HH", cab[26:30])
    return info.header_offset + 30 + n + e


def alinhar(origem, destino):
    with zipfile.ZipFile(origem) as zin, zipfile.ZipFile(destino, "w") as zout:
        for info in zin.infolist():
            dados = zin.read(info)
            novo = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            novo.compress_type = info.compress_type
            novo.external_attr = info.external_attr
            novo.extra = b""
            if info.compress_type == zipfile.ZIP_STORED:
                base = zout.fp.tell() + 30 + len(info.filename.encode("utf-8"))
                k = (-(base + 6)) % 4
                # Campo extra 0xD935, o mesmo que o zipalign usa para preenchimento.
                novo.extra = struct.pack("<HHH", 0xD935, 2 + k, 4) + b"\0" * k
            zout.writestr(novo, dados)


def conferir(caminho):
    ruins = []
    with zipfile.ZipFile(caminho) as z, open(caminho, "rb") as f:
        for info in z.infolist():
            if info.compress_type == zipfile.ZIP_STORED and deslocamento_dados(f, info) % 4:
                ruins.append(info.filename)
    if ruins:
        sys.exit("Não alinhados: " + ", ".join(ruins))
    print("Alinhamento conferido")


if __name__ == "__main__":
    if sys.argv[1] == "--conferir":
        conferir(sys.argv[2])
    else:
        alinhar(sys.argv[1], sys.argv[2])
