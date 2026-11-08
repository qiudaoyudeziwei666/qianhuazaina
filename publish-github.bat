@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ============================================================
echo  钱花在哪 - 发布到 GitHub 公开仓库
echo ============================================================
echo.
if not exist ".github-token" (
  if "%GITHUB_TOKEN%"=="" (
    echo [缺少凭据] 请先把 Personal Access Token 保存为本目录下的
    echo   .github-token 文件（纯文本，只放 token 一行），再双击本脚本。
    echo   该文件已在 .gitignore 中，不会被提交或上传。
    echo.
    pause
    exit /b 1
  )
)
echo [1/2] 先做隐私复查与快照演练...
python publish-github.py --dry-run
if errorlevel 1 goto fail
echo.
echo [2/2] 创建公开仓库并上传...
python publish-github.py %*
if errorlevel 1 goto fail
echo.
echo 完成。按任意键关闭。
pause >nul
exit /b 0
:fail
echo.
echo 发布失败，请按上面的错误信息处理。按任意键关闭。
pause >nul
exit /b 1
