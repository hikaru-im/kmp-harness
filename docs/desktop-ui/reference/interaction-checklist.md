# Interaction checklist

Record one capture or test result for every applicable cell. The current
values are intentionally pending reference capture.

| Area | Normal | Hover | Pressed | Disabled | Focused | Loading | Error | Selected | Dragging |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Sidebar session row | pending | pending | pending | pending | pending | n/a | pending | pending | pending |
| New session action | pending | pending | pending | pending | pending | pending | pending | n/a | n/a |
| Header action | pending | pending | pending | pending | pending | pending | pending | pending | n/a |
| Composer send | pending | pending | pending | pending | pending | pending | pending | n/a | n/a |
| Tool card | pending | pending | pending | n/a | pending | pending | pending | pending | pending |
| Settings control | pending | pending | pending | pending | pending | pending | pending | pending | n/a |

Keyboard checks:

- Tab order reaches sidebar toggle, session actions, header actions, composer
  controls, and send/stop in that order.
- Enter activates the focused button or submits the focused single-line field.
- Escape closes a menu or dialog and returns focus to its opener.
- Focus remains visible when the pointer is outside the focused control.

Window checks:

- Sidebar expands, collapses, and temporarily expands without changing the
  selected destination.
- The 1024×768 layout keeps the header, viewport, and composer dock visible.
- A loading or error state does not move the composer dock unexpectedly.
