#!/usr/bin/env python3
"""Cortes GTA 6: acha vídeos de youtubers reagindo aos trailers de GTA 6,
encontra os melhores momentos e gera cortes verticais (9:16) prontos para
YouTube Shorts e TikTok, com título, legenda e crédito ao criador.

Uso:
  python cortes.py tudo              busca vídeos novos, corta e monta o painel
  python cortes.py link URL [URL..]  corta vídeos específicos do YouTube
  python cortes.py arquivo CAMINHO --canal "Nome"   corta um vídeo que já está no PC
  python cortes.py buscar            só lista o que acharia (não baixa nada)
  python cortes.py painel            refaz o painel de postagem
"""

import argparse
import datetime as dt
import html
import json
import random
import re
import shutil
import unicodedata
import subprocess
import sys
from pathlib import Path

PASTA = Path(__file__).resolve().parent
CONFIG = PASTA / "config.json"
ESTADO = PASTA / "estado.json"
BAIXADOS = PASTA / "trabalho" / "baixados"
SAIDA = PASTA / "cortes"

try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass


# ---------------------------------------------------------------- utilidades

def log(msg):
    print(msg, flush=True)


def carregar_config():
    with open(CONFIG, encoding="utf-8") as f:
        return json.load(f)


def carregar_estado():
    if ESTADO.exists():
        with open(ESTADO, encoding="utf-8") as f:
            return json.load(f)
    return {"processados": {}, "cortes": []}


