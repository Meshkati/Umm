package net.meshkati.umm

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * Watches which app is in the foreground. When a marked app comes up and the user
 * hasn't already chosen to continue into it, shows [PauseActivity] on top of it.
 *
 * Window events arrive in bursts and mid-transition (the launcher reports in while the
 * previous app's window is still "active"), so events only schedule a check; once they
 * settle, the active window is read once and acted on.
 */
class AppWatchService : AccessibilityService() {

    private lateinit var prefs: UmmPrefs
    private val handler = Handler(Looper.getMainLooper())
    private val check = Runnable(::checkForeground)

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

        // Moving to any other app (including the launcher) ends the "allowed" grace.
        if (allowedPackage != null && allowedPackage != pkg) {
            Log.d(TAG, "left $allowedPackage, clearing allowance")
            allowedPackage = null
        }

        if (pkg !in prefs.blockedPackages) return
        if (pkg == allowedPackage || pauseShowing) return
        // Guard against re-launching before the pause screen has reported itself showing.
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunchAt < LAUNCH_DEBOUNCE_MS) return
        lastLaunchAt = now

        Log.i(TAG, "intercepting $pkg")
        try {
            startActivity(
                Intent(this, PauseActivity::class.java)
                    .putExtra(PauseActivity.EXTRA_PACKAGE, pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (e: Exception) {
            Log.w(TAG, "could not launch pause screen", e)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(check)
        allowedPackage = null
        pauseShowing = false
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

        private const val SETTLE_MS = 250L
        private const val LAUNCH_DEBOUNCE_MS = 2_000L
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
