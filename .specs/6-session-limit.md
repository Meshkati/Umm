# 6 — Session limit per app

## Task

Let each app have a session limit (3, 5, 10, 15 or 30 min). When one visit to the app reaches
it, a time-up screen asks whether to close the app or keep going for 1, 5 or 10 more minutes.

## Why

Android's own app timers are daily totals. A day's allowance can be fine while single visits
still run long: one video becomes ten. The aim is short sessions, with the limit set per app
(Reddit 3 min, Twitter 5, YouTube 10).

## How

- **Setting** — each row in the Apps list gets a limit button to the left of the checkbox. It
  opens a menu: No limit, 3, 5, 10, 15, 30 min. The limit is separate from the pause: an app can
  have either, both or neither. The list title becomes a header row, `Apps … Limit  Pause`.
  Each limit is stored under its own key, `limit_<package>` in `UmmPrefs`.
- **Session** — starts when the app is in front and past the pause (no pause on it, or
  "Open anyway" was tapped). It ends when another app comes to the front or the screen turns
  off. The notification shade, keyboards and Umm's own screens don't end it. Coming back later
  starts a new session with the full limit.
- **Timer** — `AppWatchService` keeps the session's package, start time and end time
  (`elapsedRealtime`) and posts a separate runnable for the end. It needs its own instance
  because window events cancel the usual settle check. The runnable just runs the foreground
  check again: if the app is still in front and its time has run out, the time-up screen shows.
  Screen off is a receiver registered in `onCreate`.
- **Time-up screen** — `PauseActivity` with `EXTRA_TIME_UP_MINUTES`. It has the same countdown
  as the pause, then "Keep going?" with Close (home, like "Not now") and +1 / +5 / +10 min.
  Extending moves the session's end; the timer is re-armed when the app is back in front. Back
  after the countdown counts as Close. Both screens share `CountdownScreen`. Time-up choices
  aren't written to the pause log. They do start the quiet window from spec 5, so
  picture-in-picture can't loop.
- **Quiet-window fix** — when the quiet window lets a paused app through, the app now also
  becomes `allowedPackage` for that visit. Before, the first window event after the 10 s
  re-paused it mid-use.

**Left out on purpose**
- Time-up stats.
- Carrying a session over a quick switch to another app.
- Limits on the per-app stats screen.
- Custom limit values.
- Counting background or picture-in-picture time.

**Verified** — on the API 36 emulator:
- The limit column and menu.
- Clock with a 3 min limit and no pause: the time-up screen at 3:00, +1 min brought it back
  59 s later, and Close went home and ended the session. Nothing was written to the pause log.
- Settings with a pause and a 5 min limit: no session while the pause showed, the session
  started on "Open anyway", and screen off then on started a new one.

Not checked: YouTube with picture-in-picture on a real phone.
