package net.foxmediax.scriptfx.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.foxmediax.scriptfx.media.Mp4Decoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object ScriptFXConfig {
    const val DEFAULT_MESSAGE_BG = 0xD91C1436.toInt()
    const val DEFAULT_MESSAGE_BORDER = 0xFFC8B4FF.toInt()

    var showHints: Boolean = true
    var messageMode: MessageDisplayMode = MessageDisplayMode.CENTER

    var dialogFadeMs: Int = 400
    var dialogTypewriterMs: Int = 25
    var npcOutline: Boolean = false

    var messageBg: Int = DEFAULT_MESSAGE_BG
    var messageBorder: Int = DEFAULT_MESSAGE_BORDER
    var centerSystemMessages: Boolean = true

    /** Максимум одновременно запущенных скриптов. 0 или меньше — без лимита. */
    var maxScripts: Int = 0

    /**
     * Полный путь к ffmpeg (например C:/ffmpeg/bin/ffmpeg.exe).
     * null или "" — искать `ffmpeg` в PATH.
     */
    var ffmpegPath: String? = null

    /**
     * Уровень логов ScriptFX: debug | info | warn | error.
     * В production обычно "info" или "warn".
     */
    var logLevel: String = "info"

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath = FabricLoader.getInstance().configDir.resolve("scriptfx.json")

    private data class Data(
        var showHints: Boolean = true,
        var messageMode: String = MessageDisplayMode.CENTER.id,
        var dialogFadeMs: Int = 400,
        var dialogTypewriterMs: Int = 25,
        var npcOutline: Boolean = false,
        var messageBg: Int = DEFAULT_MESSAGE_BG,
        var messageBorder: Int = DEFAULT_MESSAGE_BORDER,
        var centerSystemMessages: Boolean = true,
        var maxScripts: Int = 0,
        var ffmpegPath: String? = null,
        var logLevel: String = "info"
    )

    fun load() {
        if (!configPath.exists()) {
            save()
            applyMedia()
            return
        }
        try {
            val data = gson.fromJson(configPath.readText(), Data::class.java)
            if (data == null) {
                save()
                applyMedia()
                return
            }
            showHints = data.showHints
            messageMode = MessageDisplayMode.fromId(data.messageMode)
            dialogFadeMs = data.dialogFadeMs.coerceIn(1, 5000)
            dialogTypewriterMs = data.dialogTypewriterMs.coerceIn(1, 500)
            npcOutline = data.npcOutline
            messageBg = data.messageBg
            messageBorder = data.messageBorder
            centerSystemMessages = data.centerSystemMessages
            maxScripts = data.maxScripts
            ffmpegPath = data.ffmpegPath?.takeIf { it.isNotBlank() }
            logLevel = data.logLevel.takeIf { it.isNotBlank() } ?: "info"
        } catch (e: Exception) {
            try {
                Files.copy(
                    configPath,
                    configPath.resolveSibling("scriptfx.json.broken"),
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Exception) {
            }
            save()
        }
        applyMedia()
    }

    fun save() {
        val data = Data(
            showHints = showHints,
            messageMode = messageMode.id,
            dialogFadeMs = dialogFadeMs,
            dialogTypewriterMs = dialogTypewriterMs,
            npcOutline = npcOutline,
            messageBg = messageBg,
            messageBorder = messageBorder,
            centerSystemMessages = centerSystemMessages,
            maxScripts = maxScripts,
            ffmpegPath = ffmpegPath,
            logLevel = logLevel
        )
        configPath.writeText(gson.toJson(data))
    }

    private fun applyMedia() {
        Mp4Decoder.ffmpegPath = ffmpegPath
    }
}