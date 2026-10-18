package net.foxmediax.scriptfx.client.mui

import icyllis.modernui.R
import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.Drawable
import icyllis.modernui.graphics.drawable.ShapeDrawable
import icyllis.modernui.graphics.drawable.StateListDrawable

/** Общий визуальный язык: скруглённые поверхности и кнопки с состояниями (наведение, нажатие). */
object UiStyle {

    const val ACCENT = 0xFFC8B4FF.toInt()
    const val ACCENT_DARK = 0xFF7A5CE0.toInt()

    fun dp(ctx: Context, v: Int): Int =
        (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun dpf(ctx: Context, v: Float): Float =
        v * ctx.resources.displayMetrics.density

    /** Смешивает два цвета: t=0 -> a, t=1 -> b. Альфа берётся из a. */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val x = (a ushr shift) and 0xFF
            val y = (b ushr shift) and 0xFF
            return (x + (y - x) * t + 0.5f).toInt().coerceIn(0, 255)
        }
        return (a and 0xFF000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Скруглённый прямоугольник, обводка необязательна. */
    fun rounded(
        ctx: Context,
        fill: Int,
        radiusDp: Float,
        strokeColor: Int = 0,
        strokeDp: Int = 0
    ): ShapeDrawable = ShapeDrawable().apply {
        setShape(ShapeDrawable.RECTANGLE)
        setCornerRadius(dpf(ctx, radiusDp))
        setColor(fill)
        if (strokeDp > 0) setStroke(dp(ctx, strokeDp), strokeColor)
    }

    /**
     * Фон кнопки с тремя состояниями: обычный / наведён / нажат.
     * При наведении и нажатии рисуется тонкая обводка hoverStroke (0 = без обводки).
     */
    fun buttonBg(
        ctx: Context,
        normal: Int,
        hover: Int,
        pressed: Int,
        radiusDp: Float = 6f,
        hoverStroke: Int = ACCENT
    ): Drawable = StateListDrawable().apply {
        fun state(color: Int, stroke: Boolean) =
            if (stroke && hoverStroke != 0) rounded(ctx, color, radiusDp, hoverStroke, 1)
            else rounded(ctx, color, radiusDp)

        addState(intArrayOf(R.attr.state_pressed), state(pressed, true))
        addState(intArrayOf(R.attr.state_hovered), state(hover, true))
        addState(intArrayOf(), state(normal, false))
    }
}