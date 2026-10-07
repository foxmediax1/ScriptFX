package net.foxmediax.scriptfx.gui

/** Перенос и обрезка текста. width измеряет ширину строки в пикселях. */
internal object TextLayout {

    fun wrap(text: String, maxWidth: Int, width: (String) -> Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (width(candidate) > maxWidth && current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    /** Обрезает текст с "..." так, чтобы он помещался в maxWidth пикселей. */
    fun fit(text: String, maxWidth: Int, width: (String) -> Int): String {
        if (width(text) <= maxWidth) return text

        val ellipsis = "..."
        var end = text.length
        while (end > 0 && width(text.substring(0, end) + ellipsis) > maxWidth) {
            end--
        }
        return text.substring(0, end).trimEnd() + ellipsis
    }
}