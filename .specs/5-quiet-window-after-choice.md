# 5 — Quiet window after a choice

## Task

Once the user makes a choice on the pause screen, don't pause that app again for 10 seconds.

## Why

Apps with picture-in-picture or background play (YouTube) keep coming back to the foreground
right after the pause screen closes. Each return looked like a fresh open, so the pause screen
came straight back: with PiP on, YouTube could never be opened.

## How

- `PauseActivity.record` calls `AppWatchService.startQuietWindow(pkg)` along with logging the
  outcome, so every outcome starts the window: *Open anyway*, *Not now*, and leaving the
  screen without choosing (Home, recents), which can also send the app into PiP.
- The service keeps one `quietPackage` and a `quietUntil` deadline (`elapsedRealtime`).
  `checkForeground` skips that package until the deadline; other marked apps are still
  paused as usual, so the window isn't a way around the pause for everything else.
- 10 seconds (`QUIET_MS`), fixed. The existing 2-second launch debounce stays: it guards the
  gap before the pause screen reports itself showing, which is a different problem.
- The `allowedPackage` rule doesn't change: leaving the app still clears it, so returning
  after the window prompts again, once.

**Left out on purpose** — a setting for the window's length, and detecting PiP directly.

**Verified** — compiles (`assembleDebug`). Needs a check on a device: YouTube with PiP on,
both choices, then expanding the PiP window within and after 10 seconds.
