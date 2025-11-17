#!/bin/bash

set -e

echo "Silent King - Build Script"
echo "=========================="
echo ""

# Step 0: Compile Java sources
echo "Step 0: Compiling Java sources..."
./compile-java.sh
echo ""

# Step 1: Preprocess individual star images
echo "Step 1: Preprocessing star images (removing black backgrounds)..."
clojure scripts/preprocess-stars.clj
echo ""

# Step 2: Generate texture atlases (xs, small, medium, lg)
echo "Step 2: Generating texture atlases..."
echo ""

echo "  Generating xs atlas (64x64 tiles)..."
clojure scripts/generate-atlas.clj 64 assets/star-atlas-xs.png assets/star-atlas-xs.json
echo ""

echo "  Generating small atlas (128x128 tiles)..."
clojure scripts/generate-atlas.clj 128 assets/star-atlas-small.png assets/star-atlas-small.json
echo ""

echo "  Generating medium atlas (256x256 tiles)..."
clojure scripts/generate-atlas.clj 256 assets/star-atlas-medium.png assets/star-atlas-medium.json
echo ""

echo "=========================="
echo "Build complete!"
echo ""
echo "Generated assets:"
echo "  - classes/                        (Compiled Java classes)"
echo "  - assets/stars-processed/         (159 preprocessed PNG images)"
echo "  - assets/star-atlas-xs.png        (4096x4096, 64x64 tiles)"
echo "  - assets/star-atlas-xs.json       (xs atlas metadata)"
echo "  - assets/star-atlas-small.png     (4096x4096, 128x128 tiles)"
echo "  - assets/star-atlas-small.json    (small atlas metadata)"
echo "  - assets/star-atlas-medium.png    (4096x4096, 256x256 tiles)"
echo "  - assets/star-atlas-medium.json   (medium atlas metadata)"
echo ""
echo "You can now run the application with: ./run.sh"
