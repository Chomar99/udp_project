@echo off
REM Run GUI Client

echo ===================================
echo  Starting UDP GUI Client
echo ===================================
echo.

cd /d "%~dp0"

set /p clientname="Enter your username (or press Enter to be prompted): "

if "%clientname%"=="" (
    java -cp src ClientGUI
) else (
    java -cp src ClientGUI %clientname%
)

pause
