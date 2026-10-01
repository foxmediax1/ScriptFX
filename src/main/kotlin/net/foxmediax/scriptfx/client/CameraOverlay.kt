package net.foxmediax.scriptfx.client

import net.foxmediax.scriptfx.network.CameraPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Чёрный экран (cameraINEffect / cameraOUTEffect) и крупный/мелкий текст по центру экрана. */
object CameraOverlay {

    private class Timed(val text: String, val start: Long, val durationMs: Long)

    private const val BIG_SCALE = 4f
    private const val SMALL_SCALE = 2f
    private const val TEXT_FADE_IN_MS = 300L
    private const val TEXT_FADE_OUT_MS = 500L
    private const val BLOCK_GAP = 8
    private const val MAX_WIDTH_RATIO = 0.8

    // Чёрный экран: плавный переход fadeFrom -> fadeTo.
    private var fadeFrom = 0f
    private var fadeTo = 0f
    private var fadeStart = 0L
    private var fadeDurationMs = 0L
    private var lastFadeInMs = 1000L

    // Тексты.
    private var big: Timed? = null
    private var bigSmall: String? = null        // мелкий текст, прикреплённый к большому (+smalltext)
    private var bigExpectsSmall = false
    private var solo: Timed? = null             // мелкий текст сам по себе

    /** Вызывается из сетевого обработчика (клиентский поток). */
    fun receive(payload: CameraPayload) {
        val now = System.currentTimeMillis()
        when (payload.action) {
            CameraPayload.FADE_IN -> {
                val ms = payload.ticks * 50L
                if (ms > 0) lastFadeInMs = ms
                startFade(1f, ms)
            }

            CameraPayload.FADE_OUT -> {
                if (fadeTo == 0f && fadeAlpha(now) <= 0f) return // затемнения не было
                val ms = if (payload.ticks < 0) lastFadeInMs else payload.ticks * 50L
                startFade(0f, ms)
            }

            CameraPayload.BIG -> {
                big = Timed(payload.text, now, payload.ticks * 50L)
                bigSmall = null
                bigExpectsSmall = payload.flag
            }

            CameraPayload.SMALL -> {
                val b = big
                if (b != null && bigExpectsSmall && now - b.start < b.durationMs) {
                    bigSmall = payload.text          // встаёт под большой текст, время общее
                    bigExpectsSmall = false
                } else {
                    solo = Timed(payload.text, now, payload.ticks * 50L)
                }
            }

            CameraPayload.RESET -> clear()
        }
    }

    fun clear() {
        fadeFrom = 0f
        fadeTo = 0f
        fadeDurationMs = 0L
        big = null
        bigSmall = null
        bigExpectsSmall = false
        solo = null
    }

    private fun fadeAlpha(now: Long): Float {
        if (fadeDurationMs <= 0L) return fadeTo
        val t = ((now - fadeStart) / fadeDurationMs.toFloat()).coerceIn(0f, 1f)
        return fadeFrom + (fadeTo - fadeFrom) * t
    }

    private fun startFade(to: Float, ms: Long) {
        val now = System.currentTimeMillis()
        fadeFrom = fadeAlpha(now) // продолжаем с текущей яркости, без скачка
        fadeTo = to
        fadeStart = now
        fadeDurationMs = ms
    }

    private fun alphaOf(t: Timed, now: Long): Float {
        val elapsed = now - t.start
        val fadeIn = minOf(TEXT_FADE_IN_MS, t.durationMs / 3).coerceAtLeast(1)
        val fadeOut = minOf(TEXT_FADE_OUT_MS, t.durationMs / 3).coerceAtLeast(1)
        return when {
            elapsed < fadeIn -> elapsed / fadeIn.toFloat()
            elapsed > t.durationMs - fadeOut -> (t.durationMs - elapsed) / fadeOut.toFloat()
            else -> 1f
        }.coerceIn(0f, 1f)
    }

    fun render(graphics: GuiGraphicsExtractor) {
        val now = System.currentTimeMillis()
        val mc = Minecraft.getInstance()
        val screenW = mc.window.guiScaledWidth
        val screenH = mc.window.guiScaledHeight

        // 1) Чёрный экран — под текстом.
        val fadeAlpha = (fadeAlpha(now) * 255).toInt().coerceIn(0, 255)
        if (fadeAlpha > 0) graphics.fill(0, 0, screenW, screenH, fadeAlpha shl 24)

        // 2) Убираем истёкшие тексты.
        big?.let {
            if (now - it.start >= it.durationMs) {
                big = null
                bigSmall = null
                bigExpectsSmall = false
            }
        }
        solo?.let { if (now - it.start >= it.durationMs) solo = null }

        val bigNow = big
        val soloNow = solo
        val attachedSmall = bigSmall

        val smallText: String?
        val smallAlpha: Float
        when {
            bigNow != null && attachedSmall != null -> {
                smallText = attachedSmall
                smallAlpha = alphaOf(bigNow, now)
            }
            soloNow != null -> {
                smallText = soloNow.text
                smallAlpha = alphaOf(soloNow, now)
            }
            else -> {
                smallText = null
                smallAlpha = 0f
            }
        }

        val font = mc.font
        val maxWidth = screenW * MAX_WIDTH_RATIO
        val bigLines = if (bigNow != null) wrap(font, bigNow.text, (maxWidth / BIG_SCALE).toInt()) else emptyList()
        val smallLines = if (smallText != null) wrap(font, smallText, (maxWidth / SMALL_SCALE).toInt()) else emptyList()
        if (bigLines.isEmpty() && smallLines.isEmpty()) return

        val bigStep = ((font.lineHeight + 2) * BIG_SCALE).toInt()
        val smallStep = ((font.lineHeight + 2) * SMALL_SCALE).toInt()
        val bigHeight = bigLines.size * bigStep
        val smallHeight = smallLines.size * smallStep
        val gap = if (bigHeight > 0 && smallHeight > 0) BLOCK_GAP else 0

        // Весь блок (большой + мелкий) центрируется по вертикали.
        var y = screenH / 2 - (bigHeight + gap + smallHeight) / 2

        val bigAlpha = if (bigNow != null) alphaOf(bigNow, now) else 0f
        for (line in bigLines) {
            drawScaled(graphics, font, line, screenW / 2, y, BIG_SCALE, bigAlpha)
            y += bigStep
        }
        y += gap
        for (line in smallLines) {
            drawScaled(graphics, font, line, screenW / 2, y, SMALL_SCALE, smallAlpha)
            y += smallStep
        }
    }

    private fun drawScaled(
        graphics: GuiGraphicsExtractor,
        font: Font,
        text: String,
        centerX: Int,
        y: Int,
        scale: Float,
        alpha: Float
    ) {
        val a = (alpha * 255).toInt()
        if (a < 8) return // при очень малой альфе ванильный рендер текста считает его непрозрачным

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(centerX.toFloat(), y.toFloat())
        pose.scale(scale, scale)
        graphics.text(font, text, -font.width(text) / 2, 0, (a shl 24) or 0xFFFFFF, true)
        pose.popMatrix()
    }

    private fun wrap(font: Font, text: String, maxWidth: Int): List<String> {
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in text.split(" ")) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (font.width(candidate) > maxWidth && current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }
}