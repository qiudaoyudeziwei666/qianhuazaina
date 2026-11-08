@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
echo ============================================================
echo  Zhizhang - offline capture self-test
echo  Disconnects PC ADB; the phone posts a payment notification
echo  by itself; then checks whether the ledger recorded it.
echo  Takes about 30 seconds. No real payment needed.
echo ============================================================
echo.
python "%~dp0test-offline-capture.py" %*
echo.
pause
