"""
Desafio - Sistema digital que SOMA ou SUBTRAI dois números (0 a 3)
vindos de um teclado e mostra o resultado num display de 0 a 6.

Tudo aqui é montado SÓ com portas lógicas (AND, OR, NOT, XOR),
igual seria feito no papel / no simulador de circuitos.

Rodar:  python3 simulador.py
"""

# ---------------- PORTAS LÓGICAS ----------------
def NOT(a): return 1 - a
def AND(*x): return int(all(x))
def OR(*x): return int(any(x))
def XOR(a, b): return a ^ b


# ---------------- 1) CODIFICADOR 4 -> 2 (teclado) ----------------
# Teclado com 4 teclas T0..T3 (só uma apertada por vez).
#   A1 = T2 + T3
#   A0 = T1 + T3
def codificador(t0, t1, t2, t3):
    a1 = OR(t2, t3)
    a0 = OR(t1, t3)
    return a1, a0


# ---------------- 2) SOMADOR DE 2 BITS ----------------
def meio_somador(a, b):
    s = XOR(a, b)
    c = AND(a, b)
    return s, c

def somador_completo(a, b, cin):
    x = XOR(a, b)
    s = XOR(x, cin)
    cout = OR(AND(a, b), AND(cin, x))
    return s, cout

def somador_2bits(a1, a0, b1, b0):
    s0, c1 = meio_somador(a0, b0)
    s1, c2 = somador_completo(a1, b1, c1)
    return c2, s1, s0                # 3 bits -> 0 a 6


# ---------------- 3) SUBTRATOR DE 2 BITS ----------------
def meio_subtrator(a, b):
    d = XOR(a, b)
    bw = AND(NOT(a), b)              # "pede emprestado"
    return d, bw

def subtrator_completo(a, b, bin_):
    x = XOR(a, b)
    d = XOR(x, bin_)
    bout = OR(AND(NOT(a), b), AND(bin_, NOT(x)))
    return d, bout

def subtrator_2bits(a1, a0, b1, b0):
    d0, bw1 = meio_subtrator(a0, b0)
    d1, bw2 = subtrator_completo(a1, b1, bw1)
    return bw2, d1, d0               # bw2 = 1  ->  resultado negativo (A < B)


# ---------------- 4) MULTIPLEXADOR 2 -> 1 ----------------
#   Y = S'.I0 + S.I1
def mux2(sel, i0, i1):
    return OR(AND(NOT(sel), i0), AND(sel, i1))


# ---------------- 5) DECODIFICADOR binário -> 7 segmentos ----------------
#  (só 0 a 6; o 7 nunca aparece = "don't care")
#   a = R1 + R2.R0 + R2'.R0'
#   b = R2' + R1'.R0'
#   c = R2 + R1' + R0
#   d = a
#   e = R0'.(R2' + R1)
#   f = R2 + R1'.R0'
#   g = R2 + R1
def decodificador_7seg(r2, r1, r0):
    a = OR(r1, AND(r2, r0), AND(NOT(r2), NOT(r0)))
    b = OR(NOT(r2), AND(NOT(r1), NOT(r0)))
    c = OR(r2, NOT(r1), r0)
    d = a
    e = AND(NOT(r0), OR(NOT(r2), r1))
    f = OR(r2, AND(NOT(r1), NOT(r0)))
    g = OR(r2, r1)
    return a, b, c, d, e, f, g


# ---------------- SISTEMA COMPLETO (diagrama de blocos) ----------------
def sistema(tecla_a, tecla_b, op):
    """tecla_a, tecla_b: 0..3   op: 0 = soma, 1 = subtração"""
    ta = [int(tecla_a == i) for i in range(4)]
    tb = [int(tecla_b == i) for i in range(4)]

    a1, a0 = codificador(*ta)                       # COD A
    b1, b0 = codificador(*tb)                       # COD B

    c2, s1, s0 = somador_2bits(a1, a0, b1, b0)      # A + B

    neg, x1, x0 = subtrator_2bits(a1, a0, b1, b0)   # A - B
    _,   y1, y0 = subtrator_2bits(b1, b0, a1, a0)   # B - A

    # Se A - B deu negativo, usa B - A  (porque A - B != B - A)
    d1 = mux2(neg, x1, y1)
    d0 = mux2(neg, x0, y0)

    # MUX final: escolhe SOMA (op=0) ou SUBTRAÇÃO (op=1)
    r2 = mux2(op, c2, 0)
    r1 = mux2(op, s1, d1)
    r0 = mux2(op, s0, d0)

    return (r2, r1, r0), decodificador_7seg(r2, r1, r0)


# ---------------- TESTE ----------------
DIGITOS_7SEG = {  # a b c d e f g
    0: (1, 1, 1, 1, 1, 1, 0), 1: (0, 1, 1, 0, 0, 0, 0),
    2: (1, 1, 0, 1, 1, 0, 1), 3: (1, 1, 1, 1, 0, 0, 1),
    4: (0, 1, 1, 0, 0, 1, 1), 5: (1, 0, 1, 1, 0, 1, 1),
    6: (1, 0, 1, 1, 1, 1, 1),
}

def desenha(seg):
    a, b, c, d, e, f, g = seg
    h = lambda on: "___" if on else "   "
    v = lambda on: "|" if on else " "
    return [" " + h(a) + " ",
            v(f) + h(g) + v(b),
            v(e) + h(d) + v(c)]

if __name__ == "__main__":
    erros = 0
    print(" A  op  B  | R2 R1 R0 | dec | a b c d e f g")
    print("-" * 46)
    for op in (0, 1):
        for A in range(4):
            for B in range(4):
                (r2, r1, r0), seg = sistema(A, B, op)
                valor = 4 * r2 + 2 * r1 + r0
                esperado = A + B if op == 0 else abs(A - B)
                ok = valor == esperado and seg == DIGITOS_7SEG[esperado]
                erros += not ok
                sinal = "+" if op == 0 else "-"
                print(f" {A}   {sinal}  {B}  |  {r2}  {r1}  {r0} |  {valor}  | "
                      + " ".join(map(str, seg)) + ("" if ok else "   <-- ERRO"))
        print("-" * 46)

    print("\nDisplay mostrando 0 a 6:\n")
    linhas = ["", "", ""]
    for n in range(7):
        for i, l in enumerate(desenha(DIGITOS_7SEG[n])):
            linhas[i] += l + "   "
    print("\n".join(linhas))
    print("\n" + ("TODOS OS 32 CASOS CORRETOS" if erros == 0 else f"{erros} ERROS"))
