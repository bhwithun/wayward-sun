# Android App Template

A minimal, production-ready starting point for new pure Android apps built with:

- Kotlin + Jetpack Compose + Material 3
- Navigation Compose
- ViewModel + StateFlow
- DataStore (simple storage)
- Common utilities (intents, deep links)
- Dark mode by default (the preferred style)

This template exists so you (or Grok) never have to reinvent Gradle setup, manifest, basic architecture, theme, or everyday helpers again.

---

## How to Use This Template

### Method A — Manual copy + rename

1. Copy the entire `_template` folder to a sibling location and give it a good name, e.g.:

   ```
   cp -r _template my-new-app
   ```

2. Perform these renames (search & replace is your friend):

   - Package name: `com.example.template` → your real reverse-domain package  
     (do this in **all** `.kt` files and both `build.gradle.kts` files)

   - In `app/build.gradle.kts`:
     - `namespace = "com.example.template"`
     - `applicationId = "com.example.template"`

   - In `settings.gradle.kts`:
     - `rootProject.name = "my-new-app"` (optional but recommended)

   - In `app/src/main/res/values/strings.xml`:
     - Update `app_name`

   - Replace the launcher icons (see below).

3. (Optional but clean) Run from inside the new folder:

   ```powershell
   ./gradlew clean
   ```

4. Open the folder in Android Studio or build from command line:

   ```powershell
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

### Method B — Tell Grok

> "Copy everything from ../_template into this empty folder, then adapt it for [description of the app]."

Grok will copy the skeleton and do the mechanical renames + minimal adaptation for your new app.

---

## What You Get

- Modern Gradle + version catalog setup (AGP 8.5.2, Kotlin 2.0.21, Gradle 8.9)
- Compose + Material 3 + Navigation + ViewModel boilerplate
- Dark theme (default and intentional)
- `AppStorage` — simple DataStore wrapper for preferences
- `IntentUtils` — share, SMS, dial, email, open URL
- `DeepLinkUtils` — example deep link parsing + navigation
- Example `HomeScreen` + `DetailScreen` with argument passing
- Example `HomeViewModel`
- Adaptive launcher icons (placeholder) + notes on how to replace them
- Proper `.gitignore`

---

## Replacing the Launcher Icon

The modern adaptive icon lives in:

```
app/src/main/res/mipmap-anydpi-v26/
```

Legacy density folders also exist for older devices.

**Recommended:** Use Android Studio → **New → Image Asset** and generate from a vector or 1024×1024 PNG.

After generating, delete or replace the placeholder files as needed.

---

## Testing Deep Links (from a computer)

```powershell
# HTTP example
adb shell am start -a android.intent.action.VIEW -d "https://template.example.com/detail/123"

# Custom scheme example
adb shell am start -a android.intent.action.VIEW -d "templateapp://open/detail/99"
```

Update the schemes/hosts in `AndroidManifest.xml` for your real app.

---

## Common Gradle Commands

From the project root:

```powershell
./gradlew tasks                  # list available tasks
./gradlew assembleDebug
./gradlew installDebug           # installs on connected device/emulator
./gradlew clean
./gradlew bundleRelease
```

---

## After Adapting

- Delete or heavily rewrite this `README.md` with your real project documentation.
- Update `AGENTS.md` if your team has additional rules.
- Consider adding unit/instrumented tests when the project grows.

---

## Philosophy of This Template

- Dark mode is the default.
- Keep things simple and explicit.
- Put reusable "I always end up writing this" code in `util/` or `data/`.
- Use the version catalog. Never hardcode dependency versions in build files.
- Navigation is centralized in the `navigation/` package.
- State lives in ViewModels using StateFlow.

Enjoy shipping faster.
