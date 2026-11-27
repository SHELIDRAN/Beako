Beako is a manga reader for Windows, based on the [Neko](https://github.com/nekomangaorg/Neko) Android app (Apache-2.0).

## Beako 0.1.11

**Install:** on 0.1.9 or 0.1.10, choose **Update now** when Beako offers this version (or More → General → About → Check for updates). Otherwise download `Beako-0.1.11-windows-x64.msi` below and run it; it upgrades earlier versions in place and keeps your library and settings. The installer isn't code-signed yet, so Windows SmartScreen may warn you: choose *More info → Run anyway*. The `.sha256` file lets you check the download.

### New
- **More sources:** Dart sources built on the MMRCMS and HeanCMS site templates (Scan VF, Read Comics Online, OmegaScans, YugenMangas, Perf Scan and others), plus MangaHere and AllAnime, now install and run.
- **More like this, your way:** More → Library → More like this has sliders for shared genres, popularity, rating, same type and newer titles.
- **Collapsible library categories:** click a category's heading to fold it away; Collapse all / Expand all in the toolbar. Remembered per grouping.
- **Sources by language:** More → Sources has a Language menu (with counts) and Sort by name or language.
- **Graphics setting:** More → Appearance → Graphics (Automatic, OpenGL, Software). If pages show coloured blocks or patches, try OpenGL, then Software; applies after a restart.

### Improved
- **The reader stays out of the way:** turning pages with the keys hides the controls, and the next chapter no longer brings them back.
- **Sharp webtoon pages** when scrolling fast: pages are always loaded at full resolution.
- **Smoother tabs:** the underline moves quickly without stalling, and new covers wait until it lands.
- **Rounded menus everywhere,** including right-click menus, in Beako's glass style.
- **Clearer source errors:** when a site refuses a source (as comix.to does now), Beako says what the site answered instead of the script's error.

### Still to come
- A few Dart sources (MangaSee and MangaLife have closed; MangaPark and Atsumaru have JavaScript versions to install instead).
- Discord Rich Presence works if you paste a Discord application ID in More → Discord; a built-in one is coming.

Beako uses comick.dev only for discovery and tracking: it never reads chapters or images from Comick. Not affiliated with Comick, MangaDex, Mangayomi or Neko. The extension bridge and site templates are adapted from [Mangayomi](https://github.com/kodjodevf/mangayomi) and its extensions (Apache-2.0). Country flags from [flag-icons](https://github.com/lipis/flag-icons) (MIT).
