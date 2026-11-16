// Desenha os ícones do app (res/mipmap-*) com o Chromium do Playwright.
// Uso: node gerar_icones.js   (precisa do pacote playwright)
const { chromium } = require(process.env.PLAYWRIGHT || "playwright");
const path = require("path");
const fs = require("fs");

const DENSIDADES = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };

// Pote de moedas branco, meio cheio de dourado, com uma moeda caindo, num quadrado de 108 unidades.
function desenho(fundo) {
  return `
    ${fundo ? '<rect x="14" y="14" width="80" height="80" rx="18" fill="#B5432A"/>' : ""}
    <circle cx="54" cy="35" r="7" fill="#E8B65A"/>
    <rect x="36" y="45" width="36" height="7" rx="3.5" fill="#FFFFFF"/>
    <path d="M39 54h30v14a7 7 0 0 1-7 7H46a7 7 0 0 1-7-7z" fill="#FFFFFF"/>
    <path d="M43 62h22v5a3.5 3.5 0 0 1-3.5 3.5h-15A3.5 3.5 0 0 1 43 67z" fill="#E8B65A"/>`;
}

(async () => {
  const b = await chromium.launch();
  const p = await b.newPage();
  for (const [nome, f] of Object.entries(DENSIDADES)) {
    const dir = path.join(__dirname, "res", `mipmap-${nome}`);
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
