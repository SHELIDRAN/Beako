# Neko for Windows

## Goal
Port the Neko manga reader (Kotlin / Jetpack Compose, Android) to a native Windows desktop app using Compose Multiplatform for Desktop.

## The plan
Read `docs/PLAN.md` before starting any task. It holds the feature list, the fork-only features (unified manga page across sources, tag browse + popular page, AniList two-way sync, liquid glass design) and the phased roadmap.

## How to work
- Work on one roadmap item at a time; confirm which phase and item before writing code.
- Before moving code, check whether it uses Android-only APIs (`android.*`, WorkManager, Room, Coil 2, Android views) and propose the desktop replacement from the plan.
- Keep shared logic (models, API clients, parsers, sync) in a `shared` module; Windows UI in a `desktop` module.
- After every change, run the desktop Gradle build and tests; commit once per feature with a clear message.
- When a feature lands, tick its box in `docs/PLAN.md`.

## Rules
- Keep the Apache-2.0 LICENSE and upstream copyright notices.
- Do not use Comick or its lookalike domains; get tags, genres and popularity from the AniList GraphQL API and MangaDex.
- Respect MangaDex and AniList API rate limits.
