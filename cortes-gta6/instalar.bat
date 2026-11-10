@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ==================================================
echo  Cortes GTA 6 - instalacao (so precisa fazer 1 vez)
echo ==================================================
echo.

where py >nul 2>nul || (
  echo Instalando o Python...
  winget install -e --id Python.Python.3.12 --accept-source-agreements --accept-package-agreements
)
where ffmpeg >nul 2>nul || (
  echo Instalando o ffmpeg...
  winget install -e --id Gyan.FFmpeg --accept-source-agreements --accept-package-agreements
)
where deno >nul 2>nul || (
  echo Instalando o Deno - o yt-dlp precisa dele para o YouTube...
  winget install -e --id DenoLand.Deno --accept-source-agreements --accept-package-agreements
)

where py >nul 2>nul || goto reabrir
where ffmpeg >nul 2>nul || goto reabrir

if not exist venv\Scripts\python.exe (
  echo Criando o ambiente do Python...
  py -3 -m venv venv || goto erro
)
echo Instalando as bibliotecas - pode demorar alguns minutos...
venv\Scripts\python -m pip install -q -U pip
venv\Scripts\python -m pip install -U -r requisitos.txt || goto erro

echo.
echo Tudo pronto! Agora de dois cliques no rodar.bat
pause
exit /b 0

:reabrir
echo.
echo Programas instalados. FECHE esta janela e de dois cliques no instalar.bat DE NOVO
echo (o Windows so enxerga o que foi instalado numa janela nova).
pause
exit /b 0

:erro
echo.
echo Deu erro na instalacao. Tire um print desta janela e mande pro Claude.
pause
exit /b 1
