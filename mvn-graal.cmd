@echo off
setlocal

set "JAVA_HOME=D:\Graal\graalvm-25.1.3+9.1"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo GraalVM wurde nicht unter "%JAVA_HOME%" gefunden. 1>&2
    echo Siehe README.md fuer die Installation. 1>&2
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"

rem Truffle (GraalJS) benoetigt Native-Access und Unsafe. Ohne diese Flags warnt
rem das JDK, sobald die JavaScript-Engine initialisiert wird - bei exec:java
rem (run.cmd) laeuft die App in der Maven-JVM, daher hier ueber MAVEN_OPTS.
set "MAVEN_OPTS=--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow %MAVEN_OPTS%"

call mvn %*
exit /b %ERRORLEVEL%
