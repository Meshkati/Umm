# 3 — Settings and dark theme

## Task

Add a dark theme, chosen on a new Settings screen: System default (the initial value), Light or Dark.

## Why

Umm was light only, so it glared at night and broke from a phone set to dark. Its pause screen
in particular shows up on top of whatever app is being opened.

## How

- **Colors** — `UmmTheme` (`Theme.kt`) uses Material 3's baseline `lightColorScheme()` or
  `darkColorScheme()`, the same purple the app already had through the plain `MaterialTheme`,
  so no custom palette. `OpenedColor` is the one hard-coded color that needed a dark value
  (`#DB8A3A`, against `#C8741F` in light), and it reads the theme through a composition local.
  `MainActivity` and `PauseActivity` both wrap their content in `UmmTheme`.
- **Choice** — stored as `ThemeMode` in `UmmPrefs` (`theme`). On Android 12+ it is also passed to
  `UiModeManager.setApplicationNightMode`, so the window theme (`values-night/themes.xml`) and
  the starting window match before Compose draws, so the pause screen doesn't flash white over
  the app being opened. That call recreates the activity; the screen survives because it is
  `rememberSaveable`. On Android 8–11, a theme that differs from the system's only applies once
  Compose draws.
- **Bars** — `UmmTheme` sets light or dark status and navigation bar icons itself, because the
  window theme follows the system, which may disagree with the choice.
- **Settings screen** — an *Appearance* section with three radio rows (the whole row is the
  target, via `selectable`), then an *About Umm* row. The home top bar shows Stats and Settings;
  About moved into Settings, and back from About now goes to Settings.
- **Design** — the "Umm: Settings & dark theme" row on the design canvas: Settings in both
  themes, and Home, Stats, About and the pause screen in dark.

**Left out on purpose** — dynamic color (Material You): Umm keeps its own purple. The delay
chips stay on the home screen.

**Verified** — on the API 36 emulator: each choice applies at once, and the screen stays on
Settings through the recreate. With Dark: Home, Stats (seeded history), About and the pause
screen (countdown and question). Back from About goes to Settings. System default follows
`cmd uimode night yes`, and Light stays light while the phone is dark.
