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
            policy.endSession()
            handler.removeCallbacks(timeUpCheck)
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

        // The policy asks for the timer again whenever a session is still running.
        handler.removeCallbacks(timeUpCheck)
        val action = policy.onForeground(
            pkg,
            blocked = pkg in prefs.blockedPackages,
            limitMinutes = prefs.sessionLimit(pkg),
        )
        when (action) {
            is PausePolicy.Action.Pause -> launchPause(action.packageName)
            is PausePolicy.Action.TimeUp -> launchPause(action.packageName, action.minutes)
            is PausePolicy.Action.CheckAgainIn -> handler.postDelayed(timeUpCheck, action.delayMs)
            PausePolicy.Action.None -> Unit
        }
    }

    /** Shows the pause screen over [pkg]; the time-up version when [sessionMinutes] is given. */
    private fun launchPause(pkg: String, sessionMinutes: Int? = null) {
        Log.i(TAG, if (sessionMinutes == null) "intercepting $pkg" else "time up in $pkg")
        val intent = Intent(this, PauseActivity::class.java)
            .putExtra(PauseActivity.EXTRA_PACKAGE, pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (sessionMinutes != null) {
            intent.putExtra(PauseActivity.EXTRA_TIME_UP_MINUTES, sessionMinutes)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "could not launch pause screen", e)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        handler.removeCallbacks(check)
        handler.removeCallbacks(timeUpCheck)
        policy.reset()
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

        /** Shared with [PauseActivity], which reports what the user chose. */
        val policy = PausePolicy(SystemClock::elapsedRealtime)

        private const val SETTLE_MS = 250L

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
