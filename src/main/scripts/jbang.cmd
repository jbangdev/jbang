@echo off
SETLOCAL 

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

rem run it using command substitution to have just the user process once jbang is done
rem eval "exec $(${JAVA_EXEC} -classpath ${jarPath} dev.jbang.Main "$@")"
FOR /F "tokens=*" %%a in ('%JAVA_EXEC% %JBANG_JAVA_OPTIONS% -classpath %jarPath% dev.jbang.Main %*') do SET OUTPUT=%%a
IF %ERRORLEVEL% NEQ 0 EXIT /B %ERRORLEVEL%
%OUTPUT%