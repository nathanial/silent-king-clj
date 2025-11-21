# Scripts

This directory contains utility scripts for building, running, testing, and maintaining the project. It includes both PowerShell (`.ps1`) scripts for Windows and Shell (`.sh`) scripts for Linux/macOS.

## Common Scripts

- **`run.ps1` / `run.sh`**: Runs the main application.
- **`run-tests.ps1` / `run-tests.sh`**: Executes the test suite.
- **`lint.ps1`**: Runs the linter (clj-kondo) to check for code issues.
- **`format.ps1`**: Formats the code (likely using cljfmt).
- **`build.ps1` / `build.sh`**: Builds the project (e.g., creating an uberjar).
- **`compile-java.ps1` / `compile-java.sh`**: Compiles the Java sources in `src/java`.

## Asset Generation

- **`generate-images.ps1` / `generate-images.sh`**: Runs the image generation tools.
- **`generate-atlas.clj`**: Clojure script to generate texture atlases.
- **`preprocess-planets.clj` / `preprocess-stars.clj`**: Scripts to preprocess planet and star data/assets.
