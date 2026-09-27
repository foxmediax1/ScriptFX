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
                    if (TriggerParser.tryRegister(name, commands) != null) triggerCount++
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

    fun runAdHoc(commands: List<ScriptCommand>, context: ScriptContext, name: String = "ad-hoc") {
        activeRunners.add(ScriptRunner(commands, context, name))
    }

    fun tickAll(currentTick: Long) {
        activeRunners.toList().forEach { it.tick(currentTick) }
        activeRunners.removeAll { it.finished }
    }
}