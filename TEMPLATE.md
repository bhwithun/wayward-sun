# _template — Android Project Template

**This folder is a template. It is not a real project.**

Its only purpose is to be **copied** when you want to start a new Android application.

---

## Intentions

The goal of this template is to eliminate repetitive boilerplate work every time a new Android app is created.

Specifically, it provides (so you don't have to build them from scratch):

- Correct, modern Gradle + version catalog setup
- Jetpack Compose + Material 3 + Navigation Compose + ViewModel architecture
- A clean, consistent package structure
- Dark mode as the intentional default theme
- Common utilities that almost every app eventually needs:
  - Simple persistent storage (`AppStorage` using DataStore)
  - Intent helpers (share, SMS, dial, email, open URL)
  - Deep link handling utilities + example manifest entries
- A working minimal example app (Home + Detail screen with navigation and argument passing)
- Proper `.gitignore`, proguard rules, and placeholder adaptive icons
- Agent instructions (`AGENTS.md`) so future sessions understand the preferred patterns

By copying this folder, a new project starts with a solid, consistent foundation instead of reinventing the same files repeatedly.

---

## How to Use This Template

### Option 1: Manual Copy

1. Copy the entire `_template` folder to a new location (usually as a sibling folder).
2. Rename the copied folder to your app name (e.g. `my-awesome-app`).
3. Inside the new folder, perform these mechanical updates:
   - Replace the placeholder package `com.example.template` with your real package name everywhere (Kotlin files + both `build.gradle.kts` files).
   - Update `applicationId` and `namespace` in `app/build.gradle.kts`.
   - Update `rootProject.name` in `settings.gradle.kts` (recommended).
   - Change the app name in `app/src/main/res/values/strings.xml`.
   - Replace the launcher icons with real ones.
4. Clean and build:
   ```powershell
   ./gradlew clean
   ./gradlew assembleDebug
   ```

### Option 2: Ask Grok

Tell Grok something like:

> "Copy everything from ../_template into this empty folder, then adapt it for [short description of the new app]."

Grok will copy the template and perform the initial adaptation and renames.

---

## What Happens After Copying?

- The copied folder **becomes your real project**.
- You should:
  - Finish the package + name + icon renames.
  - Delete or replace `TEMPLATE.md` (this file) if you don't want it in your project.
  - Turn the regular `README.md` into proper project documentation.
  - Adapt the initial screens, theme colors, and navigation to match your actual app.
- The `AGENTS.md` file can stay (or be updated) to guide future development.

---

## Key Design Decisions (Intentions)

| Decision                    | Reason |
|----------------------------|--------|
| Dark mode only by default  | Preferred aesthetic and consistency across apps started from this template |
| Single-module structure    | Simplicity. Most new apps don't need multi-module immediately |
| DataStore via AppStorage   | Lightweight, no Room or database until actually needed |
| IntentUtils + DeepLinkUtils| These helpers are written repeatedly. Having them ready saves time |
| Version catalog            | Keeps dependency versions in one place and avoids drift |
| Compose + Material 3       | Modern Android UI standard |
| Minimal but working example| Enough to prove the skeleton works, easy to delete or extend |

---

## Important

- **Do not edit `_template` directly** as if it were a normal project (except when intentionally improving the template itself).
- Every time you want a new Android app, start by copying this folder.
- The template is deliberately opinionated so that all future apps share the same healthy starting patterns.

Copy it. Rename it. Build something great.