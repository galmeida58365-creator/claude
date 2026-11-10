@echo off
chcp 65001 >nul
cd /d "%~dp0"
schtasks /create /tn "Cortes GTA 6" /tr "\"%~dp0rodar.bat\" auto" /sc daily /st 10:00 /f
if errorlevel 1 goto falhou
echo Agendado! Todo dia as 10:00 o PC busca reacoes novas e gera os cortes.
echo O PC precisa estar ligado nesse horario. Para cancelar, use o desagendar.bat
pause
exit /b 0
:falhou
echo Nao consegui agendar. Clique com o botao direito no arquivo e escolha "Executar como administrador".
pause
