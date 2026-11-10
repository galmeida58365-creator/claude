@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist venv\Scripts\python.exe (
  echo Rode o instalar.bat primeiro.
  pause
  exit /b 1
)
echo Atualizando o yt-dlp, porque o YouTube muda toda hora...
venv\Scripts\python -m pip install -q -U "yt-dlp[default]"

if /i "%~1"=="auto" (
  venv\Scripts\python cortes.py tudo > "%~dp0ultima-rodada.log" 2>&1
  exit /b 0
)

venv\Scripts\python cortes.py tudo
start "" "%~dp0cortes\PAINEL.html"
echo.
echo Terminou! O painel com os cortes abriu no navegador.
pause
