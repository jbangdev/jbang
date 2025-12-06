@echo off

setlocal

:: Save the current code page
:: The 'chcp' command outputs "Active code page: 437"
:: We use a FOR loop to split by output by ":" and grab the second part (the number)
for /f "tokens=2 delims=:" %%a in ('chcp') do set "_JBANG_CMD_OriginalCP=%%a"

:: Clean up the variable (remove leading spaces)
set "_JBANG_CMD_OriginalCP=%_OriginalCP: =%"

:: Set code page to UTF-8 (65001)
chcp 65001 > nul

:: Call the real JBang.cmd script
call jbangx.cmd %*
set "_JBANG_EXIT_CODE=%ERRORLEVEL%"

:: Restore the original code page at the end
chcp %_JBANG_CMD_OriginalCP% > nul

exit /b %_JBANG_EXIT_CODE%

endlocal