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
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.security.MessageDigest

/**
 * Показывает сообщения скриптов над хотбаром в стиле чата:
 * [иконка] [Имя] [ремарка]: текст — по одному, с плавным появлением и затуханием.
 * Если в настройках выбран "Стандартный" вид — отправляет сообщение в обычный чат.
 */
object CenterMessageOverlay {

    private class Avatar(val id: Identifier, val width: Int, val height: Int)

    private class Entry(
        val prefix: String,      // "[Имя] [ремарка]: " или пустая строка
        val text: String,
        val speakerRgb: Int,
        val textRgb: Int,
        val durationMs: Long,
        val avatar: Avatar?
    )

    private const val FADE_IN_MS = 250L
    private const val FADE_OUT_MS = 500L
    private const val MAX_QUEUE = 20

    /** Расстояние от нижнего края экрана до нижней границы плашки (в GUI-пикселях). */
    private const val BOTTOM_MARGIN = 60

    /** Отступ между иконкой и текстом. */
    private const val AVATAR_GAP = 3

    private val queue = ArrayDeque<Entry>()
    private val avatars = HashMap<String, Avatar>()   // sha1 -> текстура
    private var current: Entry? = null
    private var shownAt = 0L

    private fun rgbOf(name: String): Int =
        ChatFormatting.getByName(name)?.color ?: 0xFFFFFF

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

    /** Вызывается из сетевого обработчика (клиентский поток). */
    fun receive(payload: ScriptMessagePayload) {
        when (ScriptFXConfig.messageMode) {
            MessageDisplayMode.VANILLA ->
                Minecraft.getInstance().player?.sendSystemMessage(payload.toComponent())

            MessageDisplayMode.CENTER -> {
                val duration = (2000L + payload.text.length * 55L).coerceIn(2500L, 9000L)
                if (queue.size >= MAX_QUEUE) queue.removeFirst()
                queue.addLast(
                    Entry(
                        prefixOf(payload),
                        payload.text,
                        rgbOf(payload.speakerColor),
                        rgbOf(payload.textColor),
                        duration,
                        avatarOf(payload.avatar)
                    )
                )
            }
        }
    }

    fun clear() {
        queue.clear()
        current = null
        val textures = Minecraft.getInstance().textureManager
        avatars.values.forEach { textures.release(it.id) }
        avatars.clear()
    }

    fun render(graphics: GuiGraphicsExtractor) {
        val now = System.currentTimeMillis()

        var entry = current
        if (entry != null && now - shownAt >= entry.durationMs) {
            current = null
            entry = null
        }
        if (entry == null) {
            entry = queue.removeFirstOrNull() ?: return
            current = entry
            shownAt = now
        }

        val elapsed = now - shownAt
        val alphaF = when {
            elapsed < FADE_IN_MS -> elapsed / FADE_IN_MS.toFloat()
            elapsed > entry.durationMs - FADE_OUT_MS -> (entry.durationMs - elapsed) / FADE_OUT_MS.toFloat()
            else -> 1f
        }.coerceIn(0f, 1f)
        val alpha = (alphaF * 255).toInt()
        if (alpha < 8) return // при очень малой альфе ванильный рендер текста считает его непрозрачным

        val mc = Minecraft.getInstance()
        val font = mc.font
        val screenW = mc.window.guiScaledWidth
        val screenH = mc.window.guiScaledHeight

        // Высота строки; иконка — квадрат такой же высоты.
        val lineStep = font.lineHeight + 2
        val avatar = entry.avatar
        val iconBlock = if (avatar != null) lineStep + AVATAR_GAP else 0
        val prefixW = font.width(entry.prefix)

        // maxContent — ширина области текста вместе с префиксом "[Имя]: ".
        val maxContent = (screenW * 0.6).toInt().coerceIn(160, 420)
        val lines = wrap(font, entry.text, (maxContent - prefixW).coerceAtLeast(40), maxContent)
        val textBlockW = lines.withIndex().maxOf { (i, line) -> font.width(line) + if (i == 0) prefixW else 0 }

        val innerW = iconBlock + textBlockW
        val totalH = lines.size * lineStep
        val left = screenW / 2 - innerW / 2

        // Нижняя граница плашки закреплена над хотбаром, блок растёт вверх.
        val boxBottom = screenH - BOTTOM_MARGIN
        val top = boxBottom - 3 - totalH

        // Полупрозрачная чёрная плашка.
        graphics.fill(left - 4, top - 3, left + innerW + 4, boxBottom, (alpha / 2) shl 24)

        // Иконка — только у первой строки.
        if (avatar != null) {
            graphics.blit(
                RenderPipelines.GUI_TEXTURED, avatar.id,
                left, top,
                0f, 0f,
                lineStep, lineStep,                 // размер на экране
                avatar.width, avatar.height,        // какую область берём из текстуры
                avatar.width, avatar.height,        // полный размер текстуры
                (alpha shl 24) or 0xFFFFFF          // белый тон + общая прозрачность
            )
        }

        val speakerColor = (alpha shl 24) or (entry.speakerRgb and 0xFFFFFF)
        val textColor = (alpha shl 24) or (entry.textRgb and 0xFFFFFF)
        val textX = left + iconBlock

        lines.forEachIndexed { i, line ->
            val y = top + i * lineStep + 1
            if (i == 0) {
                if (prefixW > 0) graphics.text(font, entry.prefix, textX, y, speakerColor, true)
                graphics.text(font, line, textX + prefixW, y, textColor, true)
            } else {
                graphics.text(font, line, textX, y, textColor, true)
            }
        }
    }

    /** firstWidth — доступная ширина первой строки (после префикса), maxWidth — остальных. */
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