@echo off
setlocal
rem Save the current code page
rem The 'chcp' command outputs "Active code page: 437"
rem We use a FOR loop to split by output by ":" and grab the second part (the number)
for /f "tokens=2 delims=:" %%a in ('chcp') do set "_OriginalCP=%%a"

rem Clean up the variable (remove leading spaces)
set "_OriginalCP=%_OriginalCP: =%"

rem Set code page to UTF-8 (65001)
chcp 65001 > nul

rem --- Your Main Script Logic Goes Here ---
call jbangx.cmd %*

rem Restore the original code page at the end
chcp %_OriginalCP% > nul
endlocal