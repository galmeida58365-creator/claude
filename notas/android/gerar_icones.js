// Desenha os ícones do app (res/mipmap-*) com o Chromium do Playwright.
// Uso: node gerar_icones.js   (precisa do pacote playwright)
// Outra cor: COR=#1E7D3C COR_CLARA=#BFD9C6 COR_FUNDO_BARRA=#DCEFE1 PASTA=res-verde node gerar_icones.js
const { chromium } = require(process.env.PLAYWRIGHT || "playwright");
const fs = require("fs");
const path = require("path");

const COR = process.env.COR || "#2453C9";
const COR_CLARA = process.env.COR_CLARA || "#B9C6E6";
const COR_FUNDO_BARRA = process.env.COR_FUNDO_BARRA || "#E3EAFB";
const PASTA = process.env.PASTA || "res";

const DENSIDADES = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };

// Folha de prova com duas linhas de nota e uma régua que mostra quanto falta, num quadrado de 108 unidades.
function desenho(fundo) {
  return `
    ${fundo ? `<rect x="14" y="14" width="80" height="80" rx="18" fill="${COR}"/>` : ""}
    <rect x="31" y="29" width="46" height="50" rx="7" fill="#FFFFFF"/>
    <rect x="38" y="38" width="20" height="5" rx="2.5" fill="${COR_CLARA}"/>
    <rect x="62" y="38" width="8" height="5" rx="2.5" fill="${COR}"/>
    <rect x="38" y="48" width="20" height="5" rx="2.5" fill="${COR_CLARA}"/>
    <rect x="62" y="48" width="8" height="5" rx="2.5" fill="${COR}"/>
    <rect x="38" y="63" width="32" height="7" rx="3.5" fill="${COR_FUNDO_BARRA}"/>
    <rect x="38" y="63" width="21" height="7" rx="3.5" fill="#18794E"/>`;
}

(async () => {
  const b = await chromium.launch();
  const p = await b.newPage();
  for (const [nome, f] of Object.entries(DENSIDADES)) {
    const dir = path.join(__dirname, PASTA, `mipmap-${nome}`);
    fs.mkdirSync(dir, { recursive: true });
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
