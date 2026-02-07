@echo off
REM Quick start script - opens server and 3 clients in separate windows

echo ===================================
echo  Quick Start - UDP Messaging System
echo ===================================
echo.
echo This will open:
echo  - 1 Server window
echo  - 3 Client windows (Alice, Bob, Charlie)
echo.

cd /d "%~dp0"

echo Compiling...
call compile.bat

echo.
echo Starting server and clients...
echo.

REM Start server
start "UDP Server" cmd /k "java -cp src Server"

REM Wait a bit for server to start
timeout /t 2 /nobreak > nul

REM Start clients
start "Client - Alice" cmd /k "java -cp src Client Alice"
timeout /t 1 /nobreak > nul

start "Client - Bob" cmd /k "java -cp src Client Bob"
timeout /t 1 /nobreak > nul

start "Client - Charlie" cmd /k "java -cp src Client Charlie"

echo.
echo ===================================
echo  All windows opened!
echo ===================================
echo.
echo Server and clients are now running in separate windows.
echo You can close this window.
echo.

pause
