// Desenha os ícones do app (res/mipmap-*) com o Chromium do Playwright.
// Uso: node gerar_icones.js   (precisa do pacote playwright)
const { chromium } = require(process.env.PLAYWRIGHT || "playwright");
const fs = require("fs");
const path = require("path");

const DENSIDADES = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };

// Cartão branco com três barras (prioridade alta, média, baixa), num quadrado de 108 unidades.
function desenho(fundo) {
  return `
    ${fundo ? '<rect x="14" y="14" width="80" height="80" rx="18" fill="#0E6B52"/>' : ""}
    <rect x="30" y="31" width="48" height="46" rx="7" fill="#FFFFFF"/>
    <rect x="37" y="40" width="34" height="6" rx="3" fill="#BF3A29"/>
    <rect x="37" y="51" width="25" height="6" rx="3" fill="#C98A1E"/>
    <rect x="37" y="62" width="16" height="6" rx="3" fill="#3C659C"/>`;
}

(async () => {
  const b = await chromium.launch();
  const p = await b.newPage();
  for (const [nome, f] of Object.entries(DENSIDADES)) {
    const dir = path.join(__dirname, "res", `mipmap-${nome}`);
    fs.mkdirSync(dir, { recursive: true });
    // Ícone clássico (48dp): o desenho ocupa o quadro inteiro, com o fundo verde.
    // Frente do ícone adaptável (108dp): só o cartão, o fundo vem da cor icone_fundo.
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
