package net.foxmediax.scriptfx.scriptengine

/**
 * Список запущенных скриптов с опциональным лимитом одновременных запусков.
 * Лимит &lt; 1 трактуется как 1. Предупреждение о лимите — не чаще раза в 100 тиков.
 */
class ScriptScheduler(
    private val maxScripts: () -> Int = { Int.MAX_VALUE },
    private val warn: (String) -> Unit = { ScriptFXLog.warn(it) }
) {
    private val runners = mutableListOf<ScriptRunner>()

    /** Последний currentTick, переданный в tick(). */
    private var lastTick: Long = 0L

    /** Тик последнего предупреждения о лимите. */
    private var lastWarnTick: Long = Long.MIN_VALUE / 2

    val activeCount: Int get() = runners.count { !it.finished }

    fun add(name: String, factory: () -> ScriptRunner): Boolean {
        val limit = maxScripts().coerceAtLeast(1)
        if (activeCount >= limit) {
            // не чаще одного сообщения за 100 тиков (и не чаще одного на «пачку» в одном тике)
            if (lastTick - lastWarnTick >= 100) {
                lastWarnTick = lastTick
                warn("start_script '$name': лимит одновременных скриптов ($limit) исчерпан")
            }
            return false
        }
        runners.add(factory())
        return true
    }

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
        lastTick = currentTick
        runners.toList().forEach { it.tick(currentTick) }
        runners.removeAll { it.finished }
    }
}