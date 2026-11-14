Beako is a manga reader for Windows, based on the [Neko](https://github.com/nekomangaorg/Neko) Android app (Apache-2.0).

## Beako 0.1.3

**Install:** download `Beako-0.1.3-windows-x64.msi` below and run it; it upgrades earlier versions in place. The installer isn't code-signed yet, so Windows SmartScreen may warn you: choose *More info → Run anyway*. The `.sha256` file lets you check the download.

### Fixed
- **Everything timed out after a restart in 0.1.2**, MangaDex included, on some networks. Automatic DNS now asks both Windows DNS and DNS over HTTPS, and Beako tries every address they give at once, keeping whichever connects first. A blocked or unreachable answer from either side no longer stops it. If you switched DNS to "Windows DNS only" as a workaround, you can set it back to Automatic.
- **The reader got stuck after moving to the next chapter:** arrow keys stopped working and the controls stayed hidden. Reader keys now work whatever was clicked last, and every chapter opens with its controls showing.

### For bug reports
- `%APPDATA%\Beako\beako.log` now also notes, once per site, what Windows DNS and DNS over HTTPS returned, so connection problems can be pinned down.

### Still to come
- Dart-based Mangayomi sources (most sources in the big repositories); JavaScript ones work.
- Discord Rich Presence works if you paste a Discord application ID in More → Discord; a built-in one is coming.

Beako uses comick.dev only for discovery and tracking: it never reads chapters or images from Comick. Not affiliated with Comick, MangaDex, Mangayomi or Neko. The extension bridge is adapted from [Mangayomi](https://github.com/kodjodevf/mangayomi) (Apache-2.0).
