package net.foxmediax.scriptfx.triggers

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import net.foxmediax.scriptfx.scriptengine.GlobalPlayEvent
import net.foxmediax.scriptfx.scriptengine.ScriptCommand
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.foxmediax.scriptfx.scriptengine.Trigger
import net.foxmediax.scriptfx.scriptengine.TriggerManager
import java.io.File

enum class TriggerKind {
    ZONE,
    INTERACT,
    TIMER,
    WORLD_START
}

data class TriggerDefinition(
    var id: String,
    var title: String,
    var kind: TriggerKind = TriggerKind.ZONE,
    var project: String = "default",
    var x: Double = 0.0,
    var y: Double = 64.0,
    var z: Double = 0.0,
    var dimension: String = "overworld",
    var radius: Double = 10.0,
    var actionScript: String = "",
    var once: Boolean = true,
    var active: Boolean = true,
    var itemId: String? = null,
    var onExit: Boolean = false
)

object TriggerRegistry {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val byId = linkedMapOf<String, TriggerDefinition>()

    fun projectsRoot(): File =
        FabricLoader.getInstance()
            .configDir
            .resolve("scriptfx/projects")
            .toFile()

    fun listProjects(): List<String> =
        projectsRoot()
            .listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()

    fun reload() {
        byId.clear()
        val root = projectsRoot()
        if (!root.isDirectory) return

        root.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
            val dir = File(projectDir, "triggers")
            if (!dir.isDirectory) return@forEach
            dir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".json", true) }
                ?.forEach { file ->
                    runCatching {
                        val def = parse(file.readText()) ?: return@forEach
                        def.project = projectDir.name
                        byId[def.id] = def
                    }.onFailure {
                        ScriptFXLog.warn("Trigger parse failed: ${file.path}: ${it.message}")
                    }
                }
        }
        rebindManager()
    }

    fun list(): List<TriggerDefinition> = byId.values.toList()

    fun list(project: String): List<TriggerDefinition> =
        byId.values.filter { it.project == project }

    fun get(id: String): TriggerDefinition? = byId[id]

    fun save(def: TriggerDefinition): Boolean {
        val cleanId = def.id.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        if (cleanId.isEmpty()) return false
        def.id = cleanId

        val dir = File(File(projectsRoot(), def.project), "triggers").also { it.mkdirs() }
        val file = File(dir, "$cleanId.json")
        file.writeText(toJson(def))
        byId[cleanId] = def
        rebindManager()
        return true
    }

    fun delete(id: String): Boolean {
        val def = byId.remove(id) ?: return false
        val file = File(File(File(projectsRoot(), def.project), "triggers"), "$id.json")
        file.delete()
        rebindManager()
        return true
    }

    /** Сбрасывает TriggerManager и регистрирует только active-триггеры. */
    fun rebindManager() {
        TriggerManager.clear()
        for (def in byId.values) {
            if (!def.active) continue
            toRuntime(def)?.let { TriggerManager.register(it) }
        }
        ScriptFXLog.info(
            "TriggerRegistry: bound ${byId.values.count { it.active }} active triggers"
        )
    }

    fun toRuntime(def: TriggerDefinition): Trigger? {
        val body = buildBody(def.actionScript)
        val name = def.id
        return when (def.kind) {
            TriggerKind.ZONE -> Trigger.Checkpoint(
                x = def.x,
                y = def.y,
                z = def.z,
                dimension = def.dimension,
                radius = def.radius,
                onExit = def.onExit,
                scriptName = name,
                body = body,
                once = def.once
            )
            TriggerKind.WORLD_START -> Trigger.WorldStart(
                scriptName = name,
                body = body,
                once = def.once
            )
            TriggerKind.INTERACT -> Trigger.GlobalPlay(
                event = GlobalPlayEvent.PLAYER_CLICK,
                itemId = def.itemId ?: "minecraft:stick",
                scriptName = name,
                body = body,
                once = def.once
            )
            TriggerKind.TIMER -> null
        }
    }

    private fun buildBody(action: String): List<ScriptCommand> {
        val a = action.trim()
        if (a.isEmpty()) return emptyList()

        val parts = a.split(Regex("\\s+"), limit = 2)
        val cmd = parts[0].removePrefix("/").lowercase()
        val rest = parts.getOrNull(1)?.trim().orEmpty()

        return when {
            rest.isNotEmpty() -> {
                val args = tokenizeArgs(rest)
                listOf(ScriptCommand(cmd, args, 0))
            }
            else -> listOf(ScriptCommand("startscript", listOf(a), 0))
        }
    }

    private fun tokenizeArgs(s: String): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        var inQuotes = false
        for (ch in s) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch.isWhitespace() && !inQuotes -> {
                    if (buf.isNotEmpty()) {
                        out += buf.toString()
                        buf.clear()
                    }
                }
                else -> buf.append(ch)
            }
        }
        if (buf.isNotEmpty()) out += buf.toString()
        return out
    }

    private fun toJson(def: TriggerDefinition): String {
        val o = JsonObject()
        o.addProperty("id", def.id)
        o.addProperty("title", def.title)
        o.addProperty("kind", def.kind.name)
        o.addProperty("x", def.x)
        o.addProperty("y", def.y)
        o.addProperty("z", def.z)
        o.addProperty("dimension", def.dimension)
        o.addProperty("radius", def.radius)
        o.addProperty("actionScript", def.actionScript)
        o.addProperty("once", def.once)
        o.addProperty("active", def.active)
        o.addProperty("onExit", def.onExit)
        def.itemId?.let { o.addProperty("itemId", it) }
        return gson.toJson(o)
    }

    private fun parse(text: String): TriggerDefinition? {
        val o = JsonParser.parseString(text).asJsonObject
        val id = o.get("id")?.asString ?: return null
        val kind = runCatching {
            TriggerKind.valueOf(o.get("kind")?.asString ?: "ZONE")
        }.getOrDefault(TriggerKind.ZONE)
        return TriggerDefinition(
            id = id,
            title = o.get("title")?.asString ?: id,
            kind = kind,
            x = o.get("x")?.asDouble ?: 0.0,
            y = o.get("y")?.asDouble ?: 64.0,
            z = o.get("z")?.asDouble ?: 0.0,
            dimension = o.get("dimension")?.asString ?: "overworld",
            radius = o.get("radius")?.asDouble ?: 10.0,
            actionScript = o.get("actionScript")?.asString ?: "",
            once = o.get("once")?.asBoolean ?: true,
            active = o.get("active")?.asBoolean ?: true,
            itemId = o.get("itemId")?.asString,
            onExit = o.get("onExit")?.asBoolean ?: false
        )
    }
}