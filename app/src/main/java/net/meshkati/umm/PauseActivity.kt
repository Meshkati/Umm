package net.meshkati.umm

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Full-screen countdown shown over a marked app, then asks whether to continue. With
 * [EXTRA_TIME_UP_MINUTES], it's the time-up screen instead: the app's session limit has run
 * out, and after the countdown it asks whether to close the app or keep going for a while.
 */
class PauseActivity : ComponentActivity() {

    private lateinit var targetPackage: String
    private var recorded = false
    /** Minutes the session has lasted; 0 on the ordinary pause screen. */
    private var timeUpMinutes = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: run { finish(); return }
        timeUpMinutes = intent.getIntExtra(EXTRA_TIME_UP_MINUTES, 0)

        val label = appLabel(targetPackage)
        val prefs = UmmPrefs(this)
        val seconds = prefs.delaySeconds
        val themeMode = prefs.themeMode

        setContent {
            UmmTheme(themeMode) {
                if (timeUpMinutes > 0) {
                    TimeUpScreen(
                        appLabel = label,
                        minutes = timeUpMinutes,
                        seconds = seconds,
                        onExtend = ::extend,
                        onClose = ::goHome,
                    )
                } else {
                    PauseScreen(
                        appLabel = label,
                        seconds = seconds,
                        onOpenAnyway = ::openAnyway,
                        onNotNow = ::goHome,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppWatchService.pauseShowing = true
    }

    override fun onStop() {
        AppWatchService.pauseShowing = false
        super.onStop()
    }

    override fun onDestroy() {
        // noHistory: leaving the screen (Home, recents) finishes it without a choice made.
        if (isFinishing) record(Outcome.LEFT)
        super.onDestroy()
    }

    /**
     * Logs the first outcome only; finishing after a choice must not also count as leaving.
     * Any outcome also starts the service's quiet window for the app. Time-up choices aren't
     * pauses, so they stay out of the log.
     */
    private fun record(outcome: Outcome) {
        if (recorded || !::targetPackage.isInitialized) return
        recorded = true
        if (timeUpMinutes == 0) PauseLog(this).record(targetPackage, outcome)
        AppWatchService.startQuietWindow(targetPackage)
    }

    private fun openAnyway() {
        record(Outcome.OPENED)
        AppWatchService.allowedPackage = targetPackage
        finish()
    }

    private fun extend(minutes: Int) {
        record(Outcome.OPENED)
        AppWatchService.extendSession(targetPackage, minutes)
        finish()
    }

    private fun goHome() {
        record(Outcome.TURNED_BACK)
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_TIME_UP_MINUTES = "time_up_minutes"
    }
}

@Composable
private fun PauseScreen(
    appLabel: String,
    seconds: Int,
    onOpenAnyway: () -> Unit,
    onNotNow: () -> Unit,
) {
    CountdownScreen(
        title = stringResource(R.string.pause_title),
        subtitle = stringResource(R.string.pause_subtitle, appLabel),
        question = stringResource(R.string.pause_question),
        seconds = seconds,
        onBack = onNotNow,
    ) { done ->
        Row {
            OutlinedButton(onClick = onNotNow, enabled = done) {
                Text(stringResource(R.string.not_now))
            }
            Spacer(Modifier.width(16.dp))
            Button(onClick = onOpenAnyway, enabled = done) {
                Text(stringResource(R.string.open_anyway))
            }
        }
    }
}

@Composable
private fun TimeUpScreen(
    appLabel: String,
    minutes: Int,
    seconds: Int,
    onExtend: (Int) -> Unit,
    onClose: () -> Unit,
) {
    CountdownScreen(
        title = stringResource(R.string.time_up_title),
        subtitle = stringResource(R.string.time_up_subtitle, minutes, appLabel),
        question = stringResource(R.string.time_up_question),
        seconds = seconds,
        onBack = onClose,
    ) { done ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = onClose, enabled = done) {
                Text(stringResource(R.string.close))
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UmmPrefs.EXTEND_OPTIONS.forEach { m ->
                    OutlinedButton(onClick = { onExtend(m) }, enabled = done) {
                        Text(stringResource(R.string.extend_minutes, m))
                    }
                }
            }
        }
    }
}

/**
 * Counts down from [seconds], then shows [question]; [actions] get whether the countdown is
 * done. Back is a no-op during the countdown; after it, back calls [onBack].
 */
@Composable
private fun CountdownScreen(
    title: String,
    subtitle: String,
    question: String,
    seconds: Int,
    onBack: () -> Unit,
    actions: @Composable (done: Boolean) -> Unit,
) {
    var remaining by remember { mutableIntStateOf(seconds) }
    val done = remaining <= 0

    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }

    BackHandler { if (done) onBack() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(48.dp))

            Box(modifier = Modifier.height(120.dp), contentAlignment = Alignment.Center) {
                if (done) {
                    Text(question, style = MaterialTheme.typography.titleLarge)
                } else {
                    Text(remaining.toString(), fontSize = 96.sp, style = MaterialTheme.typography.displayLarge)
                }
            }

            Spacer(Modifier.height(48.dp))
            actions(done)
        }
    }
}
