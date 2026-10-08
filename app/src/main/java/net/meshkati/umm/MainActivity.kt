package net.meshkati.umm

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import java.text.Collator
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Screens of the main activity; back goes to [parent]. */
private enum class Screen {
    HOME, STATS, APP_STATS, SETTINGS, ABOUT;

    val parent: Screen get() = when (this) {
        APP_STATS -> STATS
        ABOUT -> SETTINGS
        else -> HOME
    }

    /** The bottom-bar tab shown as selected on this screen. */
    val tab: Tab get() = when (this) {
        HOME -> Tab.APPS
        STATS, APP_STATS -> Tab.STATS
        SETTINGS, ABOUT -> Tab.SETTINGS
    }
}

/** Bottom-bar tabs; each opens its top-level [screen]. */
private enum class Tab(val screen: Screen, @DrawableRes val icon: Int, @StringRes val label: Int) {
    APPS(Screen.HOME, R.drawable.ic_apps, R.string.tab_apps),
    STATS(Screen.STATS, R.drawable.ic_stats, R.string.stats_title),
    SETTINGS(Screen.SETTINGS, R.drawable.ic_settings, R.string.settings_title),
}

class MainActivity : ComponentActivity() {

    private lateinit var prefs: UmmPrefs
    private lateinit var pauseLog: PauseLog

    private var serviceEnabled by mutableStateOf(false)
    private var delay by mutableStateOf(UmmPrefs.DEFAULT_DELAY)
    private var blocked by mutableStateOf<Set<String>>(emptySet())
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    /** Null until the log has been read. */
    private var events by mutableStateOf<List<PauseEvent>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = UmmPrefs(this)
        pauseLog = PauseLog(this)
        delay = prefs.delaySeconds
        blocked = prefs.blockedPackages
        themeMode = prefs.themeMode
        val apps = loadLaunchableApps()
        val appsByPackage = apps.associateBy { it.packageName }
        val packageInfo = packageManager.getPackageInfo(packageName, 0)

