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
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Сообщения скриптов (print / printNPC) и сообщения игроков из чата.
 * VANILLA: обычный чат. CENTER: стопка плашек над хотбаром, по центру по горизонтали;
 * новое сообщение внизу и подсвечено, старые плавно поднимаются вверх и тускнеют.
 */
object CenterMessageOverlay {

    private class Avatar(val id: Identifier, val width: Int, val height: Int)

    private class Entry(
        val prefix: String,       // "[Имя] [ремарка]: ", "Ник: " или ""
        val text: String,
        val speakerRgb: Int,
        val textRgb: Int,
        val avatar: Avatar?,      // PNG-аватар из printNPC
        val bornAt: Long,
        val faceUuid: UUID? = null   // игрок: рисуем голову его скина
    ) {
        val hasIcon: Boolean get() = avatar != null || faceUuid != null

        // кэш разметки (зависит от доступной ширины)
        var layoutKey = -1
        var lines: List<String> = emptyList()
        var iconBlock = 0
        var prefixW = 0
        var contentH = 0
        var bubbleW = 0
        var bubbleH = 0

        // текущая (анимированная) вертикальная позиция плашки
        var curY = Float.NaN
    }

    // ---- тайминги ----
    private const val FADE_IN_MS = 250L
    private const val FADE_OUT_MS = 500L
    private const val NEW_BUBBLE_FADE_MS = 300L
    private const val MAX_ENTRIES = 12

    // ---- геометрия (GUI-пиксели) ----
    /** От нижнего края экрана до нижней плашки (над хотбаром). */
    private const val BOTTOM_MARGIN = 64
    private const val BUBBLE_PAD_X = 6
    private const val BUBBLE_PAD_Y = 4
    private const val BUBBLE_GAP = 5
    private const val ICON_GAP = 4

    // ---- палитра ----
    private const val PLAYER_NAME_RGB = 0xFFFF55   // цвет ника игрока

    private val entries = ArrayList<Entry>()
    private val avatars = HashMap<String, Avatar>()   // sha1 -> текстура

    private var lastActivity = 0L
    private var holdMs = 0L
    private var globalAlpha = 0f
    private var lastFrame = 0L

    private fun rgbOf(name: String): Int =
        ChatFormatting.getByName(name)?.color ?: 0xFFFFFF

    /** Умножает альфу цвета ARGB на общую прозрачность (затухание/появление, 0..255). */
    private fun withFade(argb: Int, fade: Int): Int {
        val a = ((argb ushr 24) * fade.coerceIn(0, 255)) / 255
        return (a shl 24) or (argb and 0xFFFFFF)
    }

    private fun argb(alpha: Int, rgb: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

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

    /** Сообщение скрипта (print / printNPC). Вызывается из сетевого обработчика (клиентский поток). */
    fun receive(payload: ScriptMessagePayload) {
        when (ScriptFXConfig.messageMode) {
            MessageDisplayMode.VANILLA ->
                Minecraft.getInstance().player?.sendSystemMessage(payload.toComponent())

            MessageDisplayMode.CENTER -> {
                val now = System.currentTimeMillis()
                if (entries.size >= MAX_ENTRIES) entries.removeAt(0)
                entries.add(
                    Entry(
                        prefixOf(payload), payload.text,
                        rgbOf(payload.speakerColor), rgbOf(payload.textColor),
                        avatarOf(payload.avatar), now
                    )
                )
                lastActivity = now
                holdMs = (2500L + payload.text.length * 55L).coerceIn(3500L, 10000L)
            }
        }
    }

    /** Сообщение игрока из обычного чата (только в режиме CENTER). */
    fun receivePlayerChat(name: String, uuid: UUID?, text: String) {
        if (ScriptFXConfig.messageMode != MessageDisplayMode.CENTER) return
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        if (entries.size >= MAX_ENTRIES) entries.removeAt(0)
        entries.add(Entry("$name: ", text, PLAYER_NAME_RGB, 0xFFFFFF, null, now, uuid))
        lastActivity = now
        holdMs = (2500L + text.length * 55L).coerceIn(3500L, 10000L)
    }

    fun clear() {
        entries.clear()
        resetAnim()
        val textures = Minecraft.getInstance().textureManager
        avatars.values.forEach { textures.release(it.id) }
        avatars.clear()
    }

    private fun resetAnim() {
        globalAlpha = 0f
        lastFrame = 0L
    }

    // ------------------------------------------------------------------

    fun render(g: GuiGraphicsExtractor) {
        if (entries.isEmpty()) { resetAnim(); return }

        val now = System.currentTimeMillis()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceIn(0f, 0.1f)
        lastFrame = now

        val holding = now - lastActivity < holdMs
        globalAlpha = if (holding) min(1f, globalAlpha + dt * 1000f / FADE_IN_MS)
        else max(0f, globalAlpha - dt * 1000f / FADE_OUT_MS)
        if (!holding && globalAlpha <= 0f) { entries.clear(); resetAnim(); return }

        val ga = globalAlpha
        if (ga * 255 < 8) return   // при очень малой альфе текст рисуется непрозрачным

        val mc = Minecraft.getInstance()
        val font = mc.font
        val sw = mc.window.guiScaledWidth
        val sh = mc.window.guiScaledHeight

        val bubbleMaxW = (sw * 0.42).toInt().coerceIn(180, 340)
        val lineStep = font.lineHeight + 2
        val iconSize = lineStep + 3
        val maxStackH = (sh * 0.5f).toInt()

        // что помещается по высоте: от новых к старым
        var used = 0
        var firstVisible = entries.size
        for (i in entries.indices.reversed()) {
            val e = entries[i]
            layoutEntry(e, font, bubbleMaxW, lineStep, iconSize)
            val add = e.bubbleH + (if (used > 0) BUBBLE_GAP else 0)
            if (used + add > maxStackH && used > 0) break
            used += add
            firstVisible = i
        }
        if (firstVisible > 0) entries.subList(0, firstVisible).clear()

        // плашки: нижняя стоит над хотбаром, остальные выше; позиции плавно доезжают до цели
        val slide = 1f - exp(-dt * 14f)
        var y = sh - BOTTOM_MARGIN
        var rank = 0   // 0 = самое новое
        for (i in entries.indices.reversed()) {
            val e = entries[i]
            y -= e.bubbleH
            val targetY = y.toFloat()
            e.curY = if (e.curY.isNaN()) targetY else e.curY + (targetY - e.curY) * slide

            val born = ((now - e.bornAt).toFloat() / NEW_BUBBLE_FADE_MS).coerceIn(0f, 1f)
            val fadeByAge = max(0.25f, 1f - rank * 0.15f)
            val alpha = (ga * born * fadeByAge * 255).toInt()
            if (alpha >= 8) {
                drawBubble(g, font, e, sw, e.curY.toInt(), alpha, rank == 0, iconSize, lineStep)
            }

            y -= BUBBLE_GAP
            rank++
        }
    }

    private fun drawBubble(
        g: GuiGraphicsExtractor, font: Font, e: Entry,
        sw: Int, top: Int, alpha: Int, active: Boolean,
        iconSize: Int, lineStep: Int
    ) {
        val left = sw / 2 - e.bubbleW / 2
        val right = left + e.bubbleW
        val bottom = top + e.bubbleH

        val bg = withFade(if (active) ScriptFXConfig.bubbleBgNew else ScriptFXConfig.bubbleBg, alpha)
        val border = withFade(if (active) ScriptFXConfig.bubbleBorderNew else ScriptFXConfig.bubbleBorder, alpha)

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