@echo off
chcp 65001 >nul
cd /d "%~dp0"
set PY=python

:menu
cls
echo ============================================================
echo   钱花在哪 - GitHub 版本发布（本地改动不会自动上传）
echo ============================================================
echo.
echo   1  查看状态        云端版本 / 本地 HEAD / 未提交改动 / 差异   [只读]
echo   2  列出云端版本    看有哪些可回滚的版本                        [只读]
echo   3  查看差异        列出本地与云端不同的文件                    [只读]
echo   4  演练发布        导出检查 + 隐私复查，不写入云端              [只读]
echo   5  发布新版本      把「当前已提交的版本」发布为 vN
echo   6  回滚            恢复到指定版本（安全模式，保留历史）
echo   7  校验凭据        检查 GitHub 登录是否可用                    [只读]
echo   0  退出
echo.
set /p c=请输入数字后回车：
echo.

if "%c%"=="1" %PY% publish-github.py --status
if "%c%"=="2" %PY% publish-github.py --list
if "%c%"=="3" %PY% publish-github.py --diff
if "%c%"=="4" %PY% publish-github.py --dry-run
if "%c%"=="7" %PY% publish-github.py --check
if "%c%"=="5" goto publish
if "%c%"=="6" goto rollback
if "%c%"=="0" exit /b 0
echo.
echo 按任意键返回菜单...
pause >nul
goto menu

:publish
set /p n=版本说明（可留空，直接回车）：
echo.
echo 提醒：只发布已经 git commit 的内容，未提交的改动不会上传。
%PY% publish-github.py --publish --note "%n%"
echo.
echo 按任意键返回菜单...
pause >nul
goto menu

:rollback
%PY% publish-github.py --list
echo.
set /p v=输入要恢复到的版本号（例如 v1）：
echo.
%PY% publish-github.py --rollback %v%
echo.
echo 按任意键返回菜单...
pause >nul
goto menu