        setContent {
            UmmTheme(themeMode) {
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                var statsPackage by rememberSaveable { mutableStateOf("") }
                var period by rememberSaveable { mutableStateOf(Period.WEEK) }
                // Keeps each screen's saved state (search query, scroll) while another is shown.
                val saveableState = rememberSaveableStateHolder()
                val back = { screen = screen.parent }
                BackHandler(enabled = screen != Screen.HOME, onBack = back)

                Scaffold(
                    bottomBar = { UmmNavigationBar(selected = screen.tab, onSelect = { screen = it.screen }) },
                    // Only make room for the bar: each screen's own Scaffold handles the status bar.
                    contentWindowInsets = WindowInsets(0),
                ) { padding ->
                    Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                        saveableState.SaveableStateProvider(screen.name) {
                            when (screen) {
                                Screen.HOME -> Home(apps)
                                Screen.STATS -> StatsScreen(
                                    events = events,
                                    period = period,
                                    apps = appsByPackage,
                                    onPeriodChange = { period = it },
                                    onOpenApp = { statsPackage = it; screen = Screen.APP_STATS },
                                    onReset = ::resetStats,
                                    onChooseApps = { screen = Screen.HOME },
                                )
                                Screen.APP_STATS -> AppStatsScreen(
                                    packageName = statsPackage,
                                    events = events,
                                    period = period,
                                    app = appsByPackage[statsPackage],
                                    blocked = statsPackage in blocked,
                                    onUnblock = {
                                        prefs.setBlocked(statsPackage, false)
                                        blocked = prefs.blockedPackages
                                    },
                                    onBack = back,
                                )
                                Screen.SETTINGS -> SettingsScreen(
                                    themeMode = themeMode,
                                    versionName = packageInfo.versionName.orEmpty(),
                                    onThemeChange = ::changeTheme,
                                    onOpenAbout = { screen = Screen.ABOUT },
                                )
                                Screen.ABOUT -> AboutScreen(
                                    versionName = packageInfo.versionName.orEmpty(),
                                    versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
                                    onOpenLink = ::openLink,
                                    onBack = back,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled = AppWatchService.isEnabled(this)
        // Pauses are logged while we're in the background, so re-read on every return.
        lifecycleScope.launch {
            events = withContext(Dispatchers.IO) { pauseLog.read() }
        }
    }

    private fun resetStats() {
        pauseLog.clear()
        events = emptyList()
    }

    private fun changeTheme(mode: ThemeMode) {
        themeMode = mode
        prefs.themeMode = mode
        applyNightMode(this, mode)
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            // No browser installed; nothing sensible to do.
        }
    }

    @Composable
    private fun Home(apps: List<AppEntry>) {
        Scaffold(topBar = { UmmTopBar(stringResource(R.string.app_name)) }) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
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
                    icon = packageManager.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap(),
                )
            }
            .sortedWith(
                compareBy<AppEntry> { it.section == NON_LETTER_SECTION }
                    .thenBy(Collator.getInstance()) { it.label },
            )
    }
}

/** Material 3 bottom navigation; tapping the current tab goes back to its top-level screen. */
@Composable
private fun UmmNavigationBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                // The label is read out, so the icon needs no description of its own.
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}

data class AppEntry(val packageName: String, val label: String, val icon: ImageBitmap) {
    val section: String = sectionOf(label)
}

/** First letter of the label (accents stripped, upper-cased); "#" for digits, symbols, emoji. */
private fun sectionOf(label: String): String {
    val first = Normalizer.normalize(label.trim().take(1), Normalizer.Form.NFD).firstOrNull()
    return if (first != null && first.isLetter()) first.uppercaseChar().toString() else NON_LETTER_SECTION
}

private const val NON_LETTER_SECTION = "#"

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppList(apps: List<AppEntry>, blocked: Set<String>, onToggle: (String, Boolean) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val sections = remember(apps, query) {
        val q = query.trim()
        apps.filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) }
            .groupBy { it.section }
    }
    // Position of each section's header in the lazy list (header + its rows per section).
    val headerIndex = remember(sections) {
        var i = 0
        sections.mapValues { (_, items) -> i.also { i += 1 + items.size } }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val currentSection by remember(headerIndex) {
        derivedStateOf { headerIndex.entries.lastOrNull { it.value <= listState.firstVisibleItemIndex }?.key }
    }

    LaunchedEffect(query) { listState.scrollToItem(0) }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search_apps)) },
            singleLine = true,
            trailingIcon = if (query.isEmpty()) null else {
                { TextButton(onClick = { query = "" }) { Text(stringResource(R.string.clear_search)) } }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        )
        Spacer(Modifier.height(8.dp))
        if (sections.isEmpty()) {
            Text(
                stringResource(R.string.no_apps_match, query.trim()),
                modifier = Modifier.padding(vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxHeight()) {
                sections.forEach { (section, items) ->
                    stickyHeader(key = "section-$section") { SectionHeader(section) }
                    items(items, key = { it.packageName }) { app ->
                        AppRow(app, checked = app.packageName in blocked, onToggle = onToggle)
                    }
                }
            }
            if (sections.size > 1) {
                SectionIndex(sections = sections.keys.toList(), current = currentSection) { section ->
                    focusManager.clearFocus()
                    scope.launch { listState.scrollToItem(headerIndex.getValue(section)) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(section: String) {
    Text(
        section,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun AppRow(app: AppEntry, checked: Boolean, onToggle: (String, Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(app.packageName, !checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Text(app.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Checkbox(checked = checked, onCheckedChange = { onToggle(app.packageName, it) })
    }
}

/** Vertical letter rail: tap or drag along it to jump to a section. */
@Composable
private fun SectionIndex(sections: List<String>, current: String?, onSelect: (String) -> Unit) {
    val select by rememberUpdatedState(onSelect)
    fun pick(y: Float, height: Int) =
        sections[(y / height * sections.size).toInt().coerceIn(0, sections.lastIndex)]

    Column(
        modifier = Modifier
            .heightIn(max = (sections.size * 22).dp)
            .fillMaxHeight()
            .width(28.dp)
            .pointerInput(sections) { detectTapGestures { select(pick(it.y, size.height)) } }
            .pointerInput(sections) {
                detectVerticalDragGestures(onDragStart = { select(pick(it.y, size.height)) }) { change, _ ->
                    select(pick(change.position.y, size.height))
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        sections.forEach { section ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    section,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (section == current) FontWeight.Bold else FontWeight.Normal,
                    color = if (section == current) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
