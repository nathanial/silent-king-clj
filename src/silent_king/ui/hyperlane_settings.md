# silent-king.ui.hyperlane-settings (legacy)

This namespace previously implemented the **collapsible hyperlane settings panel**.

## Responsibilities (old system)

- Managed `[:ui :hyperlane-settings]` state with `clojure.spec`, including:
  - Expanded/collapsed state and simple animation progress (`:target`, `:last-update`).
  - Current hyperlane opacity, animation speed, line width, and color scheme.
- Built a panel widget with:
  - A header row that toggled the panel open/closed.
  - A master on/off toggle bound to `:hyperlane-settings/:enabled?`.
  - A dropdown for color scheme (`:blue`, `:red`, `:green`, `:rainbow`).
  - Slider rows for opacity, animation speed, and line width.
  - A reset button that restored defaults via `state/reset-hyperlane-settings!`.
- Wired widget events (`on-toggle`, `on-change`, `on-select`) to update `game-state` hyperlane settings and UI state.

## Notes for Reactified rewrite

In the new React-style system this should become:

- A pure `hyperlane-settings-panel` component that:
  - Receives current hyperlane settings and panel UI state as props.
  - Emits events like `:hyperlanes/set-enabled?`, `:hyperlanes/set-opacity`, `:hyperlanes/set-speed`, `:hyperlanes/set-width`, and `:hyperlanes/reset`.
- A clear separation between:
  - **Domain settings** (`:hyperlane-settings` under `game-state`), and
  - **View state** (expanded/collapsed, local animation progress) managed by the Reactified UI layer.

