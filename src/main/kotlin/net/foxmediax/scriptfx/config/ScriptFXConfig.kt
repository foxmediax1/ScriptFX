package net.foxmediax.scriptfx.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object ScriptFXConfig {
    // Цвета плашки сообщения по умолчанию (ARGB)
    const val DEFAULT_MESSAGE_BG = 0xD91C1436.toInt()
    const val DEFAULT_MESSAGE_BORDER = 0xFFC8B4FF.toInt()

    // Сами настройки — просто поля с дефолтными значениями
    var showHints: Boolean = true
    var maxScripts: Int = 10
    var theme: String = "dark"
    var messageMode: MessageDisplayMode = MessageDisplayMode.CENTER

    var dialogFadeMs: Int = 400
    var dialogTypewriterMs: Int = 25
    var npcOutline: Boolean = true

    // Плашка сообщения: фон и обводка (ARGB)
    var messageBg: Int = DEFAULT_MESSAGE_BG
    var messageBorder: Int = DEFAULT_MESSAGE_BORDER

    var centerSystemMessages: Boolean = true

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath = FabricLoader.getInstance().configDir.resolve("scriptfx.json")

    private data class Data(
        var showHints: Boolean = true,
        var maxScripts: Int = 10,
        var theme: String = "dark",
        var messageMode: String = MessageDisplayMode.CENTER.id,
        var dialogFadeMs: Int = 400,
        var dialogTypewriterMs: Int = 25,
        var npcOutline: Boolean = true,
        var messageBg: Int = DEFAULT_MESSAGE_BG,
        var messageBorder: Int = DEFAULT_MESSAGE_BORDER,
        var centerSystemMessages: Boolean = true
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
            dialogFadeMs = data.dialogFadeMs.coerceIn(1, 5000)
            dialogTypewriterMs = data.dialogTypewriterMs.coerceIn(1, 500)
            npcOutline = data.npcOutline
            messageBg = data.messageBg
            messageBorder = data.messageBorder
            centerSystemMessages = data.centerSystemMessages
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
        val data = Data(
            showHints, maxScripts, theme, messageMode.id,
            dialogFadeMs, dialogTypewriterMs, npcOutline,
            messageBg, messageBorder, centerSystemMessages
        )
        configPath.writeText(gson.toJson(data))
    }
}