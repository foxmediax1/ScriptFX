package net.foxmediax.scriptfx.nodes

/**
 * Состояние drag из каталога нод на холст.
 * Modern UI без полноценного Android DnD — тащим через общий holder.
 */
object NodePaletteDrag {
    @Volatile
    var template: NodeTemplate? = null

    @Volatile
    var dragging: Boolean = false

    fun begin(t: NodeTemplate) {
        template = t
        dragging = false // станет true после небольшого сдвига
    }

    fun markMoved() {
        if (template != null) dragging = true
    }

    fun consume(): NodeTemplate? {
        val t = template
        clear()
        return t
    }

    fun clear() {
        template = null
        dragging = false
    }
}