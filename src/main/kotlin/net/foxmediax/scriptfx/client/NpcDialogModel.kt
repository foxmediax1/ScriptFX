package net.foxmediax.scriptfx.client

import net.foxmediax.scriptfx.config.ScriptFXConfig

/** Состояние диалога вне экрана: typewriter / fade не сбрасываются при recreate. */
class NpcDialogModel(
    val npcName: String,
    fullText: String,
    val buttons: List<String>,
    val onChoose: (Int) -> Unit
) {
    val shownAt = System.currentTimeMillis()
    @Volatile var picked = false
    @Volatile var textDone = false

    val bodyText: String = fullText.trim().let { t ->
        val prefix = "$npcName:"
        if (npcName.isNotBlank() && t.startsWith(prefix, ignoreCase = true))
            t.substring(prefix.length).trim()
        else t
    }

    fun fade(): Float {
        val ms = ScriptFXConfig.dialogFadeMs.coerceAtLeast(1).toFloat()
        val t = ((System.currentTimeMillis() - shownAt) / ms).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun revealedChars(): Int {
        if (textDone) return bodyText.length
        val perChar = ScriptFXConfig.dialogTypewriterMs.coerceAtLeast(1)
        return ((System.currentTimeMillis() - shownAt) / perChar).toInt()
            .coerceIn(0, bodyText.length)
    }

    fun skipTypewriter() {
        textDone = true
    }

    fun isTextComplete(): Boolean = revealedChars() >= bodyText.length
}