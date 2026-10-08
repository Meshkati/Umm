package net.meshkati.umm

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class Period { WEEK, MONTH, ALL }

/** Pauses in the chart bar starting at [start]: a day (week), 7 days (month) or a calendar month (all time). */
class Bucket(val start: LocalDate, val turnedBack: Int, val opened: Int) {
    val total: Int get() = turnedBack + opened
}

class AppCount(val packageName: String, val turnedBack: Int, val opened: Int) {
    val total: Int get() = turnedBack + opened
}

class Summary(val turnedBack: Int, val opened: Int, val buckets: List<Bucket>, val apps: List<AppCount>) {
    val total: Int get() = turnedBack + opened
}

/** Most months the all-time chart shows; the totals still cover everything. */
private const val MAX_MONTHS = 12L

/**
 * Totals for [period] ending [today]: the last 7 days by day, the last 4 weeks by week,
 * or everything by calendar month. Apps are sorted by number of pauses, most first.
 */
fun summarize(
    events: List<PauseEvent>,
    period: Period,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone),
): Summary {
    val dated = events.map { it to Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
    val starts = when (period) {
        Period.WEEK -> (6L downTo 0L).map { today.minusDays(it) }
        Period.MONTH -> (3L downTo 0L).map { today.minusDays(it * 7 + 6) }
        Period.ALL -> {
            val thisMonth = today.withDayOfMonth(1)
            val first = dated.minOfOrNull { it.second }?.withDayOfMonth(1) ?: thisMonth
            val months = ChronoUnit.MONTHS.between(first, thisMonth).coerceIn(0, MAX_MONTHS - 1)
            (months downTo 0L).map { thisMonth.minusMonths(it) }
        }
    }
    val next: (LocalDate) -> LocalDate = when (period) {
        Period.WEEK -> { d -> d.plusDays(1) }
        Period.MONTH -> { d -> d.plusDays(7) }
        Period.ALL -> { d -> d.plusMonths(1) }
    }
    val from = if (period == Period.ALL) LocalDate.MIN else starts.first()
    val inPeriod = dated.filter { (_, date) -> date in from..today }

    val buckets = starts.map { start ->
        val end = next(start)
        val outcomes = inPeriod.filter { (_, date) -> date >= start && date < end }.map { it.first.outcome }
        Bucket(start, outcomes.count { it.turnedBack }, outcomes.count { !it.turnedBack })
    }
    val apps = inPeriod.map { it.first }
        .groupBy { it.packageName }
        .map { (pkg, e) -> AppCount(pkg, e.count { it.outcome.turnedBack }, e.count { !it.outcome.turnedBack }) }
        .sortedByDescending { it.total }
    return Summary(
        turnedBack = inPeriod.count { it.first.outcome.turnedBack },
        opened = inPeriod.count { !it.first.outcome.turnedBack },
        buckets = buckets,
        apps = apps,
    )
}
