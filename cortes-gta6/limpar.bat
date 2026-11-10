@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist venv\Scripts\python.exe (
  echo Rode o instalar.bat primeiro.
  pause
  exit /b 1
)
echo Apagar do PC os cortes com mais de quantos dias?
echo   Enter = o padrao do config.json
echo   0 = apagar TODOS os cortes ja feitos - so use se ja postou tudo
set "DIAS="
set /p "DIAS=Dias: "
if "%DIAS%"=="" (
  venv\Scripts\python cortes.py limpar
) else (
  venv\Scripts\python cortes.py limpar --dias %DIAS%
)
pause
