#!/bin/bash

set -e

echo "Silent King - Build Script"
echo "=========================="
echo ""

# Step 1: Preprocess individual star images
echo "Step 1: Preprocessing star images (removing black backgrounds)..."
clojure scripts/preprocess-stars.clj
echo ""

# Step 2: Generate texture atlas
echo "Step 2: Generating texture atlas..."
clojure scripts/generate-atlas.clj
echo ""

echo "=========================="
echo "Build complete!"
echo ""
echo "Generated assets:"
echo "  - assets/stars-processed/    (159 preprocessed PNG images)"
echo "  - assets/star-atlas.png      (4096x4096 texture atlas)"
echo "  - assets/star-atlas.json     (atlas metadata)"
echo ""
echo "You can now run the application with: ./run.sh"
