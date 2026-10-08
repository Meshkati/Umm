# 0 — Pause before opening marked apps

## Task

Build the minimal first version of Umm: the user marks apps (e.g. Twitter, YouTube).
When a marked app is opened, a full-screen countdown (3 / 5 / 10 s, user's choice) appears,
then asks "Still want to open it?" with *Open anyway* / *Not now*. Nothing else.

## Why

Reduce screen time by adding friction at the moment of habit: opening an app on autopilot.
A short forced pause is enough to make the choice conscious without blocking anything outright.

## How

**Detection** — an `AccessibilityService` (`AppWatchService`) subscribed to
`TYPE_WINDOW_STATE_CHANGED`. It reads only `event.packageName`; `canRetrieveWindowContent`
is off. This is the one Android mechanism that reliably reports the foreground app without
special permissions beyond the accessibility toggle.

**Interrupt** — when the package is on the list, the service starts `PauseActivity`
(`FLAG_ACTIVITY_NEW_TASK`) over the target app. Accessibility services bound by the system
are exempt from background-activity-launch restrictions, so this works on Android 10+.
Back is a no-op during the countdown; after it, back = *Not now*.

**Decisions**
- *Open anyway* records `allowedPackage` in the service; no re-prompt while the user stays in
  that app. Any other foreground app, including the launcher, clears it, so home-and-back
  prompts again.
- Ignored packages: our own, `com.android.systemui`, and enabled keyboards (via
  `InputMethodManager`; `Settings.Secure.ENABLED_INPUT_METHODS` throws on targetSdk 34+).
- *Not now* sends a `CATEGORY_HOME` intent and finishes.
- Storage is `SharedPreferences` (`UmmPrefs`): a package set and the delay. The service
  reads it on every event, so no observers are needed.
- Settings UI (`MainActivity`, Compose): service ON/OFF with a link to accessibility
  settings, delay chips, checklist of launchable apps (`<queries>` in the manifest for
  Android 11+ package visibility).

**Left out on purpose** — stats, schedules, per-app delays, overlays instead of an activity,
any persistence beyond prefs.

**Verified** — on the API 36 emulator and a Pixel 10 Pro: countdown → question → both
choices, re-prompt after leaving the app, no re-prompt while inside it.
