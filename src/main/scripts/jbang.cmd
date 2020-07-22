@echo off
SETLOCAL ENABLEEXTENSIONS

rem The Java version to install when it's not installed on the system yet
set javaVersion=11
set os=windows
set arch=x64

rem resolve application jar path from script location and convert to windows path when using cygwin
set jarPath=%~dp0jbang.jar

rem expose the name of the script being run to the script itself
set JBANG_FILE="$1"

rem clear OUTPUT to be sure not getting affected by other setting OUTPUT
set OUTPUT=

rem create TDIR based on jbang_dir in case it is missing to have a folder
rem we need to be able to write to anyway
IF "%JBANG_DIR%"=="" (set JBDIR=%userprofile%\.jbang) ELSE (set JBDIR=%JBANG_DIR%)
IF "%JBANG_CACHE_DIR%"=="" (set TDIR=%JBDIR%\cache) ELSE (set TDIR=%JBANG_CACHE_DIR%)

IF NOT EXIST "%TDIR%\jdks" ( mkdir "%TDIR%\jdks" )

rem prefer JAVA instead of PATH to resolve `java` location
rem if [[ -z "$JAVA_HOME" ]]; then JAVA_EXEC="java"; else JAVA_EXEC="$JAVA_HOME/bin/java"; fi
if "%JAVA_HOME%"=="" (
  where javac
  if %errorlevel% neq 0 (
    set JAVA_EXEC=java.exe
  ) else (
    IF NOT EXIST "%TDIR%\jdks\%javaVersion%" (
      echo "Downloading JDK %javaVersion%..."
      set url=https://api.adoptopenjdk.net/v3/binary/latest/%javaVersion%/ga/%os%/%arch%/jdk/hotspot/normal/adoptopenjdk
      powershell -Command "Invoke-WebRequest %url%" -OutFile "%TDIR%\bootstrap-jdk.zip" -Force
      echo "Installing JDK %javaVersion%..."
      del /s /q "%TDIR%\jdks\%javaVersion%.tmp"
      mkdir "%TDIR%\jdks\%javaVersion%.tmp"
      powershell -Command "Expand-Archive" "%TDIR%\bootstrap-jdk.zip" "%TDIR%\jdks\%javaVersion%.tmp"
      rem TODO move contents of unpacked contents one folder up
      ren "%TDIR%\jdks\%javaVersion%.tmp" "%TDIR%\jdks\%javaVersion%"
    )
    set JAVA_HOME="%TDIR%\jdks\%javaVersion%"
    set JAVA_EXEC="%JAVA_HOME%\bin\java.exe"
  )
) else (
  set JAVA_EXEC="%JAVA_HOME%\bin\java.exe"
)

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