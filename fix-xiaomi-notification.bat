@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
echo ============================================================
echo  Zhizhang - Xiaomi / HyperOS notification access fix
echo  No root required. USB debugging must be ON and authorized.
echo ============================================================
echo.
echo [*] If the phone shows "Allow USB debugging?", tick
echo     "Always allow from this computer" and tap OK.
echo [*] Waiting for authorization (up to 8 minutes)...
echo.
python "%~dp0fix-xiaomi-notification.py" %*
echo.
pause
