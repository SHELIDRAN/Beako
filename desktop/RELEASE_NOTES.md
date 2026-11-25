Beako is a manga reader for Windows, based on the [Neko](https://github.com/nekomangaorg/Neko) Android app (Apache-2.0).

## Beako 0.1.7

**Install:** download `Beako-0.1.7-windows-x64.msi` below and run it; it upgrades earlier versions in place. The installer isn't code-signed yet, so Windows SmartScreen may warn you: choose *More info → Run anyway*. The `.sha256` file lets you check the download.

### New
More has two new tabs, **Library** and **Reader**, with settings brought over from Neko on Android.

- **Read manga from your PC.** Point Beako at a folder (More → Library → Local manga; `Documents\Beako Local` by default). Each folder in it is a series, and each folder of images or `.cbz`/`.zip` file inside is a chapter. Your series show on Browse under "On this PC" and work like any other title, update checks included.
- **Reader:** two pages side by side (or automatically when the window is wider than tall), crop plain borders, grayscale, a page between chapters showing what's next, skip chapters you've read or duplicate uploads, slide between pages, click-zone layouts (Kindle, edges, L-shaped or off), pages loaded ahead, keep the screen awake, a page number while the controls are hidden, a default reading mode and opening in full screen.
- **Title page:** buttons tinted with the cover's colour, the cover of the volume you're reading (MangaDex), a blurred cover behind the header (off, small, default or large), icon-only buttons, and every alternative title wrapped or on one scrolling line.
- **Library:** sort ignoring "a", "an" and "the", hide a chapter by several groups when any or all of them are hidden, a default category for new titles, and a category editor to rename, reorder and delete.
- **Library updates:** only while plugged in, skip series you've dropped, refresh covers and details from MangaDex, and choose which categories to check.
- **A new loading animation** for chapters and pages.

### Still to come
- Dart-based Mangayomi sources (most sources in the big repositories); JavaScript ones work.
- Discord Rich Presence works if you paste a Discord application ID in More → Discord; a built-in one is coming.

Beako uses comick.dev only for discovery and tracking: it never reads chapters or images from Comick. Not affiliated with Comick, MangaDex, Mangayomi or Neko. The extension bridge is adapted from [Mangayomi](https://github.com/kodjodevf/mangayomi) (Apache-2.0).
