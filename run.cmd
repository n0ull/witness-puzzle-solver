@echo off
cd /d "%~dp0"
call build.cmd
if errorlevel 1 exit /b %errorlevel%
if not "%~1"=="" (
    java -Xss64m -cp build Solver %*
) else (
    java -Xss64m -cp build Solver "STEAM\\SCREENSHOT\\DIRECTORY\\" "OUTPUT\\DIRECTORY\\"
)
