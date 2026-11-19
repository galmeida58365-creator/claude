"""Baixar TikTok

Cole o link de um vídeo do TikTok e ele é baixado sozinho, na melhor
qualidade disponível e sem marca d'água. Também dá para só copiar o link
(no TikTok: Compartilhar > Copiar link) que o programa percebe e baixa.

Usa o yt-dlp (https://github.com/yt-dlp/yt-dlp) para falar com o TikTok.
"""

import json
import os
import queue
import re
import subprocess
import sys
import threading
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

try:
    import yt_dlp
except ImportError:  # avisado na tela ao abrir
    yt_dlp = None


ARQUIVO_CONFIG = Path.home() / ".baixar_tiktok.json"
PASTA_PADRAO = Path.home() / "Downloads" / "TikTok"

# Qualquer endereço do tiktok.com dentro de um texto.
LINK_TIKTOK = re.compile(r"https?://(?:[\w-]+\.)*tiktok\.com/[^\s\"'<>]+", re.IGNORECASE)

# Só links de vídeo: o endereço completo (/@perfil/video/123, /video/123,
# m.tiktok.com/v/123) ou os links curtos de compartilhar (vm., vt., /t/),
# que o yt-dlp abre para descobrir qual é o vídeo. Perfis, hashtags e
# músicas ficam de fora, para não baixar dezenas de vídeos sem querer.
LINK_VIDEO = re.compile(
    r"""https?://(?:
        (?:www\.|m\.)?tiktok\.com/(?:@[\w.-]*/)?video/\d+
      | m\.tiktok\.com/v/\d+
      | (?:vm|vt)\.tiktok\.com/\w+
      | (?:www\.)?tiktok\.com/t/\w+
    )""",
    re.IGNORECASE | re.VERBOSE,
)

# Melhor qualidade sem marca d'água: entre os formatos com vídeo, descarta os
# marcados como "watermarked" e os que não tocam (bytevc2). A ordenação põe a
# maior resolução primeiro e, na mesma resolução, prefere H.264, que abre em
# qualquer player (o H.265 pede um codec pago no Windows).
FORMATO = (
    "b[vcodec!=none][format_note!*=?watermark][format_note!*=?UNPLAYABLE]"
    "/b[vcodec!=none]"
    "/b"
)
ORDEM_FORMATOS = ["res", "vcodec:h264", "br"]
NOME_ARQUIVO = "%(uploader,creator|tiktok)s - %(title).70B [%(id)s].%(ext)s"


def achar_links(texto):
    """Links do TikTok no texto, na ordem, sem repetir."""
    links = []
    for link in LINK_TIKTOK.findall(texto or ""):
        link = link.rstrip(".,;:!?)]}")
        if link not in links:
            links.append(link)
    return links


def eh_link_de_video(link):
    return LINK_VIDEO.match(link) is not None


def chave_do_link(link):
    """Mesmo vídeo com ?is_from_webapp=1&sender_device=pc etc. conta uma vez só."""
    return link.split("?", 1)[0].split("#", 1)[0].rstrip("/").lower()


class ErroAmigavel(Exception):
    """Erro com uma mensagem pronta para mostrar na tela."""


def traduzir_erro(erro):
    texto = str(erro)
    minusculo = texto.lower()
    if "private" in minusculo:
        return "Vídeo privado: só o dono consegue ver."
    if "login" in minusculo or "log in" in minusculo or "cookies" in minusculo:
        return "O TikTok pediu login para este vídeo (ele pode ter restrição de idade)."
    if "not available" in minusculo or "404" in minusculo or "removed" in minusculo:
        return "Vídeo não encontrado: pode ter sido apagado ou o link está incompleto."
    if "unable to extract" in minusculo or "unsupported url" in minusculo:
        return ("O TikTok mudou alguma coisa. Feche o programa e abra de novo pelo "
                "arquivo Abrir-Baixar-TikTok.bat: ele atualiza o yt-dlp, que costuma resolver.")
    if any(p in minusculo for p in ("timed out", "connection", "network", "getaddrinfo", "resolve")):
        return "Sem conexão com o TikTok. Confira a internet e tente de novo."
    texto = re.sub(r"^ERROR:\s*", "", texto)
    texto = re.sub(r"^\[[^\]]+\]\s*[\w-]+:\s*", "", texto)
    return texto[:300]


