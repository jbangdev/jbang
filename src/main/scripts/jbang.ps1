#Requires -Version 5

#
# To run this script remotely type this in your PowerShell
# (where <args>... are the arguments you want to pass to JBang):
#   iex "& { $(iwr -useb https://ps.jbang.dev) } <args>..."
#
# An alternative way is to type:
#   & ([scriptblock]::Create($(iwr -useb https://ps.jbang.dev))) <args>...
# Which even allows you to store the command in a variable for re-use:
#   $jbang = ([scriptblock]::Create($(iwr -useb https://ps.jbang.dev)))
#   & $jbang <args>...
#

$old_erroractionpreference=$erroractionpreference
$erroractionpreference='stop'
$old_progresspreference=$global:progresspreference
$global:progresspreference='SilentlyContinue'

# Check that we're running the correct PowerShell
if (($PSVersionTable.PSVersion.Major) -lt 5) {
    [Console]::Error.WriteLine("PowerShell 5 or later is required to run. For instruction on how to update,")
    [Console]::Error.WriteLine("see: https://docs.microsoft.com/en-us/powershell/scripting/setup/installing-windows-powershell")
    break
}

# Check that the correct Execution Policy is set
$allowedExecutionPolicy = @('Unrestricted', 'RemoteSigned', 'ByPass')
if ((Get-ExecutionPolicy).ToString() -notin $allowedExecutionPolicy) {
    [Console]::Error.WriteLine("PowerShell requires an execution policy in [$($allowedExecutionPolicy -join ", ")] to continue.")
    [Console]::Error.WriteLine("For example, to set the execution policy to 'RemoteSigned' please run :")
    [Console]::Error.WriteLine("'Set-ExecutionPolicy RemoteSigned -scope CurrentUser'")
    break
}

if ([System.Enum]::GetNames([System.Net.SecurityProtocolType]) -notcontains 'Tls12') {
    [Console]::Error.WriteLine(".NET Framework 4.5 or later is required to run. For instructions on how to update,")
    [Console]::Error.WriteLine("see: https://www.microsoft.com/net/download")
    break
}

# The Java version to install when it's not installed on the system yet
if (-not (Test-Path env:JBANG_DEFAULT_JAVA_VERSION)) { $javaVersion='11' } else { $javaVersion=$env:JBANG_DEFAULT_JAVA_VERSION }

$os='windows'
$arch='x64'
$libc_type='c_std_lib'

if (-not (Test-Path env:JBANG_JDK_VENDOR)) {
    if (($javaVersion -eq 8) -or ($javaVersion -eq 11) -or ($javaVersion -ge 17)) {
        $distro='temurin'
    } else {
        $distro='aoj'
    }
} else {
    $distro=$env:JBANG_JDK_VENDOR
}

if (-not (Test-Path env:JBANG_DIR)) { $JBDIR="$env:userprofile\.jbang" } else { $JBDIR=$env:JBANG_DIR }
if (-not (Test-Path env:JBANG_CACHE_DIR)) { $TDIR="$JBDIR\cache" } else { $TDIR=$env:JBANG_CACHE_DIR }

# resolve application jar path from script location and convert to windows path when using cygwin
if (Test-Path "$PSScriptRoot\jbang.jar") {
  $jarPath="$PSScriptRoot\jbang.jar"
} elseif (Test-Path "$PSScriptRoot\.jbang\jbang.jar") {
  $jarPath="$PSScriptRoot\.jbang\jbang.jar"
} else {
  if (-not (Test-Path "$JBDIR\bin\jbang.jar") -or -not (Test-Path "$JBDIR\bin\jbang.ps1")) {
    New-Item -ItemType Directory -Force -Path "$TDIR\urls" >$null 2>&1
    if (-not (Test-Path env:JBANG_DOWNLOAD_VERSION)) {
        $jburl="https://github.com/jbangdev/jbang/releases/latest/download/jbang.zip"
    } else {
        $jburl="https://github.com/jbangdev/jbang/releases/download/v$env:JBANG_DOWNLOAD_VERSION/jbang.zip";
    }
    [Console]::Error.WriteLine("Downloading JBang $env:JBANG_DOWNLOAD_VERSION...")
    try { Invoke-WebRequest "$jburl" -OutFile "$TDIR\urls\jbang.zip"; $ok=$? } catch {
      $ok=$false
      $err=$_
    }
    if (-not ($ok)) { 
      [Console]::Error.WriteLine("Error downloading JBang from $jburl to $TDIR\urls\jbang.zip")
      [Console]::Error.WriteLine($err)
      break 
    }
    [Console]::Error.WriteLine("Installing JBang...")
    Remove-Item -LiteralPath "$TDIR\urls\jbang" -Force -Recurse -ErrorAction Ignore >$null 2>&1
    try { Expand-Archive -Path "$TDIR\urls\jbang.zip" -DestinationPath "$TDIR\urls"; $ok=$? } catch {
      $ok=$false 
      $err=$_
    }
    if (-not ($ok)) { 
      [Console]::Error.WriteLine("Error unzipping JBang from $TDIR\urls\jbang.zip to $TDIR\urls")
      [Console]::Error.WriteLine($err)
      break 
    }
    New-Item -ItemType Directory -Force -Path "$JBDIR\bin" >$null 2>&1
    Remove-Item -LiteralPath "$JBDIR\bin\jbang" -Force -ErrorAction Ignore >$null 2>&1
    Remove-Item -Path "$JBDIR\bin\jbang.*" -Force -ErrorAction Ignore >$null 2>&1
    Copy-Item -Path "$TDIR\urls\jbang\bin\*" -Destination "$JBDIR\bin" -Force >$null 2>&1
  }
  . "$JBDIR\bin\jbang.ps1" @args
  break
}
if (Test-Path "$jarPath.new") {
  # a new jbang version was found, we replace the old one with it
  Move-Item -Path "$jarPath.new" -Destination "$jarPath" -Force
}

