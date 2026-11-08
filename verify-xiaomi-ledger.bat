@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
echo ============================================================
echo  Zhizhang - Xiaomi ledger verification (read only)
echo ============================================================
echo.
echo [*] Run this BEFORE a payment to save a baseline,
echo     then run  verify-xiaomi-ledger.bat diff  AFTER payment.
echo.
if /i "%~1"=="diff" (
  python "%~dp0verify-xiaomi-ledger.py" --diff
) else (
  python "%~dp0verify-xiaomi-ledger.py"
)
echo.
pause
