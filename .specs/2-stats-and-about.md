# 2 — Stats and About screens

## Task

Add a Stats screen showing how often the user turned back versus opened a paused app,
overall and per app, and an About screen with the version, a privacy summary and links.

## Why

Without numbers there was no way to see whether the pause was working. There was also
nowhere in the app to find the version, the source or the license.

## How

- **Logging** — `PauseActivity` records one `Outcome` per pause through `PauseLog`, which
  appends a `millis outcome package` line to a private file, `pauses.log`. The outcomes are
  `TURNED_BACK` ("Not now", or back after the countdown), `OPENED` ("Open anyway") and
  `LEFT` (the screen was finished without a choice, e.g. Home during the countdown; caught
  in `onDestroy` when `isFinishing`). `LEFT` counts as turned back, and the per-app list
  shows it as "Left without choosing". A `recorded` flag stops a choice followed by
  `finish()` from also being logged as `LEFT`. Rotation doesn't finish the activity, so it
  isn't logged. The whole history is kept, because a line is a few dozen bytes.
- **Totals** — `summarize()` in `Stats.kt` turns events into totals, chart buckets and
  per-app counts:
  - Week: the last 7 days, one bar per day.
  - Month: the last 4 weeks, one bar per 7 days.
  - All time: one bar per calendar month, showing at most the last 12 months. The totals
    still cover everything.
- **Navigation** — `MainActivity` switches between `Screen`s held in `rememberSaveable`,
  wrapped in a `SaveableStateHolder` so the home screen keeps its search and scroll position
  while another screen is open. Back goes to `Screen.parent`. The home top bar has Stats
  and About icons. Bottom tabs were left out: two tabs is fewer than Material recommends,
  and About is rarely opened.
- **Stats screen** — has a period selector, the turned-back share, three count tiles, a
  stacked bar chart and a per-app list (bar length ∝ pauses). Tapping an app opens its own
  totals, its chart, its last 10 pauses and a "Stop pausing before …" button. Reset asks
  for confirmation. With no history at all, an empty state links back to the app list.
  The log is re-read on every `onResume`, since pauses happen while Umm is in the
  background.
- **About** — the version is read from `PackageInfo`, so it follows `appVersion`. It also
  has a privacy summary (the manifest has no internet permission) and links to the repo,
  its issues page and the GPL v3 license.
- **Design** — screens follow the mockups on the design canvas. Turned back uses the theme's
  primary color; opened anyway uses `OpenedColor` (orange). Icons are vector drawables, so
  there's no material-icons dependency.

**Left out on purpose** — a "this week" teaser card on the home screen, trimming old history.

**Verified** — on the API 36 emulator, with seeded history: Week, Month and All time
totals and charts, the per-app screen, an uninstalled app shown by package name with a
letter icon, the reset dialog, the empty state, and About. With the service on and Chrome
marked, "Not now", Home during the countdown, and "Open anyway" each logged exactly one
line (`TURNED_BACK`, `LEFT`, `OPENED`).
