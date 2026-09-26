package net.foxmediax.scriptfx.scriptengine

import net.fabricmc.loader.api.FabricLoader
import java.io.File

object ScriptManager {

    private val loadedScripts = mutableMapOf<String, List<ScriptCommand>>()
    private val activeRunners = mutableListOf<ScriptRunner>()

    fun reload() {
        loadedScripts.clear()
        val projectsDir = FabricLoader.getInstance().configDir.resolve("scriptfx/projects").toFile()
        if (!projectsDir.exists()) {
            projectsDir.mkdirs()
            return
        }

        projectsDir.listFiles()?.forEach { projectDir ->
            File(projectDir, "scripts").walkTopDown()
                .filter { it.isFile && it.extension == "sfxs" }
                .forEach { file -> loadedScripts[file.nameWithoutExtension] = ScriptParser.parse(file.readText()) }
        }

        ScriptFXLog.warn("ScriptFX: загружено скриптов — ${loadedScripts.size}")
    }

    fun loadedScript(name: String): List<ScriptCommand>? = loadedScripts[name]

    fun startScript(name: String, context: ScriptContext) {
        val commands = loadedScripts[name] ?: run {
            ScriptFXLog.warn("startscript: '$name' не найден")
            return
        }
        activeRunners.add(ScriptRunner(commands, context))
    }

    fun runAdHoc(commands: List<ScriptCommand>, context: ScriptContext) {
        activeRunners.add(ScriptRunner(commands, context))
    }

    fun tickAll(currentTick: Long) {
        activeRunners.toList().forEach { it.tick(currentTick) }
        activeRunners.removeAll { it.finished }
    }
}