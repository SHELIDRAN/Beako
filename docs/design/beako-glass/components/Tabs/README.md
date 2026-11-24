# Tabs

Text tabs on a 1 px `rim` hairline; the current tab is `text` with a 2 px `accent` underline that **travels**: its leading edge moves first and the trailing edge follows 90 ms later, so it stretches across and settles on the new label (droplet spring). Set `left`/`right` on `.bk-tabs__ind` and the two delays `--bk-delay-l` / `--bk-delay-r` by direction.

Proposed — from the styleguide reference, not yet in the Kotlin app.

- **Use** to switch views inside one page (Chapters · Details · Related). Between screens use the Sidebar; for filters use `DropletSegmented`.
- `role="tablist"`, each tab a `tab` with `aria-selected`; arrow keys move between tabs (consumer provides).
- Labels SemiBold 14, 24 px apart, 40 px tall. The underline is the only accent on the row; inactive tabs are `text-muted`.