def salvar_estado(estado):
    tmp = ESTADO.with_suffix(".tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(estado, f, ensure_ascii=False, indent=2)
    tmp.replace(ESTADO)


def nome_arquivo(texto, limite=40):
    texto = re.sub(r'[<>:"/\\|?*\x00-\x1f]', "", texto)
    texto = re.sub(r"\s+", "_", texto.strip())
    return texto[:limite].strip("._") or "video"


def exigir_ffmpeg():
    if not shutil.which("ffmpeg"):
        sys.exit("ERRO: ffmpeg não encontrado. Rode o instalar.bat (ou instale o ffmpeg) e abra o terminal de novo.")


def rodar(cmd, cwd=None):
    r = subprocess.run(cmd, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if r.returncode != 0:
        err = r.stderr.decode("utf-8", "replace")[-1500:]
        raise RuntimeError(f"ffmpeg falhou:\n{err}")
    return r


def opcoes_ytdlp(cfg):
    op = {"quiet": True, "no_warnings": True, "noprogress": True}
    if cfg.get("cookies_do_navegador"):
        op["cookiesfrombrowser"] = (cfg["cookies_do_navegador"],)
    return op


# ---------------------------------------------------------------- busca

PALAVRAS_REACAO = ("reag", "react", "reacao", "reação", "reaction", "assist", "vendo", "watch")


def _norm(texto):
    """'Davy Jones' == 'davyjones' == 'DAVY-JONES' (ignora acento, espaço e maiúscula)."""
    t = unicodedata.normalize("NFKD", texto or "").encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]", "", t)


def na_lista(canal, lista):
    c = _norm(canal)
    return bool(c) and any(c == _norm(x) for x in lista or [])


def fala_de_gta6(titulo):
    t = f" {titulo.lower()} "
    return "gta" in t and ("6" in t or re.search(r"\bvi\b", t) is not None)


def parece_reacao(titulo):
    return fala_de_gta6(titulo) and any(p in titulo.lower() for p in PALAVRAS_REACAO)


def video_serve(titulo, canal, cfg):
    """Reação ao trailer. Para os canais da lista de permitidos basta falar de GTA 6 + trailer,
    porque streamer nem sempre escreve "reagindo" no título."""
    if parece_reacao(titulo):
        return True
    return (na_lista(canal, cfg.get("canais_permitidos")) and fala_de_gta6(titulo)
            and "trailer" in titulo.lower())


def canal_ok(canal, cfg):
    if na_lista(canal, cfg.get("canais_bloqueados")):
        return False
    if cfg.get("somente_canais_permitidos"):
        return na_lista(canal, cfg.get("canais_permitidos"))
    return True


def buscar(cfg, estado):
    import yt_dlp

    op = opcoes_ytdlp(cfg)
    op["extract_flat"] = "in_playlist"
    achados = {}
    with yt_dlp.YoutubeDL(op) as ydl:
        for termo in cfg["buscas"]:
            log(f"Buscando: {termo}")
            try:
                res = ydl.extract_info(f"ytsearch{cfg['resultados_por_busca']}:{termo}", download=False)
            except Exception as e:
                log(f"  falhou: {e}")
                continue
            for e in res.get("entries") or []:
                if not e or not e.get("id"):
                    continue
                achados[e["id"]] = e

    dur_min = cfg["duracao_minima_video_min"] * 60
    dur_max = cfg["duracao_maxima_video_min"] * 60
    candidatos = []
    for vid, e in achados.items():
        if vid in estado["processados"]:
            continue
        titulo = e.get("title") or ""
        canal = e.get("channel") or e.get("uploader") or ""
        dur = e.get("duration") or 0
        views = e.get("view_count") or 0
        if not video_serve(titulo, canal, cfg):
            continue
        if not canal_ok(canal, cfg):
            continue
        if dur and not (dur_min <= dur <= dur_max):
            continue
        if views and views < cfg["visualizacoes_minimas"]:
            continue
        candidatos.append(e)

    # um vídeo por canal por rodada: primeiro os canais permitidos, depois os mais vistos
    permitidos = cfg.get("canais_permitidos")
    candidatos.sort(key=lambda e: (na_lista(e.get("channel") or e.get("uploader"), permitidos),
                                   e.get("view_count") or 0), reverse=True)
    escolhidos, canais = [], set()
    for e in candidatos:
        canal = (e.get("channel") or e.get("uploader") or "").lower()
        if canal in canais:
            continue
        canais.add(canal)
        escolhidos.append(e)
        if len(escolhidos) >= cfg["videos_por_rodada"]:
            break
    return escolhidos


def baixar(url, cfg):
    import yt_dlp

    BAIXADOS.mkdir(parents=True, exist_ok=True)
    q = int(cfg.get("qualidade_maxima", 1080))
    op = opcoes_ytdlp(cfg)
    op.update({
        "format": f"bv*[height<={q}][ext=mp4]+ba[ext=m4a]/bv*[height<={q}]+ba/b[height<={q}]/b",
        "merge_output_format": "mp4",
        "outtmpl": str(BAIXADOS / "%(id)s.%(ext)s"),
    })
    with yt_dlp.YoutubeDL(op) as ydl:
        info = ydl.extract_info(url, download=True)
    caminho = None
    for d in info.get("requested_downloads") or []:
        if d.get("filepath"):
            caminho = d["filepath"]
    if not caminho:
        caminho = str(BAIXADOS / f"{info['id']}.mp4")
    return Path(caminho), info


# ---------------------------------------------------------------- melhores momentos

def duracao_video(caminho):
    r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                        "-of", "default=nw=1:nk=1", str(caminho)], stdout=subprocess.PIPE)
    return float(r.stdout.decode().strip() or 0)


def volume_por_segundo(caminho):
    """Volume (dB) de cada segundo do vídeo. Gritos e surtos aparecem como picos."""
    import numpy as np

    taxa = 8000
    r = subprocess.run(["ffmpeg", "-v", "error", "-i", str(caminho), "-vn", "-ac", "1",
                        "-ar", str(taxa), "-f", "s16le", "-"], stdout=subprocess.PIPE)
    a = np.frombuffer(r.stdout, dtype=np.int16).astype(np.float32)
    n = len(a) // taxa
    if n == 0:
        return np.zeros(0)
    a = a[: n * taxa].reshape(n, taxa)
    rms = np.sqrt((a ** 2).mean(axis=1)) + 1e-6
    return 20 * np.log10(rms / 32768)


