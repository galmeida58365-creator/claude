@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ==================================================
echo  Cortes GTA 6 - instalacao (so precisa fazer 1 vez)
echo ==================================================
echo.

rem Usa sempre o Python 3.12: as bibliotecas da legenda tem versao pronta para ele.
py -3.12 --version >nul 2>nul || (
  echo Instalando o Python 3.12...
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

py -3.12 --version >nul 2>nul || goto reabrir
where ffmpeg >nul 2>nul || goto reabrir

rem Se ja existe um ambiente feito com outra versao do Python, apaga e refaz.
if exist venv\Scripts\python.exe (
  venv\Scripts\python -c "import sys; sys.exit(0 if sys.version_info[:2] == (3, 12) else 1)" || (
    echo Refazendo o ambiente com o Python 3.12...
    rmdir /s /q venv
  )
)
if not exist venv\Scripts\python.exe (
  echo Criando o ambiente do Python...
  py -3.12 -m venv venv || goto erro
)

echo Instalando o yt-dlp e as bibliotecas...
venv\Scripts\python -m pip install -q -U pip
venv\Scripts\python -m pip install -U "yt-dlp[default]" numpy || goto erro

echo.
echo Instalando a legenda automatica - pode demorar alguns minutos...
venv\Scripts\python -m pip install -U --only-binary=:all: faster-whisper || goto semlegenda

echo.
echo Tudo pronto! Agora de dois cliques no rodar.bat
pause
exit /b 0

:semlegenda
echo.
echo AVISO: a legenda automatica nao instalou, mas o resto esta pronto.
echo Os cortes vao sair sem legenda. Mande um print desta janela pro Claude.
echo Ja pode usar o rodar.bat
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
