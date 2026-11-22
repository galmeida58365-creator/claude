#!/usr/bin/env bash
# Gera dist/QuantoFalta.apk (ou o nome em SAIDA) a partir de notas/index.html, sem Android Studio.
# Reaproveita os ajudantes do app Caixa do Mês (pasta android/ na raiz do repositório).
# Precisa de: Java (JDK 17+), python3, curl, unzip, zip.
#
# Para gerar uma cópia separada do app (outro nome, instalável ao lado do original), use variáveis:
#   NOME_APP="Quanto Falta R" PACOTE=br.quantofalta.r SAIDA=QuantoFaltaR.apk BOLETIM=../meu-boletim-r.json ./build.sh
# Para o ícone e o fundo verdes, junte RES_EXTRA=res-verde (e "tema": "verde" no arquivo do boletim).
set -euo pipefail
cd "$(dirname "$0")"

VERSAO_CODIGO=4
VERSAO_NOME=1.3
SENHA=quantofalta   # senha da chave de assinatura em quanto-falta.p12

NOME_APP=${NOME_APP:-Quanto Falta}        # nome que aparece na tela do celular
PACOTE=${PACOTE:-br.quantofalta.app}      # identificador do app; outro identificador = outro app instalado
SAIDA=${SAIDA:-QuantoFalta.apk}           # nome do arquivo gerado em dist/
BOLETIM=${BOLETIM:-../meu-boletim.json}   # matérias que já vêm prontas (particular, fora do repositório)
RES_EXTRA=${RES_EXTRA:-}                  # pasta com recursos que substituem os de res/ (ex.: res-verde)

COMUM=../../android
FER=.ferramentas
OUT=build
DIST=../../dist
mkdir -p "$FER" "$DIST"

baixar() { [ -s "$FER/$1" ] || curl -fsSL -o "$FER/$1" "$2"; }
baixar bundletool.jar https://github.com/google/bundletool/releases/download/1.17.2/bundletool-all-1.17.2.jar
baixar android.jar https://raw.githubusercontent.com/Sable/android-platforms/master/android-33/android.jar
baixar dx.jar https://repo1.maven.org/maven2/com/jakewharton/android/repackaged/dalvik-dx/16.0.1/dalvik-dx-16.0.1.jar
if [ ! -x "$FER/aapt2" ]; then
  unzip -p "$FER/bundletool.jar" linux/aapt2 > "$FER/aapt2"
  chmod +x "$FER/aapt2"
fi

if [ ! -f quanto-falta.p12 ]; then
  keytool -genkeypair -keystore quanto-falta.p12 -storetype PKCS12 -alias quantofalta \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Quanto Falta" \
    -storepass "$SENHA" -keypass "$SENHA"
fi

rm -rf "$OUT"
mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/assets" "$OUT/assina"

# Se o arquivo do boletim existir, o app já abre com essas matérias.
if [ -f "$BOLETIM" ]; then
  python3 montar_pagina.py ../index.html "$OUT/pagina.html" "$BOLETIM"
else
  python3 montar_pagina.py ../index.html "$OUT/pagina.html"
fi
python3 "$COMUM/embutir_fontes.py" "$OUT/pagina.html" "$OUT/assets"

cp -r res "$OUT/res-fonte"
if [ -n "$RES_EXTRA" ]; then cp -r "$RES_EXTRA"/. "$OUT/res-fonte/"; fi
"$FER/aapt2" compile --dir "$OUT/res-fonte" -o "$OUT/res/"
sed "s/android:label=\"[^\"]*\"/android:label=\"$NOME_APP\"/" AndroidManifest.xml > "$OUT/AndroidManifest.xml"
"$FER/aapt2" link -o "$OUT/base.apk" -I "$FER/android.jar" \
  --manifest "$OUT/AndroidManifest.xml" --rename-manifest-package "$PACOTE" \
  --java "$OUT/gen" -A "$OUT/assets" \
  --min-sdk-version 24 --target-sdk-version 34 \
  --version-code "$VERSAO_CODIGO" --version-name "$VERSAO_NOME" \
  "$OUT"/res/*.flat

javac -nowarn -source 8 -target 8 -bootclasspath "$FER/android.jar" -d "$OUT/classes" \
  $(find src "$OUT/gen" -name '*.java') 2>&1 | grep -v 'warning: \[options\]' || true
java -cp "$FER/dx.jar" com.android.dx.command.Main --dex --min-sdk-version=24 \
  --output="$OUT/classes.dex" "$OUT/classes"
(cd "$OUT" && zip -q base.apk classes.dex)
python3 "$COMUM/alinhar.py" "$OUT/base.apk" "$OUT/alinhado.apk"

javac -nowarn -cp "$FER/bundletool.jar" -d "$OUT/assina" "$COMUM/Assina.java"
java -cp "$FER/bundletool.jar:$OUT/assina" Assina quanto-falta.p12 "$SENHA" "$OUT/alinhado.apk" "$DIST/$SAIDA"
python3 "$COMUM/alinhar.py" --conferir "$DIST/$SAIDA"
echo "Pronto: dist/$SAIDA ($NOME_APP, $PACOTE)"