class LogSilencioso:
    """Engole as mensagens do yt-dlp; os erros chegam como exceção."""

    def debug(self, msg):
        pass

    def info(self, msg):
        pass

    def warning(self, msg):
        pass

    def error(self, msg):
        pass


def baixar_video(link, pasta, avisar):
    """Baixa um vídeo e devolve o caminho do arquivo.

    avisar(texto) recebe o andamento ("Baixando 42%"...). Roda fora da tela,
    numa thread própria.
    """

    def andamento(d):
        if d.get("status") == "downloading":
            total = d.get("total_bytes") or d.get("total_bytes_estimate")
            feito = d.get("downloaded_bytes") or 0
            if total:
                avisar(f"Baixando {min(feito * 100 // total, 99)}%")
            else:
                avisar(f"Baixando {feito / 1_048_576:.1f} MB")
        elif d.get("status") == "finished":
            avisar("Finalizando...")

    opcoes = {
        "format": FORMATO,
        "format_sort": ORDEM_FORMATOS,
        "outtmpl": {"default": str(Path(pasta) / NOME_ARQUIVO)},
        "windowsfilenames": True,
        "noplaylist": True,
        "overwrites": False,
        "retries": 5,
        "fragment_retries": 5,
        "socket_timeout": 30,
        "quiet": True,
        "no_warnings": True,
        "noprogress": True,
        "logger": LogSilencioso(),
        "progress_hooks": [andamento],
    }

    Path(pasta).mkdir(parents=True, exist_ok=True)
    with yt_dlp.YoutubeDL(opcoes) as ydl:
        avisar("Procurando o vídeo...")
        try:
            info = ydl.extract_info(link, download=False)
        except yt_dlp.utils.DownloadError as erro:
            raise ErroAmigavel(traduzir_erro(erro)) from erro

        if info.get("_type") in ("playlist", "multi_video") or "entries" in info:
            raise ErroAmigavel("Esse link não é de um vídeo só (parece um perfil ou uma lista).")
        if info.get("vcodec") == "none" or not info.get("formats"):
            raise ErroAmigavel("Esse post é de fotos, não de vídeo.")

        avisar(f"Baixando ({qualidade(info)})..." if qualidade(info) else "Baixando...")
        try:
            info = ydl.process_ie_result(info, download=True)
        except yt_dlp.utils.DownloadError as erro:
            raise ErroAmigavel(traduzir_erro(erro)) from erro

        baixados = info.get("requested_downloads") or [{}]
        caminho = baixados[-1].get("filepath") or ydl.prepare_filename(info)
        return Path(caminho), info


def qualidade(info):
    """'1080p' para um vídeo 1080x1920 (o lado menor, como se costuma falar)."""
    lados = [n for n in (info.get("width"), info.get("height")) if n]
    return f"{min(lados)}p" if lados else ""


def abrir_no_sistema(caminho):
    caminho = str(caminho)
    if sys.platform.startswith("win"):
        os.startfile(caminho)  # noqa: S606 - abre a pasta/vídeo do próprio usuário
    elif sys.platform == "darwin":
        subprocess.Popen(["open", caminho])
    else:
        subprocess.Popen(["xdg-open", caminho])


