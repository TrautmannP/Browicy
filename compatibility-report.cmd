@echo off
setlocal
call "%~dp0mvn-graal.cmd" -Pcompatibility-report -pl acid3-tests -am verify %*
exit /b %errorlevel%
