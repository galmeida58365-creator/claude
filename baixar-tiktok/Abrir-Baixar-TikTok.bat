@echo off
rem Abre o Baixar TikTok. Antes, instala/atualiza o yt-dlp (o TikTok muda
rem sempre, e o yt-dlp atualizado e o que mantem os downloads funcionando).
cd /d "%~dp0"

set "PY="
where py >nul 2>nul && set "PY=py"
if not defined PY where python >nul 2>nul && set "PY=python"
if not defined PY goto sem_python

echo Atualizando o yt-dlp, um instante...
%PY% -m pip install --upgrade --quiet --disable-pip-version-check yt-dlp
if errorlevel 1 (
    echo.
    echo Nao deu para atualizar agora ^(sem internet?^). Abrindo mesmo assim...
    timeout /t 3 >nul
)

start "" %PY%w "%~dp0baixar_tiktok.py"
exit /b

:sem_python
echo.
echo O Python nao esta instalado neste computador.
echo.
echo 1. Vai abrir a pagina de download do Python.
echo 2. Baixe e instale. Na primeira tela do instalador, MARQUE a opcao
echo    "Add python.exe to PATH" e clique em "Install Now".
echo 3. Depois, abra este arquivo de novo.
echo.
start "" https://www.python.org/downloads/
pause
