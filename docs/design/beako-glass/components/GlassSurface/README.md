# GlassSurface

The floating liquid-glass panel: what is behind is blurred under a light dark tint, with a 1 px rim that fades from `rim-bright` to `rim`, and a `shadow-glass` drop shadow.

- **Use for** chrome that floats over content: the sidebar, popovers, the filter panel. Not for pages or list rows.
- **Recipe** — blur `blur-glass` and tint `glass-tint`, both multiplied by the Glass strength setting (0.5–1.5). Controls use `rim` as their tint instead (`.bk-glass--control`).
- **Reduce transparency** swaps the blur for a solid `glass-solid` fill and keeps the rim (`[data-reduce-transparency]` or the OS media query).
- **The consumer provides** the shape (`radius-*`) and the content. In Compose: `GlassSurface(shape, elevation = 12.dp)` or `Modifier.glass(shape, tint, blur)`; the blur source is `LocalHazeState`, and with none registered glass falls back to a translucent fill.
- Text on glass uses `text` and, over images, `text-shadow`, keeping 4.5:1.
