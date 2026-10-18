package net.foxmediax.scriptfx.config

/** Как показывать сообщения скриптов (print / printNPC). */
enum class MessageDisplayMode(val id: String, val displayName: String) {
    VANILLA("vanilla", "Стандартный (в чате)"),
    CENTER("center", "По центру экрана");

    companion object {
        fun fromId(id: String?): MessageDisplayMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CENTER
    }
}