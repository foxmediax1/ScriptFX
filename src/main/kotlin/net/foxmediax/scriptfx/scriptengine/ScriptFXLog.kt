package net.foxmediax.scriptfx.scriptengine

import org.slf4j.LoggerFactory

object ScriptFXLog {

    enum class Level { INFO, WARN, ERROR }
    data class Entry(val time: Long, val level: Level, val message: String)

    private val logger = LoggerFactory.getLogger("ScriptFX")
    private val lock = Any()
    private val buffer = ArrayDeque<Entry>()
    private const val MAX_ENTRIES = 500

    private val lines = ArrayDeque<String>()
    private const val MAX = 500

    fun info(msg: String) = append("INFO", msg)
    fun warn(msg: String) = append("WARN", msg)
    fun error(msg: String) = append("ERROR", msg)

    private fun append(level: String, msg: String) {
        val line = "[$level] $msg"
        // существующий вывод в логгер Minecraft оставь
        synchronized(lines) {
            if (lines.size >= MAX) lines.removeFirst()
            lines.addLast(line)
        }
    }

    fun error(message: String, e: Throwable? = null) {
        logger.error(message, e)
        push(Level.ERROR, message + (e?.message?.let { ": $it" } ?: ""))
    }

    /** Снимок текущих записей — безопасен для чтения с любого потока (в т.ч. с потока рендера). */
    fun snapshot(): List<Entry> = synchronized(lock) { buffer.toList() }

    fun clear() = synchronized(lock) { buffer.clear() }

    private fun push(level: Level, message: String) {
        synchronized(lock) {
            buffer.addLast(Entry(System.currentTimeMillis(), level, message))
            while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
        }
    }
}