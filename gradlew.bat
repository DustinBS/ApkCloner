@echo off
SETLOCAL
set SCRIPT_DIR=%~dp0
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%gradlew.ps1" %*
exit /b %ERRORLEVEL%
