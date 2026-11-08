@echo off
chcp 65001 >nul
setlocal

echo ==========================================================
echo  Zhizhang - enable notification access via ADB
echo  No root required. USB debugging must be ON and authorized.
echo ==========================================================
echo.

where adb >nul 2>nul
if errorlevel 1 (
  echo [!] adb was not found in PATH.
  echo     Put this file inside your Android SDK platform-tools folder,
  echo     or add platform-tools to your PATH, then run it again.
  echo.
  pause
  exit /b 1
)

echo [*] Connected devices:
adb devices
echo.

echo [1/3] Android 13+ : allow the listener via the system command
adb shell cmd notification allow_listener site.zhizhang.app.debug/site.zhizhang.app.service.PaymentNotificationListener 2>nul
adb shell cmd notification allow_listener site.zhizhang.app/site.zhizhang.app.service.PaymentNotificationListener 2>nul

echo [2/3] Legacy fallback : grant WRITE_SECURE_SETTINGS
adb shell pm grant site.zhizhang.app.debug android.permission.WRITE_SECURE_SETTINGS 2>nul
adb shell pm grant site.zhizhang.app android.permission.WRITE_SECURE_SETTINGS 2>nul

echo [3/3] Launch the app
adb shell am start -n site.zhizhang.app.debug/site.zhizhang.app.MainActivity >nul 2>nul

echo.
echo [OK] Done. Back in the app, open "Wo de" and tap the top button
echo      to refresh the permission state. The home page should show
echo      that auto bookkeeping is running.
echo.
echo [!] If it still does not capture notifications:
echo      Android Settings ^> Notification access ^> enable Zhizhang manually.
echo      On Android 14+ and some vendor ROMs this manual step is required.
echo      Also allow autostart / unrestricted background battery usage.
echo.
echo [!] Turn USB debugging OFF when finished.
echo.
pause
