$tools = Split-Path $MyInvocation.MyCommand.Definition
$package = Split-Path $tools
$jbang_home = Join-Path $package 'jbang-0.6.0.5'
$jbang_bat = Join-Path $jbang_home 'bin/jbang.bat'

Install-ChocolateyZipPackage `
    -PackageName 'jbang' `
    -Url 'https://github.com/maxandersen/jbang/releases/download/v0.6.0.5/jbang-0.6.0.5.zip' `
    -Checksum 'e337cf5053091c893fef81141a79a6beb7d34777a65b25b11a379adc0d6c7203' `
    -ChecksumType 'sha256' `
    -UnzipLocation $package

Install-BinFile -Name 'jbang' -Path $jbang_bat

Update-SessionEnvironment