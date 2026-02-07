@echo off
REM Compile all Java files

echo ===================================
echo  Compiling UDP Messaging System
echo ===================================
echo.

cd /d "%~dp0"

echo Compiling Java files...
javac src\*.java

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ===================================
    echo  Compilation Successful!
    echo ===================================
    echo.
    echo You can now run:
    echo   - run-server.bat    (Start the server)
    echo   - run-client.bat    (Start a client)
    echo.
) else (
    echo.
    echo ===================================
    echo  Compilation Failed!
    echo ===================================
    echo Please check the error messages above.
    echo.
)

pause
