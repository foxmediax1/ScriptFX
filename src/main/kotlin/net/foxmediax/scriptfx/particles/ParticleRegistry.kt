package net.foxmediax.scriptfx.particles

import net.fabricmc.loader.api.FabricLoader
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import java.io.File

object ParticleRegistry {

    private val byId = mutableMapOf<String, ParticleEffect>()

    fun projectsRoot(): File =
        FabricLoader.getInstance()
            .configDir
            .resolve("scriptfx/projects")
            .toFile()

    fun reload() {
        byId.clear()
        val root = projectsRoot()
        if (!root.isDirectory) return

        root.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
            val dir = File(projectDir, "particles")
            if (!dir.isDirectory) return@forEach
            dir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".particle.json", ignoreCase = true) }
                .forEach { file ->
                    val text = runCatching { file.readText() }.getOrNull() ?: return@forEach
                    val effect = ParticleEffect.parse(text)
                    if (effect != null) {
                        byId[effect.identifier] = effect
                        // также короткий id без namespace
                        val short = effect.identifier.substringAfter(':', effect.identifier)
                        if (short != effect.identifier) {
                            byId.putIfAbsent(short, effect)
                        }
                        ScriptFXLog.info("Particle loaded: ${effect.identifier} (${file.name})")
                    } else {
                        ScriptFXLog.warn("Particle parse failed: ${file.path}")
                    }
                }
        }
    }

    fun get(id: String): ParticleEffect? =
        byId[id] ?: byId[id.removeSurrounding("\"")]

    fun list(): List<ParticleEffect> =
        byId.values.distinctBy { it.identifier }.sortedBy { it.identifier }
}