@echo off
setlocal
rem  MuMu startup-ad switch. No admin rights, no hosts edit, no file deletion.
rem  Usage:
rem    double-click              -> disable startup ads (default)
rem    mumu-splash-ad.bat restore -> restore the original ad list
rem    add /quiet as 2nd arg     -> no pause (for scripting)

set "FILE=%APPDATA%\Netease\MuMuPlayer\data\startupImage\imageManager.json"
set "BAK=%FILE%.zhizhang-bak"
set "ACTION=%~1"
set "QUIET=%~2"

if /i "%ACTION%"=="restore" goto RESTORE

:DISABLE
if not exist "%FILE%" (
  echo [!] File not found: %FILE%
  echo     Launch MuMu at least once, then run this script again.
  goto DONE
)
if not exist "%BAK%" copy /Y "%FILE%" "%BAK%" >nul 2>&1
attrib -R "%FILE%" >nul 2>&1
echo {"info":[]}>"%FILE%"
attrib +R "%FILE%" >nul 2>&1
echo [OK] MuMu startup ads disabled.
echo [*] Backup saved to: %BAK%
echo [*] Restart MuMu to take effect. Undo with: %~nx0 restore
goto DONE

:RESTORE
if not exist "%BAK%" (
  echo [!] Backup not found: %BAK%
  echo     Nothing to restore.
  goto DONE
)
attrib -R "%FILE%" >nul 2>&1
copy /Y "%BAK%" "%FILE%" >nul 2>&1
echo [OK] Original startup ad list restored. Restart MuMu.
goto DONE

:DONE
if /i "%QUIET%"=="/quiet" exit /b 0
echo.
pause
exit /b 0
