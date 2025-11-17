# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Silent King is a Clojure-based star gallery visualization application that renders thousands of rotating stars with interactive pan and zoom controls. It demonstrates a high-performance rendering pipeline using:
- Entity-Component System (ECS) architecture
- 3-level LOD (Level of Detail) system with texture atlases
- Non-linear scaling for spatial separation when zooming
- LWJGL for OpenGL windowing
- Skija for 2D graphics rendering
- Frustum culling for performance optimization

## Build and Run Commands

### Building Assets
Before running the application for the first time, generate texture atlases:
```bash
./build.sh
```

This preprocesses star images and generates 3 texture atlases (xs, small, medium) used for LOD rendering.

### Running the Application
```bash
./run.sh
```

The run script automatically detects your platform (macOS ARM64/x64, Linux x64, Windows x64) and uses the appropriate deps.edn alias with platform-specific native dependencies.

### Interactive Development
The application starts an nREPL server on port 7888 (stored in `.nrepl-port`) with cider-nrepl for REPL-driven development. Connect your editor to this port for live coding.

## Architecture

### State Management
The application uses two main state atoms:
- `game-state`: Contains entities, camera, input, time, and assets
- `render-state`: Contains window, DirectContext, and Surface

State is managed through the `silent-king.state` namespace, which provides:
- Entity-component system primitives
- Pure functions for entity/component manipulation
- Stateful functions (with `!`) for atom updates

### Entity-Component System
Entities are stored as maps with a `:components` key. Common components:
- `:position` - `{:x x :y y}` world coordinates
- `:renderable` - `{:path filename}` for asset lookup
- `:transform` - `{:size px :rotation angle}` for rendering
- `:physics` - `{:rotation-speed rad/s}` for animation

Query entities using `filter-entities-with` which takes a vector of required component keys.

### 3-Level LOD System
The renderer switches between quality levels based on zoom:
- `zoom < 0.5`: XS atlas (64x64 tiles, 4096x4096 texture)
- `zoom < 1.5`: Small atlas (128x128 tiles, 4096x4096 texture)
- `zoom >= 1.5`: Medium atlas (256x256 tiles, 4096x4096 texture, highest quality)

Atlas metadata maps filenames to `{:x :y :size}` coordinates for texture sampling.

### Non-Linear Scaling
The application uses separate scaling exponents for positions and sizes to create spatial separation when zooming:
- **Position scaling**: Uses exponent 2.0 (quadratic) - stars spread apart dramatically
- **Size scaling**: Uses exponent 1.0 (linear) - stars grow at normal rate
- **Result**: At 2x zoom, stars are 4x farther apart but only 2x larger, creating visible gaps

This is implemented through `zoom->position-scale` and `zoom->size-scale` functions that apply power transformations to the zoom level. The scroll wheel callback uses `inverse-transform-position` to maintain zoom-to-cursor behavior where the world position under the cursor stays fixed.

### Asset Pipeline
1. Source images: `assets/stars/` (159 PNG files with black backgrounds)
2. Preprocessing: `scripts/preprocess-stars.clj` removes black backgrounds → `assets/stars-processed/`
3. Atlas generation: `scripts/generate-atlas.clj` packs images into texture atlases with JSON metadata
4. Runtime loading: `silent-king.assets/load-all-assets` loads texture atlases and star filenames (not full images)

### Platform-Specific Dependencies
The `deps.edn` file defines aliases for each platform that include:
- Skija native binaries (macOS-arm64, macOS-x64, linux-x64, windows-x64)
- LWJGL native libraries
- Platform-specific JVM opts (macOS requires `-XstartOnFirstThread`)

When adding new dependencies, ensure platform-specific natives are added to all relevant aliases.

### Rendering Pipeline
1. `draw-frame` in `core.clj` handles the main render loop
2. Non-linear position and size transforms applied per-star based on zoom level
3. Frustum culling filters visible stars using `star-visible?` with transformed positions
4. LOD level selected based on zoom factor (xs/small/medium)
5. Stars drawn from appropriate texture atlas using `draw-star-from-atlas`
6. UI overlay drawn in screen space (not affected by camera transform)

### Input Handling
Mouse callbacks in `setup-mouse-callbacks`:
- Cursor position: Updates pan when dragging (converts window coords to framebuffer coords for HiDPI)
- Mouse button: Toggles drag state
- Scroll wheel: Zooms while keeping world position under cursor fixed

## Key Files

- `src/silent_king/core.clj`: Main entry point, rendering loop, GLFW/OpenGL setup
- `src/silent_king/state.clj`: ECS implementation and state management
- `src/silent_king/assets.clj`: Asset loading for atlases and images
- `scripts/preprocess-stars.clj`: Build script to remove black backgrounds
- `scripts/generate-atlas.clj`: Build script to create texture atlases
- `deps.edn`: Dependencies and platform-specific aliases
- `run.sh`: Platform detection and application launcher
- `build.sh`: Asset preprocessing and atlas generation
