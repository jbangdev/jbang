@@ECHO off
@@setlocal EnableDelayedExpansion
@@set LF=^


@@SET command=#
@@FOR /F "tokens=*" %%i in ('findstr -bv @@ "%~f0"') DO SET command=!command!!LF!%%i
@@powershell -noprofile -noexit -command !command! &amp; goto:eof


# *** POWERSHELL CODE STARTS HERE *** #
Write-Host 'This is PowerShell code being run from inside a batch file!' -Fore red
$PSVersionTable
Get-Process -Id $PID | Format-Table
Exit
