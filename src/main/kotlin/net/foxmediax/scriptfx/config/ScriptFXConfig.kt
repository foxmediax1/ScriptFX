package net.foxmediax.scriptfx.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object ScriptFXConfig {
    // Сами настройки — просто поля с дефолтными значениями
    var showHints: Boolean = true
    var maxScripts: Int = 10
    var theme: String = "dark"
    var messageMode: MessageDisplayMode = MessageDisplayMode.CENTER

    var dialogFadeMs: Int = 400
    var dialogTypewriterMs: Int = 25

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath = FabricLoader.getInstance().configDir.resolve("scriptfx.json")

    private data class Data(
        var showHints: Boolean = true,
        var maxScripts: Int = 10,
        var theme: String = "dark",
        var messageMode: String = MessageDisplayMode.CENTER.id,
        var dialogFadeMs: Int = 400,
        var dialogTypewriterMs: Int = 25
    )

    fun load() {
        if (!configPath.exists()) {
            save()
            return
        }
        try {
            val data = gson.fromJson(configPath.readText(), Data::class.java)
            if (data == null) { // пустой файл
                save()
                return
            }
            showHints = data.showHints
            maxScripts = data.maxScripts.coerceIn(1, 50)
            theme = data.theme
            messageMode = MessageDisplayMode.fromId(data.messageMode)
        } catch (e: Exception) {
            // Сохраняем повреждённый файл, чтобы не потерять пользовательские значения.
            try {
                java.nio.file.Files.copy(
                    configPath,
                    configPath.resolveSibling("scriptfx.json.broken"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Exception) { }
            save()
        }
    }

    fun save() {
        val data = Data(showHints, maxScripts, theme, messageMode.id)
        configPath.writeText(gson.toJson(data))
    }
}