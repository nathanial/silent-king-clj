#!/bin/bash

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$SCRIPT_DIR/.."

echo "Silent King - Build Script"
echo "=========================="
echo ""

# Step 0: Compile Java sources
echo "Step 0: Compiling Java sources..."
./scripts/compile-java.sh
echo ""

# Step 1: Preprocess individual star images
echo "Step 1: Preprocessing star images (removing black backgrounds)..."
clojure scripts/preprocess-stars.clj
echo ""

# Step 2: Preprocess individual planet images
echo "Step 2: Preprocessing planet images (removing black backgrounds)..."
clojure scripts/preprocess-planets.clj
echo ""

# Step 3: Generate star texture atlases (xs, small, medium)
echo "Step 3: Generating star texture atlases..."
echo ""

echo "  Generating xs atlas (64x64 tiles)..."
clojure scripts/generate-atlas.clj 64 assets/star-atlas-xs.png assets/star-atlas-xs.json 4096 assets/stars-processed
echo ""

echo "  Generating small atlas (128x128 tiles)..."
clojure scripts/generate-atlas.clj 128 assets/star-atlas-small.png assets/star-atlas-small.json 4096 assets/stars-processed
echo ""

echo "  Generating medium atlas (256x256 tiles)..."
clojure scripts/generate-atlas.clj 256 assets/star-atlas-medium.png assets/star-atlas-medium.json 4096 assets/stars-processed
echo ""

# Step 4: Generate planet texture atlas
echo "Step 4: Generating planet texture atlas..."
echo ""

echo "  Generating planet atlas (256x256 tiles)..."
clojure scripts/generate-atlas.clj 256 assets/planet-atlas-medium.png assets/planet-atlas-medium.json 4096 assets/planets-processed
echo ""

echo "=========================="
echo "Build complete!"
echo ""
echo "Generated assets:"
echo "  - classes/                        (Compiled Java classes)"
echo "  - assets/stars-processed/         (preprocessed star PNG images)"
echo "  - assets/planets-processed/       (preprocessed planet PNG images)"
echo "  - assets/star-atlas-xs.png        (4096x4096, 64x64 tiles)"
echo "  - assets/star-atlas-xs.json       (xs atlas metadata)"
echo "  - assets/star-atlas-small.png     (4096x4096, 128x128 tiles)"
echo "  - assets/star-atlas-small.json    (small atlas metadata)"
echo "  - assets/star-atlas-medium.png    (4096x4096, 256x256 tiles)"
echo "  - assets/star-atlas-medium.json   (medium atlas metadata)"
echo "  - assets/planet-atlas-medium.png  (4096x4096, 256x256 tiles)"
echo "  - assets/planet-atlas-medium.json (planet atlas metadata)"
echo ""
echo "You can now run the application with: ./run.sh"
