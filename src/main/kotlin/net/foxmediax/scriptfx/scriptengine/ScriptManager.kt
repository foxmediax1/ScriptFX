package net.foxmediax.scriptfx.scriptengine

import net.fabricmc.loader.api.FabricLoader
import net.foxmediax.scriptfx.config.ScriptFXConfig
import java.io.File

object ScriptManager {

    /** Как часто (в тиках) предупреждать о достигнутом лимите. */
    private const val LIMIT_WARN_INTERVAL_TICKS = 100L

    private val loadedScripts = mutableMapOf<String, List<ScriptCommand>>()
    private val activeRunners = mutableListOf<ScriptRunner>()

    private var currentTick = 0L
    private var lastLimitWarnTick = -LIMIT_WARN_INTERVAL_TICKS
    private var rejectedSinceWarn = 0

    fun reload() {
        loadedScripts.clear()
        TriggerManager.clear()
        AvatarLoader.clearCache()

        val projectsDir = FabricLoader.getInstance().configDir.resolve("scriptfx/projects").toFile()
        if (!projectsDir.exists()) {
            projectsDir.mkdirs()
            return
        }

        var triggerCount = 0
        projectsDir.listFiles()?.forEach { projectDir ->
            File(projectDir, "scripts").walkTopDown()
                .filter { it.isFile && it.extension == "sfxs" }
                .forEach { file ->
                    val name = file.nameWithoutExtension
                    val commands = try {
                        // BOM в начале файла (его добавляют некоторые редакторы Windows)
                        // превращает первую команду в "\uFEFFprint".
                        ScriptParser.parse(file.readText().removePrefix("\uFEFF"))
                    } catch (e: Exception) {
                        ScriptFXLog.error("Не удалось загрузить скрипт '${file.name}'", e)
                        return@forEach
                    }

                    if (loadedScripts.containsKey(name)) {
                        ScriptFXLog.warn("Скрипт с именем '$name' уже загружен из другого проекта — старый будет перезаписан")
                    }
                    loadedScripts[name] = commands
                    triggerCount += TriggerParser.registerAll(name, commands)
                }
        }

        ScriptFXLog.info("ScriptFX: загружено скриптов — ${loadedScripts.size}, из них триггеров — $triggerCount")
    }

    fun loadedScript(name: String): List<ScriptCommand>? = loadedScripts[name]

    /** Возвращает false, если скрипт не найден или достигнут лимит. */
    fun startScript(name: String, context: ScriptContext): Boolean {
        val commands = loadedScripts[name] ?: run {
            ScriptFXLog.warn("startscript: '$name' не найден")
            return false
        }
        if (!hasFreeSlot(name)) return false
        activeRunners.add(ScriptRunner(commands, context, name))
        return true
    }

    fun stopScript(name: String): Int {
        val matching = activeRunners.filter { it.name == name && !it.finished }
        matching.forEach { it.stop() }
        activeRunners.removeAll(matching.toSet())
        return matching.size
    }

    fun stopAllScripts(): Int {
        val running = activeRunners.filter { !it.finished }
        running.forEach { it.stop() }
        activeRunners.clear()
        return running.size
    }

    fun runAdHoc(commands: List<ScriptCommand>, context: ScriptContext, name: String = "ad-hoc"): Boolean {
        if (!hasFreeSlot(name)) return false
        activeRunners.add(ScriptRunner(commands, context, name))
        return true
    }

    fun tickAll(currentTick: Long) {
        this.currentTick = currentTick
        activeRunners.toList().forEach { it.tick(currentTick) }
        activeRunners.removeAll { it.finished }
    }

    /** Лимит одновременно работающих скриптов (настройка maxScripts). */
    private fun hasFreeSlot(name: String): Boolean {
        val limit = ScriptFXConfig.maxScripts.coerceAtLeast(1)
        if (activeRunners.count { !it.finished } < limit) return true

        rejectedSinceWarn++
        if (currentTick - lastLimitWarnTick >= LIMIT_WARN_INTERVAL_TICKS) {
            ScriptFXLog.warn(
                "Достигнут лимит одновременных скриптов ($limit). Запуск '$name' отклонён" +
                        " (отклонено с прошлого предупреждения: $rejectedSinceWarn). " +
                        "Возможно, скрипт запускает сам себя; лимит меняется в настройке maxScripts."
            )
            lastLimitWarnTick = currentTick
            rejectedSinceWarn = 0
        }
        return false
    }
}