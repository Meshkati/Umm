package net.meshkati.umm

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/** Totals for a period, a chart over it, and a per-app breakdown. [events] is null while loading. */
@Composable
fun StatsScreen(
    events: List<PauseEvent>?,
    period: Period,
    apps: Map<String, AppEntry>,
    onPeriodChange: (Period) -> Unit,
    onOpenApp: (String) -> Unit,
    onReset: () -> Unit,
    onChooseApps: () -> Unit,
) {
    var confirmReset by remember { mutableStateOf(false) }

    Scaffold(topBar = { UmmTopBar(stringResource(R.string.stats_title)) }) { padding ->
        when {
            events == null -> Unit
            events.isEmpty() -> EmptyStats(Modifier.padding(padding), onChooseApps)
            else -> {
                val summary = remember(events, period) { summarize(events, period) }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    PeriodSelector(period, onPeriodChange)
                    if (summary.total == 0) {
                        Text(
                            stringResource(R.string.stats_period_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Overview(summary)
                        ChartCard(summary.buckets, period)
                        AppBreakdown(summary.apps, apps, onOpenApp)
                    }
                    StatsFooter(onReset = { confirmReset = true })
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_stats_title)) },
            text = { Text(stringResource(R.string.reset_stats_message)) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onReset() }) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** One app's totals and chart for [period], and its most recent pauses. */
@Composable
fun AppStatsScreen(
    packageName: String,
    events: List<PauseEvent>?,
    period: Period,
    app: AppEntry?,
    blocked: Boolean,
    onUnblock: () -> Unit,
    onBack: () -> Unit,
) {
    val label = app?.label ?: packageName
    Scaffold(topBar = { UmmTopBar(label, onBack) }) { padding ->
        if (events == null) return@Scaffold
        val appEvents = remember(events, packageName) { events.filter { it.packageName == packageName } }
        val summary = remember(appEvents, period) { summarize(appEvents, period) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(packageName, app, 56.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        pluralStringResource(R.plurals.pause_count, summary.total, summary.total),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        stringResource(period.longLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (summary.total > 0) {
                val pct = percent(summary.turnedBack, summary.total)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile(
                        summary.turnedBack.toString(),
                        stringResource(R.string.with_percent, stringResource(R.string.turned_back), pct),
                        MaterialTheme.colorScheme.primary,
                        Modifier.weight(1f),
                    )
                    Tile(
                        summary.opened.toString(),
                        stringResource(R.string.with_percent, stringResource(R.string.opened_anyway), 100 - pct),
                        OpenedColor,
                        Modifier.weight(1f),
                    )
                }
                ChartCard(summary.buckets, period)
            }
            RecentPauses(appEvents.sortedByDescending { it.at }.take(RECENT_COUNT))
            if (blocked) {
                OutlinedButton(onClick = onUnblock) { Text(stringResource(R.string.stop_pausing, label)) }
            }
        }
    }
}

private const val RECENT_COUNT = 10

/** Keeps a chart with only a bar or two from turning into wide blocks. */
private val MAX_BAR_WIDTH = 56.dp

private val Period.shortLabel: Int
    get() = when (this) {
        Period.WEEK -> R.string.period_week
        Period.MONTH -> R.string.period_month
        Period.ALL -> R.string.period_all
    }

private val Period.longLabel: Int
    get() = when (this) {
        Period.WEEK -> R.string.period_week_long
        Period.MONTH -> R.string.period_month_long
        Period.ALL -> R.string.period_all_long
    }

private fun percent(part: Int, total: Int): Int = Math.round(part * 100f / total)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(period: Period, onChange: (Period) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        Period.entries.forEachIndexed { i, p ->
            SegmentedButton(
                selected = p == period,
                onClick = { onChange(p) },
                shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size),
            ) { Text(stringResource(p.shortLabel)) }
        }
    }
}

@Composable
private fun Overview(summary: Summary) {
    val pct = percent(summary.turnedBack, summary.total)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row {
            Text(
                stringResource(R.string.stats_percent, pct),
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.stats_turned_back_share),
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ShareBar(summary.turnedBack, summary.opened, 12.dp, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(summary.total.toString(), stringResource(R.string.stats_pauses), null, Modifier.weight(1f))
            Tile(
                summary.turnedBack.toString(),
                stringResource(R.string.turned_back),
                MaterialTheme.colorScheme.primary,
                Modifier.weight(1f),
            )
            Tile(summary.opened.toString(), stringResource(R.string.opened_anyway), OpenedColor, Modifier.weight(1f))
        }
    }
}

/** Horizontal bar split into turned back (primary) and opened anyway. */
@Composable
private fun ShareBar(turnedBack: Int, opened: Int, height: Dp, modifier: Modifier = Modifier) {
    Row(modifier = modifier.height(height).clip(RoundedCornerShape(height / 2))) {
        if (turnedBack > 0) {
            Box(Modifier.weight(turnedBack.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        }
        if (opened > 0) {
            Box(Modifier.weight(opened.toFloat()).fillMaxHeight().background(OpenedColor))
        }
    }
}

@Composable
private fun Tile(value: String, label: String, dot: Color?, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dot != null) {
                    Box(Modifier.size(10.dp).background(dot, CircleShape))
                    Spacer(Modifier.width(6.dp))
                }
                Text(value, style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartCard(buckets: List<Bucket>, period: Period) {
    val title = when (period) {
        Period.WEEK -> R.string.chart_days
        Period.MONTH -> R.string.chart_weeks
        Period.ALL -> R.string.chart_months
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            BarChart(buckets, period)
        }
    }
}

/** Stacked bars: turned back at the bottom, opened anyway on top, each bar's total above it. */
@Composable
private fun BarChart(buckets: List<Bucket>, period: Period) {
    val max = buckets.maxOf { it.total }.coerceAtLeast(1)
    val barArea = 100.dp
    val gap = if (buckets.size > 5) 8.dp else 20.dp
    val topShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(barArea + 20.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.Bottom,
        ) {
            buckets.forEach { b ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (b.total > 0) {
                        Text(
                            b.total.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    if (b.opened > 0) {
                        Box(Modifier.widthIn(max = MAX_BAR_WIDTH).fillMaxWidth().height(barArea * b.opened / max).background(OpenedColor, topShape))
                    }
                    if (b.turnedBack > 0) {
                        val shape = if (b.opened > 0) RoundedCornerShape(0.dp) else topShape
                        Box(
                            Modifier.widthIn(max = MAX_BAR_WIDTH)
                                .fillMaxWidth()
                                .height(barArea * b.turnedBack / max)
                                .background(MaterialTheme.colorScheme.primary, shape)
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            buckets.forEach { b ->
                Text(
                    bucketLabel(b.start, period),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun bucketLabel(start: LocalDate, period: Period): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (period) {
        Period.WEEK ->
            if (start == LocalDate.now()) stringResource(R.string.today)
            else start.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        Period.MONTH -> monthDay(start, locale)
        Period.ALL -> start.month.getDisplayName(TextStyle.SHORT, locale)
    }
}

private fun monthDay(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale).format(date)

@Composable
private fun AppBreakdown(counts: List<AppCount>, apps: Map<String, AppEntry>, onOpenApp: (String) -> Unit) {
    val max = counts.maxOf { it.total }
    Column {
        Text(stringResource(R.string.by_app), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        counts.forEach { count ->
            val app = apps[count.packageName]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenApp(count.packageName) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(count.packageName, app, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Text(
                            app?.label ?: count.packageName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                        )
                        Text(
                            pluralStringResource(R.plurals.pause_count, count.total, count.total),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ShareBar(count.turnedBack, count.opened, 8.dp, Modifier.fillMaxWidth(count.total.toFloat() / max))
                    Text(
                        stringResource(R.string.app_breakdown, count.turnedBack, count.opened),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
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
private fun StatsFooter(onReset: () -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.stats_local_only),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onReset,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.reset_stats)) }
        }
    }
}

@Composable
private fun EmptyStats(modifier: Modifier, onChooseApps: () -> Unit) {
    val ringBackground = MaterialTheme.colorScheme.surfaceContainer
    val ring = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Canvas(Modifier.size(96.dp)) {
            drawCircle(ringBackground)
            drawCircle(ring, radius = 22.dp.toPx(), style = Stroke(width = 6.dp.toPx()))
        }
        Text(stringResource(R.string.stats_empty_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.stats_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onChooseApps) { Text(stringResource(R.string.choose_apps)) }
    }
}

@Composable
private fun RecentPauses(events: List<PauseEvent>) {
    if (events.isEmpty()) return
    Column {
        Text(stringResource(R.string.recent), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        events.forEach { event ->
            Row(modifier = Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                val color = if (event.outcome.turnedBack) MaterialTheme.colorScheme.primary else OpenedColor
                Box(Modifier.size(10.dp).background(color, CircleShape))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(if (event.outcome.turnedBack) R.string.turned_back else R.string.opened_anyway),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (event.outcome == Outcome.LEFT) {
                        Text(
                            stringResource(R.string.left_without_choosing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    whenLabel(event.at),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

/** "Today 21:42", "Yesterday 23:04", "Tue 22:30", or the date for anything older than a week. */
@Composable
private fun whenLabel(at: Long): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val day = when {
        date == today -> stringResource(R.string.today)
        date == today.minusDays(1) -> stringResource(R.string.yesterday)
        date > today.minusDays(7) -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        else -> monthDay(date, locale)
    }
    return "$day ${DateFormat.getTimeFormat(context).format(Date(at))}"
}
