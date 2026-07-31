# Android Template - Agent Instructions

This folder (`_template`) is the canonical skeleton used to bootstrap new pure Android applications.

## Project Type
- Pure Android (no React Native, Flutter, or multi-platform frameworks unless explicitly requested).
- Language: Kotlin
- UI: Jetpack Compose + Material 3
- Architecture: Single-module by default, ViewModel + StateFlow, Navigation Compose
- Persistence (simple): DataStore Preferences via the provided `AppStorage` helper
- Theme: Dark mode is the intentional default

## Preferred Package Structure

```
com.example.<app>/
├── MainActivity.kt
├── data/
│   └── AppStorage.kt                 # Thin DataStore wrapper (extend here)
├── navigation/
│   ├── NavRoutes.kt
│   └── AppNavHost.kt
├── ui/
│   ├── theme/
│   │   └── Theme.kt                  # Always start dark-only
│   ├── components/                   # Small reusable Composables
│   └── screens/                      # One file per screen
├── viewmodel/                        # One ViewModel per major screen/feature
└── util/
    ├── IntentUtils.kt
    └── DeepLinkUtils.kt
```

When creating a new app:
1. Copy `_template` (or tell Grok to do it).
2. Immediately do a mechanical package rename (`com.example.template` → real package).
3. Update `applicationId`, `namespace`, `rootProject.name`, and `app_name`.
4. Replace icons.
5. Adapt the initial navigation graph and theme colors for the described app.

## Coding Standards

- **Compose**
  - Prefer small, focused composables.
  - Hoist state to the caller or ViewModel.
  - Use `remember`, `derivedStateOf`, and `LaunchedEffect` appropriately.
  - Never put long-running work or heavy logic directly in composables.

- **ViewModel + State**
  - Use `ViewModel` (or `AndroidViewModel` only when `Application` context is truly needed).
  - Expose `StateFlow` (or `SharedFlow` for events). Avoid holding mutable state in the UI.
  - Keep `UiState` data classes small and explicit.

- **Navigation**
  - Define all routes in `NavRoutes.kt`.
  - Pass only primitives or small parcelables as arguments.
  - Handle deep links in `DeepLinkUtils` + manifest.

- **Data & Storage**
  - Start with `AppStorage` (DataStore). Promote to a Repository only when you have multiple sources or complex logic.
  - Never hardcode keys as raw strings in multiple places — use the `Keys` object inside `AppStorage`.

- **Utilities**
  - Put "I always end up writing this" code in `util/`:
    - `IntentUtils` (share, SMS, dial, email, openUrl)
    - `DeepLinkUtils`
  - Extend these files rather than duplicating the helpers in every new project.

- **Dependencies**
  - Always use the version catalog (`gradle/libs.versions.toml`).
  - Do not add ad-hoc `implementation("group:artifact:1.2.3")` lines with raw versions.
  - Justify any new dependency in the commit or PR description.

- **Theme**
  - Dark is the default. The `TemplateTheme` ignores system light/dark.
  - Only introduce light mode support when the specific app has a clear requirement.
  - Keep color tokens in `Theme.kt` (or a small `Color.kt` if it grows).

## When Bootstrapping a New App from This Template

- Perform the rename **first** before writing domain logic.
- Keep the Gradle + manifest + navigation skeleton intact unless there is a strong reason to change it.
- Adapt:
  - App name and launcher icons
  - Theme colors
  - Initial screens and navigation graph
  - Any example ViewModel / storage usage to match the actual domain
- Delete or replace the template `README.md` with real project documentation.
- Update or keep `AGENTS.md` — future Grok sessions will read it.

## Do's

- Write clear, self-documenting code with good names.
- Add comments for non-obvious decisions (especially around deep links, storage keys, or navigation).
- Handle loading, error, and empty states in screens.
- Use `rememberCoroutineScope` + `LaunchedEffect` for one-off side effects in Composables.
- Keep pull requests focused.

## Don'ts

- Do not add Hilt, Room, Retrofit, Firebase, or other heavy frameworks "just in case".
- Do not duplicate the utilities already provided in `util/` and `data/AppStorage.kt`.
- Do not ignore the version catalog.
- Do not commit `local.properties`, build outputs, or IDE files.
- Avoid magic strings for routes, preference keys, or intent actions.

## Common Commands (PowerShell on Windows)

```powershell
# From inside a project created from the template
./gradlew clean
./gradlew assembleDebug
./gradlew installDebug

# Run on a specific device
./gradlew installDebug -Pandroid.injected.invoked.from.ide=true
```

## Deep Links (for testing)

```powershell
adb shell am start -a android.intent.action.VIEW -d "https://template.example.com/detail/123"
adb shell am start -a android.intent.action.VIEW -d "templateapp://open/detail/99"
```

Update the manifest and `DeepLinkUtils` when you define real deep link routes.

## Philosophy

This template exists to remove friction. When someone says "make a new Android app for X", the answer should be:

1. Copy template (or ask Grok)
2. Rename package
3. Start solving the actual problem

Everything else (Gradle, manifest, basic architecture, theme, common helpers) should already be correct and consistent.

Follow these rules. When in doubt, keep it simple, explicit, and dark.
