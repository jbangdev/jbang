@echo off
SETLOCAL ENABLEEXTENSIONS ENABLEDELAYEDEXPANSION

rem The Java version to install when it's not installed on the system yet
if "%JBANG_DEFAULT_JAVA_VERSION%"=="" (set javaVersion=17) else (set javaVersion=%JBANG_DEFAULT_JAVA_VERSION%)

if "%JBANG_DIR%"=="" (set JBDIR=%userprofile%\.jbang) else (set JBDIR=%JBANG_DIR%)
if "%JBANG_CACHE_DIR%"=="" (set TDIR=%JBDIR%\cache) else (set TDIR=%JBANG_CACHE_DIR%)
if "%JBANG_USE_NATIVE%"=="" (set JBANG_USE_NATIVE=false)

rem detect architecture for platform-specific binary lookup
set "jbang_arch=x64"
if "%PROCESSOR_ARCHITECTURE%"=="ARM64" set "jbang_arch=aarch64"

rem apply a staged native binary update; move keeps .new if the exe is still
rem locked (e.g. another jbang running) so it is retried on the next run
if exist "%~dp0jbang.bin-windows-%jbang_arch%.exe.new" (
  move /y "%~dp0jbang.bin-windows-%jbang_arch%.exe.new" "%~dp0jbang.bin-windows-%jbang_arch%.exe" > nul 2>&1
)

rem resolve native binary or jar path from script location
set binaryPath=
set jarPath=
if "%JBANG_USE_NATIVE%"=="true" (
  rem Look for platform-specific native binary first, then fall back to jbang.bin.exe
  if exist "%~dp0jbang.bin-windows-%jbang_arch%.exe" (
    set binaryPath=%~dp0jbang.bin-windows-%jbang_arch%.exe
  ) else if exist "%~dp0jbang.bin.exe" (
    set binaryPath=%~dp0jbang.bin.exe
  ) else (
    echo WARNING: JBang native binary (jbang.bin-windows-%jbang_arch%.exe or jbang.bin.exe^) not found in %~dp0 1>&2
  )
)
if "!binaryPath!"=="" (
  rem Fall back to JAR if no native binary found or native binary disabled
  if exist "%~dp0jbang.jar" (
    set jarPath=%~dp0jbang.jar
  ) else if exist "%~dp0.jbang\jbang.jar" (
    set jarPath=%~dp0.jbang\jbang.jar
  )
)
if "!binaryPath!"=="" if "!jarPath!"=="" (
  if not exist "%JBDIR%\bin\jbang.jar" (
    powershell -NoProfile -ExecutionPolicy Bypass -NonInteractive -Command "%~dp0jbang.ps1 version" > nul
    if !ERRORLEVEL! NEQ 0 ( exit /b %ERRORLEVEL% )
  )
  call "%JBDIR%\bin\jbang.cmd" %*
  exit /b %ERRORLEVEL%
)

if not "!binaryPath!"=="" goto :run_with_cli
if not exist "%jarPath%" goto :run_with_cli

if exist "%jarPath%.new" (
  rem a new jbang version was found, we replace the old one with it; move
  rem keeps .new if the jar is still locked so it is retried on the next run
  move /y "%jarPath%.new" "%jarPath%" > nul 2>&1
)

