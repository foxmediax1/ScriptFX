package net.foxmediax.scriptfx.npc

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.io.File

enum class NpcMode(val id: String) {
    DIALOG("dialog"),
    TRAIDING("traiding"),
    INTERACT("interact");

    companion object {
        fun from(raw: String?): NpcMode =
            entries.find { it.id.equals(raw, ignoreCase = true) } ?: INTERACT
    }
}

data class NpcDefinition(
    val id: String,
    val displayName: String = id,
    val model: String = "scriptfx:female_models",
    val texture: String = "scriptfx:textures/npc/temple_skins.png",
    val animation: String = "scriptfx:female_models",
    val defaultAnim: String = "idle",
    val defaultMode: String = NpcMode.INTERACT.id,
    val maxHealth: Float = 20f,
    val invulnerable: Boolean = false,
    val lookAtPlayer: Boolean = true,
    val gravity: Boolean = true,
    val collide: Boolean = true,
    val nametag: Boolean = true,
    val silent: Boolean = false,
    val speed: Float = 0.25f,
    val onInteractScript: String = "",
    val interactKey: String = "X"
) {
    fun mode(): NpcMode = NpcMode.from(defaultMode)
}

object NpcRegistry {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val byProject = mutableMapOf<String, MutableMap<String, NpcDefinition>>()

    fun projectsRoot(): File =
        FabricLoader.getInstance().configDir.resolve("scriptfx/projects").toFile()

    fun npcDir(project: String): File =
        File(projectsRoot(), "$project/npc").also { if (!it.exists()) it.mkdirs() }

    fun reload() {
        byProject.clear()
        val root = projectsRoot()
        if (!root.isDirectory) return
        root.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
            val npcFolder = File(projectDir, "npc")
            if (!npcFolder.isDirectory) return@forEach
            val map = mutableMapOf<String, NpcDefinition>()
            npcFolder.listFiles()
                ?.filter { it.isFile && it.extension == "fxnpc" }
                ?.forEach { file ->
                    runCatching {
                        val def = gson.fromJson(file.readText(), NpcDefinition::class.java)
                        if (def.id.isNotBlank()) map[def.id] = def
                    }
                }
            byProject[projectDir.name] = map
        }
    }

    fun listProjects(): List<String> =
        projectsRoot().listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted().orEmpty()

    fun listNpcs(project: String): List<NpcDefinition> =
        byProject[project]?.values?.sortedBy { it.id }.orEmpty()

    fun get(project: String, id: String): NpcDefinition? =
        byProject[project]?.get(id)

    /** Поиск по id во всех проектах (для spawn в скрипте). */
    fun findById(id: String): Pair<String, NpcDefinition>? {
        for ((project, map) in byProject) {
            map[id]?.let { return project to it }
        }
        return null
    }

    fun save(project: String, def: NpcDefinition): Boolean {
        if (def.id.isBlank()) return false
        val dir = npcDir(project)
        val file = File(dir, "${def.id}.fxnpc")
        return runCatching {
            file.writeText(gson.toJson(def))
            byProject.getOrPut(project) { mutableMapOf() }[def.id] = def
            true
        }.getOrDefault(false)
    }

    fun delete(project: String, id: String): Boolean {
        val file = File(npcDir(project), "$id.fxnpc")
        val ok = !file.exists() || file.delete()
        byProject[project]?.remove(id)
        return ok
    }
}