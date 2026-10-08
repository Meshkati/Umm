package com.seyed.umm

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * Watches which app is in the foreground. When a marked app comes up and the user
 * hasn't already chosen to continue into it, shows [PauseActivity] on top of it.
 */
class AppWatchService : AccessibilityService() {

    private lateinit var prefs: UmmPrefs

    override fun onServiceConnected() {
        prefs = UmmPrefs(this)
        Log.i(TAG, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (isIgnored(pkg)) return
        Log.d(TAG, "foreground: $pkg")

        // Moving to any other app (including the launcher) ends the "allowed" grace.
        if (allowedPackage != null && allowedPackage != pkg) {
            Log.d(TAG, "left $allowedPackage, clearing allowance")
            allowedPackage = null
        }

        if (pkg !in prefs.blockedPackages) return
        if (pkg == allowedPackage || pauseShowing) return

        Log.i(TAG, "intercepting $pkg")
        pauseShowing = true
        startActivity(
            Intent(this, PauseActivity::class.java)
                .putExtra(PauseActivity.EXTRA_PACKAGE, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        allowedPackage = null
        pauseShowing = false
        super.onDestroy()
    }

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