def normalizar(x):
    import numpy as np

    x = np.asarray(x, dtype=np.float32)
    if x.size == 0 or float(x.max() - x.min()) < 1e-6:
        return np.zeros_like(x)
    return (x - x.min()) / (x.max() - x.min())


def suavizar(x, janela):
    import numpy as np

    if janela <= 1 or len(x) < janela:
        return x
    k = np.ones(janela, dtype=np.float32) / janela
    return np.convolve(x, k, mode="same")


def achar_momentos(caminho, info, cfg):
    """Devolve [(inicio, fim)] dos melhores momentos.

    Junta dois sinais: o "mais repetido" do YouTube (o gráfico em cima da barra
    do vídeo, quando existe) e os picos de volume (quando o youtuber grita).
    """
    import numpy as np

    dur = duracao_video(caminho)
    vol = volume_por_segundo(caminho)
    n = int(min(len(vol), dur)) if dur else len(vol)
    if n < 20:
        return []
    vol = vol[:n]

    # volume acima do normal daquele vídeo
    base = np.median(vol)
    desvio = np.median(np.abs(vol - base)) + 1e-6
    excesso = np.clip((vol - base) / desvio, 0, None)
    sinal_vol = normalizar(suavizar(excesso, 3))

    sinal_heat = np.zeros(n, dtype=np.float32)
    heat = (info or {}).get("heatmap") or []
    for h in heat:
        a, b = int(h.get("start_time", 0)), int(h.get("end_time", 0)) + 1
        sinal_heat[max(0, a):min(n, b)] = max(h.get("value", 0), 0)
    if heat:
        sinal_heat = normalizar(sinal_heat)
        pontos = 0.55 * sinal_heat + 0.45 * sinal_vol
    else:
        pontos = sinal_vol
    pontos = suavizar(pontos, 5)

    antes = int(cfg["segundos_antes_do_pico"])
    depois = int(cfg["segundos_depois_do_pico"])
    tam = antes + depois
    ini_min = int(cfg["pular_inicio_seg"])
    fim_max = n - int(cfg["pular_fim_seg"])
    if fim_max - ini_min < tam:
        ini_min, fim_max = 0, n
    pontos[:ini_min] = 0
    pontos[fim_max:] = 0

    momentos = []
    livre = pontos.copy()
    melhor = float(livre.max()) if livre.size else 0
    for _ in range(int(cfg["cortes_por_video"])):
        p = int(livre.argmax())
        if livre[p] <= 0 or livre[p] < 0.35 * melhor:
            break
        ini = max(0, p - antes)
        fim = min(n, ini + tam)
        ini = max(0, fim - tam)
        momentos.append((float(ini), float(fim)))
        livre[max(0, ini - tam // 2):min(n, fim + tam // 2)] = 0
    momentos.sort()
    return momentos


# ---------------------------------------------------------------- legendas e texto na tela

def ass_texto(t):
    return t.replace("\\", "/").replace("{", "(").replace("}", ")").replace("\n", " ")


def ass_tempo(s):
    s = max(0.0, s)
    h = int(s // 3600)
    m = int(s % 3600 // 60)
    return f"{h}:{m:02d}:{s % 60:05.2f}"


_modelo = None


def transcrever(audio_wav, cfg):
    """Palavras com tempo [(inicio, fim, palavra)] usando faster-whisper, se instalado."""
    global _modelo
    try:
        from faster_whisper import WhisperModel
    except ImportError:
        log("  (legendas desligadas: faster-whisper não instalado)")
        return []
    if _modelo is None:
        log(f"  carregando o modelo de legenda '{cfg['modelo_whisper']}' (a 1ª vez baixa o modelo)...")
        _modelo = WhisperModel(cfg["modelo_whisper"], device="auto", compute_type="int8")
    segs, _ = _modelo.transcribe(str(audio_wav), word_timestamps=True, vad_filter=True,
                                 language=cfg.get("idioma") or None)
    palavras = []
    for s in segs:
        for w in s.words or []:
            txt = w.word.strip()
            if txt:
                palavras.append((w.start, w.end, txt))
    return palavras


def agrupar_palavras(palavras, max_palavras=3, max_seg=1.3):
    grupos, atual = [], []
    for w in palavras:
        if atual and (len(atual) >= max_palavras or w[1] - atual[0][0] > max_seg or w[0] - atual[-1][1] > 0.6):
            grupos.append(atual)
            atual = []
        atual.append(w)
        if re.search(r"[.!?,]$", w[2]):
            grupos.append(atual)
            atual = []
    if atual:
        grupos.append(atual)
    return grupos


def montar_ass(destino, canal, duracao, palavras, cfg):
    fonte = cfg.get("fonte", "Arial")
    linha1 = ass_texto(cfg["titulo_linha1"].format(canal=canal).upper())
    linha2 = ass_texto(cfg["titulo_linha2"].format(canal=canal).upper())
    credito = ass_texto(f"Créditos: {canal}  •  vídeo completo no canal")
    fim = ass_tempo(duracao)

    linhas = [
        "[Script Info]",
        "ScriptType: v4.00+",
        "PlayResX: 1080",
        "PlayResY: 1920",
        "WrapStyle: 0",
        "ScaledBorderAndShadow: yes",
        "",
        "[V4+ Styles]",
        "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, "
        "Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, "
        "Shadow, Alignment, MarginL, MarginR, MarginV, Encoding",
        f"Style: Titulo,{fonte},76,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,-1,0,0,0,100,100,0,0,1,7,3,8,50,50,250,1",
        f"Style: Legenda,{fonte},88,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,-1,0,0,0,100,100,0,0,1,8,3,2,60,60,400,1",
        f"Style: Credito,{fonte},40,&H00E6E6E6,&H000000FF,&H00000000,&H64000000,-1,0,0,0,100,100,0,0,1,4,1,2,40,40,120,1",
        "",
        "[Events]",
        "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text",
        f"Dialogue: 1,0:00:00.00,{fim},Titulo,,0,0,0,,{{\\fad(250,0)}}{linha1}\\N{{\\c&H00FFFF&}}{linha2}",
        f"Dialogue: 1,0:00:00.00,{fim},Credito,,0,0,0,,{credito}",
    ]
    grupos = agrupar_palavras(palavras)
    for i, g in enumerate(grupos):
        ini = g[0][0]
        fim_g = g[-1][1]
        if i + 1 < len(grupos):
            prox = grupos[i + 1][0][0]
            if prox - fim_g < 0.4:
                fim_g = prox
        if fim_g <= ini:
            continue
        texto = ass_texto(" ".join(w[2] for w in g).upper())
        pop = "{\\fscx85\\fscy85\\t(0,90,\\fscx100\\fscy100)}"
        linhas.append(f"Dialogue: 2,{ass_tempo(ini)},{ass_tempo(min(fim_g, duracao))},Legenda,,0,0,0,,{pop}{texto}")
    destino.write_text("\n".join(linhas) + "\n", encoding="utf-8")


# ---------------------------------------------------------------- corte

def renderizar(video, ini, fim, pasta, canal, cfg):
    """Gera o corte vertical 1080x1920: fundo desfocado, vídeo no meio, título em cima, legenda embaixo."""
    pasta.mkdir(parents=True, exist_ok=True)
    dur = fim - ini

    palavras = []
    if cfg.get("legendas"):
        wav = pasta / "audio.wav"
        rodar(["ffmpeg", "-y", "-v", "error", "-ss", f"{ini:.2f}", "-t", f"{dur:.2f}", "-i", str(video),
               "-vn", "-ac", "1", "-ar", "16000", str(wav)])
        try:
            palavras = transcrever(wav, cfg)
        except Exception as e:
            log(f"  legenda falhou ({e}); seguindo sem legenda nesta rodada")
            cfg["legendas"] = False
        wav.unlink(missing_ok=True)

    montar_ass(pasta / "legenda.ass", canal, dur, palavras, cfg)

    zoom = float(cfg.get("zoom_do_video", 1.0))
    largura = int(round(1080 * zoom / 2) * 2)
    filtro = (
        "[0:v]scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,"
        "boxblur=25:3,eq=brightness=-0.18[fundo];"
        f"[0:v]scale={largura}:-2,crop='min(iw,1080)':ih[frente];"
        "[fundo][frente]overlay=(W-w)/2:(H-h)/2,setsar=1,fps=30,ass=legenda.ass[v];"
        f"[0:a]loudnorm=I=-14:TP=-1.5:LRA=11,afade=t=in:d=0.3,afade=t=out:st={max(dur - 0.6, 0):.2f}:d=0.6[a]"
    )
    rodar(["ffmpeg", "-y", "-v", "error", "-ss", f"{ini:.2f}", "-t", f"{dur:.2f}", "-i", str(video),
           "-filter_complex", filtro, "-map", "[v]", "-map", "[a]",
           "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p",
           "-c:a", "aac", "-b:a", "160k", "-ar", "44100", "-movflags", "+faststart", "corte.mp4"],
          cwd=pasta)
    (pasta / "legenda.ass").unlink(missing_ok=True)
    return pasta / "corte.mp4"


def textos_postagem(canal, url, canal_url, cfg, semente):
    rnd = random.Random(semente)
    titulo = rnd.choice(cfg["titulos"]).format(canal=canal)[:100]
    linhas = [titulo, ""]
    if url:
        linhas.append(f"🎥 Reação completa de {canal}: {url}")
    if canal_url:
        linhas.append(f"👉 Siga {canal}: {canal_url}")
    linhas += [
        f"Todos os créditos a {canal}. Corte feito por {cfg['nome_da_conta']} ({cfg['arroba']}).",
        "",
        cfg["hashtags_youtube"],
    ]
    descricao = "\n".join(linhas)
    tiktok = f"{titulo.replace('#gta6', '').strip()} | créditos: {canal} {cfg['hashtags_tiktok']}"
    return titulo, descricao, tiktok


def processar_video(video, info, canal, url, canal_url, cfg, estado):
    vid = (info or {}).get("id") or nome_arquivo(Path(video).stem)
    log(f"\n▶ {canal}: {(info or {}).get('title') or Path(video).name}")
    momentos = achar_momentos(video, info, cfg)
    if not momentos:
        log("  nenhum momento bom encontrado")
    hoje = dt.date.today().isoformat()
    feitos = []
    for i, (ini, fim) in enumerate(momentos, 1):
        pasta = SAIDA / hoje / f"{nome_arquivo(canal, 25)}_{vid}_{i}"
        log(f"  corte {i}: {int(ini // 60)}:{int(ini % 60):02d} até {int(fim // 60)}:{int(fim % 60):02d}")
        try:
            arq = renderizar(video, ini, fim, pasta, canal, cfg)
        except Exception as e:
            log(f"  ERRO no corte {i}: {e}")
            continue
        url_t = f"{url}&t={int(ini)}s" if url and "watch?v=" in url else url
        titulo, descricao, tiktok = textos_postagem(canal, url_t, canal_url, cfg, f"{vid}{i}")
        (pasta / "titulo.txt").write_text(titulo, encoding="utf-8")
        (pasta / "descricao_youtube.txt").write_text(descricao, encoding="utf-8")
        (pasta / "legenda_tiktok.txt").write_text(tiktok, encoding="utf-8")
        corte = {
            "arquivo": str(arq.relative_to(SAIDA)).replace("\\", "/"),
            "canal": canal, "video_id": vid, "url": url_t or "",
            "inicio": ini, "fim": fim, "titulo": titulo,
            "descricao": descricao, "tiktok": tiktok, "criado": hoje,
        }
        estado["cortes"].append(corte)
        feitos.append(corte)
        log(f"  ✔ {arq}")
    estado["processados"][vid] = {"canal": canal, "url": url or "", "data": hoje, "cortes": len(feitos)}
    salvar_estado(estado)
    return feitos


def processar_link(url, cfg, estado):
    try:
        video, info = baixar(url, cfg)
    except Exception as e:
        log(f"ERRO ao baixar {url}: {e}")
        return []
    canal = info.get("channel") or info.get("uploader") or "Youtuber"
    canal_url = info.get("channel_url") or info.get("uploader_url") or ""
    link = info.get("webpage_url") or url
    try:
        return processar_video(video, info, canal, link, canal_url, cfg, estado)
    finally:
        if cfg.get("apagar_videos_baixados", True):
            Path(video).unlink(missing_ok=True)


# ---------------------------------------------------------------- painel de postagem

def agenda(n, cfg):
    horarios = cfg.get("horarios_sugeridos") or ["18:00"]
    dia = dt.date.today() + dt.timedelta(days=1)
    slots = []
    while len(slots) < n:
        for h in horarios:
            slots.append(f"{dia.strftime('%d/%m')} às {h}")
        dia += dt.timedelta(days=1)
    return slots[:n]


def gerar_painel(cfg, estado):
    SAIDA.mkdir(parents=True, exist_ok=True)
    cortes = [c for c in estado["cortes"] if (SAIDA / c["arquivo"]).exists()]
    # intercala canais pra não postar 3 seguidos do mesmo youtuber
    por_canal = {}
    for c in cortes:
        por_canal.setdefault(c["canal"], []).append(c)
    ordem = []
    while any(por_canal.values()):
        for k in list(por_canal):
            if por_canal[k]:
                ordem.append(por_canal[k].pop(0))
    slots = agenda(len(ordem), cfg)

    cards = []
    for i, (c, slot) in enumerate(zip(ordem, slots)):
        e = html.escape
        cid = e(c["arquivo"])
        cards.append(f"""
<article class="card" data-id="{cid}">
  <video src="{e(c['arquivo'])}" controls preload="metadata"></video>
  <div class="info">
    <div class="topo"><b>#{i + 1} · {e(c['canal'])}</b><span class="slot">Sugestão: {e(slot)}</span></div>
    <label>Título (YouTube)</label>
    <div class="campo"><textarea rows="2" readonly>{e(c['titulo'])}</textarea><button>Copiar</button></div>
    <label>Descrição (YouTube)</label>
    <div class="campo"><textarea rows="6" readonly>{e(c['descricao'])}</textarea><button>Copiar</button></div>
    <label>Legenda (TikTok)</label>
    <div class="campo"><textarea rows="3" readonly>{e(c['tiktok'])}</textarea><button>Copiar</button></div>
    <div class="marcas">
      <label><input type="checkbox" data-k="yt"> Postado no YouTube</label>
      <label><input type="checkbox" data-k="tt"> Postado no TikTok</label>
      <a href="{e(c['arquivo'])}" download>Baixar vídeo</a>
    </div>
  </div>
</article>""")

    pagina = f"""<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Painel de Cortes</title>
<style>
:root{{--bg:#0f1115;--card:#181b22;--txt:#eceef2;--sub:#9aa3b2;--borda:#2a2f3a;--acento:#ff3d7f;--ok:#2ecc71}}
*{{box-sizing:border-box}}body{{margin:0;background:var(--bg);color:var(--txt);font:15px/1.4 system-ui,Segoe UI,Arial,sans-serif}}
header{{padding:20px 16px;border-bottom:1px solid var(--borda)}}h1{{margin:0;font-size:22px}}h1 span{{color:var(--acento)}}
header p{{margin:6px 0 0;color:var(--sub)}}
main{{max-width:1100px;margin:0 auto;padding:16px;display:grid;gap:16px}}
.card{{display:grid;grid-template-columns:270px 1fr;gap:16px;background:var(--card);border:1px solid var(--borda);border-radius:12px;padding:12px}}
.card.feito{{opacity:.45}}video{{width:100%;aspect-ratio:9/16;background:#000;border-radius:8px}}
.topo{{display:flex;justify-content:space-between;gap:8px;flex-wrap:wrap;margin-bottom:6px}}.slot{{color:var(--acento);font-weight:600}}
label{{display:block;color:var(--sub);font-size:13px;margin-top:8px}}
.campo{{display:flex;gap:6px}}textarea{{flex:1;background:#0c0e12;color:var(--txt);border:1px solid var(--borda);border-radius:6px;padding:6px;font:inherit;resize:vertical}}
button{{background:var(--acento);color:#fff;border:0;border-radius:6px;padding:0 12px;font-weight:600;cursor:pointer}}button.ok{{background:var(--ok)}}
.marcas{{display:flex;gap:16px;flex-wrap:wrap;align-items:center;margin-top:10px}}.marcas label{{display:inline;color:var(--txt);margin:0}}
a{{color:var(--acento)}}
@media(max-width:700px){{.card{{grid-template-columns:1fr}}video{{max-height:70vh}}}}
</style></head><body>
<header><h1>{html.escape(cfg['nome_da_conta'])} <span>· cortes prontos</span></h1>
<p>{len(ordem)} cortes. Poste no YouTube Studio (Criar → Enviar vídeos) e no TikTok (tiktok.com/upload). Marque o que já postou.</p></header>
<main>{''.join(cards) or '<p>Nenhum corte ainda. Rode o rodar.bat.</p>'}</main>
<script>
const chave = id => 'corte:' + id;
document.querySelectorAll('.card').forEach(card => {{
  const id = card.dataset.id;
  let est = {{}}; try {{ est = JSON.parse(localStorage.getItem(chave(id)) || '{{}}'); }} catch (e) {{}}
  const atualizar = () => card.classList.toggle('feito', !!(est.yt && est.tt));
  card.querySelectorAll('input[type=checkbox]').forEach(cb => {{
    cb.checked = !!est[cb.dataset.k];
    cb.onchange = () => {{ est[cb.dataset.k] = cb.checked; try {{ localStorage.setItem(chave(id), JSON.stringify(est)); }} catch (e) {{}} atualizar(); }};
  }});
  atualizar();
  card.querySelectorAll('.campo button').forEach(b => b.onclick = async () => {{
    const t = b.previousElementSibling;
    try {{ await navigator.clipboard.writeText(t.value); }} catch (e) {{ t.select(); document.execCommand('copy'); }}
    b.textContent = 'Copiado!'; b.classList.add('ok');
    setTimeout(() => {{ b.textContent = 'Copiar'; b.classList.remove('ok'); }}, 1200);
  }});
}});
</script></body></html>"""
    (SAIDA / "PAINEL.html").write_text(pagina, encoding="utf-8")
    log(f"\nPainel: {SAIDA / 'PAINEL.html'}  ({len(ordem)} cortes)")


# ---------------------------------------------------------------- comandos

def main():
    ap = argparse.ArgumentParser(description="Cortes de reações aos trailers de GTA 6")
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("tudo")
    sub.add_parser("buscar")
    sub.add_parser("painel")
    p = sub.add_parser("link")
    p.add_argument("urls", nargs="+")
    p = sub.add_parser("arquivo")
    p.add_argument("caminho")
    p.add_argument("--canal", required=True)
    p.add_argument("--url", default="")
    a = ap.parse_args()

    cfg = carregar_config()
    estado = carregar_estado()

    if a.cmd == "painel":
        gerar_painel(cfg, estado)
        return

    if a.cmd == "buscar":
        for e in buscar(cfg, estado):
            log(f"- {e.get('channel') or e.get('uploader')}: {e.get('title')} "
                f"({e.get('view_count') or '?'} views) https://youtu.be/{e['id']}")
        return

    exigir_ffmpeg()
    if a.cmd == "arquivo":
        processar_video(Path(a.caminho), None, a.canal, a.url, "", cfg, estado)
    elif a.cmd == "link":
        for u in a.urls:
            processar_link(u, cfg, estado)
    elif a.cmd == "tudo":
        escolhidos = buscar(cfg, estado)
        log(f"\n{len(escolhidos)} vídeo(s) novo(s) para cortar")
        for e in escolhidos:
            processar_link(f"https://www.youtube.com/watch?v={e['id']}", cfg, estado)
    gerar_painel(cfg, estado)


if __name__ == "__main__":
    main()
