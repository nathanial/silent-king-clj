# Image Generator Wrapper Powershell
# Usage: ./scripts/generate-images.ps1 --prompts <file> --reference-images <files...> --output <dir>

$envFile = ".env"
if (-not $env:OPENROUTER_API_KEY -and (Test-Path $envFile)) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match "^([^#=]+)=(.*)") {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2], "Process")
        }
    }
}

if (-not $env:OPENROUTER_API_KEY) {
    Write-Host "Warning: OPENROUTER_API_KEY not found in environment. Application may fail if not provided elsewhere." -ForegroundColor Yellow
}

clojure -M:image-gen -m silent-king.tools.image-gen $args

