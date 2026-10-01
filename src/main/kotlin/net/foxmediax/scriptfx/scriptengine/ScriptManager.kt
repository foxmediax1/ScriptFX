package net.foxmediax.scriptfx.scriptengine

import net.fabricmc.loader.api.FabricLoader
import java.io.File

object ScriptManager {

    private val loadedScripts = mutableMapOf<String, List<ScriptCommand>>()
    private val activeRunners = mutableListOf<ScriptRunner>()

    fun reload() {
        loadedScripts.clear()
        TriggerManager.clear()

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
                    val commands = ScriptParser.parse(file.readText())
                    val name = file.nameWithoutExtension
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

    fun startScript(name: String, context: ScriptContext) {
        val commands = loadedScripts[name] ?: run {
            ScriptFXLog.warn("startscript: '$name' не найден")
            return
        }
        activeRunners.add(ScriptRunner(commands, context, name))
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

    fun runAdHoc(commands: List<ScriptCommand>, context: ScriptContext, name: String = "ad-hoc") {
        activeRunners.add(ScriptRunner(commands, context, name))
    }

    fun tickAll(currentTick: Long) {
        activeRunners.toList().forEach { it.tick(currentTick) }
        activeRunners.removeAll { it.finished }
    }
}