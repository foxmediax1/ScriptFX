package net.foxmediax.scriptfx.scriptengine

import net.fabricmc.loader.api.FabricLoader
import net.foxmediax.scriptfx.config.ScriptFXConfig
import java.io.File
import net.foxmediax.scriptfx.npc.NpcRegistry

object ScriptManager {

    private val loadedScripts = mutableMapOf<String, List<ScriptCommand>>()
    private val scheduler = ScriptScheduler(limit = { ScriptFXConfig.maxScripts })

    fun reload() {
        loadedScripts.clear()
        TriggerManager.clear()
        AvatarLoader.clearCache()
        NpcRegistry.reload()

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
                        ScriptParser.parse(file.readText())
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
        return scheduler.add(name) { newRunner(commands, name, context) }
    }

    fun runAdHoc(commands: List<ScriptCommand>, context: ScriptContext, name: String = "ad-hoc"): Boolean =
        scheduler.add(name) { newRunner(commands, name, context) }

    fun stopScript(name: String, reason: String = "командой stop_script"): Int =
        scheduler.stop(name, reason)

    fun stopAllScripts(reason: String = "stop_all_scripts"): Int =
        scheduler.stopAll(reason)

    fun tickAll(currentTick: Long) = scheduler.tick(currentTick)

    private fun newRunner(commands: List<ScriptCommand>, name: String, context: ScriptContext) =
        ScriptRunner(
            commands = commands,
            scriptName = name,
            executor = { CommandRegistry.execute(it, context) },
            lookup = { loadedScript(it) }
        )
}