class App:
    def __init__(self, raiz):
        self.raiz = raiz
        self.config = self.ler_config()
        self.pasta = tk.StringVar(value=self.config.get("pasta") or str(PASTA_PADRAO))
        self.vigiar = tk.BooleanVar(value=self.config.get("vigiar", True))
        self.link = tk.StringVar()

        self.fila = queue.Queue()            # links esperando para baixar
        self.recados = queue.Queue()         # recados da thread de download para a tela
        self.ja_pedidos = set()              # chaves dos links já colocados na fila
        self.arquivos = {}                   # linha da lista -> arquivo baixado
        self.ultima_area = self.ler_area_de_transferencia()  # o que já estava copiado não baixa

        self.montar_tela()
        threading.Thread(target=self.trabalhar, daemon=True).start()
        self.raiz.after(100, self.ler_recados)
        self.raiz.after(800, self.olhar_area_de_transferencia)
        self.raiz.protocol("WM_DELETE_WINDOW", self.fechar)

        if yt_dlp is None:
            messagebox.showerror(
                "Falta o yt-dlp",
                "O programa precisa do yt-dlp para baixar do TikTok.\n\n"
                "Abra pelo arquivo Abrir-Baixar-TikTok.bat (ele instala sozinho) "
                "ou rode no terminal:\n\npython -m pip install -U yt-dlp",
            )

    # ---------- tela ----------

    def montar_tela(self):
        r = self.raiz
        r.title("Baixar TikTok")
        r.geometry("720x480")
        r.minsize(520, 360)

        estilo = ttk.Style()
        if "vista" in estilo.theme_names():
            estilo.theme_use("vista")
        elif "clam" in estilo.theme_names():
            estilo.theme_use("clam")
        estilo.configure("Grande.TButton", padding=(14, 6))
        estilo.configure("Treeview", rowheight=26)

        corpo = ttk.Frame(r, padding=14)
        corpo.pack(fill="both", expand=True)

        ttk.Label(corpo, text="Cole o link do vídeo do TikTok:", font=("Segoe UI", 11)).pack(anchor="w")

        linha = ttk.Frame(corpo)
        linha.pack(fill="x", pady=(6, 4))
        self.campo = ttk.Entry(linha, textvariable=self.link, font=("Segoe UI", 11))
        self.campo.pack(side="left", fill="x", expand=True, ipady=4)
        self.campo.bind("<<Paste>>", lambda e: self.raiz.after(50, self.pegar_do_campo))
        self.campo.bind("<Return>", lambda e: self.pegar_do_campo(avisar_se_vazio=True))
        ttk.Button(linha, text="Colar e baixar", style="Grande.TButton",
                   command=self.colar_e_baixar).pack(side="left", padx=(8, 0))

        ttk.Checkbutton(
            corpo, variable=self.vigiar, command=self.salvar_config,
            text="Baixar sozinho quando eu copiar um link do TikTok (Compartilhar > Copiar link)",
        ).pack(anchor="w", pady=(2, 10))

        caixa = ttk.Frame(corpo)
        caixa.pack(fill="both", expand=True)
        colunas = ("video", "situacao")
        self.lista = ttk.Treeview(caixa, columns=colunas, show="headings", selectmode="browse", height=6)
        self.lista.heading("video", text="Vídeo")
        self.lista.heading("situacao", text="Situação")
        self.lista.column("video", width=430, anchor="w")
        self.lista.column("situacao", width=220, anchor="w")
        self.lista.tag_configure("ok", foreground="#1a7f37")
        self.lista.tag_configure("erro", foreground="#c62828")
        self.lista.bind("<Double-1>", self.abrir_item)
        rolagem = ttk.Scrollbar(caixa, orient="vertical", command=self.lista.yview)
        self.lista.configure(yscrollcommand=rolagem.set)
        rolagem.pack(side="right", fill="y")
        self.lista.pack(side="left", fill="both", expand=True)

        rodape = ttk.Frame(corpo)
        rodape.pack(fill="x", pady=(10, 0))
        ttk.Label(rodape, text="Salvar em:").pack(side="left")
        ttk.Label(rodape, textvariable=self.pasta, foreground="#555").pack(side="left", padx=6)
        ttk.Button(rodape, text="Abrir pasta", command=self.abrir_pasta).pack(side="right")
        ttk.Button(rodape, text="Trocar pasta", command=self.trocar_pasta).pack(side="right", padx=6)

        ttk.Label(corpo, text="Dica: clique duas vezes num vídeo baixado para assistir.",
                  foreground="#777").pack(anchor="w", pady=(6, 0))
        self.campo.focus_set()

    # ---------- receber links ----------

    def colar_e_baixar(self):
        texto = self.ler_area_de_transferencia()
        if not achar_links(texto):
            messagebox.showinfo("Nenhum link", "Não achei um link do TikTok copiado.\n\n"
                                "No TikTok, toque em Compartilhar > Copiar link e tente de novo.")
            return
        self.ultima_area = texto
        self.receber_texto(texto)

    def pegar_do_campo(self, avisar_se_vazio=False):
        texto = self.link.get()
        if achar_links(texto):
            self.link.set("")
            self.receber_texto(texto)
        elif avisar_se_vazio and texto.strip():
            messagebox.showinfo("Link não reconhecido", "Isso não parece um link do TikTok.")

    def olhar_area_de_transferencia(self):
        if self.vigiar.get():
            texto = self.ler_area_de_transferencia()
            if texto != self.ultima_area:
                self.ultima_area = texto
                links = [l for l in achar_links(texto) if eh_link_de_video(l)]
                if links:
                    self.receber_texto(texto, so_videos=True)
        self.raiz.after(800, self.olhar_area_de_transferencia)

    def receber_texto(self, texto, so_videos=False):
        for link in achar_links(texto):
            if not eh_link_de_video(link):
                if not so_videos:
                    self.lista.insert("", 0, values=(link, "Não é link de vídeo"), tags=("erro",))
                continue
            chave = chave_do_link(link)
            if chave in self.ja_pedidos:
                continue
            self.ja_pedidos.add(chave)
            item = self.lista.insert("", 0, values=(link, "Na fila"))
            self.fila.put((item, link, chave))

    def ler_area_de_transferencia(self):
        try:
            return self.raiz.clipboard_get()
        except tk.TclError:  # área vazia ou com imagem
            return ""

    # ---------- baixar (thread) ----------

    def trabalhar(self):
        while True:
            item, link, chave = self.fila.get()
            if yt_dlp is None:
                self.recados.put(("erro", item, "Falta instalar o yt-dlp", chave))
                continue
            try:
                caminho, info = baixar_video(
                    link, self.pasta.get(),
                    lambda texto, item=item: self.recados.put(("andamento", item, texto, None)),
                )
                titulo = (info.get("title") or "").strip() or caminho.name
                self.recados.put(("ok", item, (titulo, caminho, qualidade(info)), chave))
            except ErroAmigavel as erro:
                self.recados.put(("erro", item, str(erro), chave))
            except Exception as erro:  # nunca deixar a fila parar
                self.recados.put(("erro", item, traduzir_erro(erro), chave))

    def ler_recados(self):
        try:
            while True:
                tipo, item, dado, chave = self.recados.get_nowait()
                if not self.lista.exists(item):
                    continue
                video = self.lista.set(item, "video")
                if tipo == "andamento":
                    self.lista.item(item, values=(video, dado))
                elif tipo == "ok":
                    titulo, caminho, resolucao = dado
                    self.arquivos[item] = caminho
                    situacao = f"Baixado ✓ ({resolucao})" if resolucao else "Baixado ✓"
                    self.lista.item(item, values=(titulo, situacao), tags=("ok",))
                else:
                    self.ja_pedidos.discard(chave)  # deixa tentar de novo
                    self.lista.item(item, values=(video, "Erro: " + dado), tags=("erro",))
        except queue.Empty:
            pass
        self.raiz.after(100, self.ler_recados)

    # ---------- botões ----------

    def abrir_item(self, _evento):
        item = self.lista.focus()
        if item in self.arquivos and self.arquivos[item].exists():
            abrir_no_sistema(self.arquivos[item])
        elif item:
            situacao = self.lista.set(item, "situacao")
            if situacao.startswith("Erro"):
                messagebox.showinfo("Detalhes", situacao)

    def abrir_pasta(self):
        pasta = Path(self.pasta.get())
        pasta.mkdir(parents=True, exist_ok=True)
        abrir_no_sistema(pasta)

    def trocar_pasta(self):
        nova = filedialog.askdirectory(initialdir=self.pasta.get(), title="Onde salvar os vídeos?")
        if nova:
            self.pasta.set(nova)
            self.salvar_config()

    # ---------- configuração ----------

    def ler_config(self):
        try:
            return json.loads(ARQUIVO_CONFIG.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            return {}

    def salvar_config(self):
        try:
            ARQUIVO_CONFIG.write_text(
                json.dumps({"pasta": self.pasta.get(), "vigiar": self.vigiar.get()}),
                encoding="utf-8",
            )
        except OSError:
            pass

    def fechar(self):
        baixando = any(
            self.lista.set(i, "situacao").startswith(("Na fila", "Procurando", "Baixando", "Finalizando"))
            for i in self.lista.get_children()
        )
        if baixando and not messagebox.askyesno("Fechar?", "Ainda tem vídeo baixando. Fechar mesmo assim?"):
            return
        self.salvar_config()
        self.raiz.destroy()


def main():
    if sys.platform.startswith("win"):
        try:  # letras nítidas em telas com zoom
            import ctypes
            ctypes.windll.shcore.SetProcessDpiAwareness(1)
        except Exception:
            pass
    raiz = tk.Tk()
    App(raiz)
    raiz.mainloop()


if __name__ == "__main__":
    main()
