@echo off

rem resolve application jar path from script location and convert to windows path when using cygwin
set jarPath=%~dp0\jbang.jar

rem prefer JAVA instead of PATH to resolve `java` location
rem if [[ -z "$JAVA_HOME" ]]; then JAVA_EXEC="java"; else JAVA_EXEC="$JAVA_HOME/bin/java"; fi

rem expose the name of the script being run to the script itself
set JBANG_FILE="$1"

rem run it using command substitution to have just the user process once jbang is done
rem eval "exec $(${JAVA_EXEC} -classpath ${jarPath} dk.xam.jbang.Main "$@")"
FOR /F "tokens=*" %%a in ('java %JBANG_JAVA_OPTIONS% -classpath %jarPath% dk.xam.jbang.Main %*') do SET OUTPUT=%%a
%OUTPUT%