# Find/get a JDK
$JAVA_EXEC=""
if (Test-Path env:JAVA_HOME) {
  # Determine if a (working) JDK is available in JAVA_HOME
  if (Test-Path "$env:JAVA_HOME\bin\javac.exe") {
    $JAVA_EXEC="$env:JAVA_HOME\bin\java.exe"
  } else {
    [Console]::Error.WriteLine("JAVA_HOME is set but does not seem to point to a valid Java JDK")
  }
}
if ($JAVA_EXEC -eq "") {
  # Determine if a (working) JDK is available on the PATH
  $ok=$false; try { if (Get-Command "javac") { $ok=$true } } catch {}
  if ($ok) {
    $env:JAVA_HOME=""
    $JAVA_EXEC="java.exe"
  } elseif (Test-Path "$JBDIR\currentjdk\bin\javac") {
    $env:JAVA_HOME="$JBDIR\currentjdk"
    $JAVA_EXEC="$JBDIR\currentjdk\bin\java"
  } else {
    $env:JAVA_HOME="$TDIR\jdks\$javaVersion"
    $JAVA_EXEC="$env:JAVA_HOME\bin\java.exe"
    # Check if we installed a JDK before
    if (-not (Test-Path "$TDIR\jdks\$javaVersion")) {
      # If not, download and install it
      New-Item -ItemType Directory -Force -Path "$TDIR\jdks" >$null 2>&1
      [Console]::Error.WriteLine("Downloading JDK $javaVersion. Be patient, this can take several minutes...")
      $jdkurl="https://api.foojay.io/disco/v3.0/directuris?distro=$distro&javafx_bundled=false&libc_type=$libc_type&archive_type=zip&operating_system=$os&package_type=jdk&version=$javaVersion&architecture=$arch&latest=available"
      try { Invoke-WebRequest "$jdkurl" -OutFile "$TDIR\bootstrap-jdk.zip"; $ok=$? } catch { $ok=$false }
      if (-not ($ok)) { [Console]::Error.WriteLine("Error downloading JDK"); break }
      [Console]::Error.WriteLine("Installing JDK $javaVersion...")
      Remove-Item -LiteralPath "$TDIR\jdks\$javaVersion.tmp" -Force -Recurse -ErrorAction Ignore >$null 2>&1
      try { Expand-Archive -Path "$TDIR\bootstrap-jdk.zip" -DestinationPath "$TDIR\jdks\$javaVersion.tmp"; $ok=$? } catch { $ok=$false }
      if (-not ($ok)) { [Console]::Error.WriteLine("Error installing JDK"); break }
      $dirs=Get-ChildItem -Directory -Path "$TDIR\jdks\$javaVersion.tmp"
      foreach ($d in $dirs) {
        $p=$d.FullName
        Move-Item -Path "$p\*" -Destination "$TDIR\jdks\$javaVersion.tmp" -Force
      }
      # Check if the JDK was installed properly
      $ok=$false; try { & $TDIR\jdks\$javaVersion.tmp\bin\javac -version >$null 2>&1; $ok=$true } catch {}
      if (-not ($ok)) { [Console]::Error.WriteLine("Error installing JDK"); break }
      # Activate the downloaded JDK giving it its proper name
      Rename-Item -Path "$TDIR\jdks\$javaVersion.tmp" -NewName "$javaVersion" >$null 2>&1
      # Set the current JDK
      & "$JAVA_EXEC" -classpath "$jarPath" dev.jbang.Main jdk default $javaVersion
    }
  }
}

$env:JBANG_RUNTIME_SHELL="powershell"
$env:JBANG_STDIN_NOTTY=$MyInvocation.ExpectingInput
$output = & "$JAVA_EXEC" $env:JBANG_JAVA_OPTIONS -classpath "$jarPath" dev.jbang.Main @args
$err=$LASTEXITCODE

$erroractionpreference=$old_erroractionpreference
$global:progresspreference=$old_progresspreference

if ($err -eq 255) {
  Invoke-Expression "& $output"
} elseif ($output -ne "") {
  Write-Output $output
  break
} else {
  break
}
