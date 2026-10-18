package net.foxmediax.scriptfx.quest

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.foxmediax.scriptfx.npc.NpcRegistry
import java.io.File

data class QuestObjective(
    var id: String = "",
    var text: String = "",
    var target: Int = 1          // сколько нужно выполнить (1 = простая галочка)
)

data class QuestDefinition(
    var id: String = "",
    var title: String = "",
    var description: String = "",
    var objectives: MutableList<QuestObjective> = mutableListOf()
)

/** Квесты проектов: config/scriptfx/projects/<проект>/quests/<id>.fxquest (JSON). */
object QuestRegistry {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val byProject = mutableMapOf<String, MutableMap<String, QuestDefinition>>()

    fun questDir(project: String): File =
        File(NpcRegistry.projectsRoot(), "$project/quests").also { if (!it.exists()) it.mkdirs() }

    fun reload() {
        byProject.clear()
        val root = NpcRegistry.projectsRoot()
        if (!root.isDirectory) return

        root.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
            val folder = File(projectDir, "quests")
            if (!folder.isDirectory) return@forEach

            val map = mutableMapOf<String, QuestDefinition>()
            folder.listFiles()
                ?.filter { it.isFile && it.extension.equals("fxquest", ignoreCase = true) }
                ?.forEach { file ->
                    runCatching {
                        val def = gson.fromJson(file.readText(), QuestDefinition::class.java)
                        if (def.id.isNotBlank()) map[def.id] = def
                    }
                }
            byProject[projectDir.name] = map
        }
    }

    fun list(project: String): List<QuestDefinition> =
        byProject[project]?.values?.sortedBy { it.id }.orEmpty()

    fun get(project: String, id: String): QuestDefinition? = byProject[project]?.get(id)

    fun findById(id: String): Pair<String, QuestDefinition>? {
        for ((project, map) in byProject) map[id]?.let { return project to it }
        return null
    }

    fun save(project: String, def: QuestDefinition): Boolean {
        if (def.id.isBlank()) return false
        return runCatching {
            File(questDir(project), "${def.id}.fxquest").writeText(gson.toJson(def))
            byProject.getOrPut(project) { mutableMapOf() }[def.id] = def
            true
        }.getOrDefault(false)
    }

    fun delete(project: String, id: String): Boolean {
        val file = File(questDir(project), "$id.fxquest")
        val ok = !file.exists() || file.delete()
        byProject[project]?.remove(id)
        return ok
    }
}