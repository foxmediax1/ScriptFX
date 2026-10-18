package net.foxmediax.scriptfx.gui

/** Текущая страница документации и история переходов (для кнопок "домой" и "назад"). */
internal class DocumentationNavigator(
    private val homeSection: String = "Главное меню",
    private val homeSubsection: String = "Credits"
) {
    var section: String = homeSection
        private set
    var subsection: String = homeSubsection
        private set

    /** История хранит страницы, с которых мы ушли. */
    private val history = mutableListOf<Pair<String, String>>()
    private var index = -1

    val canGoBack: Boolean get() = index >= 0

    fun reset() {
        section = homeSection
        subsection = homeSubsection
        history.clear()
        index = -1
    }

    fun open(newSection: String, newSubsection: String, addToHistory: Boolean = true) {
        if (addToHistory && (section != newSection || subsection != newSubsection)) {
            // Переход после "назад" отбрасывает "будущую" часть истории.
            while (history.size > index + 1) history.removeAt(history.lastIndex)
            history.add(section to subsection)
            index = history.lastIndex
        }
        section = newSection
        subsection = newSubsection
    }

    /** Возвращается на предыдущую страницу. false, если вернуться некуда. */
    fun back(): Boolean {
        if (index < 0) return false
        val previous = history[index]
        index--
        open(previous.first, previous.second, addToHistory = false)
        return true
    }

    fun home() = open(homeSection, homeSubsection)
}