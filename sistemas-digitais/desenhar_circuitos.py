"""
Gera os desenhos dos circuitos (portas lógicas) e do diagrama de blocos.

Rodar:  pip install schemdraw matplotlib pillow
        python3 desenhar_circuitos.py
Saída:  pasta desenhos/  (um PNG por bloco + desenhos.pdf com tudo)

Obs.: nos desenhos, uma entrada com o mesmo nome (ex.: "A1" aparecendo
duas vezes) é o MESMO fio, só foi repetido para o desenho ficar limpo.
"""
import io
import os

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch
import schemdraw
from schemdraw.parsing import logicparse
from PIL import Image, ImageDraw, ImageFont

schemdraw.use("matplotlib")
AQUI = os.path.dirname(os.path.abspath(__file__))
SAIDA = os.path.join(AQUI, "desenhos")
os.makedirs(SAIDA, exist_ok=True)

FONTE = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONTE_N = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"


def fonte(caminho, tam):
    try:
        return ImageFont.truetype(caminho, tam)
    except OSError:
        return ImageFont.load_default()


def circuito_png(expr, saida):
    d = logicparse(expr, outlabel=saida, gateH=1.6)
    buf = io.BytesIO()
    d.save(buf, dpi=130)
    plt.close("all")
    buf.seek(0)
    return Image.open(buf).convert("RGB")


def folha(nome_arquivo, titulo, itens, rodape=None):
    """itens = [(rótulo da expressão, expressão para o desenho, nome da saída)]"""
    f_tit, f_exp, f_rod = fonte(FONTE, 34), fonte(FONTE, 24), fonte(FONTE_N, 20)
    blocos = []
    for rotulo, expr, saida in itens:
        img = circuito_png(expr, saida)
        blocos.append((rotulo, img))

    largura = max(900, max(i.width for _, i in blocos) + 80)
    altura = 90 + sum(i.height + 70 for _, i in blocos) + (90 if rodape else 20)
    pag = Image.new("RGB", (largura, altura), "white")
    dr = ImageDraw.Draw(pag)
    dr.text((40, 25), titulo, font=f_tit, fill="black")
    dr.line((40, 75, largura - 40, 75), fill="black", width=3)

    y = 95
    for rotulo, img in blocos:
        dr.text((40, y), rotulo, font=f_exp, fill=(20, 60, 160))
        y += 40
        pag.paste(img, (60, y))
        y += img.height + 30
    if rodape:
        linhas, atual = [], ""
        for palavra in rodape.split():
            teste = (atual + " " + palavra).strip()
            if dr.textlength(teste, font=f_rod) > largura - 80:
                linhas.append(atual)
                atual = palavra
            else:
                atual = teste
        linhas.append(atual)
        for ln in linhas:
            dr.text((40, y), ln, font=f_rod, fill=(90, 90, 90))
            y += 28

    caminho = os.path.join(SAIDA, nome_arquivo)
    pag.save(caminho)
    return pag


# ---------------------------------------------------------------- blocos
paginas = []
NOTA = "Nomes iguais = mesmo fio (repetido só pra organizar o desenho)."

paginas.append(folha("1_codificador.png", "1) Codificador 4→2 (teclado → binário)", [
    ("A1 = T2 + T3", "T2 or T3", "A1"),
    ("A0 = T1 + T3", "T1 or T3", "A0"),
], "T0 não precisa de porta (gera 00). Fazer 2 iguais: um p/ A, outro p/ B."))

paginas.append(folha("2_meio_somador.png", "2a) Meio somador (bit 0)", [
    ("S0 = A0 ⊕ B0", "A0 xor B0", "S0"),
    ("C1 = A0 · B0   (vai-um)", "A0 and B0", "C1"),
]))

paginas.append(folha("3_somador_completo.png", "2b) Somador completo (bit 1)", [
    ("S1 = A1 ⊕ B1 ⊕ C1", "(A1 xor B1) xor C1", "S1"),
    ("C2 = A1·B1 + C1·(A1 ⊕ B1)", "(A1 and B1) or (C1 and (A1 xor B1))", "C2"),
], NOTA))

paginas.append(folha("4_meio_subtrator.png", "3a) Meio subtrator (bit 0)", [
    ("D0 = A0 ⊕ B0", "A0 xor B0", "D0"),
    ("Bw1 = A0' · B0   (empresta 1)", "(not A0) and B0", "Bw1"),
]))

paginas.append(folha("5_subtrator_completo.png", "3b) Subtrator completo (bit 1)", [
    ("D1 = A1 ⊕ B1 ⊕ Bw1", "(A1 xor B1) xor Bw1", "D1"),
    ("Bw2 = A1'·B1 + Bw1·(A1 ⊕ B1)'", "((not A1) and B1) or (Bw1 and (not (A1 xor B1)))", "Bw2"),
], "Bw2 = 1  →  A − B deu negativo (A < B).  " + NOTA))

