package net.foxmediax.scriptfx.scriptengine

import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

object ScriptFXLog {
    private val logger = LoggerFactory.getLogger("ScriptFX")

    var debugEnabled: Boolean = false

    // Буфер последних логов для отображения в панели
    private val logBuffer = CopyOnWriteArrayList<String>()
    private const val MAX_LOGS = 500

    fun info(msg: String) {
        logger.info(msg)
        addToBuffer("[INFO] $msg")
    }

    fun warn(msg: String) {
        logger.warn(msg)
        addToBuffer("[WARN] $msg")
    }

    fun error(msg: String, throwable: Throwable? = null) {
        if (throwable != null) {
            logger.error(msg, throwable)
            addToBuffer("[ERROR] $msg: ${throwable.message}")
        } else {
            logger.error(msg)
            addToBuffer("[ERROR] $msg")
        }
    }

    fun debug(msg: String) {
        if (debugEnabled) {
            logger.info("[DEBUG] $msg")
            addToBuffer("[DEBUG] $msg")
        }
    }

    fun getRecentLogs(): List<String> {
        return logBuffer.toList()
    }

    fun clearLogs() {
        logBuffer.clear()
    }

    private fun addToBuffer(line: String) {
        logBuffer.add(line)
        if (logBuffer.size > MAX_LOGS) {
            logBuffer.removeAt(0)
        }
    }
}