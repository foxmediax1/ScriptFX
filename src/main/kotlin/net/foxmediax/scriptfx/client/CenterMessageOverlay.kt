package net.foxmediax.scriptfx.client

import com.mojang.blaze3d.platform.NativeImage
import net.foxmediax.scriptfx.ScriptFX
import net.foxmediax.scriptfx.config.MessageDisplayMode
import net.foxmediax.scriptfx.config.ScriptFXConfig
import net.foxmediax.scriptfx.network.ScriptMessagePayload
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.resources.Identifier
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Сообщения скриптов (print / printNPC) и сообщения игроков из чата.
 * VANILLA: обычный чат. CENTER: по одному сообщению внизу экрана (над хотбаром):
 * появилось, повисело, исчезло, затем следующее из очереди.
 */
object CenterMessageOverlay {

    private class Avatar(val id: Identifier, val width: Int, val height: Int)

    private class Entry(
        val prefix: String,       // "[Имя] [ремарка]: ", "Ник: " или ""
        val text: String,
        val speakerRgb: Int,
        val textRgb: Int,
        val avatar: Avatar?,      // PNG-аватар из printNPC
        val faceUuid: UUID? = null   // игрок: голова его скина
    ) {
        val hasIcon: Boolean get() = avatar != null || faceUuid != null

        /** Сколько сообщение висит при пустой очереди. */
        val baseHoldMs: Long = (2500L + text.length * 55L).coerceIn(2500L, 9000L)

        // кэш разметки (зависит от доступной ширины)
        var layoutKey = -1
        var lines: List<String> = emptyList()
        var iconBlock = 0
        var prefixW = 0
        var contentH = 0
        var bubbleW = 0
        var bubbleH = 0
    }

    private enum class Phase { FADE_IN, HOLD, FADE_OUT }

    // ---- тайминги ----
    private const val FADE_IN_MS = 250L
    private const val FADE_OUT_MS = 350L
    private const val GAP_MS = 120L        // пауза между сообщениями
    private const val MIN_HOLD_MS = 1500L
    private const val MAX_QUEUE = 30

    // ---- геометрия (GUI-пиксели) ----
    /** От нижнего края экрана до плашки (над хотбаром). */
    private const val BOTTOM_MARGIN = 64
    private const val BUBBLE_PAD_X = 6
    private const val BUBBLE_PAD_Y = 4
    private const val ICON_GAP = 4
    private const val SLIDE_PX = 6         // «всплытие» при появлении

    private const val PLAYER_NAME_RGB = 0xFFFF55   // цвет ника игрока

    private val queue = ArrayDeque<Entry>()
    private var current: Entry? = null
    private var phase = Phase.FADE_IN
    private var phaseStart = 0L
    private var holdMs = 0L
    private var nextAllowedAt = 0L

    private val avatars = HashMap<String, Avatar>()   // sha1 -> текстура

    private fun rgbOf(name: String): Int =
        ChatFormatting.getByName(name)?.color ?: 0xFFFFFF

