package net.foxmediax.scriptfx.core.states

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object StatesManager {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /** Глобальные состояния сервера */
    private val global = StateStorage()

    /** Состояния игроков */
    private val players = ConcurrentHashMap<UUID, StateStorage>()

    private var dataFolder: File? = null

    fun global(): StateStorage = global

    fun player(player: ServerPlayer): StateStorage {
        return players.computeIfAbsent(player.uuid) { StateStorage() }
    }

    fun player(uuid: UUID): StateStorage {
        return players.computeIfAbsent(uuid) { StateStorage() }
    }

    fun clearPlayer(uuid: UUID) {
        players.remove(uuid)
    }

    fun clearAll() {
        global.clear()
        players.clear()
    }

    // ==================== СОХРАНЕНИЕ / ЗАГРУЗКА ====================

    fun init() {
        val folder = FabricLoader.getInstance().configDir.resolve("scriptfx/states").toFile()
        if (!folder.exists()) {
            folder.mkdirs()
        }
        dataFolder = folder
    }

    fun save() {
        val folder = dataFolder ?: return

        try {
            // Глобальные
            val globalFile = File(folder, "global.json")
            globalFile.writeText(gson.toJson(global.asMap()))

            // Игроки
            val playersFolder = File(folder, "players")
            if (!playersFolder.exists()) playersFolder.mkdirs()

            players.forEach { (uuid, storage) ->
                val file = File(playersFolder, "$uuid.json")
                file.writeText(gson.toJson(storage.asMap()))
            }

            ScriptFXLog.info("States сохранены")
        } catch (e: Exception) {
            ScriptFXLog.error("Не удалось сохранить States", e)
        }
    }

    fun load() {
        val folder = dataFolder ?: return

        try {
            // Глобальные
            val globalFile = File(folder, "global.json")
            if (globalFile.exists()) {
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val data: Map<String, Any> = gson.fromJson(globalFile.readText(), type) ?: emptyMap()
                global.load(data)
            }

            // Игроки
            val playersFolder = File(folder, "players")
            if (playersFolder.exists()) {
                playersFolder.listFiles()?.forEach { file ->
                    if (file.extension == "json") {
                        try {
                            val uuid = UUID.fromString(file.nameWithoutExtension)
                            val type = object : TypeToken<Map<String, Any>>() {}.type
                            val data: Map<String, Any> = gson.fromJson(file.readText(), type) ?: emptyMap()
                            val storage = StateStorage()
                            storage.load(data)
                            players[uuid] = storage
                        } catch (e: Exception) {
                            ScriptFXLog.warn("Не удалось загрузить states игрока из ${file.name}")
                        }
                    }
                }
            }

            ScriptFXLog.info("States загружены (global + ${players.size} игроков)")
        } catch (e: Exception) {
            ScriptFXLog.error("Не удалось загрузить States", e)
        }
    }

    fun savePlayer(uuid: UUID) {
        val folder = dataFolder ?: return
        val storage = players[uuid] ?: return

        try {
            val playersFolder = File(folder, "players")
            if (!playersFolder.exists()) playersFolder.mkdirs()

            val file = File(playersFolder, "$uuid.json")
            file.writeText(gson.toJson(storage.asMap()))
        } catch (e: Exception) {
            ScriptFXLog.error("Не удалось сохранить states игрока $uuid", e)
        }
    }
}