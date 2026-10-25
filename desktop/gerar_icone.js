// Desenha icone.ico (16 a 256 px) com o Chromium do Playwright. Uso: node gerar_icone.js
const { chromium } = require(process.env.PLAYWRIGHT || "playwright");
const fs = require("fs");
const path = require("path");

// Mesmo desenho do app Android: cartão com as barras das prioridades sobre o quadrado verde.
const SVG = `
  <rect x="14" y="14" width="80" height="80" rx="18" fill="#0E6B52"/>
  <rect x="30" y="31" width="48" height="46" rx="7" fill="#FFFFFF"/>
  <rect x="37" y="40" width="34" height="6" rx="3" fill="#BF3A29"/>
  <rect x="37" y="51" width="25" height="6" rx="3" fill="#C98A1E"/>
  <rect x="37" y="62" width="16" height="6" rx="3" fill="#3C659C"/>`;
const TAMANHOS = [16, 24, 32, 48, 64, 128, 256];

(async () => {
  const b = await chromium.launch();
  const p = await b.newPage();
  const pngs = [];
  for (const px of TAMANHOS) {
    await p.setViewportSize({ width: px, height: px });
    await p.setContent(`<body style="margin:0;background:transparent"><svg xmlns="http://www.w3.org/2000/svg" width="${px}" height="${px}" viewBox="14 14 80 80">${SVG}</svg></body>`);
    pngs.push(await p.locator("svg").screenshot({ omitBackground: true }));
  }
  await b.close();

  // Arquivo .ico com as imagens em PNG.
  const cab = Buffer.alloc(6 + 16 * pngs.length);
  cab.writeUInt16LE(0, 0);
  cab.writeUInt16LE(1, 2);
  cab.writeUInt16LE(pngs.length, 4);
  let deslocamento = cab.length;
  pngs.forEach((png, i) => {
    const px = TAMANHOS[i];
    const o = 6 + 16 * i;
    cab.writeUInt8(px >= 256 ? 0 : px, o);
    cab.writeUInt8(px >= 256 ? 0 : px, o + 1);
    cab.writeUInt16LE(1, o + 4);
    cab.writeUInt16LE(32, o + 6);
    cab.writeUInt32LE(png.length, o + 8);
    cab.writeUInt32LE(deslocamento, o + 12);
    deslocamento += png.length;
  });
  fs.writeFileSync(path.join(__dirname, "icone.ico"), Buffer.concat([cab, ...pngs]));
  console.log("icone.ico gerado");
})();
