# Repository Guidelines

## Project Structure & Module Organization
Silent King mixes Clojure UI/gameplay code under `src/silent_king` and supporting Java noise helpers in `src/java/silentking`. Namespaces mirror domains (for example `widgets/core.clj`, `galaxy.clj`), so keep new files aligned with their directories and module names. Tests live in `test/silent_king`, mirroring the source tree, and `test/silent_king/test_runner.clj` wires them together. Asset tooling sits inside `scripts/`, while generated sprites and atlases land in `assets/`. Compiled classes are written to `classes/`, and `deps.edn` defines runtime/test aliases.

## Build, Test, and Development Commands
- `./build.sh` – compiles Java, preprocesses `assets/stars`, and regenerates atlas PNG/JSON files; re-run after touching art or noise code.
- `./compile-java.sh` – only rebuilds `src/java/...` if you need a fast iteration loop.
- `./run.sh` – detects your OS/arch and launches `silent-king.core` with the matching alias.
- `./run-tests.sh` or `clojure -M:test` – executes the widget regression suite locally.
- `clojure -M:ALIAS -m silent-king.core` – manual run for a new alias (e.g., `macos-arm64`).

## Coding Style & Naming Conventions
Namespaces use kebab-case and start with `silent-king.`; file paths must match. Stick to two-space indentation, vertically align maps, and keep reducer pipelines small and pure. Provide docstrings and reuse the existing section banners (`;; =============================================================================`) to flag domains. Keywords stay kebab-case (`:hover-cursor`), and `*warn-on-reflection*` is enabled, so add type hints if reflection warnings surface. Java files follow the same package path (`silentking`) and compile cleanly with JDK `javac`.

## Testing Guidelines
Unit tests rely on `clojure.test`; mirror source namespaces (`widgets/layout_test.clj`) and name vars after the function under test. Run `./run-tests.sh` before committing, and append new namespaces to `silent-king.test-runner` so they execute in CI. Favor deterministic tests—use the state helpers from the existing widget suites to avoid real rendering or file IO.

## Commit & Pull Request Guidelines
History favors short imperative summaries (“ui scale”, “minimap tests”). Keep subjects under 72 chars and group related changes together; add a brief body when behavior or data contracts shift. Pull requests should link issues, list validation commands (`./run.sh`, `./build.sh`), and include screenshots or clips for UI/asset changes. Call out follow-up work or migrations so downstream agents can prioritize next steps.

## Assets & Configuration Tips
Regenerated atlas PNG/JSON pairs in `assets/` are large—only commit them when specs change, and note the script/command used. Custom preprocessing or atlas sizing belongs inside `scripts/*` so the build stays reproducible. Keep `deps.edn` aliases documented when adding tooling, and ensure contributors have a working JDK (`JAVA_HOME`) plus Clojure CLI installed before running scripts.
