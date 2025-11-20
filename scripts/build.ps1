$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path $MyInvocation.MyCommand.Path
Set-Location $ScriptDir\..

Write-Host "Silent King - Build Script"
Write-Host "=========================="
Write-Host ""

# Step 0: Compile Java sources
Write-Host "Step 0: Compiling Java sources..."
.\scripts\compile-java.ps1
Write-Host ""

# Step 1: Preprocess individual star images
Write-Host "Step 1: Preprocessing star images (removing black backgrounds)..."
clojure scripts/preprocess-stars.clj
Write-Host ""

# Step 2: Preprocess individual planet images
Write-Host "Step 2: Preprocessing planet images (removing black backgrounds)..."
clojure scripts/preprocess-planets.clj
Write-Host ""

# Step 3: Generate star texture atlases (xs, small, medium)
Write-Host "Step 3: Generating star texture atlases..."
Write-Host ""

Write-Host "  Generating xs atlas (64x64 tiles)..."
clojure scripts/generate-atlas.clj 64 assets/star-atlas-xs.png assets/star-atlas-xs.json 4096 assets/stars-processed
Write-Host ""

Write-Host "  Generating small atlas (128x128 tiles)..."
clojure scripts/generate-atlas.clj 128 assets/star-atlas-small.png assets/star-atlas-small.json 4096 assets/stars-processed
Write-Host ""

Write-Host "  Generating medium atlas (256x256 tiles)..."
clojure scripts/generate-atlas.clj 256 assets/star-atlas-medium.png assets/star-atlas-medium.json 4096 assets/stars-processed
Write-Host ""

# Step 4: Generate planet texture atlas
Write-Host "Step 4: Generating planet texture atlas..."
Write-Host ""

Write-Host "  Generating planet atlas (256x256 tiles)..."
clojure scripts/generate-atlas.clj 256 assets/planet-atlas-medium.png assets/planet-atlas-medium.json 4096 assets/planets-processed
Write-Host ""

Write-Host "=========================="
Write-Host "Build complete!"
Write-Host ""
Write-Host "Generated assets:"
Write-Host "  - classes/                        (Compiled Java classes)"
Write-Host "  - assets/stars-processed/         (preprocessed star PNG images)"
Write-Host "  - assets/planets-processed/       (preprocessed planet PNG images)"
Write-Host "  - assets/star-atlas-xs.png        (4096x4096, 64x64 tiles)"
Write-Host "  - assets/star-atlas-xs.json       (xs atlas metadata)"
Write-Host "  - assets/star-atlas-small.png     (4096x4096, 128x128 tiles)"
Write-Host "  - assets/star-atlas-small.json    (small atlas metadata)"
Write-Host "  - assets/star-atlas-medium.png    (4096x4096, 256x256 tiles)"
Write-Host "  - assets/star-atlas-medium.json   (medium atlas metadata)"
Write-Host "  - assets/planet-atlas-medium.png  (4096x4096, 256x256 tiles)"
Write-Host "  - assets/planet-atlas-medium.json (planet atlas metadata)"
Write-Host ""
Write-Host "You can now run the application with: .\run.ps1"

