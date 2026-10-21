package net.foxmediax.scriptfx.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.foxmediax.scriptfx.media.Mp4Decoder
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object ScriptFXConfig {
    const val DEFAULT_MESSAGE_BG = 0xD91C1436.toInt()
    const val DEFAULT_MESSAGE_BORDER = 0xFFC8B4FF.toInt()

    var showHints: Boolean = true
    var theme: String = "dark"
    var messageMode: MessageDisplayMode = MessageDisplayMode.CENTER

    var dialogFadeMs: Int = 400
    var dialogTypewriterMs: Int = 25
    var npcOutline: Boolean = true

    var messageBg: Int = DEFAULT_MESSAGE_BG
    var messageBorder: Int = DEFAULT_MESSAGE_BORDER
    var centerSystemMessages: Boolean = true

    var maxScripts: Int = 0
    var debugMode: Boolean = false
    var ffmpegPath: String? = null

    /**
     * Громкость видео ScriptFX 0.0 … 1.0.
     * Ползунок «ScriptFX Video» в Настройки → Музыка и звуки.
     */
    var videoVolume: Float = 1f

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath = FabricLoader.getInstance().configDir.resolve("scriptfx.json")

    private data class Data(
        var showHints: Boolean = true,
        var theme: String = "dark",
        var messageMode: String = MessageDisplayMode.CENTER.id,
        var dialogFadeMs: Int = 400,
        var dialogTypewriterMs: Int = 25,
        var npcOutline: Boolean = true,
        var messageBg: Int = DEFAULT_MESSAGE_BG,
        var messageBorder: Int = DEFAULT_MESSAGE_BORDER,
        var centerSystemMessages: Boolean = true,
        var maxScripts: Int = 0,
        var debugMode: Boolean = false,
        var ffmpegPath: String? = null,
        var videoVolume: Float = 1f
    )

    fun load() {
        if (!configPath.exists()) {
            save()
            applyAll()
            return
        }
        try {
            val data = gson.fromJson(configPath.readText(), Data::class.java)
            if (data == null) {
                save()
                applyAll()
                return
            }
            showHints = data.showHints
            theme = data.theme
            messageMode = MessageDisplayMode.fromId(data.messageMode)
            dialogFadeMs = data.dialogFadeMs.coerceIn(1, 5000)
            dialogTypewriterMs = data.dialogTypewriterMs.coerceIn(1, 500)
            npcOutline = data.npcOutline
            messageBg = data.messageBg
            messageBorder = data.messageBorder
            centerSystemMessages = data.centerSystemMessages
            maxScripts = data.maxScripts
            debugMode = data.debugMode
            ffmpegPath = data.ffmpegPath?.takeIf { it.isNotBlank() }
            videoVolume = data.videoVolume.coerceIn(0f, 1f)
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
        applyAll()
    }

    fun save() {
        val data = Data(
            showHints = showHints,
            theme = theme,
            messageMode = messageMode.id,
            dialogFadeMs = dialogFadeMs,
            dialogTypewriterMs = dialogTypewriterMs,
            npcOutline = npcOutline,
            messageBg = messageBg,
            messageBorder = messageBorder,
            centerSystemMessages = centerSystemMessages,
            maxScripts = maxScripts,
            debugMode = debugMode,
            ffmpegPath = ffmpegPath,
            videoVolume = videoVolume.coerceIn(0f, 1f)
        )
        configPath.writeText(gson.toJson(data))
    }

    private fun applyAll() {
        Mp4Decoder.ffmpegPath = ffmpegPath
        ScriptFXLog.debugEnabled = debugMode
    }

    /** Итоговая громкость ролика: slider × Master (0..1). */
    fun effectiveVideoVolume(): Float {
        val master = try {
            val opts = net.minecraft.client.Minecraft.getInstance().options
            val m = opts.javaClass.methods.firstOrNull {
                it.name == "getSoundSourceOption" && it.parameterCount == 1
            }
            if (m != null) {
                m.isAccessible = true
                val opt = m.invoke(opts, net.minecraft.sounds.SoundSource.MASTER)
                val get = opt?.javaClass?.methods?.firstOrNull { it.name == "get" && it.parameterCount == 0 }
                ((get?.invoke(opt) as? Number)?.toDouble() ?: 1.0).toFloat()
            } else 1f
        } catch (_: Throwable) {
            1f
        }
        return (videoVolume.coerceIn(0f, 1f) * master.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    }
}