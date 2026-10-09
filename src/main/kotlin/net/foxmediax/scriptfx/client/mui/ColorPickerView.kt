package net.foxmediax.scriptfx.client.mui

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.Canvas
import icyllis.modernui.graphics.LinearGradient
import icyllis.modernui.graphics.Paint
import icyllis.modernui.graphics.Shader
import icyllis.modernui.view.MotionEvent
import icyllis.modernui.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ColorHex {
    fun format(argb: Int): String = "#%08X".format(argb)

    /** "#AARRGGBB" или "#RRGGBB" (тогда альфа берётся из baseAlpha). null, если разобрать нельзя. */
    fun parse(text: String, baseAlpha: Int): Int? {
        val t = text.trim().removePrefix("#")
        if (t.length != 6 && t.length != 8) return null
        val v = t.toLongOrNull(16) ?: return null
        return if (t.length == 8) v.toInt() else (baseAlpha shl 24) or v.toInt()
    }
}

internal fun hsvToRgb(h: Float, s: Float, v: Float): Int {
    val hh = (((h % 360f) + 360f) % 360f) / 60f
    val c = v * s
    val x = c * (1f - abs(hh % 2f - 1f))
    val m = v - c
    val r: Float
    val g: Float
    val b: Float
    when (hh.toInt()) {
        0 -> { r = c; g = x; b = 0f }
        1 -> { r = x; g = c; b = 0f }
        2 -> { r = 0f; g = c; b = x }
        3 -> { r = 0f; g = x; b = c }
        4 -> { r = x; g = 0f; b = c }
        else -> { r = c; g = 0f; b = x }
    }
    fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}

