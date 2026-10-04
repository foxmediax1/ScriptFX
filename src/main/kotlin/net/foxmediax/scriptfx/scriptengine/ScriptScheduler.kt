package net.foxmediax.scriptfx.scriptengine

/**
 * Список запущенных скриптов с лимитом одновременных запусков.
 * Не зависит от Minecraft и конфига: лимит приходит функцией.
 */
class ScriptScheduler(
    private val limit: () -> Int,
    private val warn: (String) -> Unit = { ScriptFXLog.warn(it) }
) {
    private val runners = mutableListOf<ScriptRunner>()

    private var currentTick = 0L
    private var lastLimitWarnTick = -LIMIT_WARN_INTERVAL_TICKS
    private var rejectedSinceWarn = 0

    /** Сколько скриптов работает прямо сейчас. */
    val activeCount: Int get() = runners.count { !it.finished }

    /**
     * Добавляет раннер, если не достигнут лимит. Раннер создаётся фабрикой только после
     * проверки, чтобы отклонённый запуск не писал в лог "Скрипт запущен".
     */
    fun add(name: String, factory: () -> ScriptRunner): Boolean {
        val max = limit().coerceAtLeast(1)
        if (activeCount >= max) {
            rejectedSinceWarn++
            if (currentTick - lastLimitWarnTick >= LIMIT_WARN_INTERVAL_TICKS) {
                warn(
                    "Достигнут лимит одновременных скриптов ($max). Запуск '$name' отклонён" +
                            " (отклонено с прошлого предупреждения: $rejectedSinceWarn). " +
                            "Возможно, скрипт запускает сам себя; лимит меняется в настройке maxScripts."
                )
                lastLimitWarnTick = currentTick
                rejectedSinceWarn = 0
            }
            return false
        }
        runners.add(factory())
        return true
    }

    /** Останавливает все работающие экземпляры скрипта с этим именем. */
    fun stop(name: String, reason: String): Int {
        val matching = runners.filter { it.name == name && !it.finished }
        matching.forEach { it.stop(reason) }
        runners.removeAll(matching.toSet())
        return matching.size
    }

    fun stopAll(reason: String): Int {
        val running = runners.filter { !it.finished }
        running.forEach { it.stop(reason) }
        runners.clear()
        return running.size
    }

    fun tick(currentTick: Long) {
        this.currentTick = currentTick
        runners.toList().forEach { it.tick(currentTick) }
        runners.removeAll { it.finished }
    }

    companion object {
        /** Как часто (в тиках) предупреждать о достигнутом лимите. */
        const val LIMIT_WARN_INTERVAL_TICKS = 100L
    }
}