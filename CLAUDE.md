# Beako

## Goal
Beako is a native Windows manga reader, built by porting the Neko manga reader (Kotlin / Jetpack Compose, Android) to Compose Multiplatform for Desktop. The app's name is **Beako** everywhere users see it (window title, installer, API registrations, User-Agent); credit Neko as the upstream project. Kotlin packages keep their `org.nekomanga` names.

## The plan
Read `docs/PLAN.md` before starting any task. It holds the feature list, the fork-only features (unified manga page across sources, tag browse + popular page, AniList two-way sync, liquid glass design) and the phased roadmap.

## The UI
Build every screen from `docs/UI_BLUEPRINT.md` (the owner's screen plan: sidebar, Library, Latest updates, Browse, Search with filters and results, history, settings; visual rules; the errors and gaps already decided; dependencies). Don't change its decisions without asking the owner. Comick's site is a layout reference only; Beako uses its own neutral liquid-glass style.

## Sources and discovery (owner's plan, Nov 13 2026)
Three separate jobs, three separate services:

1. **Find titles: comick.dev first, MangaDex as fallback.** The core reason for Comick is its **tag filtering** (searchable tri-state tags where included tags are compulsory matches) and its daily trending catalog; treat the tag picker as a must-have, specified in "2. Discovery" in `docs/PLAN.md`. comick.dev is Comick's official site, now branded "ComicK – The Tracker" (confirmed by the owner from the site, Nov 13 2026): a tracker and catalog like AniList. Its API still lists some chapters and images, but Beako never reads chapters from it. Use it as the main search engine for what's latest, popular and trending, and for its detailed tags (female lead, male lead, comedic misunderstanding and so on). MangaDex is the **fallback search**: when a Comick search can't answer, Beako searches MangaDex automatically and says so ("Showing MangaDex results — Comick is unavailable" or "…found no matches"), with a "Try Comick again" button. Fallback triggers: Comick unreachable, a Cloudflare HTML response, a 5xx error, a 429 rate limit, or zero results for a keyword search. The user can also switch a search to MangaDex by hand. MangaDex results open the same title page; Beako links them back to Comick through shared AniList/MyAnimeList/MangaUpdates IDs so tracking still works.
2. **Track reading: comick.dev and AniList**, both as trackers.
3. **Read chapters: installed Mangayomi-format extensions.** Chapters come from sources the user installs from a Mangayomi extension repository they add, found through [wotaku.wiki/ext/mangayomi](https://wotaku.wiki/ext/mangayomi) (Dart and JavaScript sources; repository list and plan in "2b" in `docs/PLAN.md`). The app ships with none. MangaDex is also a built-in reading source.

Flow: Browse (comick.dev search, tags, popular) → title page → an **"Available on"** dropdown lists the installed extensions that carry this title → pick one to read. The reader falls back to the next source when one is missing a chapter (see the unified manga page in `docs/PLAN.md`).

### Comick developer app (registered Nov 13, 2026)
- App name: Beako. Client type: public ("Browser or mobile app without a private backend"), so OAuth 2.0 with S256 PKCE and **no client secret**.
- Client ID: `hySzjzgfgtlSwqnDsdXspsPdWTqnjVzW` (public; safe to keep in source).
- Callback URL: `http://127.0.0.1:47823/comick/callback` (the app listens on that local port only while signing in).
- Scopes: `library:read library:write offline_access`, sent with `resource=https://api.comick.dev/integrations/v1`. Refresh token in Windows Credential Manager, access token in memory only.
- Catalog requests (search, tags, charts) need no login. Send a `User-Agent` naming Beako and honor `Cache-Control`.
- Full API notes: `docs/api/COMICK.md` (OAuth, catalog and library endpoints, tracker mapping, limits); spec: `docs/api/comick-openapi.json`. Follow them for all Comick code. Key rules: refresh tokens rotate with no grace period (serialize refreshes, never retry an ambiguous one); catalog limit 200 requests/min per IP, library 60/min per app+user; check `Content-Type` before parsing because Cloudflare can answer with HTML.
- Comick's catalog API answers Beako's own HTTP client (OkHttp with the Beako User-Agent), including from this container; only `curl` and the web fetcher get Cloudflare's browser check, and the docs pages stay blocked. Unit tests use real captured responses in `shared/src/test/resources/fixtures/comick`; `BEAKO_LIVE=1 ./gradlew -PdesktopOnly :shared:test` runs the live checks. Verified live (Nov 13 2026): included tags are all-of (compulsory), "Most Recent Popular" is `/top?type=trending&day=N` (a map keyed by window), "Most Followed New Comics" is a search sorted by `user_follow_count` with `time=N`.

## Build
The port is Windows-only. Build with the `-PdesktopOnly` switch, which skips the Android modules (`:app`, `:core`, `:constants`, `:baselineprofile`) and the Android Gradle Plugin, so no Android SDK is needed. The Android code stays in the repo as the source to port from.

- Run the app: `./gradlew -PdesktopOnly :desktop:run`
- Build and test everything: `./gradlew -PdesktopOnly build` (if ktfmt's check fails, run `./gradlew -PdesktopOnly ktfmtFormat` first)
- Windows installer (on Windows): `./gradlew -PdesktopOnly :desktop:packageMsi`

- CI: `.github/workflows/beako-desktop.yml` builds, tests and packages the MSI on `windows-latest`; a `beako-v<version>` tag publishes a release. The version is `beakoVersion` in `gradle.properties`.

## Modules
- `desktop`: Compose Multiplatform for Desktop app (entry point `org.nekomanga.desktop.MainKt`). Versions in `gradle/cmp.versions.toml` (catalog `cmp`). Layout: `theme` (colours, type, glass, droplet), `components` (sidebar, cards, controls), `state` (app container, navigation, screen models), `screens`. `BEAKO_OPEN=search:<query>`, `title:<query>`, `read:<query>` (first MangaDex match) or `screen:<library|updates|browse|history|more>` starts the app there, for screenshots; `BEAKO_HOME=<dir>` keeps all app data in that folder.
- `shared`: Android-free Kotlin/JVM logic (`org.nekomanga.shared`): HTTP with rate limits and typed errors (`net`), models, `MangaDexApi`, `ComickApi` (catalog only), and `Discovery` (Comick-first search with MangaDex fallback, title matching). MangaDex DTOs are copied from Neko under their original package. Tests use fixtures in `shared/src/test/resources/fixtures` (MangaDex ones captured live, Comick ones constructed); `BEAKO_LIVE=1 ./gradlew -PdesktopOnly :shared:test` also runs live MangaDex tests.
- `rust/neko-desktop`: the earlier Rust/egui Windows prototype (0.4.0), a second read-only porting source with its own CI (`.github/workflows/rust-desktop.yml`). See "Rust prototype" in `docs/PLAN.md`. Its comick.dev catalog, tag search, charts and tracker are worth porting.
- `app`, `core`, `constants`, `baselineprofile`: upstream Android code, read-only porting source. The Android Compose catalog is `androidxCompose`, because the name `compose` clashes with the Compose Multiplatform plugin's extension.

## How to work
- Work on one roadmap item at a time; confirm which phase and item before writing code.
- Before moving code, check whether it uses Android-only APIs (`android.*`, WorkManager, Room, Coil 2, Android views) and propose the desktop replacement from the plan.
- Keep shared logic (models, API clients, parsers, sync) in a `shared` module; Windows UI in a `desktop` module.
- After every change, run the desktop Gradle build and tests; commit once per feature with a clear message. These commit rules override older guidance in `.agents/AGENTS.md`.
- When a feature lands, tick its box in `docs/PLAN.md`.

## Skills and plugins (use automatically, no need to ask)
- `apollo-skills:apollo-kotlin` + `apollo-skills:graphql-operations`: any AniList GraphQL work (tag browse, popular/trending, sync).
- `design-skills:design-systems`, `design-skills:interaction-design`: liquid glass design tokens, reader controls, source dropdown.
- `apple-design` (in `.claude/skills`): Apple HIG reference for the liquid glass look (materials, motion, liquid-glass pages); adapt to Compose.
- `design-skills:accessibility-audit`: check text contrast on glass panels before a UI feature lands.
- `design-skills:ux-writing`: labels, empty, error and fallback messages on every new screen.
- `/simplify`: after porting a chunk of Android code into `shared` or `desktop`.
- `/code-review` (default level): before committing each roadmap item.
- `/security-review`: before committing any login, OAuth or token-storage code.
- `/run`: to launch the desktop app and screenshot a UI change.
- Skip: `liquid-glass:*` (SwiftUI only, reference at most), `vercel:*` (no web app here), Android emulator tools.

## Rules
- Keep the Apache-2.0 LICENSE and upstream copyright notices.
- comick.dev is allowed only as a catalog and tracker (search, tags, popular/trending, library sync through its official developer API and OAuth). **Never read chapters or page images from Comick** (owner's decision, Nov 13 2026): don't call its chapter-list, chapter-detail or `get_images` endpoints for reading, and never offer Comick in the "Available on" dropdown. AniList and MangaDex stay as secondary sources of tags and popularity.
- Ship no extensions or extension repositories; users add their own. Load extensions with the least access possible and say clearly that they are third-party code.
- Respect MangaDex and AniList API rate limits.

## Architecture and engineering rules (owner, Nov 14 2026)

### 1. Repository map
- `shared/src/main/kotlin/org/nekomanga/shared/extensions/`: JS extension runtime (`JsSource`), HTTP wrapper (`ExtensionHttp`), repository/install manager (`ExtensionManager`) and multi-source resolver (`ReadingSources`).
- `net` package: sealed `ApiError` hierarchy (including `ApiError.SourceFailed`).
- `desktop/src/main/kotlin/org/nekomanga/desktop/state/`: desktop UI state and wiring (`AppContainer`, `DetailsModel`).
- `desktop/src/test/kotlin/org/nekomanga/desktop/`: desktop unit and UI tests (`DetailsModelTest`, etc.).
- `shared/.../diagnostics/DiagnosticLog`: the diagnostic log; `desktop/.../platform/SelfTest`: the packaged-runtime self-test.

### 2. Build, format and test commands
- **Always format before building or committing:** `./gradlew -PdesktopOnly ktfmtFormat -q`
- **Full desktop build and test (constrained worker mode):** `./gradlew -PdesktopOnly build --console=plain --max-workers=1`
- **Run a single test class:**
  - `./gradlew -PdesktopOnly :desktop:test --tests "org.nekomanga.desktop.<TestClassName>" --max-workers=1`
  - `./gradlew -PdesktopOnly :shared:test --tests "org.nekomanga.shared.<TestClassName>" --max-workers=1`
- **Completion gate:** never declare a task complete until `ktfmtFormat` has run and the JUnit XML reports in `desktop/build/test-results/test/` (and `shared/build/test-results/test/` if `shared` changed) confirm the new tests ran and were not skipped.

### 3. Kotlin and coroutine testing rules (strict)
- **No `Unit?` test signatures:** JUnit silently skips `@Test` functions whose inferred return type is `Unit?` (common when an expression-body test ends with a `?.` safe call). Prefer block bodies (`fun myTest() { runTest { ... } }`) or annotate `: Unit` explicitly.
- **Virtual vs. real time:** `runTest` uses a virtual clock that fires timeouts instantly while background threads (such as `JsSource` execution) run in wall-clock time. When testing real extension execution or I/O dispatchers, poll with `kotlinx.coroutines.withTimeout` on a real dispatcher instead of relying on virtual delay.
- **Verify execution in XML:** after running Gradle tests, check `TEST-*.xml` that the `tests` count went up and `skipped` did not.

### 4. Extension sandbox and security boundaries (high priority)
The JS extension runtime executes untrusted third-party scripts and is the highest-risk surface in the app:
- **Host policy enforcement:** every outbound request (script downloads in `ExtensionManager`, API calls in `ExtensionHttp`, and chapter/cover image loads in `AppContainer` / the image loader) MUST be validated against the extension host policy before connecting. Never pass raw extension URLs to an unrestricted image loader.
- **Execution timeouts and cancellation:** synchronous JS execution (`JsSource`) must enforce strict timeouts/interrupts so infinite loops can't hang worker threads. Closing or unloading a busy source must cancel pending work without blocking the caller or the UI thread.
- **Bounded responses:** all HTTP responses read for extensions (`ExtensionHttp`) must enforce a strict maximum size to prevent out-of-memory crashes from malicious or broken servers.
- **Error mapping:** convert extension `IOException` and sandbox failures into `ApiError.SourceFailed` inside `ReadingSources`, so upstream error handling covers extensions uniformly.

### 5. Multi-source and storage conventions
- **Windows path safety:** Windows enforces a 260-character `MAX_PATH`. Any chapter ID or URL longer than 40 characters MUST be hashed deterministically when naming download folders; shorter IDs (such as 36-character MangaDex UUIDs) stay unchanged.
- **Source switching and gap filling:**
  - Cache per-title source search results in `ReadingSources`, so reopening a title page doesn't re-query every installed extension.
  - Switching the active source must not trigger false "new chapter" notifications or leave stale "from <source>" labels on chapter rows.
  - Missing upload times from extensions fall back to unknown (null / empty), never `0L` (1970-01-01).

### 6. Packaging (Windows MSI; learned from 0.1.0 QA, Nov 14 2026)
- The installer bundles its own Java runtime. Keep `includeAllModules = true` in `desktop/build.gradle.kts` for 0.1.x: jpackage's automatic module list dropped `jdk.httpserver` (Comick and AniList sign-in crashed with `NoClassDefFoundError: com/sun/net/httpserver/HttpServer`) and `jdk.localedata`.
- Gradle tests run on a full JDK, so they can't catch a missing runtime module. When code starts depending on a new JDK area (a `com.sun.*` or `jdk.*` API, a charset, a locale, a security provider), add a check for it to `SelfTest`.
- `Beako.exe --self-test <report>` checks the bundled runtime and exits 0 or 1. CI runs it on the packaged app before uploading the installer; a failure must block the release. Locally: `./gradlew -PdesktopOnly :desktop:createDistributable`, then run `desktop/build/compose/binaries/main/app/Beako/bin/Beako --self-test <file>`.

### 7. Title page and source picker (UI rules)
- The "Reading from <source>" / "Not available on any source" pill is always clickable and opens the Source Picker. Never make source choice depend on automatic matching having found something.
- The picker lists MangaDex and every installed, enabled source with its state: Matched, No automatic match, Checking, or Error with the source's message. A failed source is shown, never hidden; the pill adds "· N failed".
- Every source in the picker has a manual search, prefilled with the title and offering its alternative names (romaji, original script) as one-click queries. Picking a result binds it (saved in the library entry's `sourceLinks`, or the MangaDex id) and switches to it. Comick is never listed.

### 8. Images and diagnostics
- Comick's resized covers are always JPEG: build `https://meo.comick.pictures/<key without extension>-m.jpg`, never keep the original's `.png` (those 404; about half the covers were blank in 0.1.0).
- Extension URLs are normalised like a browser would (spaces and illegal characters percent-encoded) and checked with OkHttp's URL parser, the same one that sends the request; never `java.net.URI`, which rejects addresses sites use.
- DNS defaults to **Automatic**: DNS over HTTPS (Cloudflare, then Google) with the system's DNS as fallback, and a brief cache. Some networks block sites by answering their lookups with addresses that never connect ("Connect timed out" for Comick, Weeb Central and Comix in 0.1.1 QA), which browsers' secure DNS avoids. Keep it the default; "Windows DNS only" and single providers stay as options.
- Before a search, a source's filter list comes from `__filters()`, which returns `[]` when the script's `getFilterList` throws (Mangafire), as Mangayomi does.
- `beako.log` in the data folder (`%APPDATA%\Beako` on Windows; `BEAKO_HOME/data` when set) records source failures (name, id, stack trace), image load failures and uncaught errors. It rotates at 1 MB to `beako.log.1`. Never log tokens, passwords or request headers.

### 9. Git and release workflow
- Work in small, verifiable checkpoints: implement → format → run targeted tests → commit.
- Never commit client secrets to source code. Desktop sign-in flows (AniList, Discord RPC) embed only public client/application IDs and use PKCE or local redirect flows where applicable.
