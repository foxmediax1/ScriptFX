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
    var videoVolume: Float = 1f

    // Chat
    var chatScrim: Int = 0x99080810.toInt()
    var chatPanel: Int = 0xF01A1A1E.toInt()
    var chatMsgBg: Int = 0xFF101014.toInt()
    var chatInputBg: Int = 0xFF0C0C12.toInt()
    var chatInputBorder: Int = 0xFF2A2140.toInt()
    var chatText: Int = 0xFFEDEDED.toInt()
    var chatHint: Int = 0xFF888899.toInt()
    var chatBtn: Int = 0xFF2A2140.toInt()
    var chatSuggestBg: Int = 0xE6121220.toInt()
    var chatSuggestSel: Int = 0x55FFFFFF
    var chatWidthDp: Int = 460
    var chatHeightDp: Int = 480

    // Dialog
    var dialogCard: Int = 0xF2141420.toInt()
    var dialogCardStroke: Int = 0x66C8B4FF
    var dialogBody: Int = 0xFFF2F2F7.toInt()
    var dialogBtn: Int = 0xFF1B1B2C.toInt()
    var dialogBtnHover: Int = 0xFF2A2446.toInt()
    var dialogBtnPressed: Int = 0xFF3C3162.toInt()
    var dialogHint: Int = 0xFF8888A0.toInt()
    var dialogChipText: Int = 0xFF14101F.toInt()
    var dialogCardWidthDp: Int = 270
    var dialogButtonsWidthDp: Int = 190
    var dialogBodyHeightDp: Int = 130

    // Quest journal
    var questCover: Int = 0xFF2B1F3A.toInt()
    var questSpine: Int = 0xFF171021.toInt()
    var questPaperL: Int = 0xFFEEE5CF.toInt()
    var questPaperR: Int = 0xFFF6EEDC.toInt()
    var questInk: Int = 0xFF2A2118.toInt()
    var questInkMuted: Int = 0xFF6D5D45.toInt()
    var questLine: Int = 0x332A2118
    var questTabIdle: Int = 0xFFDCCFAE.toInt()
    var questRowSel: Int = 0xFFE2D3AE.toInt()
    var questRowHover: Int = 0xFFE9DDBF.toInt()
    var questBarBg: Int = 0x26000000
    var questActive: Int = 0xFFB07A1E.toInt()
    var questDone: Int = 0xFF2E7D4F.toInt()
    var questFailed: Int = 0xFFA23B3B.toInt()

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
        var videoVolume: Float = 1f,
        var chatScrim: Int = 0x99080810.toInt(),
        var chatPanel: Int = 0xF01A1A1E.toInt(),
        var chatMsgBg: Int = 0xFF101014.toInt(),
        var chatInputBg: Int = 0xFF0C0C12.toInt(),
        var chatInputBorder: Int = 0xFF2A2140.toInt(),
        var chatText: Int = 0xFFEDEDED.toInt(),
        var chatHint: Int = 0xFF888899.toInt(),
        var chatBtn: Int = 0xFF2A2140.toInt(),
        var chatSuggestBg: Int = 0xE6121220.toInt(),
        var chatSuggestSel: Int = 0x55FFFFFF,
        var chatWidthDp: Int = 460,
        var chatHeightDp: Int = 480,
        var dialogCard: Int = 0xF2141420.toInt(),
        var dialogCardStroke: Int = 0x66C8B4FF,
        var dialogBody: Int = 0xFFF2F2F7.toInt(),
        var dialogBtn: Int = 0xFF1B1B2C.toInt(),
        var dialogBtnHover: Int = 0xFF2A2446.toInt(),
        var dialogBtnPressed: Int = 0xFF3C3162.toInt(),
        var dialogHint: Int = 0xFF8888A0.toInt(),
        var dialogChipText: Int = 0xFF14101F.toInt(),
        var dialogCardWidthDp: Int = 270,
        var dialogButtonsWidthDp: Int = 190,
        var dialogBodyHeightDp: Int = 130,
        var questCover: Int = 0xFF2B1F3A.toInt(),
        var questSpine: Int = 0xFF171021.toInt(),
        var questPaperL: Int = 0xFFEEE5CF.toInt(),
        var questPaperR: Int = 0xFFF6EEDC.toInt(),
        var questInk: Int = 0xFF2A2118.toInt(),
        var questInkMuted: Int = 0xFF6D5D45.toInt(),
        var questLine: Int = 0x332A2118,
        var questTabIdle: Int = 0xFFDCCFAE.toInt(),
        var questRowSel: Int = 0xFFE2D3AE.toInt(),
        var questRowHover: Int = 0xFFE9DDBF.toInt(),
        var questBarBg: Int = 0x26000000,
        var questActive: Int = 0xFFB07A1E.toInt(),
        var questDone: Int = 0xFF2E7D4F.toInt(),
        var questFailed: Int = 0xFFA23B3B.toInt()
    )

    fun load() {
        if (!configPath.exists()) {
            save(); applyAll(); return
        }
        try {
            val data = gson.fromJson(configPath.readText(), Data::class.java) ?: run {
                save(); applyAll(); return
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

            chatScrim = data.chatScrim
            chatPanel = data.chatPanel
            chatMsgBg = data.chatMsgBg
            chatInputBg = data.chatInputBg
            chatInputBorder = data.chatInputBorder
            chatText = data.chatText
            chatHint = data.chatHint
            chatBtn = data.chatBtn
            chatSuggestBg = data.chatSuggestBg
            chatSuggestSel = data.chatSuggestSel
            chatWidthDp = data.chatWidthDp.coerceIn(280, 900)
            chatHeightDp = data.chatHeightDp.coerceIn(240, 900)

            dialogCard = data.dialogCard
            dialogCardStroke = data.dialogCardStroke
            dialogBody = data.dialogBody
            dialogBtn = data.dialogBtn
            dialogBtnHover = data.dialogBtnHover
            dialogBtnPressed = data.dialogBtnPressed
            dialogHint = data.dialogHint
            dialogChipText = data.dialogChipText
            dialogCardWidthDp = data.dialogCardWidthDp.coerceIn(180, 480)
            dialogButtonsWidthDp = data.dialogButtonsWidthDp.coerceIn(140, 360)
            dialogBodyHeightDp = data.dialogBodyHeightDp.coerceIn(60, 320)

            questCover = data.questCover
            questSpine = data.questSpine
            questPaperL = data.questPaperL
            questPaperR = data.questPaperR
            questInk = data.questInk
            questInkMuted = data.questInkMuted
            questLine = data.questLine
            questTabIdle = data.questTabIdle
            questRowSel = data.questRowSel
            questRowHover = data.questRowHover
            questBarBg = data.questBarBg
            questActive = data.questActive
            questDone = data.questDone
            questFailed = data.questFailed
        } catch (_: Exception) {
            try {
                Files.copy(
                    configPath,
                    configPath.resolveSibling("scriptfx.json.broken"),
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Exception) {}
            save()
        }
        applyAll()
    }

    fun save() {
        configPath.writeText(
            gson.toJson(
                Data(
                    showHints, theme, messageMode.id,
                    dialogFadeMs, dialogTypewriterMs, npcOutline,
                    messageBg, messageBorder, centerSystemMessages,
                    maxScripts, debugMode, ffmpegPath, videoVolume.coerceIn(0f, 1f),
                    chatScrim, chatPanel, chatMsgBg, chatInputBg, chatInputBorder,
                    chatText, chatHint, chatBtn, chatSuggestBg, chatSuggestSel,
                    chatWidthDp, chatHeightDp,
                    dialogCard, dialogCardStroke, dialogBody, dialogBtn, dialogBtnHover,
                    dialogBtnPressed, dialogHint, dialogChipText,
                    dialogCardWidthDp, dialogButtonsWidthDp, dialogBodyHeightDp,
                    questCover, questSpine, questPaperL, questPaperR, questInk, questInkMuted,
                    questLine, questTabIdle, questRowSel, questRowHover, questBarBg,
                    questActive, questDone, questFailed
                )
            )
        )
    }

    fun resetUiTheme() {
        messageBg = DEFAULT_MESSAGE_BG
        messageBorder = DEFAULT_MESSAGE_BORDER
        chatScrim = 0x99080810.toInt()
        chatPanel = 0xF01A1A1E.toInt()
        chatMsgBg = 0xFF101014.toInt()
        chatInputBg = 0xFF0C0C12.toInt()
        chatInputBorder = 0xFF2A2140.toInt()
        chatText = 0xFFEDEDED.toInt()
        chatHint = 0xFF888899.toInt()
        chatBtn = 0xFF2A2140.toInt()
        chatSuggestBg = 0xE6121220.toInt()
        chatSuggestSel = 0x55FFFFFF
        chatWidthDp = 460
        chatHeightDp = 480
        dialogCard = 0xF2141420.toInt()
        dialogCardStroke = 0x66C8B4FF
        dialogBody = 0xFFF2F2F7.toInt()
        dialogBtn = 0xFF1B1B2C.toInt()
        dialogBtnHover = 0xFF2A2446.toInt()
        dialogBtnPressed = 0xFF3C3162.toInt()
        dialogHint = 0xFF8888A0.toInt()
        dialogChipText = 0xFF14101F.toInt()
        dialogCardWidthDp = 270
        dialogButtonsWidthDp = 190
        dialogBodyHeightDp = 130
        dialogFadeMs = 400
        dialogTypewriterMs = 25
        questCover = 0xFF2B1F3A.toInt()
        questSpine = 0xFF171021.toInt()
        questPaperL = 0xFFEEE5CF.toInt()
        questPaperR = 0xFFF6EEDC.toInt()
        questInk = 0xFF2A2118.toInt()
        questInkMuted = 0xFF6D5D45.toInt()
        questLine = 0x332A2118
        questTabIdle = 0xFFDCCFAE.toInt()
        questRowSel = 0xFFE2D3AE.toInt()
        questRowHover = 0xFFE9DDBF.toInt()
        questBarBg = 0x26000000
        questActive = 0xFFB07A1E.toInt()
        questDone = 0xFF2E7D4F.toInt()
        questFailed = 0xFFA23B3B.toInt()
        save()
    }

    private fun applyAll() {
        Mp4Decoder.ffmpegPath = ffmpegPath
        ScriptFXLog.debugEnabled = debugMode
    }

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