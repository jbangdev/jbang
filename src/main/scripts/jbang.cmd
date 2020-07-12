@echo off
SETLOCAL ENABLEEXTENSIONS

rem resolve application jar path from script location and convert to windows path when using cygwin
set jarPath=%~dp0jbang.jar

rem prefer JAVA instead of PATH to resolve `java` location
rem if [[ -z "$JAVA_HOME" ]]; then JAVA_EXEC="java"; else JAVA_EXEC="$JAVA_HOME/bin/java"; fi
if "%JAVA_HOME%"=="" (
  set JAVA_EXEC=java.exe
) else (
  set JAVA_EXEC="%JAVA_HOME%\bin\java.exe"
)

rem expose the name of the script being run to the script itself
set JBANG_FILE="$1"

rem clear OUTPUT to be sure not getting affected by other setting OUTPUT
set OUTPUT=

rem create TDIR based on jbang_dir in case it is missing to have a folder
rem we need to be able to write to anyway
IF "%JBANG_DIR%"=="" (set TDIR=%userprofile%\.jbang) ELSE (set TDIR=%JBANG_DIR%\.jbang)

IF NOT EXIST "%TDIR%" ( mkdir "%TDIR%")

set tmpfile=%TDIR%\%RANDOM%.tmp
rem execute jbang and pipe to temporary random file
%JAVA_EXEC% > "%tmpfile%" %JBANG_JAVA_OPTIONS% -classpath "%jarPath%" dev.jbang.Main %*
set ERROR=%ERRORLEVEL%
rem catch errorlevel straight after; rem or FOR /F swallow would have swallowed the errorlevel

IF %ERROR% NEQ 0 (

    del ""%tmpfile%""
    exit /b %ERROR%
)

rem read generated java command by jang, delete temporary file and execute.
for %%A in ("%tmpfile%") do for /f "usebackq delims=" %%B in (%%A) do (
  set "OUTPUT=%%B"
  goto :break
)

:break
del "%tmpfile%"
%OUTPUT%
exit /b %ERRORLEVEL%