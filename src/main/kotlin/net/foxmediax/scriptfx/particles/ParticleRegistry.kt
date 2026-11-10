package net.foxmediax.scriptfx.particles

import net.fabricmc.loader.api.FabricLoader
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import java.io.File

object ParticleRegistry {

    data class Entry(
        val effect: ParticleEffect,
        val file: File,
        val project: String
    )

    private val byId = mutableMapOf<String, Entry>()

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
                        val entry = Entry(effect, file, projectDir.name)
                        byId[effect.identifier] = entry
                        val short = effect.identifier.substringAfter(':', effect.identifier)
                        if (short != effect.identifier) {
                            byId.putIfAbsent(short, entry)
                        }
                        ScriptFXLog.info("Particle loaded: ${effect.identifier} (${file.name})")
                    } else {
                        ScriptFXLog.warn("Particle parse failed: ${file.path}")
                    }
                }
        }
    }

    fun get(id: String): ParticleEffect? =
        byId[id]?.effect ?: byId[id.removeSurrounding("\"")]?.effect

    fun getEntry(id: String): Entry? =
        byId[id] ?: byId[id.removeSurrounding("\"")]

    fun list(): List<ParticleEffect> =
        byId.values
            .map { it.effect }
            .distinctBy { it.identifier }
            .sortedBy { it.identifier }

    fun listEntries(): List<Entry> =
        byId.values
            .distinctBy { it.effect.identifier }
            .sortedBy { it.effect.identifier }

    fun listProjects(): List<String> =
        projectsRoot()
            .listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()

    enum class CreateType { BURST, STEADY }

    /**
     * Создаёт минимальный Snowstorm .particle.json и перезагружает реестр.
     * @return identifier или null
     */
    fun create(
        project: String,
        shortName: String,
        type: CreateType = CreateType.BURST
    ): String? {
        val clean = shortName.trim()
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .ifBlank { return null }

        val projectDir = File(projectsRoot(), project)
        val dir = File(projectDir, "particles").also { it.mkdirs() }
        val file = File(dir, "$clean.particle.json")
        if (file.exists()) {
            ScriptFXLog.warn("Particle file already exists: ${file.path}")
            return null
        }

        val identifier = "scriptfx:$clean"
        file.writeText(templateJson(identifier, type))
        reload()
        return identifier
    }

    fun delete(identifier: String): Boolean {
        val entry = getEntry(identifier) ?: return false
        val ok = entry.file.delete()
        if (ok) reload()
        return ok
    }

    private fun templateJson(identifier: String, type: CreateType): String {
        val rateBlock = when (type) {
            CreateType.BURST -> """
                "minecraft:emitter_rate_instant": {
                  "particle_count": 40
                },
                "minecraft:emitter_lifetime_once": {
                  "active_time": 1
                }
            """.trimIndent()
            CreateType.STEADY -> """
                "minecraft:emitter_rate_steady": {
                  "spawn_rate": 20,
                  "max_particles": 200
                },
                "minecraft:emitter_lifetime_looping": {
                  "active_time": 3
                }
            """.trimIndent()
        }

        return """
        {
          "format_version": "1.10.0",
          "particle_effect": {
            "description": {
              "identifier": "$identifier",
              "basic_render_parameters": {
                "material": "particles_alpha",
                "texture": "minecraft:textures/particle/particles"
              }
            },
            "components": {
              $rateBlock,
              "minecraft:emitter_shape_point": {
                "offset": [0, 0, 0]
              },
              "minecraft:particle_lifetime_expression": {
                "max_lifetime": 1.2
              },
              "minecraft:particle_initial_speed": 1.5,
              "minecraft:particle_motion_dynamic": {
                "linear_acceleration": [0, 1.5, 0]
              },
              "minecraft:particle_appearance_billboard": {
                "size": [0.25, 0.25],
                "facing_camera_mode": "rotate_xyz",
                "uv": {
                  "texture_width": 128,
                  "texture_height": 128,
                  "uv": [0, 0],
                  "uv_size": [8, 8]
                }
              },
              "minecraft:particle_appearance_tinting": {
                "color": [0.7, 0.3, 1.0, 1.0]
              }
            }
          }
        }
        """.trimIndent()
    }
}