@echo off
setlocal
if "%~1"=="" (
  echo Usage: inspect.cmd URL [report.json]
  exit /b 2
)
call "%~dp0mvn-graal.cmd" -q -pl browser-cli -am package -DskipTests
if errorlevel 1 exit /b %errorlevel%
if "%~2"=="" (
  "D:\Graal\graalvm-25.1.3+9.1\bin\java.exe" --sun-misc-unsafe-memory-access=allow -jar "%~dp0browser-cli\target\browicy-inspect.jar" "%~1"
) else (
  "D:\Graal\graalvm-25.1.3+9.1\bin\java.exe" --sun-misc-unsafe-memory-access=allow -jar "%~dp0browser-cli\target\browicy-inspect.jar" "%~1" --output "%~2"
)
