$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path $MyInvocation.MyCommand.Path
Set-Location $ScriptDir\..

Write-Host "Silent King - Starting..."

# In PowerShell/Windows, we assume windows-x64 since that's the only Windows alias we have.
$ALIAS = "windows-x64"
Write-Host "Running on Windows x64"

Write-Host "Launching with alias: $ALIAS"
Write-Host ""
clojure -M:$ALIAS -m silent-king.core

