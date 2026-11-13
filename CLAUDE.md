# Beako

## Goal
Beako is a native Windows manga reader, built by porting the Neko manga reader (Kotlin / Jetpack Compose, Android) to Compose Multiplatform for Desktop. The app's name is **Beako** everywhere users see it (window title, installer, API registrations, User-Agent); credit Neko as the upstream project. Kotlin packages keep their `org.nekomanga` names.

## The plan
Read `docs/PLAN.md` before starting any task. It holds the feature list, the fork-only features (unified manga page across sources, tag browse + popular page, AniList two-way sync, liquid glass design) and the phased roadmap.

## Sources and discovery (owner's plan, Nov 13 2026)
Three separate jobs, three separate services:

1. **Find titles: comick.dev first, MangaDex second.** comick.dev is Comick's official site, now branded "ComicK – The Tracker" (confirmed by the owner from the site, Nov 13 2026): a tracker and catalog like AniList. Its API still lists some chapters and images, but Beako never reads chapters from it. Use it as the main search engine for what's latest, popular and trending, and for its detailed tags (female lead, male lead, comedic misunderstanding and so on). MangaDex is the secondary search.
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
- comick.dev and api.comick.dev sit behind a Cloudflare browser check that blocks cloud machines (this container, CI). Test Comick code against saved sample responses; live calls work from a normal Windows PC.

## Build
The port is Windows-only. Build with the `-PdesktopOnly` switch, which skips the Android modules (`:app`, `:core`, `:constants`, `:baselineprofile`) and the Android Gradle Plugin, so no Android SDK is needed. The Android code stays in the repo as the source to port from.

- Run the app: `./gradlew -PdesktopOnly :desktop:run`
- Build and test: `./gradlew -PdesktopOnly :desktop:build`
- Windows installer (on Windows): `./gradlew -PdesktopOnly :desktop:packageMsi`

## Modules
- `desktop`: Compose Multiplatform for Desktop app (entry point `org.nekomanga.desktop.MainKt`). Versions in `gradle/cmp.versions.toml` (catalog `cmp`).
- `shared`: not created yet (Phase 0).
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
- `/simplify`: after porting a chunk of Android code into `shared` or `desktop`.
- `/code-review` (default level): before committing each roadmap item.
- `/security-review`: before committing any login, OAuth or token-storage code.
- `/run`: to launch the desktop app and screenshot a UI change.
- Skip: `liquid-glass:*` (SwiftUI only, reference at most), `vercel:*` (no web app here), Android emulator tools.

## Rules
- Keep the Apache-2.0 LICENSE and upstream copyright notices.
- comick.dev is allowed only as a catalog and tracker (search, tags, popular/trending, library sync through its official developer API and OAuth). **Never read chapters or page images from Comick** (owner's decision, Nov 13 2026): don't call its chapter-list, chapter-detail or `get_images` endpoints for reading, and never offer Comick in the "Available on" dropdown. Never use Comick lookalike domains or any site claiming to host Comick chapters; they're reported unsafe. AniList and MangaDex stay as secondary sources of tags and popularity.
- Ship no extensions or extension repositories; users add their own. Load extensions with the least access possible and say clearly that they are third-party code.
- Respect MangaDex and AniList API rate limits.
