#!/usr/bin/env bash
# Gera dist/CaixaDoMes-Windows.zip: o programa de computador (Electron) com a página embutida.
# Precisa de: node/npm, python3, curl, unzip, zip.
set -euo pipefail
cd "$(dirname "$0")"

VERSAO=$(node -p 'require("./package.json").version')
ELECTRON=37.2.6

FER=.ferramentas
OUT=build
DIST=../dist
mkdir -p "$FER" "$DIST"

[ -s "$FER/electron-win.zip" ] || curl -fsSL -o "$FER/electron-win.zip" \
  "https://github.com/electron/electron/releases/download/v$ELECTRON/electron-v$ELECTRON-win32-x64.zip"
[ -d "$FER/node_modules/resedit" ] || npm install --silent --no-save --prefix "$FER" resedit@3.1.0

rm -rf "$OUT"
APP="$OUT/CaixaDoMes"
mkdir -p "$APP"
unzip -q "$FER/electron-win.zip" -d "$APP"
rm -f "$APP/resources/default_app.asar"
# Só os idiomas usados (deixa o pacote bem menor).
find "$APP/locales" -name '*.pak' ! -name 'pt-BR.pak' ! -name 'en-US.pak' -delete

mkdir -p "$APP/resources/app"
cp package.json main.js "$APP/resources/app/"
python3 ../android/embutir_fontes.py ../index.html "$APP/resources/app"

mv "$APP/electron.exe" "$APP/CaixaDoMes.exe"
NODE_PATH="$FER/node_modules" node editar_exe.js "$APP/CaixaDoMes.exe" icone.ico "$VERSAO"
cp LEIA-ME.txt "$OUT/"

rm -f "$DIST/CaixaDoMes-Windows.zip"
(cd "$OUT" && zip -qr -9 "../$DIST/CaixaDoMes-Windows.zip" CaixaDoMes LEIA-ME.txt)
echo "Pronto: dist/CaixaDoMes-Windows.zip ($(du -h "$DIST/CaixaDoMes-Windows.zip" | cut -f1))"
