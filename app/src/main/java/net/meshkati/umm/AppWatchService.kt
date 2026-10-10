package net.meshkati.umm

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat

/**
 * Watches which app is in the foreground. When a marked app comes up and the user
 * hasn't already chosen to continue into it, shows [PauseActivity] on top of it.
 * When an app with a session limit has been in front for that long, shows the time-up
 * version of [PauseActivity].
 *
 * Window events arrive in bursts and mid-transition (the launcher reports in while the
 * previous app's window is still "active"), so events only schedule a check; once they
 * settle, the active window is read once and acted on.
 */
class AppWatchService : AccessibilityService() {

    private lateinit var prefs: UmmPrefs
    private val handler = Handler(Looper.getMainLooper())
    private val check = Runnable(::checkForeground)
    // A separate instance: window events cancel [check], which must not cancel the timer.
    private val timeUpCheck = Runnable(::checkForeground)

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (sessionPackage != null) Log.d(TAG, "screen off, ending session")
            endSession()
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onServiceConnected() {
        prefs = UmmPrefs(this)
        Log.i(TAG, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // STATE_CHANGED: a new window appeared. WINDOWS_CHANGED: order/focus changed, which
        // is all we get when a backgrounded app's existing window is brought to the front.
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return
        handler.removeCallbacks(check)
        handler.postDelayed(check, SETTLE_MS)
    }

    private fun checkForeground() {
        val pkg = activeWindowPackage() ?: return
        if (isIgnored(pkg)) return
        Log.d(TAG, "foreground: $pkg")

        // Moving to any other app (including the launcher) ends the "allowed" grace and the session.
        if (allowedPackage != null && allowedPackage != pkg) {
            Log.d(TAG, "left $allowedPackage, clearing allowance")
            allowedPackage = null
        }
        if (sessionPackage != null && sessionPackage != pkg) {
            Log.d(TAG, "left $sessionPackage, ending session")
            endSession()
        }

        val now = SystemClock.elapsedRealtime()
        if (pkg in prefs.blockedPackages && pkg != allowedPackage) {
            if (pkg == quietPackage && now < quietUntil) {
                // Just chose for this app and it came straight back (picture-in-picture):
                // let it through for this visit, or the next event after the window re-pauses it.
                allowedPackage = pkg
            } else {
                if (!pauseShowing) launchPause(pkg, now)
                return
            }
        }

        val limit = prefs.sessionLimit(pkg)
        if (limit == 0) return
        if (sessionPackage != pkg) {
            Log.i(TAG, "session in $pkg, $limit min")
            sessionPackage = pkg
            sessionStartedAt = now
            sessionEndsAt = now + limit * MINUTE_MS
        }
        val left = sessionEndsAt - now
        if (left > 0) {
            handler.removeCallbacks(timeUpCheck)
            handler.postDelayed(timeUpCheck, left)
        } else if (!pauseShowing) {
            launchPause(pkg, now, sessionMinutes = ((now - sessionStartedAt + MINUTE_MS / 2) / MINUTE_MS).toInt())
        }
    }

    /** Shows the pause screen over [pkg]; the time-up version when [sessionMinutes] is given. */
    private fun launchPause(pkg: String, now: Long, sessionMinutes: Int? = null) {
        // Guard against re-launching before the pause screen has reported itself showing.
        if (now - lastLaunchAt < LAUNCH_DEBOUNCE_MS) return
        lastLaunchAt = now

        Log.i(TAG, if (sessionMinutes == null) "intercepting $pkg" else "time up in $pkg")
        val intent = Intent(this, PauseActivity::class.java)
            .putExtra(PauseActivity.EXTRA_PACKAGE, pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (sessionMinutes != null) {
            intent.putExtra(PauseActivity.EXTRA_TIME_UP_MINUTES, sessionMinutes.coerceAtLeast(1))
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "could not launch pause screen", e)
        }
    }

    private fun endSession() {
        sessionPackage = null
        handler.removeCallbacks(timeUpCheck)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        handler.removeCallbacks(check)
        endSession()
        allowedPackage = null
        pauseShowing = false
        quietPackage = null
        super.onDestroy()
    }

    /** Package owning the active (top, focused) application window; only its name is read. */
    private fun activeWindowPackage(): String? =
        windows.firstOrNull { it.isActive }?.root?.packageName?.toString()
            ?: rootInActiveWindow?.packageName?.toString()

    // Windows that overlay the current app without really changing it: our own pause
    // screen, system UI (notification shade, dialogs) and keyboards.
    private fun isIgnored(pkg: String): Boolean =
        pkg == packageName ||
            pkg == "com.android.systemui" ||
            pkg in inputMethodPackages()

    private fun inputMethodPackages(): Set<String> =
        getSystemService(InputMethodManager::class.java)
            .enabledInputMethodList
            .map { it.packageName }
            .toSet()

    companion object {
        private const val TAG = "AppWatchService"

        /** Package the user chose "Open anyway" for; stays allowed until they switch apps. */
        @Volatile var allowedPackage: String? = null

        /** True while PauseActivity is on screen, so we don't launch it twice. */
        @Volatile var pauseShowing: Boolean = false

        /**
         * Package the user just made a choice for, and when (elapsedRealtime) that stops
         * shielding it. Apps with picture-in-picture or background play keep resurfacing
         * right after the pause screen closes, which would prompt again in a loop.
         */
        @Volatile private var quietPackage: String? = null
        @Volatile private var quietUntil = 0L

        /** Don't intercept [pkg] again for [QUIET_MS], whatever the user chose. */
        fun startQuietWindow(pkg: String) {
            quietUntil = SystemClock.elapsedRealtime() + QUIET_MS
            quietPackage = pkg
        }

        /**
         * App whose session limit is running, and when (elapsedRealtime) the session started
         * and runs out. A session ends when another app comes to the front or the screen
         * turns off.
         */
        @Volatile private var sessionPackage: String? = null
        @Volatile private var sessionStartedAt = 0L
        @Volatile private var sessionEndsAt = 0L

        /**
         * Lets the session in [pkg] run [minutes] more from now. The timer is re-armed by the
         * check that runs when the app is back in front.
         */
        fun extendSession(pkg: String, minutes: Int) {
            if (sessionPackage == pkg) sessionEndsAt = SystemClock.elapsedRealtime() + minutes * MINUTE_MS
        }

        private const val SETTLE_MS = 250L
        private const val LAUNCH_DEBOUNCE_MS = 2_000L
        private const val QUIET_MS = 10_000L
        private const val MINUTE_MS = 60_000L
        private var lastLaunchAt = 0L

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, AppWatchService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }
    }
}
