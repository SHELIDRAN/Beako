# ArrowButton

A large pill call to action with a round arrow knob on the right; on hover or focus the knob's `accent-fill` sweeps across the whole pill and the label turns `accent-ink`.

Proposed — designed from the reference motion study, not yet in the Kotlin app.

## Variants

| Class | Rest | Use |
| --- | --- | --- |
| `bk-arrow` | `surface` pill, `text` label | Secondary forward actions: "Open title page" |
| `bk-arrow--ink` | `ink-fill` pill, `ink-on-fill` label | Strong, but not the accent |
| `bk-arrow--accent` | already filled `accent-fill` | The one accent action on a screen: "Continue reading", "Start chapter 1" |

## Anatomy

`a.bk-arrow` > `span.bk-arrow__label` + `span.bk-arrow__fill` + `span.bk-arrow__knob` (an 16 px arrow icon). Height `control-cta` (48); the knob and fill are `control-cta − 8`, inset 4 px.

## Motion

Fill width: 420 ms `cubic-bezier(.65,0,.35,1)`; the arrow nudges 3 px right. Pressing dips it to 96%. Reduce motion: no sweep, the colour change is instant.

## Rules

- Use it for navigation forward (it is a link or a button that moves you on). Not for toggles or destructive actions.
- One accent ArrowButton per screen at most. Keep labels to two or three words, sentence case.
- The consumer provides the label and the `href` / click handler.
