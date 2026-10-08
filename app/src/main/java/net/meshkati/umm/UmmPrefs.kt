package net.meshkati.umm

import android.content.Context
import android.content.SharedPreferences

/** Thin wrapper over SharedPreferences: which apps to pause before, and for how long. */
class UmmPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("umm", Context.MODE_PRIVATE)

    var blockedPackages: Set<String>
        get() = prefs.getStringSet(KEY_BLOCKED, emptySet())!!.toSet()
        set(value) = prefs.edit().putStringSet(KEY_BLOCKED, value.toSet()).apply()

    var delaySeconds: Int
        get() = prefs.getInt(KEY_DELAY, DEFAULT_DELAY)
        set(value) = prefs.edit().putInt(KEY_DELAY, value).apply()

    fun setBlocked(packageName: String, blocked: Boolean) {
        blockedPackages = if (blocked) blockedPackages + packageName else blockedPackages - packageName
    }

    companion object {
        const val DEFAULT_DELAY = 5
        val DELAY_OPTIONS = listOf(3, 5, 10)

        private const val KEY_BLOCKED = "blocked_packages"
        private const val KEY_DELAY = "delay_seconds"
    }
}
