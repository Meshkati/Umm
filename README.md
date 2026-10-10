# Umm: pause before apps

Umm… do you really want to open that?

A tiny, free, open-source Android app that makes you pause before opening apps you'd
rather use less.

Mark the apps you want to be mindful about (say, Twitter and YouTube). Whenever one of
them comes to the foreground, Umm shows a full-screen countdown (3, 5 or 10 seconds)
and then asks whether you still want to open it. "Open anyway" lets you in; "Not now"
takes you back to the home screen. Once you've chosen to continue, you aren't asked again
until you leave that app, and after any choice that app isn't paused again for 10 seconds
(so picture-in-picture and background play don't bring the pause screen straight back).

Apps can also have a **session limit** (3 to 30 minutes), set separately from the pause.
When one visit to the app reaches its limit, Umm shows a time-up screen with the same
countdown, then lets you close the app or keep going for 1, 5 or 10 more minutes. Leaving
the app or turning the screen off ends the session; next time you get the full limit again.

A bottom bar switches between three tabs. **Apps** is where you pick the apps, their session limits
and the pause length. **Stats** shows how often you turned back versus opened the app anyway, per week,
month or all time, and per app. **Settings** picks a light or dark theme (or follows the
phone), and leads to **About**, which shows the version and links to the source.

That's it. No schedules, no accounts.

## How it works

Umm runs an [AccessibilityService](app/src/main/java/net/meshkati/umm/AppWatchService.kt)
that listens for window changes and, once they settle, reads the package name of the
active window — nothing else: no text, no content, no input. When the package is on your
list, it launches [`PauseActivity`](app/src/main/java/net/meshkati/umm/PauseActivity.kt)
on top of it. (`canRetrieveWindowContent` must be on for the service to ask which window
is active; the only call made is `windows[i].root.packageName`.)

Settings are stored in `SharedPreferences`. Each pause and what you chose is appended to a
private file (`pauses.log`, see [`PauseLog`](app/src/main/java/net/meshkati/umm/PauseLog.kt))
for the Stats screen. Nothing leaves the device: Umm has no internet permission.

## Build

Requirements: JDK 17+ and the Android SDK (platform 36). The wrapper fetches Gradle.

```sh
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # build and install on the connected device/emulator
```

The project reads the SDK location from `local.properties` (`sdk.dir=...`), which is not
committed. `gradle.properties` points Gradle at the JDK bundled with Android Studio on
macOS; change `org.gradle.java.home` or remove that line if your setup differs.

## Install and set up

1. Install the APK (`./gradlew installDebug`, or copy the APK to the phone and open it).
2. Open Umm and tap **Open accessibility settings**.
3. Find **Umm** in the list and turn it on. Android will warn you that the app can
   observe your actions; this is the permission the pause screen relies on.
4. Back in Umm, pick a pause length and tick the apps you want to pause before.

### If the toggle is greyed out ("Restricted setting")

Android 13+ blocks accessibility services for apps installed outside an app store.
Go to **Settings → Apps → Umm → ⋮ (top right) → Allow restricted settings**, then
try again.

### If it stops working after a while

Some manufacturers (Samsung, Xiaomi, Huawei, Oppo) kill background services aggressively.
Exclude Umm from battery optimisation in **Settings → Apps → Umm → Battery**, and
on Xiaomi also enable **Autostart**.

## Versioning

Umm follows [Semantic Versioning](https://semver.org). The version lives in one place,
`appVersion` in [`app/build.gradle.kts`](app/build.gradle.kts); `versionCode` is derived
from it as `MAJOR * 10000 + MINOR * 100 + PATCH` (so `0.1.0` → `100`, `1.2.3` → `10203`).

To release: bump `appVersion`, commit, and tag the commit `vX.Y.Z`.

## Project layout

```
app/src/main/java/net/meshkati/umm/
  AppWatchService.kt   accessibility service: detects the foreground app, times sessions
  PauseActivity.kt     countdown + "Not now" / "Open anyway"; logs the outcome; time-up screen
  MainActivity.kt      Apps tab: service status, pause length, app list; bottom bar and navigation
  StatsScreen.kt       Stats and per-app stats screens
  AboutScreen.kt       About screen
  Stats.kt             totals and chart buckets for a period
  PauseLog.kt          append-only log of pauses and their outcomes
  Ui.kt                shared top bar and app icon
  UmmPrefs.kt          SharedPreferences wrapper: paused apps, limits, delay, theme
app/src/main/res/xml/accessibility_service_config.xml
```

Kotlin, Jetpack Compose (Material 3), minSdk 26, targetSdk 36.
