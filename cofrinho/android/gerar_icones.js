// Desenha os ícones do app (res/mipmap-*) com o Chromium do Playwright.
// Uso: node gerar_icones.js   (precisa do pacote playwright)
// Se existir icone.jpg (ou icone.png) nesta pasta, o ícone é essa imagem; senão, o pote de moedas.
const { chromium } = require(process.env.PLAYWRIGHT || "playwright");
const path = require("path");
const fs = require("fs");

const DENSIDADES = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };

// Enquadramento da imagem, em pixels da própria imagem: o centro do ícone e a largura que ele mostra.
// O Android recorta as bordas (só ~2/3 do meio aparecem), então o rosto deve ficar perto do centro.
const FOTO = { centroX: 325, centroY: 300, largura: 600 };

const arquivoFoto = ["icone.jpg", "icone.png"].map((n) => path.join(__dirname, n)).find((f) => fs.existsSync(f));

function desenhoPote(fundo) {
  return `
    ${fundo ? '<rect x="14" y="14" width="80" height="80" rx="18" fill="#B5432A"/>' : ""}
    <circle cx="54" cy="35" r="7" fill="#E8B65A"/>
    <rect x="36" y="45" width="36" height="7" rx="3.5" fill="#FFFFFF"/>
    <path d="M39 54h30v14a7 7 0 0 1-7 7H46a7 7 0 0 1-7-7z" fill="#FFFFFF"/>
    <path d="M43 62h22v5a3.5 3.5 0 0 1-3.5 3.5h-15A3.5 3.5 0 0 1 43 67z" fill="#E8B65A"/>`;
}

// A imagem preenche o quadrado de 108 unidades; no ícone clássico, com os cantos arredondados.
function desenhoFoto(fundo, dados, w, h) {
  const k = 108 / FOTO.largura;
  const img = `<image href="${dados}" x="${54 - FOTO.centroX * k}" y="${54 - FOTO.centroY * k}" width="${w * k}" height="${h * k}" preserveAspectRatio="none"/>`;
  return fundo
    ? `<clipPath id="c"><rect x="14" y="14" width="80" height="80" rx="18"/></clipPath><g clip-path="url(#c)">${img}</g>`
    : img;
}

(async () => {
  const b = await chromium.launch();
  const p = await b.newPage();
  let desenho = desenhoPote;
  if (arquivoFoto) {
    const tipo = arquivoFoto.endsWith(".png") ? "image/png" : "image/jpeg";
    const dados = `data:${tipo};base64,${fs.readFileSync(arquivoFoto).toString("base64")}`;
    await p.setContent(`<img src="${dados}">`);
    const [w, h] = await p.$eval("img", (i) => [i.naturalWidth, i.naturalHeight]);
    desenho = (fundo) => desenhoFoto(fundo, dados, w, h);
  }
  for (const [nome, f] of Object.entries(DENSIDADES)) {
    const dir = path.join(__dirname, "res", `mipmap-${nome}`);
    fs.mkdirSync(dir, { recursive: true });
    // Ícone clássico (48dp) e frente do ícone adaptável (108dp, o fundo vem da cor icone_fundo).
    for (const [arquivo, dp, fundo, viewBox] of [
      ["ic_launcher.png", 48, true, "14 14 80 80"],
      ["ic_launcher_frente.png", 108, false, "0 0 108 108"],
    ]) {
      const px = Math.round(dp * f);
      await p.setViewportSize({ width: px, height: px });
      await p.setContent(`<body style="margin:0;background:transparent"><svg xmlns="http://www.w3.org/2000/svg" width="${px}" height="${px}" viewBox="${viewBox}">${desenho(fundo)}</svg></body>`);
      await p.locator("svg").screenshot({ path: path.join(dir, arquivo), omitBackground: true });
    }
  }
  await b.close();
})();
