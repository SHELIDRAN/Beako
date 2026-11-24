# MorphSelect

A small pill trigger ("This week ⌄") that grows out of itself into a popover — an action on top, a two-way segmented choice below — and shrinks back into the pill with the new value once you choose.

Proposed — from the widget reference motion, not yet in the Kotlin app. Shown inside a **WidgetCard** (`bk-widget`): a `surface` card, `radius-widget` 28, `shadow-soft-raised`, with a header row (icon · trigger · icon) and a body.

## Anatomy

`.bk-morph` > `button.bk-morph__trigger` (value + chevron, `aria-haspopup`, `aria-expanded`) + `.bk-morph__pop` (`role="dialog"`) holding `.bk-morph__action` and `.bk-morph__seg` (a sliding `chip-fill` indicator behind `role="radio"` options).

## Motion

| Moment | Spec |
| --- | --- |
| Open | **Bloom**: the trigger blurs and fades out; the popover grows from the trigger's size (0.42 × 0.3, origin at the trigger) to 1 on the droplet spring (≈420 ms) while sharpening from an 8 px blur; the action row and the choice row rise in 70 ms and 130 ms after |
| Choose | Indicator slides (550 ms spring); popover closes 380 ms later so the choice is seen |
| Close | Reverse; Esc or a click outside also closes and returns focus to the trigger |

Reduce motion: open and close are instant.

## Rules

- Two or three options only; more belongs in a menu. The action row is optional.
- The trigger always shows the current value. The popover is `surface` with `shadow-float`, `radius-menu` 20.
