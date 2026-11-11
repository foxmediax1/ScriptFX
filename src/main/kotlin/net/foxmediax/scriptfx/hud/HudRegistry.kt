package net.foxmediax.scriptfx.hud

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import java.io.File

enum class HudAnchor {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

enum class HudElementType { RECT, TEXT, IMAGE }

data class HudElement(
    var id: String,
    var type: HudElementType = HudElementType.TEXT,
    var x: Float = 0f,
    var y: Float = 0f,
    var w: Float = 100f,
    var h: Float = 24f,
    var text: String = "",
    var color: Int = 0xFFFFFFFF.toInt(),
    var fontSize: Float = 12f,
    var imagePath: String = "",
    var visible: Boolean = true
)

data class HudAnim(
    var id: String = "fade",
    var type: String = "fade",
    var durationMs: Int = 250,
    var from: Float = 0f,
    var to: Float = 1f
)

data class HudDefinition(
    var id: String,
    var title: String = id,
    var project: String = "default",
    var width: Float = 220f,
    var height: Float = 120f,
    var anchor: HudAnchor = HudAnchor.TOP_LEFT,
    var offsetX: Float = 12f,
    var offsetY: Float = 12f,
    var background: Int = 0xCC12081F.toInt(),
    var elements: MutableList<HudElement> = mutableListOf(),
    var showAnim: HudAnim = HudAnim("show", "fade", 250, 0f, 1f),
    var hideAnim: HudAnim = HudAnim("hide", "fade", 200, 1f, 0f)
)

object HudRegistry {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val byId = linkedMapOf<String, HudDefinition>()

    fun projectsRoot(): File =
        FabricLoader.getInstance().configDir.resolve("scriptfx/projects").toFile()

    fun listProjects(): List<String> =
        projectsRoot().listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted().orEmpty()

    fun reload() {
        byId.clear()
        val root = projectsRoot()
        if (!root.isDirectory) return
        root.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
            val dir = File(projectDir, "hud")
            if (!dir.isDirectory) return@forEach
            dir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".json", true) }
                ?.forEach { file ->
                    runCatching {
                        parse(file.readText())?.let {
                            it.project = projectDir.name
                            byId[it.id] = it
                        }
                    }.onFailure {
                        ScriptFXLog.warn("HUD parse failed: ${file.path}: ${it.message}")
                    }
                }
        }
        ScriptFXLog.info("HudRegistry: loaded ${byId.size} hud(s)")
    }

    fun list(): List<HudDefinition> = byId.values.toList()
    fun list(project: String) = byId.values.filter { it.project == project }
    fun get(id: String): HudDefinition? = byId[id]

    fun save(def: HudDefinition): Boolean {
        val clean = def.id.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        if (clean.isEmpty()) return false
        def.id = clean
        val dir = File(File(projectsRoot(), def.project), "hud").also { it.mkdirs() }
        File(dir, "$clean.json").writeText(toJson(def))
        byId[clean] = def
        return true
    }

    fun delete(id: String): Boolean {
        val def = byId.remove(id) ?: return false
        File(File(File(projectsRoot(), def.project), "hud"), "$id.json").delete()
        return true
    }

    fun createStub(project: String, id: String, title: String = id): HudDefinition {
        val def = HudDefinition(
            id = id,
            title = title,
            project = project,
            elements = mutableListOf(
                HudElement("bg", HudElementType.RECT, 0f, 0f, 220f, 120f, color = 0xCC1A1028.toInt()),
                HudElement(
                    "title", HudElementType.TEXT, 12f, 14f, 196f, 22f,
                    text = title, fontSize = 14f, color = 0xFFE0D4FF.toInt()
                ),
                HudElement(
                    "body", HudElementType.TEXT, 12f, 42f, 196f, 60f,
                    text = "Описание HUD", fontSize = 12f, color = 0xFFB0A0C8.toInt()
                )
            )
        )
        save(def)
        return def
    }

    private fun toJson(def: HudDefinition): String {
        val o = JsonObject()
        o.addProperty("id", def.id)
        o.addProperty("title", def.title)
        o.addProperty("width", def.width)
        o.addProperty("height", def.height)
        o.addProperty("anchor", def.anchor.name)
        o.addProperty("offsetX", def.offsetX)
        o.addProperty("offsetY", def.offsetY)
        o.addProperty("background", def.background)
        val els = JsonArray()
        def.elements.forEach { e ->
            els.add(JsonObject().apply {
                addProperty("id", e.id)
                addProperty("type", e.type.name)
                addProperty("x", e.x); addProperty("y", e.y)
                addProperty("w", e.w); addProperty("h", e.h)
                addProperty("text", e.text)
                addProperty("color", e.color)
                addProperty("fontSize", e.fontSize)
                addProperty("imagePath", e.imagePath)
                addProperty("visible", e.visible)
            })
        }
        o.add("elements", els)
        fun animObj(a: HudAnim) = JsonObject().apply {
            addProperty("id", a.id)
            addProperty("type", a.type)
            addProperty("durationMs", a.durationMs)
            addProperty("from", a.from)
            addProperty("to", a.to)
        }
        o.add("showAnim", animObj(def.showAnim))
        o.add("hideAnim", animObj(def.hideAnim))
        return gson.toJson(o)
    }

    private fun parse(text: String): HudDefinition? {
        val o = JsonParser.parseString(text).asJsonObject
        val id = o.get("id")?.asString ?: return null
        val elements = mutableListOf<HudElement>()
        o.getAsJsonArray("elements")?.forEach { el ->
            val e = el.asJsonObject
            elements += HudElement(
                id = e.get("id")?.asString ?: "el",
                type = runCatching { HudElementType.valueOf(e.get("type").asString) }
                    .getOrDefault(HudElementType.TEXT),
                x = e.get("x")?.asFloat ?: 0f,
                y = e.get("y")?.asFloat ?: 0f,
                w = e.get("w")?.asFloat ?: 100f,
                h = e.get("h")?.asFloat ?: 24f,
                text = e.get("text")?.asString ?: "",
                color = e.get("color")?.asInt ?: 0xFFFFFFFF.toInt(),
                fontSize = e.get("fontSize")?.asFloat ?: 12f,
                imagePath = e.get("imagePath")?.asString ?: "",
                visible = e.get("visible")?.asBoolean ?: true
            )
        }
        fun anim(key: String, fb: HudAnim): HudAnim {
            val a = o.getAsJsonObject(key) ?: return fb
            return HudAnim(
                id = a.get("id")?.asString ?: fb.id,
                type = a.get("type")?.asString ?: "fade",
                durationMs = a.get("durationMs")?.asInt ?: 250,
                from = a.get("from")?.asFloat ?: fb.from,
                to = a.get("to")?.asFloat ?: fb.to
            )
        }
        return HudDefinition(
            id = id,
            title = o.get("title")?.asString ?: id,
            width = o.get("width")?.asFloat ?: 220f,
            height = o.get("height")?.asFloat ?: 120f,
            anchor = runCatching { HudAnchor.valueOf(o.get("anchor").asString) }
                .getOrDefault(HudAnchor.TOP_LEFT),
            offsetX = o.get("offsetX")?.asFloat ?: 12f,
            offsetY = o.get("offsetY")?.asFloat ?: 12f,
            background = o.get("background")?.asInt ?: 0xCC12081F.toInt(),
            elements = elements,
            showAnim = anim("showAnim", HudAnim("show", "fade", 250, 0f, 1f)),
            hideAnim = anim("hideAnim", HudAnim("hide", "fade", 200, 1f, 0f))
        )
    }
}