paginas.append(folha("6_mux.png", "4) Multiplexador 2→1", [
    ("Y = S'·I0 + S·I1", "((not S) and I0) or (S and I1)", "Y"),
], "Usar 5 MUX: 2 com S = Bw2 (A−B ou B−A) e 3 com S = OP (soma ou subtração)."))

paginas.append(folha("7_decodificador_7seg.png", "5) Decodificador binário → 7 segmentos", [
    ("a = R1 + R2·R0 + R2'·R0'   (d = a)", "R1 or ((R2 and R0) or ((not R2) and (not R0)))", "a, d"),
    ("b = R2' + R1'·R0'", "(not R2) or ((not R1) and (not R0))", "b"),
    ("c = R2 + R1' + R0", "(R2 or R0) or (not R1)", "c"),
    ("e = R0'·(R2' + R1)", "(not R0) and ((not R2) or R1)", "e"),
    ("f = R2 + R1'·R0'", "R2 or ((not R1) and (not R0))", "f"),
    ("g = R2 + R1", "R2 or R1", "g"),
], "O segmento d usa o mesmo fio do a."))


# ---------------------------------------------------- diagrama de blocos
def diagrama_blocos():
    fig, ax = plt.subplots(figsize=(10, 13))
    ax.set_xlim(0, 12)
    ax.set_ylim(0, 16)
    ax.axis("off")

    def caixa(x, y, w, h, txt, cor="#e8f0ff"):
        ax.add_patch(FancyBboxPatch((x - w / 2, y - h / 2), w, h,
                                    boxstyle="round,pad=0.05,rounding_size=0.15",
                                    fc=cor, ec="black", lw=2))
        ax.text(x, y, txt, ha="center", va="center", fontsize=12, weight="bold")

    def seta(x1, y1, x2, y2, txt=None, lado="right"):
        ax.annotate("", xy=(x2, y2), xytext=(x1, y1),
                    arrowprops=dict(arrowstyle="-|>", lw=1.8, color="black"))
        if txt:
            dx = 0.12 if lado == "right" else -0.12
            ax.text((x1 + x2) / 2 + dx, (y1 + y2) / 2, txt, fontsize=10,
                    ha="left" if lado == "right" else "right", va="center", color="#1a3ea0")

    def linha(pts, cor="black"):
        xs, ys = zip(*pts)
        ax.plot(xs, ys, color=cor, lw=1.8)

    def ponto(x, y, cor="black"):
        ax.plot(x, y, "o", color=cor, ms=6)

    ax.text(6, 15.6, "6) Diagrama de blocos", ha="center", fontsize=18, weight="bold")

    # teclados e codificadores
    caixa(3, 14.5, 2.6, 0.8, "Teclado A\n(0 a 3)", "#fff3d6")
    caixa(9, 14.5, 2.6, 0.8, "Teclado B\n(0 a 3)", "#fff3d6")
    seta(3, 14.1, 3, 13.2, "T0..T3")
    seta(9, 14.1, 9, 13.2, "T0..T3")
    caixa(3, 12.8, 2.6, 0.8, "COD 4→2")
    caixa(9, 12.8, 2.6, 0.8, "COD 4→2")

    # barramentos A (azul) e B (vermelho)
    azul, verm = "#1a3ea0", "#b0202a"
    linha([(3, 12.4), (3, 11.6)], azul)
    linha([(1.3, 11.6), (10.7, 11.6)], azul)
    ax.text(0.2, 11.6, "A\n(A1 A0)", color=azul, fontsize=10, va="center", weight="bold")
    linha([(9, 12.4), (9, 11.0)], verm)
    linha([(1.7, 11.0), (11.1, 11.0)], verm)
    ax.text(11.25, 11.0, "B\n(B1 B0)", color=verm, fontsize=10, va="center", weight="bold")
    ponto(3, 11.6, azul)
    ponto(9, 11.0, verm)

    # somador e subtratores
    blocos = [(1.5, "SOMADOR\nA + B"), (6, "SUB 1\nA − B"), (10.5, "SUB 2\nB − A")]
    for x, txt in blocos:
        for dx, cor, ybus in ((-0.2, azul, 11.6), (0.2, verm, 11.0)):
            ponto(x + dx, ybus, cor)
            ax.annotate("", xy=(x + dx, 9.95), xytext=(x + dx, ybus),
                        arrowprops=dict(arrowstyle="-|>", lw=1.8, color=cor))
        caixa(x, 9.5, 2.4, 0.9, txt, "#e6f7e6")
    ax.text(10.75, 10.4, "B entra\nprimeiro", fontsize=8, color="#555", va="center")

    # MUX da diferença
    caixa(8.25, 7.2, 3.6, 1.0, "MUX 2→1  (×2)\nescolhe maior − menor", "#f3e6ff")
    seta(6.6, 9.05, 7.6, 7.7, "D1 D0")
    seta(10.5, 9.05, 9.1, 7.7, "D1 D0")
    linha([(5.3, 9.05), (5.3, 7.2)], "#8a2be2")
    ax.annotate("", xy=(6.45, 7.2), xytext=(5.3, 7.2),
                arrowprops=dict(arrowstyle="-|>", lw=1.8, color="#8a2be2"))
    ax.text(5.2, 8.1, "Bw2\n(seleção)\n0 → A−B\n1 → B−A", fontsize=9,
            color="#8a2be2", ha="right", va="center")

    # MUX final
    caixa(5, 5.0, 4.4, 1.0, "MUX 2→1  (×3)\n0 → soma   1 → subtração", "#f3e6ff")
    linha([(1.5, 9.05), (1.5, 5.8), (4.0, 5.8)])
    ax.annotate("", xy=(4.0, 5.52), xytext=(4.0, 5.8),
                arrowprops=dict(arrowstyle="-|>", lw=1.8))
    ax.text(1.6, 7.0, "C2 S1 S0", fontsize=10, color="#1a3ea0")
    linha([(8.25, 6.7), (8.25, 5.8), (6.0, 5.8)])
    ax.annotate("", xy=(6.0, 5.52), xytext=(6.0, 5.8),
                arrowprops=dict(arrowstyle="-|>", lw=1.8))
    ax.text(8.35, 6.2, "Dif1 Dif0", fontsize=10, color="#1a3ea0")
    caixa(0.9, 5.0, 1.4, 0.8, "Tecla\nOP", "#fff3d6")
    seta(1.6, 5.0, 2.78, 5.0)
    ax.text(1.0, 4.35, "0 = +\n1 = −", fontsize=9, ha="center", va="top")

    # decodificador e display
    seta(5, 4.5, 5, 3.6, "R2 R1 R0")
    caixa(5, 3.1, 4.4, 1.0, "DECODIFICADOR\nbinário → 7 segmentos", "#ffe6e6")
    seta(5, 2.6, 5, 1.75, "a b c d e f g")

    # display 7 segmentos desenhando "6"
    cx, cy, w, h = 5, 1.0, 0.6, 0.55
    on, off = "#d81b1b", "#e8e8e8"
    segs = {  # (x1,y1,x2,y2)
        "a": (cx - w / 2, cy + h, cx + w / 2, cy + h),
        "b": (cx + w / 2, cy + h, cx + w / 2, cy),
        "c": (cx + w / 2, cy, cx + w / 2, cy - h),
        "d": (cx - w / 2, cy - h, cx + w / 2, cy - h),
        "e": (cx - w / 2, cy, cx - w / 2, cy - h),
        "f": (cx - w / 2, cy + h, cx - w / 2, cy),
        "g": (cx - w / 2, cy, cx + w / 2, cy),
    }
    ax.add_patch(FancyBboxPatch((cx - 0.75, cy - 0.8), 1.5, 1.6,
                                boxstyle="round,pad=0.02", fc="#222", ec="black", lw=2))
    for s, (x1, y1, x2, y2) in segs.items():
        ax.plot([x1, x2], [y1, y2], color=on if s != "b" else "#444", lw=6,
                solid_capstyle="round")
        tx = {"a": (0, .17), "b": (.2, 0), "c": (.2, 0), "d": (0, -.17),
              "e": (-.2, 0), "f": (-.2, 0), "g": (0, .13)}[s]
        ax.text((x1 + x2) / 2 + tx[0], (y1 + y2) / 2 + tx[1], s, color="white",
                fontsize=7, ha="center", va="center")
    ax.text(6.0, 1.0, "DISPLAY\n0 a 6\n(ex.: 3+3 = 6)", fontsize=11, weight="bold", va="center")

    caminho = os.path.join(SAIDA, "8_diagrama_de_blocos.png")
    fig.savefig(caminho, dpi=130, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    return Image.open(caminho).convert("RGB")


paginas.append(diagrama_blocos())

pdf = os.path.join(SAIDA, "desenhos.pdf")
paginas[0].save(pdf, save_all=True, append_images=paginas[1:])
print("Desenhos salvos em", SAIDA)
for n in sorted(os.listdir(SAIDA)):
    print(" -", n)
