Beako is a manga reader for Windows, based on the [Neko](https://github.com/nekomangaorg/Neko) Android app (Apache-2.0).

## Beako 0.1.8

**Install:** download `Beako-0.1.8-windows-x64.msi` below and run it; it upgrades earlier versions in place. The installer isn't code-signed yet, so Windows SmartScreen may warn you: choose *More info → Run anyway*. The `.sha256` file lets you check the download.

### New
- **Browse › MangaDex:** Popular new (most followed, added in the last 30 days), Latest updates, Recently added and Top rated, plus a **Feed** of new chapters from the titles you follow, grouped by title. Feed chapters open straight in the reader. Sign in to MangaDex to see the feed.
- **About 240 more sources.** Most Dart sources in Mangayomi repositories are built on the Madara, MangaReader and MangaBox templates; Beako now runs those through built-in JavaScript versions of the templates, in the same sandbox as other sources.
- **Full screen by default** (More → Appearance → Start in full screen). F11 switches anywhere, and closing the reader keeps the app in full screen.

### Fixed
- **Less lag** with big libraries: saving the library no longer runs on the UI thread on every page turn or update check, and long grids scroll more smoothly.
- **The side menu collapses again** after you click a destination; it only stays open while you navigate with the keyboard.

### Still to come
- Dart sources not built on the Madara, MangaReader or MangaBox templates; JavaScript sources and those templates work.
- Discord Rich Presence works if you paste a Discord application ID in More → Discord; a built-in one is coming.

Beako uses comick.dev only for discovery and tracking: it never reads chapters or images from Comick. Not affiliated with Comick, MangaDex, Mangayomi or Neko. The extension bridge is adapted from [Mangayomi](https://github.com/kodjodevf/mangayomi) (Apache-2.0).
