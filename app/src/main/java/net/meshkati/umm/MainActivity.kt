package net.meshkati.umm

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

class MainActivity : ComponentActivity() {

    private lateinit var prefs: UmmPrefs

    private var serviceEnabled by mutableStateOf(false)
    private var delay by mutableStateOf(UmmPrefs.DEFAULT_DELAY)
    private var blocked by mutableStateOf<Set<String>>(emptySet())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = UmmPrefs(this)
        delay = prefs.delaySeconds
        blocked = prefs.blockedPackages
        val apps = loadLaunchableApps()

        setContent {
            MaterialTheme {
                Scaffold { padding ->
                    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                        ServiceStatusCard(
                            enabled = serviceEnabled,
                            onOpenSettings = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                        )
                        Spacer(Modifier.height(16.dp))
                        DelaySelector(selected = delay) {
                            delay = it
                            prefs.delaySeconds = it
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.apps_label), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        AppList(apps = apps, blocked = blocked) { pkg, checked ->
                            prefs.setBlocked(pkg, checked)
                            blocked = prefs.blockedPackages
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled = AppWatchService.isEnabled(this)
    }

    private fun loadLaunchableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != packageName }
            .map { pkg ->
                val info = packageManager.getApplicationInfo(pkg, 0)
                AppEntry(
                    packageName = pkg,
                    label = packageManager.getApplicationLabel(info).toString(),
                    icon = packageManager.getApplicationIcon(info),
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}

private data class AppEntry(val packageName: String, val label: String, val icon: Drawable)

@Composable
private fun ServiceStatusCard(enabled: Boolean, onOpenSettings: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(if (enabled) R.string.status_on else R.string.status_off),
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            if (!enabled) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenSettings) {
                    Text(stringResource(R.string.open_accessibility_settings))
                }
            }
        }
    }
}

@Composable
private fun DelaySelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.delay_label), style = MaterialTheme.typography.titleMedium)
        UmmPrefs.DELAY_OPTIONS.forEach { s ->
            FilterChip(
                selected = s == selected,
                onClick = { onSelect(s) },
                label = { Text(stringResource(R.string.delay_seconds, s)) },
            )
        }
    }
}

@Composable
private fun AppList(apps: List<AppEntry>, blocked: Set<String>, onToggle: (String, Boolean) -> Unit) {
    LazyColumn {
        items(apps, key = { it.packageName }) { app ->
            val checked = app.packageName in blocked
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(app.packageName, !checked) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(app.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Checkbox(checked = checked, onCheckedChange = { onToggle(app.packageName, it) })
            }
        }
    }
}
