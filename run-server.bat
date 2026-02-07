@echo off
REM Run the UDP Server

echo ===================================
echo  Starting UDP Server
echo ===================================
echo.

cd /d "%~dp0"

java -cp src Server

pause
