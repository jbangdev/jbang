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
if (-not (Test-Path env:JBANG_DEFAULT_JAVA_VERSION)) { $javaVersion='17' } else { $javaVersion=$env:JBANG_DEFAULT_JAVA_VERSION }

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
if (-not (Test-Path env:JBANG_USE_NATIVE)) { $env:JBANG_USE_NATIVE="false" }

# Base URL for downloading JBang releases.
# Override for testing or corporate mirrors.
# Example: $env:JBANG_DOWNLOAD_BASEURL='http://localhost:18080'
if (-not (Test-Path env:JBANG_DOWNLOAD_BASEURL)) { $jbangDownloadBaseUrl='https://github.com/jbangdev/jbang/releases' } else { $jbangDownloadBaseUrl=$env:JBANG_DOWNLOAD_BASEURL }

# Number of retry attempts for downloads
if (-not (Test-Path env:JBANG_DOWNLOAD_RETRY)) { $downloadRetry=5 } else { $downloadRetry=[int]$env:JBANG_DOWNLOAD_RETRY }
if (-not (Test-Path env:JBANG_DOWNLOAD_RETRY_DELAY)) { $downloadRetryDelay=0 } else { $downloadRetryDelay=[int]$env:JBANG_DOWNLOAD_RETRY_DELAY }

function Invoke-Download {
    param([string]$url, [string]$outFile)
    $attempt=0
    while ($true) {
        $attempt++
        try {
            Invoke-WebRequest "$url" -OutFile "$outFile"
            return $true
        } catch {
            if ($attempt -gt $downloadRetry) {
                return $false
            }
            if ($downloadRetryDelay -gt 0) {
                $sleepSeconds = $downloadRetryDelay
            } else {
                # Exponential backoff: 1, 2, 4, 8, ...
                $sleepSeconds = [Math]::Pow(2, $attempt - 1)
            }
            [Console]::Error.WriteLine("Download $attempt/$($downloadRetry + 1) failed. Retry in $sleepSeconds second(s)...")
            if ($attempt -eq 1) {
                [Console]::Error.WriteLine("(Set JBANG_DOWNLOAD_RETRY=0 to disable retries)")
            }
            Start-Sleep -Seconds $sleepSeconds
        }
    }
}

# Function to execute jbang (either native binary or JAR) and handle output
function Invoke-JBang {
    param([string]$binaryPath, [string]$jarPath, [string]$javaExec, [Parameter(ValueFromRemainingArguments=$true)]$args)
    
    $oldShell, $oldNotty, $oldCmd=$env:JBANG_RUNTIME_SHELL, $env:JBANG_STDIN_NOTTY, $env:JBANG_LAUNCH_CMD
    $env:JBANG_RUNTIME_SHELL, $env:JBANG_STDIN_NOTTY, $env:JBANG_LAUNCH_CMD="powershell", $MyInvocation.ExpectingInput, $PSCommandPath
    
    if ($binaryPath) {
        # Run native binary
        $output = & "$binaryPath" $args
        $err=$LASTEXITCODE
    } else {
        # Run JAR
        $output = & "$javaExec" $env:JBANG_JAVA_OPTIONS -classpath "$jarPath" dev.jbang.Main $args
        $err=$LASTEXITCODE
    }
    
    $erroractionpreference=$old_erroractionpreference
    $global:progresspreference=$old_progresspreference
    
    if ($err -eq 255) {
      Invoke-Expression "& $output"
    } elseif ($output -ne "") {
      Write-Output $output
    }
    
    $env:JAVA_HOME, $env:JBANG_RUNTIME_SHELL, $env:JBANG_STDIN_NOTTY, $env:JBANG_LAUNCH_CMD=$oldJavaHome, $oldShell, $oldNotty, $oldCmd
}

# detect architecture for platform-specific binary lookup
$jbang_arch = if ([System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture -eq [System.Runtime.InteropServices.Architecture]::Arm64) { "aarch64" } else { "x64" }

# resolve native binary or jar path from script location
$binaryPath=""
$jarPath=""
if ($env:JBANG_USE_NATIVE -eq "true") {
  # Look for platform-specific native binary first, then fall back to jbang.bin.exe
  if (Test-Path "$PSScriptRoot\jbang-windows-${jbang_arch}.bin.exe") {
    $binaryPath="$PSScriptRoot\jbang-windows-${jbang_arch}.bin.exe"
  } elseif (Test-Path "$PSScriptRoot\jbang.bin.exe") {
    $binaryPath="$PSScriptRoot\jbang.bin.exe"
  } else {
    [Console]::Error.WriteLine("WARNING: JBang native binary (jbang-windows-${jbang_arch}.bin.exe or jbang.bin.exe) not found in $PSScriptRoot")
  }
}
if (-not $binaryPath) {
  # Fall back to JAR if no native binary found or native binary disabled
  if (Test-Path "$PSScriptRoot\jbang.jar") {
    $jarPath="$PSScriptRoot\jbang.jar"
  } elseif (Test-Path "$PSScriptRoot\.jbang\jbang.jar") {
    $jarPath="$PSScriptRoot\.jbang\jbang.jar"
  }
}
if (-not $binaryPath -and -not $jarPath) {
  if (-not (Test-Path "$JBDIR\bin\jbang.jar") -or -not (Test-Path "$JBDIR\bin\jbang.ps1")) {
    New-Item -ItemType Directory -Force -Path "$TDIR\urls" >$null 2>&1
    if (Test-Path env:JBANG_DOWNLOAD_URL) {
        $jburl=$env:JBANG_DOWNLOAD_URL
    } elseif (-not (Test-Path env:JBANG_DOWNLOAD_VERSION)) {
        $jburl="$jbangDownloadBaseUrl/latest/download/jbang.zip"
    } else {
        # Numeric versions get a 'v' prefix (e.g. 0.120.0 -> v0.120.0); named
        # release tags (e.g. 'early-access') are used as-is.
        if ($env:JBANG_DOWNLOAD_VERSION -match '^[0-9]') {
            $jbtag = "v$env:JBANG_DOWNLOAD_VERSION"
        } else {
            $jbtag = $env:JBANG_DOWNLOAD_VERSION
        }
        $jburl="$jbangDownloadBaseUrl/download/$jbtag/jbang.zip";
    }
    $dlVersion = if ($env:JBANG_DOWNLOAD_VERSION) { $env:JBANG_DOWNLOAD_VERSION } else { 'latest' }
    [Console]::Error.WriteLine("Downloading JBang $dlVersion from $jburl...")
    $ok = Invoke-Download "$jburl" "$TDIR\urls\jbang.zip"
    if (-not ($ok)) {
      [Console]::Error.WriteLine("Error downloading JBang from $jburl to $TDIR\urls\jbang.zip")
      exit 1
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

# Find/get a JDK (only needed for JAR execution)
$JAVA_EXEC=""
$oldJavaHome=$env:JAVA_HOME
if (-not $binaryPath) {
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
        $ok = Invoke-Download "$jdkurl" "$TDIR\bootstrap-jdk.zip"
        if (-not ($ok)) { [Console]::Error.WriteLine("Error downloading JDK"); exit 1 }
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
}

# Execute jbang
Invoke-JBang -binaryPath $binaryPath -jarPath $jarPath -javaExec $JAVA_EXEC -args $args
