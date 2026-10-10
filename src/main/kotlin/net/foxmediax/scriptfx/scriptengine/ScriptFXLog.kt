package net.foxmediax.scriptfx.scriptengine

import org.slf4j.LoggerFactory

object ScriptFXLog {

    enum class Level { INFO, WARN, ERROR }

    data class Entry(
        val time: Long,
        val level: Level,
        val message: String
    )

    private val logger = LoggerFactory.getLogger("ScriptFX")
    private val lock = Any()
    private val buffer = ArrayDeque<Entry>()
    private const val MAX_ENTRIES = 500

    fun info(msg: String) {
        logger.info(msg)
        push(Level.INFO, msg)
    }

    fun warn(msg: String) {
        logger.warn(msg)
        push(Level.WARN, msg)
    }

    fun error(msg: String) {
        logger.error(msg)
        push(Level.ERROR, msg)
    }

    fun error(message: String, e: Throwable? = null) {
        if (e != null) logger.error(message, e)
        else logger.error(message)
        val extra = e?.message?.let { ": $it" } ?: ""
        push(Level.ERROR, message + extra)
    }

    /** Снимок для UI — безопасен с любого потока. */
    fun snapshot(): List<Entry> = synchronized(lock) { buffer.toList() }

    fun clear() = synchronized(lock) { buffer.clear() }

    private fun push(level: Level, message: String) {
        synchronized(lock) {
            buffer.addLast(Entry(System.currentTimeMillis(), level, message))
            while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
        }
    }
}