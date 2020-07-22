@echo off
SETLOCAL ENABLEEXTENSIONS ENABLEDELAYEDEXPANSION

rem The Java version to install when it's not installed on the system yet
set javaVersion=11

set os=windows
set arch=x64

set url="https://api.adoptopenjdk.net/v3/binary/latest/%javaVersion%/ga/%os%/%arch%/jdk/hotspot/normal/adoptopenjdk"

rem resolve application jar path from script location and convert to windows path when using cygwin
set jarPath=%~dp0jbang.jar

rem expose the name of the script being run to the script itself
set JBANG_FILE="$1"

rem clear OUTPUT to be sure not getting affected by other setting OUTPUT
set OUTPUT=

rem create TDIR based on jbang_dir in case it is missing to have a folder
rem we need to be able to write to anyway
if "%JBANG_DIR%"=="" (set JBDIR=%userprofile%\.jbang) else (set JBDIR=%JBANG_DIR%)
if "%JBANG_CACHE_DIR%"=="" (set TDIR=%JBDIR%\cache) else (set TDIR=%JBANG_CACHE_DIR%)

if not exist "%TDIR%\jdks" ( mkdir "%TDIR%\jdks" )

rem prefer JAVA instead of PATH to resolve `java` location
rem if [[ -z "$JAVA_HOME" ]]; then JAVA_EXEC="java"; else JAVA_EXEC="$JAVA_HOME/bin/java"; fi
if "%JAVA_HOME%"=="" (
  where javac 2>&1 > nul
  if %errorlevel% equ 0 (
    set JAVA_EXEC=java.exe
  ) else (
    if not exist "%TDIR%\jdks\%javaVersion%" (
      echo Downloading JDK %javaVersion%...
      powershell -NonInteractive -Command "$ProgressPreference = 'SilentlyContinue'; Invoke-WebRequest %url% -OutFile %TDIR%\bootstrap-jdk.zip"
      echo Installing JDK %javaVersion%...
      if exist "%TDIR%\jdks\%javaVersion%.tmp" ( rd /s /q "%TDIR%\jdks\%javaVersion%.tmp" 2>&1 > nul )
      powershell -NonInteractive -Command "$ProgressPreference = 'SilentlyContinue'; Expand-Archive -Path %TDIR%\bootstrap-jdk.zip -DestinationPath %TDIR%\jdks\%javaVersion%.tmp"
	  for /d %%d in (%TDIR%\jdks\%javaVersion%.tmp\*) do (
        powershell -NonInteractive -Command "Move-Item %%d\* !TDIR!\jdks\%javaVersion%.tmp"
	  )
      ren "%TDIR%\jdks\%javaVersion%.tmp" "%javaVersion%"
    )
    set JAVA_HOME=%TDIR%\jdks\%javaVersion%
    set JAVA_EXEC=!JAVA_HOME!\bin\java.exe
  )
) else (
  set JAVA_EXEC="%JAVA_HOME%\bin\java.exe"
)

set tmpfile=%TDIR%\%RANDOM%.tmp
rem execute jbang and pipe to temporary random file
!JAVA_EXEC! > "%tmpfile%" %JBANG_JAVA_OPTIONS% -classpath "%jarPath%" dev.jbang.Main %*
set ERROR=%ERRORLEVEL%
rem catch errorlevel straight after; rem or FOR /F swallow would have swallowed the errorlevel

if %ERROR% NEQ 0 (
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