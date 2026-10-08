package net.meshkati.umm

import android.content.Context
import java.io.File

/** What happened on the pause screen. */
enum class Outcome {
    /** Tapped "Not now" (or back once the countdown was over). */
    TURNED_BACK,

    /** Tapped "Open anyway". */
    OPENED,

    /** Left the pause screen without choosing, e.g. pressed Home during the countdown. */
    LEFT;

    /** Leaving without choosing still means the app wasn't opened, so it counts as turning back. */
    val turnedBack: Boolean get() = this != OPENED
}

data class PauseEvent(val at: Long, val packageName: String, val outcome: Outcome)

/**
 * Every pause and its outcome, appended to a private file as one tab-separated line
 * (`millis outcome package`). A line is a few dozen bytes, so the file is read whole.
 */
class PauseLog(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    fun record(packageName: String, outcome: Outcome, at: Long = System.currentTimeMillis()) {
        synchronized(lock) { file.appendText("$at\t${outcome.name}\t$packageName\n") }
    }

    fun read(): List<PauseEvent> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        file.readLines().mapNotNull(::parse)
    }

    fun clear() {
        synchronized(lock) { file.delete() }
    }

    private fun parse(line: String): PauseEvent? {
        val parts = line.split('\t')
        if (parts.size != 3) return null
        val at = parts[0].toLongOrNull() ?: return null
        val outcome = Outcome.entries.firstOrNull { it.name == parts[1] } ?: return null
        return PauseEvent(at, parts[2], outcome)
    }

    companion object {
        private const val FILE_NAME = "pauses.log"
        private val lock = Any()
    }
}
