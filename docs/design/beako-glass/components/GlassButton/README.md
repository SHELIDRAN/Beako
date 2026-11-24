# GlassButton

A pill button on glass; `primary` makes it the solid, high-emphasis action.

- **Default** — glass control (`rim` tint), `text` label. **Primary** — solid `text` fill with `ground` ink; at most one per view. **Accent** — `accent-fill` with `accent-ink` (4.8:1); on hover it lifts 1 px and glows (`shadow-accent-glow`). Use it for the one action the screen exists for ("Add to library"); a view may have a Primary *or* an Accent, not both competing. **Disabled** — 45% opacity, no hover.
- **Size** — min height `control-md`, 14 px side padding, 6 px gap to an optional `icon-sm` icon, `radius-pill`, `label-large`.
- **Labels** are verbs in sentence case: "Try Comick again", "Clear all", "Add to library".
- **The consumer provides** `label`, `onClick`, optionally `icon` and `primary` or `accent`.
- **Press** — every variant dips to 96% while held.
