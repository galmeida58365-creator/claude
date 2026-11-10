@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist venv\Scripts\python.exe (
  echo Rode o instalar.bat primeiro.
  pause
  exit /b 1
)
echo Cole o link do video do YouTube (reacao ao trailer) e aperte Enter:
set /p "LINK="
if "%LINK%"=="" exit /b 0
venv\Scripts\python -m pip install -q -U "yt-dlp[default]"
venv\Scripts\python cortes.py link "%LINK%"
start "" "%~dp0cortes\PAINEL.html"
pause
