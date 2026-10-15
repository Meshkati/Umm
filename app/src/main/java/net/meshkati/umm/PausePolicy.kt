package net.meshkati.umm

/**
 * Decides, each time an app comes to the front, whether to show the pause screen or the
 * time-up screen over it. Remembers what the user chose on the pause screen and the running
 * session. Plain Kotlin with times from [clock] (elapsedRealtime in the app), so it can be
 * tested without a device.
 */
class PausePolicy(private val clock: () -> Long) {

    /** What [AppWatchService] should do after a check. */
    sealed interface Action {
        data object None : Action

        /** Show the pause screen over [packageName]. */
        data class Pause(val packageName: String) : Action

        /** Show the time-up screen over [packageName], whose session has lasted [minutes]. */
        data class TimeUp(val packageName: String, val minutes: Int) : Action

        /** Check again in [delayMs], when the running session runs out. */
        data class CheckAgainIn(val delayMs: Long) : Action
    }

    /** Package the user chose "Open anyway" for; stays allowed until they switch apps. */
    @Volatile var allowedPackage: String? = null

    /** True while PauseActivity is on screen, so we don't launch it twice. */
    @Volatile var pauseShowing: Boolean = false

    /**
     * Package the user just made a choice for, and when that stops shielding it. Apps with
     * picture-in-picture or background play keep resurfacing right after the pause screen
     * closes, which would prompt again in a loop.
     */
    @Volatile private var quietPackage: String? = null
    @Volatile private var quietUntil = 0L

    /**
     * App whose session limit is running, and when the session started and runs out. A
     * session ends when another app comes to the front or the screen turns off.
     */
    @Volatile private var sessionPackage: String? = null
    @Volatile private var sessionStartedAt = 0L
    @Volatile private var sessionEndsAt = 0L

    private var lastLaunchAt: Long? = null

    /**
     * [pkg] is in front. [blocked] is whether it's marked to pause before, and [limitMinutes]
     * its session limit (0 for none).
     */
    fun onForeground(pkg: String, blocked: Boolean, limitMinutes: Int): Action {
        val now = clock()

        // Moving to any other app (including the launcher) ends the "allowed" grace and the session.
        if (allowedPackage != null && allowedPackage != pkg) allowedPackage = null
        if (sessionPackage != null && sessionPackage != pkg) endSession()

        if (blocked && pkg != allowedPackage) {
            if (pkg == quietPackage && now < quietUntil) {
                // Just chose for this app and it came straight back (picture-in-picture):
                // let it through for this visit, or the next event after the window re-pauses it.
                allowedPackage = pkg
            } else {
                return if (pauseShowing) Action.None else launch(Action.Pause(pkg), now)
            }
        }

        if (limitMinutes == 0) return Action.None
        if (sessionPackage != pkg) {
            sessionPackage = pkg
            sessionStartedAt = now
            sessionEndsAt = now + limitMinutes * MINUTE_MS
        }
        val left = sessionEndsAt - now
        return when {
            left > 0 -> Action.CheckAgainIn(left)
            pauseShowing -> Action.None
            else -> {
                val minutes = ((now - sessionStartedAt + MINUTE_MS / 2) / MINUTE_MS).toInt()
                launch(Action.TimeUp(pkg, minutes.coerceAtLeast(1)), now)
            }
        }
    }

    /** Don't intercept [pkg] again for [QUIET_MS], whatever the user chose. */
    fun startQuietWindow(pkg: String) {
        quietUntil = clock() + QUIET_MS
        quietPackage = pkg
    }

    /**
     * Lets the session in [pkg] run [minutes] more from now. The timer is re-armed by the
     * check that runs when the app is back in front.
     */
    fun extendSession(pkg: String, minutes: Int) {
        if (sessionPackage == pkg) sessionEndsAt = clock() + minutes * MINUTE_MS
    }

    fun endSession() {
        sessionPackage = null
    }

    /** Forgets everything, as when the service stops. */
    fun reset() {
        endSession()
        allowedPackage = null
        pauseShowing = false
        quietPackage = null
    }

    /** [action], unless a pause screen was launched moments ago and hasn't reported in yet. */
    private fun launch(action: Action, now: Long): Action {
        val last = lastLaunchAt
        if (last != null && now - last < LAUNCH_DEBOUNCE_MS) return Action.None
        lastLaunchAt = now
        return action
    }

    companion object {
        const val QUIET_MS = 10_000L
        private const val LAUNCH_DEBOUNCE_MS = 2_000L
        private const val MINUTE_MS = 60_000L
    }
}
