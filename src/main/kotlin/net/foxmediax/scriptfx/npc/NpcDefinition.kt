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

enum class NpcModelPreset(
    val label: String,
    val model: String,
    val texture: String,
    val animation: String
) {
    FEMALE(
        "Female",
        "scriptfx:female_models",
        "scriptfx:textures/npc/temple_female_skin.png",
        "scriptfx:female_models"
    ),
    MALE(
        "Male",
        "scriptfx:male_models",
        "scriptfx:textures/npc/temple_male_skin.png",
        "scriptfx:male_models"
    ),
    MALE_SLIM(
        "Male slim",
        "scriptfx:male_slim_models",
        "scriptfx:textures/npc/temple_male_slim_skin.png",
        "scriptfx:male_slim_models"
    );

    companion object {
        fun fromModelPath(model: String?): NpcModelPreset? =
            entries.find { it.model.equals(model, ignoreCase = true) }
    }
}

data class NpcDefinition(
    val id: String,
    val displayName: String = id,
    val model: String = NpcModelPreset.FEMALE.model,
    val texture: String = NpcModelPreset.FEMALE.texture,
    val animation: String = NpcModelPreset.FEMALE.animation,
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

    companion object {
        fun create(id: String, preset: NpcModelPreset): NpcDefinition =
            NpcDefinition(
                id = id,
                displayName = id,
                model = preset.model,
                texture = preset.texture,
                animation = preset.animation
            )

        /** Кастом из customs/<name>.* */
        fun createCustom(id: String, assetName: String): NpcDefinition {
            val n = assetName.trim().ifBlank { id }
            return NpcDefinition(
                id = id,
                displayName = id,
                model = "customs:$n",
                texture = "customs:$n",
                animation = "customs:$n"
            )
        }
    }
}

object NpcRegistry {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val byProject = mutableMapOf<String, MutableMap<String, NpcDefinition>>()

    fun projectsRoot(): File =
        FabricLoader.getInstance().configDir.resolve("scriptfx/projects").toFile()

    fun npcRoot(project: String): File =
        File(projectsRoot(), "$project/npc").also { if (!it.exists()) it.mkdirs() }

    fun npcFolder(project: String, npcId: String): File =
        File(npcRoot(project), npcId)

    fun npcFile(project: String, npcId: String): File =
        File(npcFolder(project, npcId), "$npcId.fxnpc")

    fun customsDir(project: String, npcId: String): File =
        File(npcFolder(project, npcId), "customs")

    fun customsModels(project: String, npcId: String): File =
        File(customsDir(project, npcId), "models")

    fun customsTextures(project: String, npcId: String): File =
        File(customsDir(project, npcId), "textures")

    fun customsAnimations(project: String, npcId: String): File =
        File(customsDir(project, npcId), "animations")

    fun ensureNpcLayout(project: String, npcId: String): File {
        val folder = npcFolder(project, npcId)
        folder.mkdirs()
        customsModels(project, npcId).mkdirs()
        customsTextures(project, npcId).mkdirs()
        customsAnimations(project, npcId).mkdirs()
        return folder
    }

    fun reload() {
        byProject.clear()
        val root = projectsRoot()
        if (!root.isDirectory) return

        root.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
            val npcFolder = File(projectDir, "npc")
            if (!npcFolder.isDirectory) return@forEach
            val map = mutableMapOf<String, NpcDefinition>()

            npcFolder.listFiles()?.forEach { entry ->
                when {
                    entry.isDirectory -> {
                        val id = entry.name
                        val primary = File(entry, "$id.fxnpc")
                        if (primary.isFile) loadFxnpc(primary, map)
                        entry.listFiles()
                            ?.filter { it.isFile && it.extension.equals("fxnpc", true) }
                            ?.forEach { loadFxnpc(it, map) }
                    }
                    entry.isFile && entry.extension.equals("fxnpc", true) -> {
                        loadFxnpc(entry, map)
                    }
                }
            }
            byProject[projectDir.name] = map
        }
    }

    private fun loadFxnpc(file: File, map: MutableMap<String, NpcDefinition>) {
        runCatching {
            val def = gson.fromJson(file.readText(), NpcDefinition::class.java)
            if (def != null && def.id.isNotBlank()) map[def.id] = def
        }
    }

    fun listProjects(): List<String> =
        projectsRoot().listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted().orEmpty()

    fun listNpcs(project: String): List<NpcDefinition> =
        byProject[project]?.values?.sortedBy { it.id }.orEmpty()

    fun get(project: String, id: String): NpcDefinition? = byProject[project]?.get(id)

    fun findById(id: String): Pair<String, NpcDefinition>? {
        for ((project, map) in byProject) {
            map[id]?.let { return project to it }
        }
        return null
    }

    fun save(project: String, def: NpcDefinition): Boolean {
        if (def.id.isBlank()) return false
        return runCatching {
            ensureNpcLayout(project, def.id)
            val file = npcFile(project, def.id)
            file.writeText(gson.toJson(def))
            val legacy = File(npcRoot(project), "${def.id}.fxnpc")
            if (legacy.isFile && legacy.absolutePath != file.absolutePath) legacy.delete()
            byProject.getOrPut(project) { mutableMapOf() }[def.id] = def
            true
        }.getOrDefault(false)
    }

    fun delete(project: String, id: String): Boolean {
        val folder = npcFolder(project, id)
        val legacy = File(npcRoot(project), "$id.fxnpc")
        var ok = true
        if (folder.isDirectory) ok = folder.deleteRecursively()
        if (legacy.isFile) ok = legacy.delete() && ok
        byProject[project]?.remove(id)
        return ok
    }
}