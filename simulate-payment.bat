@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
echo ============================================================
echo  Zhizhang - simulate a payment notification (NO real payment)
echo  Uses ADB to inject a test notification into the phone.
echo ============================================================
echo.
python "%~dp0simulate-payment.py" %*
echo.
pause