    private fun argb(alpha: Int, rgb: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

    /** Умножает альфу цвета ARGB на общую прозрачность (затухание/появление, 0..255). */
    private fun withFade(argb: Int, fade: Int): Int {
        val a = ((argb ushr 24) * fade.coerceIn(0, 255)) / 255
        return (a shl 24) or (argb and 0xFFFFFF)
    }

    /** PNG-байты -> зарегистрированная текстура (с кэшем). null, если картинка битая. */
    private fun avatarOf(png: ByteArray): Avatar? {
        if (png.isEmpty()) return null
        val key = MessageDigest.getInstance("SHA-1").digest(png)
            .joinToString("") { "%02x".format(it) }
        avatars[key]?.let { return it }
        return try {
            val image = NativeImage.read(png)
            val width = image.width
            val height = image.height
            val id = ScriptFX.id("avatar/$key")
            val texture = DynamicTexture({ "scriptfx avatar $key" }, image)
            texture.upload()
            Minecraft.getInstance().textureManager.register(id, texture)
            Avatar(id, width, height).also { avatars[key] = it }
        } catch (e: Exception) {
            null
        }
    }

    private fun prefixOf(payload: ScriptMessagePayload): String = when {
        payload.speaker.isBlank() -> ""
        payload.remark.isBlank() -> "[${payload.speaker}]: "
        else -> "[${payload.speaker}] [${payload.remark}]: "
    }

    private fun enqueue(entry: Entry) {
        if (queue.size >= MAX_QUEUE) queue.removeFirst()
        queue.addLast(entry)
    }

    /** Сообщение скрипта (print / printNPC). Вызывается из сетевого обработчика (клиентский поток). */
    fun receive(payload: ScriptMessagePayload) {
        when (ScriptFXConfig.messageMode) {
            MessageDisplayMode.VANILLA ->
                Minecraft.getInstance().player?.sendSystemMessage(payload.toComponent())

            MessageDisplayMode.CENTER -> {
                enqueue(
                    Entry(
                        prefixOf(payload), payload.text,
                        rgbOf(payload.speakerColor), rgbOf(payload.textColor),
                        avatarOf(payload.avatar)
                    )
                )
                ScriptChatHistory.add(payload.toComponent())   // запись в историю окна чата
            }
        }
    }

    /** Сообщение игрока из обычного чата (только в режиме CENTER). */
    fun receivePlayerChat(name: String, uuid: UUID?, text: String) {
        if (ScriptFXConfig.messageMode != MessageDisplayMode.CENTER) return
        if (text.isBlank()) return
        enqueue(Entry("$name: ", text, PLAYER_NAME_RGB, 0xFFFFFF, null, uuid))
    }

    /** Системное сообщение чата (ответ команды, вход игрока, смерть и т.п.): простая плашка без иконки. */
    fun receiveSystem(text: String) {
        if (ScriptFXConfig.messageMode != MessageDisplayMode.CENTER) return
        if (!ScriptFXConfig.centerSystemMessages) return
        val clean = text.replace('\n', ' ').trim()
        if (clean.isEmpty()) return
        enqueue(Entry("", clean, 0xFFFFFF, 0xFFFFFF, null))
    }

    /** Пример плашки: кнопка «Показать в игре» в настройках. */
    fun preview() {
        enqueue(Entry("[Пример]: ", "Так выглядит сообщение", 0xFF55FF, 0xFFFFFF, null))
    }

    fun clear() {
        queue.clear()
        current = null
        nextAllowedAt = 0L
        val textures = Minecraft.getInstance().textureManager
        avatars.values.forEach { textures.release(it.id) }
        avatars.clear()
    }

    // ------------------------------------------------------------------

    fun render(g: GuiGraphicsExtractor) {
        val now = System.currentTimeMillis()

        // берём следующее сообщение из очереди
        var e = current
        if (e == null) {
            if (now < nextAllowedAt) return
            e = queue.removeFirstOrNull() ?: return
            current = e
            phase = Phase.FADE_IN
            phaseStart = now
            // при длинной очереди показываем быстрее, чтобы сообщения не копились
            holdMs = (e.baseHoldMs / (1f + 0.25f * queue.size)).toLong().coerceAtLeast(MIN_HOLD_MS)
        }

        val elapsed = now - phaseStart
        var alphaF = 1f
        when (phase) {
            Phase.FADE_IN -> {
                alphaF = (elapsed.toFloat() / FADE_IN_MS).coerceIn(0f, 1f)
                if (elapsed >= FADE_IN_MS) { phase = Phase.HOLD; phaseStart = now }
            }
            Phase.HOLD -> {
                if (elapsed >= holdMs) { phase = Phase.FADE_OUT; phaseStart = now }
            }
            Phase.FADE_OUT -> {
                if (elapsed >= FADE_OUT_MS) {
                    current = null
                    nextAllowedAt = now + GAP_MS
                    return
                }
                alphaF = 1f - elapsed.toFloat() / FADE_OUT_MS
            }
        }

        val alpha = (alphaF * 255).toInt()
        if (alpha < 8) return   // при очень малой альфе текст рисуется непрозрачным

        val mc = Minecraft.getInstance()
        val font = mc.font
        val sw = mc.window.guiScaledWidth
        val sh = mc.window.guiScaledHeight

        val bubbleMaxW = (sw * 0.42).toInt().coerceIn(180, 340)
        val lineStep = font.lineHeight + 2
        val iconSize = lineStep + 3

        layoutEntry(e, font, bubbleMaxW, lineStep, iconSize)

        val slide = if (phase == Phase.FADE_IN) ((1f - alphaF) * SLIDE_PX).toInt() else 0
        val top = sh - BOTTOM_MARGIN - e.bubbleH + slide

        drawBubble(g, font, e, sw, top, alpha, iconSize, lineStep)
    }

    private fun drawBubble(
        g: GuiGraphicsExtractor, font: Font, e: Entry,
        sw: Int, top: Int, alpha: Int,
        iconSize: Int, lineStep: Int
    ) {
        val left = sw / 2 - e.bubbleW / 2
        val right = left + e.bubbleW
        val bottom = top + e.bubbleH

        val bg = withFade(ScriptFXConfig.messageBg, alpha)
        val border = withFade(ScriptFXConfig.messageBorder, alpha)

        g.fill(left, top, right, bottom, bg)
        g.fill(left, top, right, top + 1, border)
        g.fill(left, bottom - 1, right, bottom, border)
        g.fill(left, top, left + 1, bottom, border)
        g.fill(right - 1, top, right, bottom, border)

        val contentTop = top + BUBBLE_PAD_Y

        // иконка по центру содержимого: голова скина игрока или PNG-аватар
        val iconX = left + BUBBLE_PAD_X
        val iconY = contentTop + (e.contentH - iconSize) / 2
        val face = e.faceUuid
        if (face != null) {
            val skin = Minecraft.getInstance().connection?.getPlayerInfo(face)?.skin
                ?: DefaultPlayerSkin.get(face)
            PlayerFaceExtractor.extractRenderState(g, skin, iconX, iconY, iconSize, argb(alpha, 0xFFFFFF))
        } else {
            e.avatar?.let { av ->
                g.blit(
                    RenderPipelines.GUI_TEXTURED, av.id,
                    iconX, iconY,
                    0f, 0f,
                    iconSize, iconSize,
                    av.width, av.height,
                    av.width, av.height,
                    argb(alpha, 0xFFFFFF)
                )
            }
        }

        val speakerColor = argb(alpha, e.speakerRgb)
        val textColor = argb(alpha, e.textRgb)
        val textX = left + BUBBLE_PAD_X + e.iconBlock
        val textTop = contentTop + (e.contentH - e.lines.size * lineStep) / 2

        e.lines.forEachIndexed { i, line ->
            val ly = textTop + i * lineStep + 1
            if (i == 0) {
                if (e.prefixW > 0) g.text(font, e.prefix, textX, ly, speakerColor, true)
                g.text(font, line, textX + e.prefixW, ly, textColor, true)
            } else {
                g.text(font, line, textX, ly, textColor, true)
            }
        }
    }

    private fun layoutEntry(e: Entry, font: Font, maxW: Int, lineStep: Int, iconSize: Int) {
        if (e.layoutKey == maxW) return
        e.layoutKey = maxW

        e.iconBlock = if (e.hasIcon) iconSize + ICON_GAP else 0
        e.prefixW = font.width(e.prefix)
        val innerMax = maxW - BUBBLE_PAD_X * 2 - e.iconBlock

        e.lines = wrap(font, e.text, (innerMax - e.prefixW).coerceAtLeast(40), innerMax)
        val textBlockW = e.lines.withIndex().maxOf { (i, line) ->
            font.width(line) + if (i == 0) e.prefixW else 0
        }
        e.bubbleW = min(maxW, e.iconBlock + textBlockW + BUBBLE_PAD_X * 2)
        e.contentH = max(e.lines.size * lineStep, if (e.hasIcon) iconSize else 0)
        e.bubbleH = e.contentH + BUBBLE_PAD_Y * 2
    }

    /** firstWidth — ширина первой строки (после префикса), maxWidth — остальных. */
    private fun wrap(font: Font, text: String, firstWidth: Int, maxWidth: Int): List<String> {
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        var limit = firstWidth
        for (word in text.split(" ")) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (font.width(candidate) > limit && current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder(word)
                limit = maxWidth
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty() || lines.isEmpty()) lines.add(current.toString())
        return lines
    }
}