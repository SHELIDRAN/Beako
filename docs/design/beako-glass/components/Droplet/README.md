# Droplet

The clear glass pill that marks a selection everywhere in Beako: mostly transparent, a faint rim, a soft shadow and no bright glint.

- **Fill** — vertical gradient `droplet-top` → `droplet-bottom`; 1 px `droplet-top` rim; `shadow-droplet`; `radius-pill`.
- **Motion** — it slides to the new selection on a spring (damping 0.62, stiffness 400, about 550 ms with a little overshoot). Reduce motion snaps it.
- **Sizes** — 48 × `control-md` in the sidebar, `segment-width` × `control-sm` in segmented switches.
- Only one droplet per group. It is decoration: the selected option also changes its text from `text-muted` to `text` and carries the selected state for screen readers.
