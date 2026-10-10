package net.meshkati.umm

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import androidx.annotation.StringRes
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real accessibility service on an emulator, with the system Settings app marked
 * to pause before. Changes the device's accessibility settings, so run it on an emulator only.
 */
@RunWith(AndroidJUnit4::class)
class PauseScreenTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val umm = context.packageName
    private lateinit var device: UiDevice
    private var servicesBefore = ""

    @Before
    fun setUp() {
        // UI Automator turns other accessibility services off unless told not to, Umm's included.
        Configurator.getInstance().uiAutomationFlags = UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        device.pressHome()

        UmmPrefs(context).apply {
            blockedPackages = setOf(MARKED)
            delaySeconds = UmmPrefs.DELAY_OPTIONS.first()
        }
        AppWatchService.policy.reset()
        enableService()
    }

    @After
    fun tearDown() {
        if (servicesBefore.isEmpty() || servicesBefore == "null") {
            device.executeShellCommand("settings delete secure enabled_accessibility_services")
        } else {
            device.executeShellCommand("settings put secure enabled_accessibility_services $servicesBefore")
        }
    }

    @Test
    fun openingAMarkedAppShowsThePauseScreen() {
        // when
        open(MARKED)

        // then
        assertTrue(device.wait(Until.hasObject(By.pkg(umm)), TIMEOUT_MS))
    }

    @Test
    fun notNowTakesYouToTheHomeScreen() {
        // given
        open(MARKED)

        // when
        choose(R.string.not_now)

        // then
        assertTrue(device.wait(Until.hasObject(By.pkg(homePackage()).depth(0)), TIMEOUT_MS))
    }

    @Test
    fun openAnywayLandsYouInTheApp() {
        // given
        open(MARKED)

        // when
        choose(R.string.open_anyway)

        // then
        device.wait(Until.gone(By.pkg(umm)), TIMEOUT_MS)
        assertFalse("pause screen came back", device.wait(Until.hasObject(By.pkg(umm)), STAY_MS))
        assertEquals(MARKED, device.currentPackageName)
    }

    @Test
    fun bringingAMarkedAppBackFromRecentsShowsThePauseScreen() {
        // given
        open(MARKED)
        choose(R.string.open_anyway)
        device.wait(Until.hasObject(By.pkg(MARKED).depth(0)), TIMEOUT_MS)
        device.pressHome()
        SystemClock.sleep(PausePolicy.QUIET_MS)

        // when
        device.pressRecentApps()
        device.wait(Until.findObject(By.desc(appLabel(MARKED))), TIMEOUT_MS).click()

        // then
        assertTrue(device.wait(Until.hasObject(By.pkg(umm)), TIMEOUT_MS))
    }

    private fun enableService() {
        val ours = ComponentName(context, AppWatchService::class.java).flattenToString()
        servicesBefore = device.executeShellCommand("settings get secure enabled_accessibility_services").trim()
        val enabled = servicesBefore.split(':').filter { it.isNotEmpty() && it != "null" && it != ours } + ours
        device.executeShellCommand("settings put secure enabled_accessibility_services ${enabled.joinToString(":")}")
        device.executeShellCommand("settings put secure accessibility_enabled 1")

        val manager = context.getSystemService(AccessibilityManager::class.java)
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .none { it.resolveInfo.serviceInfo.name == AppWatchService::class.java.name }
        ) {
            check(SystemClock.uptimeMillis() < deadline) { "accessibility service didn't start" }
            SystemClock.sleep(100)
        }
    }

    private fun open(pkg: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
    }

    /** Taps a pause screen button once the countdown has enabled it. */
    private fun choose(@StringRes label: Int) {
        val button = By.clickable(true).enabled(true).hasDescendant(By.text(context.getString(label)))
        device.wait(Until.findObject(button), TIMEOUT_MS).click()
    }

    // device.launcherPackageName can't see the launcher from the test app and finds Settings' fallback home.
    private fun homePackage(): String =
        device.executeShellCommand("cmd role get-role-holders android.app.role.HOME").trim()

    private fun appLabel(pkg: String): String =
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()

    private companion object {
        const val MARKED = "com.android.settings"
        const val TIMEOUT_MS = 15_000L
        /** How long the app must stay in front, unpaused, after "Open anyway". */
        const val STAY_MS = 5_000L
    }
}
