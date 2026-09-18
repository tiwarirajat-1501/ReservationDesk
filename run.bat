@echo off
if not exist out mkdir out
if not exist tickets mkdir tickets

:: Compile project
javac -cp "lib\*" -d out src\*.java src\exception\*.java src\model\*.java src\dao\*.java src\service\*.java src\view\*.java src\test\*.java
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Compilation failed! Check errors above.
    pause
    exit /b %ERRORLEVEL%
)

:: Run application
java -cp "out;lib\*" Main %*
