# Alert

A compact raised notice that sits inside content — an icon, one line of text, an optional action — with an accent version that glows to ask for attention.

Proposed — from the styleguide reference, not yet in the Kotlin app. `Banner` stays the screen-wide notice across the top; Alert is for one item or section.

| Class | Look | Use |
| --- | --- | --- |
| `bk-alert` | `surface`, `rim` border, `shadow-droplet`, muted icon | Neutral information that will clear itself |
| `--accent` | `accent-soft` wash, `accent-rim`, `shadow-accent-glow`, `accent` icon | Needs action: failed downloads, a source that needs signing in |
| `--quiet` | no fill or shadow | Low-priority status lines |
| `--danger` | `danger` icon | Errors — always with words saying what failed |

- One accent alert visible at a time. Text stays `text` (never orange); the icon and glow carry the emphasis, and the words say what happened.
- `role="alert"` for errors and failures, `role="status"` otherwise.
