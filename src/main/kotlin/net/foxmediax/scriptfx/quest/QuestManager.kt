package net.foxmediax.scriptfx.quest

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.foxmediax.scriptfx.network.QuestSyncPayload
import net.foxmediax.scriptfx.network.QuestToastPayload
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.math.max

/** Состояние одного квеста у игрока (хранится в мире). status: active / completed / failed. */
class PlayerQuest(
    var status: String = "active",
    var progress: MutableMap<String, Int> = mutableMapOf()
)

/** Серверная логика квестов. Вызывается из потока сервера (команды скриптов). */
object QuestManager {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val mapType = object : TypeToken<MutableMap<String, PlayerQuest>>() {}.type
    private val states = HashMap<UUID, MutableMap<String, PlayerQuest>>()

    // ---------------------------------------------------------------- хранение

    private fun file(server: MinecraftServer, id: UUID): Path =
        server.getWorldPath(LevelResource.ROOT).resolve("scriptfx/quests/$id.json")

    private fun stateOf(player: ServerPlayer): MutableMap<String, PlayerQuest> =
        states.getOrPut(player.uuid) { load(player) }

    private fun load(player: ServerPlayer): MutableMap<String, PlayerQuest> {
        val f = file(player.level().server, player.uuid)
        if (!f.exists()) return mutableMapOf()
        return runCatching {
            gson.fromJson<MutableMap<String, PlayerQuest>>(f.readText(), mapType) ?: mutableMapOf()
        }.getOrElse {
            ScriptFXLog.warn("Квесты игрока ${player.uuid}: не удалось прочитать файл (${it.message})")
            mutableMapOf()
        }
    }

    private fun persist(player: ServerPlayer) {
        val f = file(player.level().server, player.uuid)
        runCatching {
            f.parent.createDirectories()
            f.writeText(gson.toJson(stateOf(player)))
        }.onFailure { ScriptFXLog.warn("Квесты игрока ${player.uuid}: не удалось сохранить (${it.message})") }
    }

    // ---------------------------------------------------------------- события

    fun onJoin(player: ServerPlayer) = sync(player)

    fun onLeave(id: UUID) {
        states.remove(id)
    }

    /** После /scriptfx reload: определения могли измениться, обновляем журналы. */
    fun resyncAll(server: MinecraftServer) {
        server.playerList.players.forEach { sync(it) }
    }

    // ---------------------------------------------------------------- действия

    fun start(player: ServerPlayer, questId: String): Boolean {
        val def = definition(questId) ?: return false
        val state = stateOf(player)
        if (state[questId]?.status == "active") return false   // уже идёт

        state[questId] = PlayerQuest("active")                  // повторный старт: заново
        persist(player)
        sync(player)
        toast(player, "started", def.title, def.objectives.firstOrNull()?.text.orEmpty())
        return true
    }

    fun progress(player: ServerPlayer, questId: String, objectiveId: String, amount: Int): Boolean {
        val def = definition(questId) ?: return false
        val quest = stateOf(player)[questId]?.takeIf { it.status == "active" } ?: return false
        val obj = def.objectives.firstOrNull { it.id == objectiveId }
        if (obj == null) {
            ScriptFXLog.warn("quest_progress: у квеста '$questId' нет цели '$objectiveId'")
            return false
        }

        val target = max(1, obj.target)
        val old = quest.progress[objectiveId] ?: 0
        val now = (old + amount).coerceIn(0, target)
        if (now == old) return false

        quest.progress[objectiveId] = now
        persist(player)
        sync(player)

        val line = if (target > 1) "${obj.text} ($now/$target)" else obj.text
        toast(player, "updated", def.title, if (now >= target) "✔ $line" else line)
        return true
    }

    fun complete(player: ServerPlayer, questId: String): Boolean {
        val def = definition(questId) ?: return false
        val quest = stateOf(player)[questId]?.takeIf { it.status == "active" } ?: return false

        quest.status = "completed"
        def.objectives.forEach { quest.progress[it.id] = max(1, it.target) }
        persist(player)
        sync(player)
        toast(player, "completed", def.title, "")
        return true
    }

    fun fail(player: ServerPlayer, questId: String): Boolean {
        val def = definition(questId) ?: return false
        val quest = stateOf(player)[questId]?.takeIf { it.status == "active" } ?: return false

        quest.status = "failed"
        persist(player)
        sync(player)
        toast(player, "failed", def.title, "")
        return true
    }

    /** none / active / completed / failed */
    fun statusOf(player: ServerPlayer, questId: String): String =
        stateOf(player)[questId]?.status ?: "none"

    // ---------------------------------------------------------------- сеть

    private fun definition(questId: String): QuestDefinition? {
        val def = QuestRegistry.findById(questId)?.second
        if (def == null) ScriptFXLog.warn("Квест '$questId' не найден (файл .fxquest в папке quests проекта)")
        return def
    }

    fun sync(player: ServerPlayer) {
        val arr = JsonArray()
        for ((id, quest) in stateOf(player)) {
            val def = QuestRegistry.findById(id)?.second ?: continue
            val o = JsonObject()
            o.addProperty("id", def.id)
            o.addProperty("title", def.title.ifBlank { def.id })
            o.addProperty("description", def.description)
            o.addProperty("status", quest.status)
            val objs = JsonArray()
            for (ob in def.objectives) {
                val x = JsonObject()
                x.addProperty("id", ob.id)
                x.addProperty("text", ob.text)
                x.addProperty("target", max(1, ob.target))
                x.addProperty("progress", quest.progress[ob.id] ?: 0)
                objs.add(x)
            }
            o.add("objectives", objs)
            arr.add(o)
        }
        ServerPlayNetworking.send(player, QuestSyncPayload(arr.toString()))
    }

    private fun toast(player: ServerPlayer, kind: String, title: String, text: String) {
        ServerPlayNetworking.send(player, QuestToastPayload(kind, title, text))
    }
}