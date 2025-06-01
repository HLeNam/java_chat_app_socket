@echo off
echo Building Server Fat JAR with all dependencies...

:: Tạo thư mục build cho Server
if not exist "Server\build" mkdir "Server\build"
if not exist "Server\build\classes" mkdir "Server\build\classes"
if not exist "Server\build\lib-extracted" mkdir "Server\build\lib-extracted"

:: Compile Java files của Server
echo Compiling Server classes...

:: Tạo danh sách tất cả file .java trong Server\src
dir /s /b "Server\src\*.java" > "Server\build\sources.txt"

:: Compile sử dụng file list
javac -d "Server\build\classes" -cp "Server\lib\*" @"Server\build\sources.txt"

:: Kiểm tra có lỗi compile không
if %errorlevel% neq 0 (
    echo Error compiling Server!
    del "Server\build\sources.txt"
    pause
    exit /b 1
)

:: Xóa file tạm
del "Server\build\sources.txt"

:: Extract tất cả JAR dependencies vào thư mục tạm
echo Extracting dependencies...
if exist "Server\lib\*.jar" (
    for %%f in ("Server\lib\*.jar") do (
        echo Extracting %%f...
        cd "Server\build\lib-extracted"
        jar xf "%%~ff"
        :: Xóa META-INF để tránh conflict
        if exist "META-INF\MANIFEST.MF" del "META-INF\MANIFEST.MF"
        if exist "META-INF\*.SF" del /q "META-INF\*.SF" 2>nul
        if exist "META-INF\*.DSA" del /q "META-INF\*.DSA" 2>nul
        if exist "META-INF\*.RSA" del /q "META-INF\*.RSA" 2>nul
        cd "..\..\..\"
    )
) else (
    echo No dependencies found in Server\lib\
)

:: Copy compiled classes vào thư mục lib-extracted
echo Merging compiled classes with dependencies...
if exist "Server\build\classes\*" (
    xcopy /s /e /y "Server\build\classes\*" "Server\build\lib-extracted\"
) else (
    echo No compiled classes found!
    pause
    exit /b 1
)

:: Tạo manifest file cho Fat JAR
echo Creating manifest...
echo Main-Class: Main > "Server\build\MANIFEST.MF"
echo. >> "Server\build\MANIFEST.MF"

:: Tạo Fat JAR
echo Creating Fat JAR...
cd "Server\build\lib-extracted"
if exist "..\MANIFEST.MF" (
    jar cfm "..\..\ChatServer-fat.jar" "..\MANIFEST.MF" .
) else (
    echo Manifest file not found!
    cd "..\..\..\"
    pause
    exit /b 1
)
cd "..\..\..\"

:: Cleanup
echo Cleaning up temporary files...
if exist "Server\build\lib-extracted" rmdir /s /q "Server\build\lib-extracted"
if exist "Server\build\classes" rmdir /s /q "Server\build\classes"
if exist "Server\build\MANIFEST.MF" del "Server\build\MANIFEST.MF"

echo Server Fat JAR created successfully: Server\ChatServer-fat.jar