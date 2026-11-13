# Neko for Windows

## Goal
Port the Neko manga reader (Kotlin / Jetpack Compose, Android) to a native Windows desktop app using Compose Multiplatform for Desktop.

## The plan
Read `docs/PLAN.md` before starting any task. It holds the feature list, the fork-only features (unified manga page across sources, tag browse + popular page, AniList two-way sync, liquid glass design) and the phased roadmap.

## Build
The port is Windows-only. Build with the `-PdesktopOnly` switch, which skips the Android modules (`:app`, `:core`, `:constants`, `:baselineprofile`) and the Android Gradle Plugin, so no Android SDK is needed. The Android code stays in the repo as the source to port from.

- Run the app: `./gradlew -PdesktopOnly :desktop:run`
- Build and test: `./gradlew -PdesktopOnly :desktop:build`
- Windows installer (on Windows): `./gradlew -PdesktopOnly :desktop:packageMsi`

## Modules
- `desktop`: Compose Multiplatform for Desktop app (entry point `org.nekomanga.desktop.MainKt`). Versions in `gradle/cmp.versions.toml` (catalog `cmp`).
- `shared`: not created yet (Phase 0).
- `rust/neko-desktop`: the earlier Rust/egui Windows prototype (0.4.0), a second read-only porting source with its own CI (`.github/workflows/rust-desktop.yml`). See "Rust prototype" in `docs/PLAN.md`. Don't port its Comick code.
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
- Do not use Comick or its lookalike domains; get tags, genres and popularity from the AniList GraphQL API and MangaDex.
- Respect MangaDex and AniList API rate limits.
