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

/** Full-screen countdown shown over a marked app, then asks whether to continue. */
class PauseActivity : ComponentActivity() {

    private lateinit var targetPackage: String
    private var recorded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: run { finish(); return }

        val label = appLabel(targetPackage)
        val seconds = UmmPrefs(this).delaySeconds

        setContent {
            MaterialTheme {
                PauseScreen(
                    appLabel = label,
                    seconds = seconds,
                    onOpenAnyway = ::openAnyway,
                    onNotNow = ::goHome,
                )
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

    /** Logs the first outcome only; finishing after a choice must not also count as leaving. */
    private fun record(outcome: Outcome) {
        if (recorded || !::targetPackage.isInitialized) return
        recorded = true
        PauseLog(this).record(targetPackage, outcome)
    }

    private fun openAnyway() {
        record(Outcome.OPENED)
        AppWatchService.allowedPackage = targetPackage
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
    }
}

@Composable
private fun PauseScreen(
    appLabel: String,
    seconds: Int,
    onOpenAnyway: () -> Unit,
    onNotNow: () -> Unit,
) {
    var remaining by remember { mutableIntStateOf(seconds) }
    val done = remaining <= 0

    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }

    // Back is a no-op during the countdown; after it, back counts as "Not now".
    BackHandler { if (done) onNotNow() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.pause_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.pause_subtitle, appLabel),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))

            Box(modifier = Modifier.height(120.dp), contentAlignment = Alignment.Center) {
                if (done) {
                    Text(stringResource(R.string.pause_question), style = MaterialTheme.typography.titleLarge)
                } else {
                    Text(remaining.toString(), fontSize = 96.sp, style = MaterialTheme.typography.displayLarge)
                }
            }

            Spacer(Modifier.height(48.dp))
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
}
