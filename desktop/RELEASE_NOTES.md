Beako is a manga reader for Windows, based on the [Neko](https://github.com/nekomangaorg/Neko) Android app (Apache-2.0).

## Beako 0.1.5

**Install:** download `Beako-0.1.5-windows-x64.msi` below and run it; it upgrades earlier versions in place. The installer isn't code-signed yet, so Windows SmartScreen may warn you: choose *More info → Run anyway*. The `.sha256` file lets you check the download.

### New
- **27 more colour schemes**, in More → Appearance. Neko's themes come across in both light and dark: Kitsune Fire, Miami Vice, Cherry Blossom, Crayon, Royal, Neon Nights, Nord, Tokyo Night, Tako, Teal Ocean, Jungle, Citrus Squeeze, Chocolate, plus Monochrome (light). Each brings its own background, text and accent colour, adjusted where needed so text stays easy to read. Schemes are shown as small previews, in a dark row and a light row.
- **Follow Windows light or dark mode:** pick a light and a dark scheme, and Beako switches between them with Windows.
- **A new Beako logo** for the window, taskbar, menu and installer.
- **Search results have a right-click menu:** Add to library, Track on Comick and Copy link.
- **Ctrl+F** jumps to the search box on Library and Search (elsewhere it opens Search).
- **Keyboard focus is always visible:** every button, chip, tab, card and row shows a ring when you reach it with Tab. Icon-only buttons and the collapsed menu's icons show their name on hover.
- **Beako follows Windows' own settings** for animation and transparency effects, on top of its own switches.
- **Richer glass:** the menu and card captions take on more colour from what's behind them.
- **Windows frame:** the title bar matches the colour scheme, and on Windows 11 it uses Mica (More → Appearance → Mica window frame).

### Fixed
- On the title page, **Start reading / Continue reading stretched across the whole row** in 0.1.4 and hid the Download, library and source buttons.

### Still to come
- Dart-based Mangayomi sources (most sources in the big repositories); JavaScript ones work.
- Discord Rich Presence works if you paste a Discord application ID in More → Discord; a built-in one is coming.

Beako uses comick.dev only for discovery and tracking: it never reads chapters or images from Comick. Not affiliated with Comick, MangaDex, Mangayomi or Neko. The extension bridge is adapted from [Mangayomi](https://github.com/kodjodevf/mangayomi) (Apache-2.0).
