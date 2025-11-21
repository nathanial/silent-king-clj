$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path $MyInvocation.MyCommand.Path
Set-Location $ScriptDir\..

Write-Host "Silent King - Running Tests..."

# We assume windows-x64 since we are in the PowerShell script
$PLATFORM_ALIAS = "windows-x64"
Write-Host "Running on Windows x64"

# Combine platform alias and test alias
$aliasArg = "-M:windows-x64:test"
Write-Host "Running tests with alias: $aliasArg"
clojure -M:windows-x64:test
