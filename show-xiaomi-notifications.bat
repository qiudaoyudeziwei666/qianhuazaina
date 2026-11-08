@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
echo ============================================================
echo  Zhizhang - notification diagnostic log (read only)
echo ============================================================
echo.
if /i "%~1"=="all" (
  python "%~dp0show-xiaomi-notifications.py" --all
) else if /i "%~1"=="clear" (
  python "%~dp0show-xiaomi-notifications.py" --clear
) else (
  python "%~dp0show-xiaomi-notifications.py"
)
echo.
pause
