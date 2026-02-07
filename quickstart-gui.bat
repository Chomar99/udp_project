@echo off
REM Quick start script with GUI - opens server and 3 GUI clients

echo ===================================
echo  Quick Start - UDP Messaging (GUI)
echo ===================================
echo.
echo This will open:
echo  - 1 Server window
echo  - 3 GUI Client windows (Alice, Bob, Charlie)
echo.

cd /d "%~dp0"

echo Compiling...
javac src\*.java

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Compilation failed!
    pause
    exit /b 1
)

echo.
echo Starting server and GUI clients...
echo.

REM Start server
start "UDP Server" cmd /k "java -cp src Server"

REM Wait for server to start
timeout /t 2 /nobreak > nul

REM Start GUI clients
start "GUI Client - Alice" cmd /c "java -cp src ClientGUI Alice"
timeout /t 1 /nobreak > nul

start "GUI Client - Bob" cmd /c "java -cp src ClientGUI Bob"
timeout /t 1 /nobreak > nul

start "GUI Client - Charlie" cmd /c "java -cp src ClientGUI Charlie"

echo.
echo ===================================
echo  All windows opened!
echo ===================================
echo.
echo Server and GUI clients are now running.
echo You can close this window.
echo.

pause
