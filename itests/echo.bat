@echo off
setlocal
echo ARGS = %*
:loop
set _arg=%~1
if "%_arg%" == "" goto loopend
echo ARG = %_arg%
shift
goto loop
:loopend
