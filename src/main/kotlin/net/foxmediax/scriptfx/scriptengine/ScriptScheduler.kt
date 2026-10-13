package net.foxmediax.scriptfx.scriptengine

/**
 * Список запущенных скриптов с опциональным лимитом одновременных запусков.
 */
class ScriptScheduler(
    private val maxScripts: () -> Int = { Int.MAX_VALUE },
    private val warn: (String) -> Unit = { ScriptFXLog.warn(it) }
) {
    private val runners = mutableListOf<ScriptRunner>()

    val activeCount: Int get() = runners.count { !it.finished }

    fun add(name: String, factory: () -> ScriptRunner): Boolean {
        val limit = maxScripts()
        if (limit in 1..activeCount) {
            warn("start_script '$name': лимит одновременных скриптов ($limit) исчерпан")
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
        runners.toList().forEach { it.tick(currentTick) }
        runners.removeAll { it.finished }
    }
}