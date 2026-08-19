# Sol Widget

Android app + home-screen widget for a home PV system on [Solcast](https://toolkit.solcast.com.au/).

It loads the same data as the toolkit **Graph: Live and Forecasts** view:

- **Live** — `GET /rooftop_sites/{id}/estimated_actuals`
- **Forecast** — `GET /rooftop_sites/{id}/forecasts`

and plots predicted power (kW) plus today’s energy (kWh).

Default rooftop resource ID: `84d7-8b52-33f3-bd7b`.

## Setup

1. Open [toolkit.solcast.com.au](https://toolkit.solcast.com.au/) and copy your API key (account menu).
2. Install the app, open **Settings**, paste the API key, confirm the resource ID, then **Save and fetch**.
3. Long-press the Android home screen → **Widgets** → **PV Live & Forecast**.

The API key stays on the device in DataStore. It is not hardcoded in the project.

## Quota

Hobbyist Solcast accounts allow **10 requests per UTC day**. Each refresh uses **2** (live + forecast). The app:

- caches the last successful response
- auto-refreshes at most **4 times per UTC day** (about every 6 hours)
- lets you tap Refresh for extra fetches
- shows a demo curve until a key is saved or if the cache is empty

## Build

```powershell
cd C:\Users\brian\GrokProjects\sol-widget
.\gradlew assembleDebug
.\gradlew installDebug
.\gradlew installRelease
```

## Add the widget

After install: home screen → Widgets → **Sol Widget** / **PV Live & Forecast**. Tap the widget to open the full graph.
