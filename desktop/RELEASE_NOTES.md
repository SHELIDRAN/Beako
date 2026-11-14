Beako is a manga reader for Windows, based on the [Neko](https://github.com/nekomangaorg/Neko) Android app (Apache-2.0).

## Beako 0.1.2

**Install:** download `Beako-0.1.2-windows-x64.msi` below and run it; it upgrades earlier versions in place. The installer isn't code-signed yet, so Windows SmartScreen may warn you: choose *More info → Run anyway*. The `.sha256` file lets you check the download.

### Fixed
- **Full screen kept the title bar** on Windows while the button said "Exit full screen". Full screen now covers the whole monitor, taskbar included, and leaving it puts the window back as it was.
- **Comick, Weeb Central and Comix timed out** on networks that block sites through DNS, while the browser could open them. DNS now defaults to **Automatic**: sites are looked up with DNS over HTTPS (like your browser's secure DNS), falling back to Windows DNS when that's unavailable. You can change it in More → Library updates and network.
- **Mangafire failed every search** ("getFilterList not implemented"): sources without a filter list now search without one, as in Mangayomi.

### Better error reports
- "Couldn't reach …" now says why: the address couldn't be found, the secure connection failed, the connection timed out, or it was refused.
- `%APPDATA%\Beako\beako.log` now records every failed connection (address and error, never passwords or sign-in tokens), and a broken image only once.

### Tip
- If Comix answers with "cannot read property 'items' of undefined", your copy of the Comix source uses Comix's old API. Install Comix from the m2k3a repository (`https://raw.githubusercontent.com/m2k3a/mangayomi-extensions/main/index.json`) in More → Sources instead.

### Still to come
- Dart-based Mangayomi sources (most sources in the big repositories); JavaScript ones work.
- Discord Rich Presence works if you paste a Discord application ID in More → Discord; a built-in one is coming.

Beako uses comick.dev only for discovery and tracking: it never reads chapters or images from Comick. Not affiliated with Comick, MangaDex, Mangayomi or Neko. The extension bridge is adapted from [Mangayomi](https://github.com/kodjodevf/mangayomi) (Apache-2.0).
