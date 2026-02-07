@echo off
REM Run a UDP Client

echo ===================================
echo  Starting UDP Client
echo ===================================
echo.

cd /d "%~dp0"

set /p clientname="Enter your client name (or press Enter for random): "

if "%clientname%"=="" (
    java -cp src Client
) else (
    java -cp src Client %clientname%
)

pause
