# Silent King

**Silent King** is a high-performance Clojure-based star gallery visualization application. It renders thousands of rotating stars with interactive pan and zoom controls, demonstrating a robust rendering pipeline using LWJGL and Skija.

## Features

*   **High-Performance Rendering**: Utilizes LWJGL for OpenGL windowing and Skija for 2D graphics.
*   **Pure Data World Model**: The world state is stored as pure data (stars, hyperlanes, neighbors) in a `game-state` atom.
*   **3-Level LOD System**: Automatically switches between XS, Small, and Medium texture atlases based on zoom level for optimal performance.
*   **Non-Linear Scaling**: Implements separate scaling for position (quadratic) and size (linear) to create a dynamic "spatial separation" effect when zooming.
*   **Frustum Culling**: Optimizes rendering by only drawing stars currently visible in the viewport.
*   **Interactive Controls**: Smooth pan and zoom interactions with mouse support.

## Prerequisites

*   **Java JDK**: A working JDK installation (ensure `JAVA_HOME` is set).
*   **Clojure CLI**: The Clojure command-line tools must be installed.

## Quick Start

### 1. Build Assets

Before running the application for the first time, you need to generate the texture atlases. This process preprocesses star images and creates the necessary assets for the LOD system.

**Windows (PowerShell):**
```powershell
./scripts/build.ps1
```

**Linux / macOS:**
```bash
./build.sh
```

### 2. Run the Application

The run script automatically detects your operating system and launches the application with the appropriate native dependencies.

**Windows (PowerShell):**
```powershell
./scripts/run.ps1
```

**Linux / macOS:**
```bash
./run.sh
```

## Asset Generation

The project includes a tool for generating game assets (stars, planets, ships) using the OpenRouter API (Gemini 2.5 Flash Image).

### Prerequisites for Generation

1.  Obtain an **OpenRouter API Key**.
2.  Set it in your environment or create a `.env` file in the project root:
    ```
    OPENROUTER_API_KEY=your_key_here
    ```

### Running the Generator

Use the provided helper scripts to generate assets based on prompt files.

**Windows (PowerShell):**
```powershell
./scripts/generate-images.ps1 `
  --prompts prompts/star_prompts.txt `
  --reference-images assets/star.png `
  --output assets/star_variations
```

**Linux / macOS:**
```bash
./scripts/generate-images.sh \
  --prompts prompts/star_prompts.txt \
  --reference-images assets/star.png \
  --output assets/star_variations
```

**Options:**
*   `--prompts <file>`: Path to the prompts file (required).
*   `--reference-images <files>`: One or more reference images for style transfer.
*   `--output <dir>`: Output directory for generated images.
*   `--size <pixels>`: Image size (default: 1024).

## Controls

*   **Pan**: Click and drag with the **Left Mouse Button** to move around the galaxy.
*   **Zoom**: Use the **Mouse Scroll Wheel** to zoom in and out. The view zooms towards the cursor position.

## Development

### Project Structure

*   `src/silent_king`: Core Clojure source code (UI, gameplay, rendering).
*   `src/java/silentking`: Helper Java classes.
*   `assets`: Generated assets (sprites, atlases).
*   `scripts`: Build and utility scripts (asset generation, testing).
*   `test`: Unit tests.

### Interactive Development (REPL)

The application starts an nREPL server on port **7888** (configured in `.nrepl-port`) with `cider-nrepl`. You can connect your editor to this port for a live coding experience.

### Running Tests

To run the widget regression suite:

**Windows (PowerShell):**
```powershell
./scripts/run-tests.ps1
```

**Linux / macOS:**
```bash
./run-tests.sh
```

Or using the Clojure CLI directly:
```bash
clojure -M:test
```

### Code Style

*   **Namespaces**: Kebab-case, matching file paths (e.g., `silent-king.widgets.core`).
*   **Indentation**: Two-space indentation.
*   **Parentheses**: Ensure balanced parentheses. A helper script is available: `python3 scripts/check_parens.py path/to/file.clj`.

## License

All rights reserved.
