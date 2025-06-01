@echo off
title Building Java Chat Application
color 0A

echo ========================================
echo    Java Chat Application Builder
echo ========================================
echo Built by: HLeNam
echo Build Time: %date% %time%
echo ========================================
echo.

:: Kiểm tra Java có sẵn không
echo [1/4] Checking Java installation...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java not found! Please install Java JDK and add to PATH.
    pause
    exit /b 1
)
echo [OK] Java found.
echo.

:: Kiểm tra các file script có tồn tại không
echo [2/4] Checking build scripts...
if not exist "build_server.bat" (
    echo [ERROR] build_server.bat not found!
    pause
    exit /b 1
)
if not exist "build_client.bat" (
    echo [ERROR] build_client.bat not found!
    pause
    exit /b 1
)
echo [OK] Build scripts found.
echo.

:: Build Server
echo [3/4] Building Server Fat JAR...
echo ----------------------------------------
call build_server.bat
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Failed to build Server!
    echo Check the error messages above.
    pause
    exit /b 1
)
echo [OK] Server build completed.
echo.

:: Build Client  
echo [4/4] Building Client Fat JAR...
echo ----------------------------------------
call build_client.bat
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Failed to build Client!
    echo Check the error messages above.
    pause
    exit /b 1
)
echo [OK] Client build completed.
echo.

:: Kiểm tra JAR files đã được tạo chưa
echo ========================================
echo Verifying build results...
echo ========================================

set SERVER_JAR=Server\ChatServer-fat.jar
set CLIENT_JAR=Client\ChatClient-fat.jar

if exist "%SERVER_JAR%" (
    echo [OK] %SERVER_JAR% - Created successfully
    for %%A in ("%SERVER_JAR%") do echo     Size: %%~zA bytes
) else (
    echo [ERROR] %SERVER_JAR% - Not found!
    set BUILD_ERROR=1
)

if exist "%CLIENT_JAR%" (
    echo [OK] %CLIENT_JAR% - Created successfully
    for %%A in ("%CLIENT_JAR%") do echo     Size: %%~zA bytes
) else (
    echo [ERROR] %CLIENT_JAR% - Not found!
    set BUILD_ERROR=1
)

echo.

if defined BUILD_ERROR (
    echo ========================================
    echo BUILD FAILED!
    echo ========================================
    pause
    exit /b 1
)

:: Thành công
color 0F
echo ========================================
echo BUILD COMPLETED SUCCESSFULLY!
echo ========================================
echo.
echo Output Files:
echo   Server: %SERVER_JAR%
echo   Client: %CLIENT_JAR%
echo.
echo How to run:
echo   Start Server: java -jar %SERVER_JAR%
echo   Start Client: java -jar %CLIENT_JAR%
echo.
echo Notes:
echo - These are Fat JARs containing all dependencies
echo - No additional libraries needed
echo - Can be distributed independently
echo.

:: Tùy chọn chạy luôn
echo ========================================
echo Quick Actions:
echo ========================================
echo [1] Run Server now
echo [2] Run Client now  
echo [3] Run both (Server first, then Client)
echo [4] Exit
echo.
set /p choice="Enter your choice (1-4): "

if "%choice%"=="1" (
    echo Starting Server...
    start "Chat Server" java -jar %SERVER_JAR%
) else if "%choice%"=="2" (
    echo Starting Client...
    start "Chat Client" java -jar %CLIENT_JAR%
) else if "%choice%"=="3" (
    echo Starting Server...
    start "Chat Server" java -jar %SERVER_JAR%
    timeout /t 3 /nobreak > nul
    echo Starting Client...
    start "Chat Client" java -jar %CLIENT_JAR%
) else if "%choice%"=="4" (
    echo Goodbye!
) else (
    echo Invalid choice. Exiting...
)

echo.
echo Build completed at %date% %time%
pause