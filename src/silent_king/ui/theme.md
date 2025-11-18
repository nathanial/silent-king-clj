# silent-king.ui.theme (legacy)

This namespace previously defined the **design system** for the widget-based UI.

## Responsibilities (old system)

- Provided color palette maps (backgrounds, text colors, interactive colors, shadows).
- Defined scales for:
  - Typography (`:title`, `:heading`, `:subheading`, `:body`, `:small`, `:tiny`).
  - Spacing (`:xs`, `:sm`, `:md`, `:lg`, `:xl`).
  - Border radii (`:sm`, `:md`, `:lg`, `:xl`, `:xxl`).
  - Shadows and panel dimensions for control panel, dashboard, inspector, etc.
- Exposed helper functions like:
  - `get-color`, `get-font-size`, `get-spacing`, `get-border-radius`, `get-shadow`, `get-panel-dimension`, `get-widget-size`.
- Served as the central source of truth for visual consistency across all legacy widgets.

## Notes for Reactified rewrite

For the Reactified UI, we should:

- Either port this theme almost verbatim into a **new design system module**, or
- Use it as a starting point to define a more React-style theming API (e.g. theme map passed through component trees).

It remains a good catalog of the visual language (colors, sizes, spacing) that the new UI should likely respect.

