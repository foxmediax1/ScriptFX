package net.foxmediax.scriptfx.scriptengine

import org.slf4j.LoggerFactory

object ScriptFXLog {
    private val logger = LoggerFactory.getLogger("ScriptFX")
    fun warn(message: String) = logger.warn(message)
    fun error(message: String, e: Throwable? = null) = logger.error(message, e)
}