@echo off
setlocal

set "JAVA_HOME=D:\Graal\graalvm-25.1.3+9.1"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo GraalVM wurde nicht unter "%JAVA_HOME%" gefunden. 1>&2
    echo Siehe README.md fuer die Installation. 1>&2
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
call mvn %*
exit /b %ERRORLEVEL%
