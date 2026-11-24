# SearchField

The pill search box at the top of Browse and Search.

- `control-xl` (44 px) high, `rim`-tinted glass, `radius-pill`, 14 px padding, 10 px gaps.
- Leading `icon-md` search icon and placeholder in `text-muted`; typed text `body-large` in `text`.
- A clear button (✕, "Clear search") appears once there is text.
- The placeholder names where the search goes ("Search Comick"); it is also the field's accessible name.
- **The consumer provides** `value`, `onValueChange`, `placeholder`, optionally a focus requester (Ctrl+F).
