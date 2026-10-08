# 4 — Bottom navigation

## Task

Move Stats and Settings out of the home screen's top bar and into a bottom navigation bar,
with three tabs: Apps, Stats and Settings.

## Why

Stats and Settings were small icons in the corner of the home screen, and each opened as a
screen with a back arrow, as if it were a detail of the app list. With three top-level
places, a bottom bar is Android's native way to show them and keeps each one a tap away.
Spec 2 left bottom tabs out because two tabs is fewer than Material recommends; with
Settings added there are now three.

## How

- **Bar** — `MainActivity` wraps every screen in one outer `Scaffold` whose `bottomBar` is a
  Material 3 `NavigationBar` (`UmmNavigationBar`). Each `Tab` names its top-level `Screen`,
  icon and label; each `Screen` names the `tab` shown as selected, so the bar stays visible on
  the screens opened from inside a tab (one app's stats under Stats, About under Settings).
- **Insets** — the outer `Scaffold` takes no window insets of its own (`WindowInsets(0)`):
  it only reserves room for the bar, and consumes that padding, so the inner screens'
  `Scaffold`s still handle the status bar but don't add the navigation bar a second time.
- **Navigation** — tapping a tab opens its top-level screen, so tapping the current tab
  from a screen inside it goes back to the top. Back from Stats or Settings goes to Apps,
  as before (`Screen.parent`). Screens keep their saved state (search, scroll) across tab
  switches through the existing `SaveableStateHolder`.
- **Screens** — unchanged except that Stats and Settings lose their back arrow, and the
  home top bar loses its Stats and Settings icons. The Apps tab keeps the service card, the
  pause length chips and the app list. The Apps icon is a new vector drawable (`ic_apps`);
  Stats and Settings reuse theirs.
- **Design** — the "Option A" row on the "Umm bottom navigation sketches" design canvas.

**Left out on purpose** — remembering a tab's inner screen when switching away (switching
tabs always opens the tab's top level), and moving the service status and pause length into
Settings ("Option B" on the canvas).

**Verified** — not built or run: this session had no Android SDK, and its network policy
blocks Google's Maven repository, so it couldn't fetch the Android Gradle plugin. Needs a
check on a device or emulator: the three tabs, back from each, the bar on the per-app stats
and About screens, no double padding above the system navigation bar (gesture and
three-button), and the bar in dark theme.
