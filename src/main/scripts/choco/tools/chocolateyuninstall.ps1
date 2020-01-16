$tools = Split-Path $MyInvocation.MyCommand.Definition
$package = Split-Path $tools
$jbang_home = Join-Path $package 'jbang-@projectVersion@'
$jbang_bat = Join-Path $ant_home 'bin/jbang/bat'

Uninstall-BinFile -Name 'jbang' -Path $jbang_bat
