package net.foxmediax.scriptfx.scriptengine

import net.foxmediax.scriptfx.config.ScriptFXConfig
import org.slf4j.LoggerFactory

object ScriptFXLog {

    enum class Level(val rank: Int) {
        DEBUG(0),
        INFO(1),
        WARN(2),
        ERROR(3)
    }

    data class Entry(
        val time: Long,
        val level: Level,
        val message: String
    )

    private val logger = LoggerFactory.getLogger("ScriptFX")
    private val lock = Any()
    private val buffer = ArrayDeque<Entry>()
    private const val MAX_ENTRIES = 500

    private fun minLevel(): Level = when (ScriptFXConfig.logLevel.lowercase().trim()) {
        "debug" -> Level.DEBUG
        "warn" -> Level.WARN
        "error" -> Level.ERROR
        else -> Level.INFO
    }

    private fun enabled(level: Level): Boolean = level.rank >= minLevel().rank

    fun debug(msg: String) {
        if (!enabled(Level.DEBUG)) return
        logger.debug(msg)
        push(Level.DEBUG, msg)
    }

    fun info(msg: String) {
        if (!enabled(Level.INFO)) return
        logger.info(msg)
        push(Level.INFO, msg)
    }

    fun warn(msg: String) {
        if (!enabled(Level.WARN)) return
        logger.warn(msg)
        push(Level.WARN, msg)
    }

    fun error(msg: String) {
        if (!enabled(Level.ERROR)) return
        logger.error(msg)
        push(Level.ERROR, msg)
    }

    fun error(message: String, e: Throwable? = null) {
        if (!enabled(Level.ERROR)) return
        if (e != null) logger.error(message, e) else logger.error(message)
        val extra = e?.message?.let { ": $it" } ?: ""
        push(Level.ERROR, message + extra)
    }

    fun snapshot(): List<Entry> = synchronized(lock) { buffer.toList() }

    fun clear() = synchronized(lock) { buffer.clear() }

    private fun push(level: Level, message: String) {
        synchronized(lock) {
            buffer.addLast(Entry(System.currentTimeMillis(), level, message))
            while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
        }
    }
}