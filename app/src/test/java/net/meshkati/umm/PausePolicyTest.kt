package net.meshkati.umm

import net.meshkati.umm.PausePolicy.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class PausePolicyTest {

    private var now = 0L
    private val policy = PausePolicy { now }

    @Test
    fun openAnywayKeepsTheAppOpenWhileYouStayInIt() {
        // given
        policy.onForeground(VIDEO, blocked = true, limitMinutes = 0)
        now += 5 * SECOND
        chooseOpenAnyway(VIDEO)
        now += 1 * MINUTE

        // when
        val actions = List(3) {
            now += 1 * SECOND
            policy.onForeground(VIDEO, blocked = true, limitMinutes = 0)
        }

        // then
        assertEquals(List(3) { Action.None }, actions)
    }

    @Test
    fun appComingBackRightAfterAChoiceIsNotPausedAgain() {
        // given
        policy.onForeground(VIDEO, blocked = true, limitMinutes = 0)
        now += 5 * SECOND
        chooseNotNow(VIDEO)
        policy.onForeground(LAUNCHER, blocked = false, limitMinutes = 0)
        now += 1 * SECOND

        // when
        val action = policy.onForeground(VIDEO, blocked = true, limitMinutes = 0)

        // then
        assertEquals(Action.None, action)
    }

    @Test
    fun sessionLimitShowsTimeUpOnceItRunsOut() {
        // given
        policy.onForeground(SOCIAL, blocked = false, limitMinutes = 15)
        now += 15 * MINUTE

        // when
        val action = policy.onForeground(SOCIAL, blocked = false, limitMinutes = 15)

        // then
        assertEquals(Action.TimeUp(SOCIAL, 15), action)
    }

    @Test
    fun switchingAppsRestartsTheSessionLimit() {
        // given
        policy.onForeground(SOCIAL, blocked = false, limitMinutes = 15)
        now += 10 * MINUTE
        policy.onForeground(LAUNCHER, blocked = false, limitMinutes = 0)
        now += 1 * MINUTE

        // when
        val action = policy.onForeground(SOCIAL, blocked = false, limitMinutes = 15)

        // then
        assertEquals(Action.CheckAgainIn(15 * MINUTE), action)
    }

    /** What PauseActivity does when the user taps "Open anyway". */
    private fun chooseOpenAnyway(pkg: String) {
        policy.startQuietWindow(pkg)
        policy.allowedPackage = pkg
    }

    /** What PauseActivity does when the user taps "Not now". */
    private fun chooseNotNow(pkg: String) {
        policy.startQuietWindow(pkg)
    }

    private companion object {
        const val VIDEO = "com.example.video"
        const val SOCIAL = "com.example.social"
        const val LAUNCHER = "com.example.launcher"
        const val SECOND = 1_000L
        const val MINUTE = 60 * SECOND
    }
}
