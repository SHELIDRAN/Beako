# ToggleIconButton

A round on/off button: off is an `accent-fill` circle with a plus; on, an `ink-fill` circle **rises** from below and the plus turns into a minus; off, the ink carries on up and out through the top.

Proposed — designed from the reference motion study, not yet in the Kotlin app.

- **Use** for a single binary action next to a title: add to / remove from library, follow / unfollow.
- **State** lives in `aria-pressed`; the accessible name changes with it ("Add to library" ⇄ "Remove from library"). The shape change (+ / −) carries the state, never the colour alone.
- **Size** `control-cta` (48). `bk-toggle-icon--ghost` starts on `surface` for places where an accent would compete.
- **Motion** rise 380 ms `cubic-bezier(.65,0,.35,1)`; the glyph rotates 180° on the droplet spring; presses dip to 96%. Set `data-was-on` when it turns off so the ink exits upward instead of dropping back. Reduce motion: instant.
