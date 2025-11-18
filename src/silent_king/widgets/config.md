# silent-king.widgets.config (legacy)

This namespace previously contained **global configuration for the widget system**.

## Responsibilities (old system)

- Defined `ui-scale`, a global scalar applied to:
  - Skija canvas transforms for widget rendering.
  - Hit testing conversions between screen and widget space.

## Notes for Reactified rewrite

In the new UI layer we will still need:

- A notion of **UI scale** for accessibility and DPI handling.

This legacy configuration is a reminder to design the Reactified renderer and layout engine with a configurable global scale in mind.

