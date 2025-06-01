@echo off
echo Building Client Fat JAR with resources in src...

:: Tạo thư mục build cho Client
if not exist "Client\build" mkdir "Client\build"
if not exist "Client\build\classes" mkdir "Client\build\classes"
if not exist "Client\build\lib-extracted" mkdir "Client\build\lib-extracted"

:: Xác định cấu trúc thư mục src
echo Detecting source structure...
if exist "Client\src\main\java" (
    echo Using Maven-style structure: src/main/java
    set "SRC_DIR=Client\src\main\java"
) else (
    echo Using simple structure: src/
    set "SRC_DIR=Client\src"
)

:: Detect resources location
if exist "Client\src\resources" (
    echo Resources directory found in: src/resources
    set "RES_DIR=Client\src\resources"
) else if exist "Client\src\main\resources" (
    echo Resources directory found in: src/main/resources
    set "RES_DIR=Client\src\main\resources"
) else (
    echo WARNING: No resources directory found! Creating one...
    mkdir "Client\src\resources\sounds" 2>nul
    set "RES_DIR=Client\src\resources"
    echo Please put your sound files in %RES_DIR%\sounds\
)

:: Compile Java files của Client
echo Compiling Client classes from %SRC_DIR%...
dir /s /b "%SRC_DIR%\*.java" > "Client\build\sources.txt"
javac -d "Client\build\classes" -cp "Client\lib\*" @"Client\build\sources.txt"
if %errorlevel% neq 0 (
    echo Error compiling Client!
    del "Client\build\sources.txt"
    pause
    exit /b 1
)
del "Client\build\sources.txt"

:: Copy resources từ src/resources vào classes
echo Copying resources from %RES_DIR%...
if exist "%RES_DIR%" (
    echo Found resources directory, copying...
    
    :: Copy thư mục sounds trực tiếp vào thư mục gốc classes
    if exist "%RES_DIR%\sounds" (
        echo Copying sounds directory to classpath root...
        xcopy /s /e /y "%RES_DIR%\sounds\*" "Client\build\classes\sounds\"
    )
    
    :: Verify copy
    echo.
    echo Verifying resources copy:
    if exist "Client\build\classes\sounds" (
        dir "Client\build\classes\sounds\"
        echo Resources copied successfully!
    ) else (
        echo WARNING: Resources not copied correctly!
        echo Checking for sounds in %RES_DIR%...
        dir "%RES_DIR%\sounds\" 2>nul
    )
    echo.
) else (
    echo ERROR: Resources directory not found!
    pause
    exit /b 1
)

:: Extract dependencies
echo Extracting dependencies...
if exist "Client\lib\*.jar" (
    for %%f in ("Client\lib\*.jar") do (
        echo Extracting %%f...
        cd "Client\build\lib-extracted"
        jar xf "%%~ff"
        if exist "META-INF\MANIFEST.MF" del "META-INF\MANIFEST.MF"
        if exist "META-INF\*.SF" del /q "META-INF\*.SF" 2>nul
        if exist "META-INF\*.DSA" del /q "META-INF\*.DSA" 2>nul
        if exist "META-INF\*.RSA" del /q "META-INF\*.RSA" 2>nul
        cd "..\..\..\"
    )
)

:: Copy compiled classes và resources
echo Merging compiled classes and resources...
xcopy /s /e /y "Client\build\classes\*" "Client\build\lib-extracted\"

:: Tạo manifest
echo Creating manifest...
if exist "%SRC_DIR%\Main.java" (
    echo Main-Class: Main > "Client\build\MANIFEST.MF"
) else if exist "%SRC_DIR%\client\ChatClient.java" (
    echo Main-Class: client.ChatClient > "Client\build\MANIFEST.MF"
) else if exist "%SRC_DIR%\ChatClient.java" (
    echo Main-Class: ChatClient > "Client\build\MANIFEST.MF"
) else (
    echo Main-Class: Main > "Client\build\MANIFEST.MF"
)
echo. >> "Client\build\MANIFEST.MF"

:: Tạo JAR
echo Creating Fat JAR...
cd "Client\build\lib-extracted"

:: Debug: List contents before creating JAR
echo.
echo Contents before JAR creation:
dir /b
echo.
if exist "sounds" (
    echo Sounds directory contents:
    dir "sounds\"
    echo Sounds ready for JAR packaging!
) else (
    echo WARNING: No sounds directory found in JAR root!
)
echo.

jar cfm "..\..\ChatClient-fat.jar" "..\MANIFEST.MF" .
cd "..\..\..\"

:: Kiểm tra kỹ JAR cuối cùng
echo.
echo === KIỂM TRA FINAL JAR ===
echo.

:: Kiểm tra thư mục sounds có trong JAR không
jar tf "Client\ChatClient-fat.jar" | findstr /i /c:"sounds/" > nul
if %errorlevel% equ 0 (
    echo ✅ Thư mục sounds có trong JAR!
) else (
    echo ❌ KHÔNG TÌM THẤY thư mục sounds trong JAR!
)

:: Liệt kê các file âm thanh trong JAR
echo.
echo Sound files in JAR:
jar tf "Client\ChatClient-fat.jar" | findstr /i /c:".wav"
echo.

:: SỬA PHẦN NÀY - Kiểm tra một file cụ thể (ví dụ: calling.wav) và giải nén để xác nhận
echo Testing sound file accessibility...

:: Xóa file cũ nếu còn sót lại
if exist "sounds\" rmdir /s /q "sounds\"

:: Thử giải nén file âm thanh từ JAR
jar xf "Client\ChatClient-fat.jar" sounds/calling.wav

:: Kiểm tra xem file có được giải nén thành công không
if exist "sounds\calling.wav" (
    echo ✅ SUCCESS: Đã giải nén thành công file âm thanh!
    echo Đường dẫn chính xác: sounds/calling.wav
    echo Vui lòng gọi trong code Java: soundPlayer.playSound("sounds/calling.wav", true);
    
    :: Dọn dẹp file đã giải nén
    rmdir /s /q "sounds\"
) else (
    echo ❌ ERROR: Không thể giải nén file âm thanh từ JAR.
    echo Vui lòng kiểm tra lại cấu trúc JAR.
)

:: Cleanup
echo Cleaning up...
if exist "Client\build\lib-extracted" rmdir /s /q "Client\build\lib-extracted"
if exist "Client\build\classes" rmdir /s /q "Client\build\classes"
if exist "Client\build\MANIFEST.MF" del "Client\build\MANIFEST.MF"

echo.
echo ================================================
echo Client Fat JAR created successfully!
echo ================================================
echo JAR file: Client\ChatClient-fat.jar
echo ================================================
echo.

pause