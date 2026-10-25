// Programa de computador do Caixa do Mês: abre a mesma página numa janela própria.
// Os dados ficam no localStorage desta janela, guardado na pasta de dados do programa.
const { app, BrowserWindow, Menu, nativeTheme, shell } = require("electron");
const path = require("path");

app.userAgentFallback = `${app.userAgentFallback} CaixaDoMesDesktop`;

let janela = null;

function abrirFora(url) {
  if (/^https?:\/\//.test(url)) shell.openExternal(url);
}

function criarJanela() {
  janela = new BrowserWindow({
    width: 1240,
    height: 860,
    minWidth: 380,
    minHeight: 500,
    title: "Caixa do Mês",
    backgroundColor: nativeTheme.shouldUseDarkColors ? "#0E1412" : "#EDF1EE",
    show: false,
    // webSecurity desligado só para a página local poder falar com o Firebase (sincronização);
    // a janela nunca navega para fora dos arquivos do programa.
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true, spellcheck: false, webSecurity: false },
  });
  janela.webContents.setWindowOpenHandler(({ url }) => {
    abrirFora(url);
    return { action: "deny" };
  });
  janela.webContents.on("will-navigate", (evento, url) => {
    if (!url.startsWith("file:")) {
      evento.preventDefault();
      abrirFora(url);
    }
  });
  janela.once("ready-to-show", () => janela.show());
  janela.on("closed", () => { janela = null; });
  janela.loadFile(path.join(__dirname, "index.html"));
}

// Uma janela só: abrir o programa de novo traz a janela que já está aberta.
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on("second-instance", () => {
    if (!janela) return;
    if (janela.isMinimized()) janela.restore();
    janela.focus();
  });
  Menu.setApplicationMenu(null);
  app.whenReady().then(criarJanela);
  app.on("window-all-closed", () => app.quit());
}
