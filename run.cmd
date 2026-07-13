@echo off
pushd "%~dp0"
call "%~dp0mvn-graal.cmd" -pl desktop -am compile exec:java
set "EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %EXIT_CODE%