/** Возвращает [оттенок 0..360, насыщенность 0..1, яркость 0..1]. */
internal fun rgbToHsv(argb: Int): FloatArray {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val d = mx - mn
    val h = when {
        d == 0f -> 0f
        mx == r -> 60f * (((g - b) / d) % 6f)
        mx == g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    return floatArrayOf(if (h < 0f) h + 360f else h, if (mx == 0f) 0f else d / mx, mx)
}

/** Палитра: квадрат насыщенность/яркость, полоса оттенка (справа), полоса прозрачности (снизу). */
class ColorPickerView(
    ctx: Context,
    private val sv: Int,    // сторона квадрата, px
    private val bar: Int,   // толщина полос, px
    private val gap: Int    // зазор, px
) : View(ctx) {

    private enum class Zone { NONE, SV, HUE, ALPHA }

    var onChange: ((Int) -> Unit)? = null

    val totalWidth: Int get() = sv + gap + bar
    val totalHeight: Int get() = sv + gap + bar

    private var hue = 0f
    private var sat = 1f
    private var value = 1f
    private var alpha = 255
    private var zone = Zone.NONE
    private val paint = Paint()

    fun currentColor(): Int = (alpha shl 24) or hsvToRgb(hue, sat, value)

    /** Установка извне (onChange не вызывается). */
    fun setColor(argb: Int) {
        alpha = argb ushr 24
        val hsv = rgbToHsv(argb)
        if (hsv[1] > 0f && hsv[2] > 0f) hue = hsv[0]   // у серых оттенок не определён: оставляем прежний
        sat = hsv[1]
        value = hsv[2]
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val s = sv.toFloat()
        val b = bar.toFloat()
        val g = gap.toFloat()
        val rgb = hsvToRgb(hue, sat, value)
        val hueRgb = hsvToRgb(hue, 1f, 1f)
        val opaque = 0xFF000000.toInt()

        // --- квадрат: слева белый -> цвет оттенка, затем сверху прозрачный -> чёрный
        paint.setShader(LinearGradient(0f, 0f, s, 0f, 0xFFFFFFFF.toInt(), hueRgb or opaque, Shader.TileMode.CLAMP, null))
        canvas.drawRect(0f, 0f, s, s, paint)
        paint.setShader(LinearGradient(0f, 0f, 0f, s, 0x00000000, opaque, Shader.TileMode.CLAMP, null))
        canvas.drawRect(0f, 0f, s, s, paint)

        // --- полоса оттенка
        val hx = s + g
        val colors = intArrayOf(
            0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(),
            0xFF00FFFF.toInt(), 0xFF0000FF.toInt(), 0xFFFF00FF.toInt(), 0xFFFF0000.toInt()
        )
        val pos = floatArrayOf(0f, 1f / 6f, 2f / 6f, 3f / 6f, 4f / 6f, 5f / 6f, 1f)
        paint.setShader(LinearGradient(0f, 0f, 0f, s, colors, pos, Shader.TileMode.CLAMP, null))
        canvas.drawRect(hx, 0f, hx + b, s, paint)

        // --- полоса прозрачности (шахматка + градиент)
        val ay = s + g
        paint.setShader(null)
        drawChecker(canvas, 0f, ay, s, ay + b)
        paint.setShader(LinearGradient(0f, 0f, s, 0f, rgb, rgb or opaque, Shader.TileMode.CLAMP, null))
        canvas.drawRect(0f, ay, s, ay + b, paint)
        paint.setShader(null)

        // --- маркеры
        marker(canvas, sat * s, (1f - value) * s, rgb or opaque)

        val hy = (hue / 360f) * s
        paint.setColor(0xFF000000.toInt())
        canvas.drawRect(hx - 2f, hy - 2f, hx + b + 2f, hy + 2f, paint)
        paint.setColor(0xFFFFFFFF.toInt())
        canvas.drawRect(hx - 1f, hy - 1f, hx + b + 1f, hy + 1f, paint)

        val ax = (alpha / 255f) * s
        paint.setColor(0xFF000000.toInt())
        canvas.drawRect(ax - 2f, ay - 2f, ax + 2f, ay + b + 2f, paint)
        paint.setColor(0xFFFFFFFF.toInt())
        canvas.drawRect(ax - 1f, ay - 1f, ax + 1f, ay + b + 1f, paint)
    }

    private fun marker(canvas: Canvas, cx: Float, cy: Float, fill: Int) {
        val r = 5f
        paint.setColor(0xFF000000.toInt())
        canvas.drawRect(cx - r - 1f, cy - r - 1f, cx + r + 1f, cy + r + 1f, paint)
        paint.setColor(0xFFFFFFFF.toInt())
        canvas.drawRect(cx - r, cy - r, cx + r, cy + r, paint)
        paint.setColor(fill)
        canvas.drawRect(cx - r + 2f, cy - r + 2f, cx + r - 2f, cy + r - 2f, paint)
    }

    private fun drawChecker(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float) {
        val cell = 6f
        var row = 0
        var y = y0
        while (y < y1) {
            var col = 0
            var x = x0
            while (x < x1) {
                paint.setColor(if ((row + col) % 2 == 0) 0xFFCCCCCC.toInt() else 0xFF888888.toInt())
                canvas.drawRect(x, y, min(x + cell, x1), min(y + cell, y1), paint)
                x += cell
                col++
            }
            y += cell
            row++
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val s = sv.toFloat()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                zone = when {
                    x in 0f..s && y in 0f..s -> Zone.SV
                    x >= s + gap && x <= s + gap + bar && y in 0f..s -> Zone.HUE
                    y >= s + gap && y <= s + gap + bar && x in 0f..s -> Zone.ALPHA
                    else -> Zone.NONE
                }
                if (zone == Zone.NONE) return false
                update(x, y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (zone != Zone.NONE) update(x, y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                zone = Zone.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun update(x: Float, y: Float) {
        val s = sv.toFloat()
        when (zone) {
            Zone.SV -> {
                sat = (x / s).coerceIn(0f, 1f)
                value = 1f - (y / s).coerceIn(0f, 1f)
            }
            Zone.HUE -> hue = ((y / s).coerceIn(0f, 1f) * 360f).coerceAtMost(359.99f)
            Zone.ALPHA -> alpha = ((x / s).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            Zone.NONE -> return
        }
        invalidate()
        onChange?.invoke(currentColor())
    }
}