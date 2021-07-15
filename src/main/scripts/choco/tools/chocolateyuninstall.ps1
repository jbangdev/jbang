$tools = Split-Path $MyInvocation.MyCommand.Definition
$package = Split-Path $tools
$jbang_home = Join-Path $package 'jbang-@projectVersion@'
$jbang_bat = Join-Path $jbang_home 'bin/jbang.cmd'

Uninstall-BinFile -Name 'jbang' -Path $jbang_bat
