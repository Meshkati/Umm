package net.meshkati.umm

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** The theme choice, and the way to About. */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    versionName: String,
    onThemeChange: (ThemeMode) -> Unit,
    onOpenAbout: () -> Unit,
) {
    Scaffold(topBar = { UmmTopBar(stringResource(R.string.settings_title)) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.settings_appearance),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.selectableGroup()) {
                ThemeOption(ThemeMode.SYSTEM, themeMode, R.string.theme_system, R.string.theme_system_detail, onThemeChange)
                ThemeOption(ThemeMode.LIGHT, themeMode, R.string.theme_light, null, onThemeChange)
                ThemeOption(ThemeMode.DARK, themeMode, R.string.theme_dark, null, onThemeChange)
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable(onClick = onOpenAbout)
                    .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_about_detail, versionName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    painterResource(R.drawable.ic_chevron),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ThemeOption(
    mode: ThemeMode,
    selected: ThemeMode,
    @StringRes label: Int,
    @StringRes detail: Int?,
    onSelect: (ThemeMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = mode == selected, role = Role.RadioButton, onClick = { onSelect(mode) })
            .padding(start = 16.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row handles the click, so the button itself doesn't (one target for TalkBack).
        RadioButton(selected = mode == selected, onClick = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(
                    stringResource(detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
