# Silent King - Gemini Context

## Project Overview
**Silent King** is a high-performance Clojure application for visualizing star galleries. It features a custom rendering pipeline built on **LWJGL** (OpenGL) and **Skija** (2D graphics).

### Key Features
- **Pure Data State**: World state is managed as pure Clojure data in atoms (`game-state`).
- **LOD System**: 3-level Level of Detail system (XS, Small, Medium) using texture atlases.
- **Non-Linear Scaling**: Separate scaling for position vs. size to enhance visualization during zoom.
- **Entity Component System**: Custom lightweight ECS for managing game entities.

## Development Environment

### Prerequisites
- **Java JDK**: `JAVA_HOME` must be set.
- **Clojure CLI**: Required for dependency management (`deps.edn`).

### Build & Run
**Platform Note**: You are running on **Windows (win32)**. Use the provided PowerShell scripts.

1.  **Build Assets (First Run Only)**
    *   *Command:* `powershell ./scripts/build.ps1`
    *   *Purpose:* Preprocesses images and generates texture atlases. The app will not render correctly without this.

2.  **Run Application**
    *   *Command:* `powershell ./scripts/run.ps1`
    *   *Details:* Automatically detects the OS and invokes the correct `deps.edn` alias (e.g., `:windows-x64`).

### Testing & Quality
-   **Run Tests (Kaocha):**
    *   *Command:* `powershell ./scripts/run-tests.ps1`
    *   *Alternative:* `clojure -M:test`
-   **Linting (clj-kondo):**
    *   *Command:* `powershell ./scripts/lint.ps1`
-   **Formatting:**
    *   *Command:* `powershell ./scripts/format.ps1`

## Architecture & Codebase

### Directory Structure
-   `src/silent_king/`: Core Clojure source code.
    -   `core.clj`: Entry point, render loop, window management.
    -   `state.clj`: ECS implementation, state atoms.
    -   `assets.clj`: Atlas loading and asset management.
    -   `schemas.clj`: Malli schemas for boundary validation.
-   `src/java/`: Java interop classes.
-   `scripts/`: PowerShell (`.ps1`) and Bash scripts for build/ops.
-   `assets/`: Raw images and generated atlases.

### State Management
-   **`game-state` Atom**: Holds the entire world model (`:stars`, `:hyperlanes`, UI state).
-   **`render-state` Atom**: Holds graphical resources (Window, Skija Context).
-   **ECS**: Entities are maps with components (e.g., `:position`, `:renderable`).

### Validation (Malli)
Validation is **optional** and geared towards boundaries (initial generation, UI events).
-   **Enable:** Bind `silent-king.schemas/*validate-boundaries?*` to `true`.
-   **Standard:** Tests run with validation enabled.

## Notes for AI Agents
-   **Conventions**: Use kebab-case for Clojure namespaces. Indent with 2 spaces.
-   **Platform Specifics**: Always respect the local `win32` environment when suggesting commands (prefer `powershell` or `.ps1`).
-   **Dependencies**: Check `deps.edn` for available libraries. Do not assume libraries are present unless explicitly listed.
-   **Reference**: See `CLAUDE.md` for deeper architectural decision records if needed.
