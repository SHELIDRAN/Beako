# Custom Rules

- **Commit once per feature**: When a feature or roadmap item is done and the desktop build and tests pass, commit it (see `CLAUDE.md`). Keep each commit to one feature so it can be reviewed and reverted on its own.
- **Commit message style**: Use conventional commit standards, using `ref:` instead of `refactor:`. The commit message header must not include a scope in parentheses (e.g., use `style: description` instead of `style(scope): description`).
- **Always import classes when possible**: Do not use fully qualified class names in type signatures or code references (e.g. use `viewModel: ReaderViewModel` instead of `viewModel: eu.kanade.tachiyomi.ui.reader.ReaderViewModel` by adding the corresponding `import` statement at the top of the file).
- **Always use Neko `Size` tokens**: In Jetpack Compose layouts, paddings, and dimension specifications, always use `org.nekomanga.presentation.theme.Size` tokens (e.g. `Size.small`, `Size.medium`, `Size.large`, `Size.extraLarge`) instead of arbitrary hardcoded `.dp` values whenever possible.
- **Always hoist Composable state**: Keep composables stateless and decoupled by hoisting state up to caller screens/containers, accepting plain data models / UI state and emitting events via lambda callbacks (e.g., `onClick`, `onValueChange`). Avoid coupling child composables directly to ViewModels.
- **Always use `collectAsStateWithLifecycle()`**: When collecting `StateFlow` or `Flow` streams inside Jetpack Compose, always use `collectAsStateWithLifecycle()` from `androidx.lifecycle.compose` to ensure safe, lifecycle-bounded coroutine execution.
- **Always use `TimberKt` for logging**: Use Neko's structured `TimberKt` logging wrapper (`TimberKt.d { ... }`, `TimberKt.e(throwable) { ... }`) rather than `android.util.Log`, `println`, or raw `Timber`.
- **Never hardcode user-facing strings**: Always define user-facing text in `strings.xml` and reference them via `stringResource(R.string.xyz)` or `context.getString(R.string.xyz)`.
- **Always prefer Material 3 theme colors**: In Compose, prefer `MaterialTheme.colorScheme` tokens (e.g., `surface`, `primary`, `background`, `onSurfaceVariant`) over hardcoded color literals (`Color(0xFF...)` or `android.graphics.Color`).
- **Always enforce immutable UI state**: UI state models should be immutable `data class`es containing only `val` properties to ensure predictable Compose recomposition and prevent state mutation bugs.
- **Always format Kotlin code with `ktfmt`**: After making Kotlin code modifications, format files with `./gradlew ktfmtFormat` to maintain repo code style only after all changes are made and before committing.