rem Find/get a JDK (only needed for JAR execution)
set JAVA_EXEC=
if not "%JAVA_HOME%"=="" (
  rem Determine if a (working) JDK is available in JAVA_HOME
  if exist "%JAVA_HOME%\bin\javac.exe" (
    set JAVA_EXEC=%JAVA_HOME%\bin\java.exe
  ) else (
    echo JAVA_HOME is set but does not seem to point to a valid Java JDK 1>&2
  )
)
if "!JAVA_EXEC!"=="" (
  rem Determine if a (working) JDK is available on the PATH
  where javac > nul 2>&1
  if !errorlevel! equ 0 (
    set JAVA_HOME=
    set JAVA_EXEC=java.exe
  ) else if exist "%JBDIR%\currentjdk\bin\javac" (
    set JAVA_HOME=%JBDIR%\currentjdk
    set JAVA_EXEC=%JBDIR%\currentjdk\bin\java
  ) else (
    set JAVA_HOME=%TDIR%\jdks\%javaVersion%
    set JAVA_EXEC=!JAVA_HOME!\bin\java.exe
    rem Check if we installed a JDK before
    if not exist "%TDIR%\jdks\%javaVersion%" (
      rem If not, download and install it
      powershell -NoProfile -ExecutionPolicy Bypass -NonInteractive -Command "%~dp0jbang.ps1 jdk install %JBANG_DEFAULT_JAVA_VERSION%"
      if !ERRORLEVEL! NEQ 0 ( exit /b %ERRORLEVEL% )
      rem Set the current JDK
      "!JAVA_EXEC!" -jar "%jarPath%" jdk default "%javaVersion%"
    )
  )
)

rem Setup environment for execution
set JBANG_RUNTIME_SHELL=cmd
set JBANG_LAUNCH_CMD=%~f0
rem tell jbang whether stdin is a tty or not
2>nul >nul timeout /t 0 && (set JBANG_STDIN_NOTTY=false) || (set JBANG_STDIN_NOTTY=true)

rem execute jbang, it writes the command to execute (exit code 255) to a temporary
rem file instead of stdout so interactive commands keep their console
call :make_exec_dir

set CMD=!JAVA_EXEC!
SETLOCAL DISABLEDELAYEDEXPANSION
"%CMD%" %JBANG_JAVA_OPTIONS% -jar "%jarPath%" %*
set ERROR=%ERRORLEVEL%
goto :onwards

:run_with_cli
rem Setup environment for execution
set JBANG_RUNTIME_SHELL=cmd
set JBANG_LAUNCH_CMD=%~f0
rem tell jbang whether stdin is a tty or not
2>nul >nul timeout /t 0 && (set JBANG_STDIN_NOTTY=false) || (set JBANG_STDIN_NOTTY=true)

rem execute jbang, it writes the command to execute (exit code 255) to a temporary file
call :make_exec_dir

SETLOCAL DISABLEDELAYEDEXPANSION
"%binaryPath%" %*
set ERROR=%ERRORLEVEL%

:onwards
set JBANG_EXECUTE_FILE=

rem catch errorlevel straight after; rem or FOR /F swallow would have swallowed the errorlevel

if %ERROR% EQU 255 (
  rem read generated java command by jang, delete temporary file and execute.
  for %%A in ("%execfile%") do for /f "usebackq delims=" %%B in (%%A) do (
    set "OUTPUT=%%B"
    goto :break
  )
:break
  if defined execdir rmdir /s /q "%execdir%" 2>nul
  %OUTPUT%
  exit /b %ERRORLEVEL%
) else (
  if defined execdir rmdir /s /q "%execdir%" 2>nul
  exit /b %ERROR%
)

rem Creates a directory of our own for jbang's temporary file. cmd processes
rem started in the same second get the same %RANDOM% values, so we can't rely on
rem random names, but mkdir fails if the directory exists so only one jbang can
rem claim each name and the others try the next one.
:make_exec_dir
if not exist "%TDIR%" mkdir "%TDIR%" 2>nul
call :claim_exec_dir "%TDIR%"
if not defined execdir call :claim_exec_dir "%TEMP%"
if defined execdir goto :make_exec_dir_done
rem Neither is writable. Use a path that doesn't exist so commands that need it
rem fail with an error and the rest still work.
set "execfile=%TDIR%\exec-unavailable\cmd"
set "JBANG_EXECUTE_FILE=%execfile%"
goto :eof
:make_exec_dir_done
set "execfile=%execdir%\cmd"
set "JBANG_EXECUTE_FILE=%execfile%"
goto :eof

:claim_exec_dir
set /a exectries=0
:claim_exec_dir_retry
set /a exectries+=1
set "execdir=%~1\exec-%RANDOM%"
mkdir "%execdir%" 2>nul && goto :eof
if %exectries% LSS 50 goto :claim_exec_dir_retry
set execdir=
goto :eof
