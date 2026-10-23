@echo off
cd /d "%~dp0"
if not exist build mkdir build
javac -d build *.java
exit /b %errorlevel%
