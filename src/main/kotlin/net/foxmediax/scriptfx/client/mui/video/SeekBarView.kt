package net.foxmediax.scriptfx.client.mui.video

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.Canvas
import icyllis.modernui.graphics.Paint
import icyllis.modernui.view.MotionEvent
import icyllis.modernui.view.View
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_ACCENT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_DIVIDER

/**
 * Простой слайдер без анимации: рисует дорожку и ползунок.
 * Клик/перетаскивание меняет fraction, вызывает onSeek.
 */
class SeekBarView(context: Context) : View(context) {

    var fraction: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var initialFraction: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            fraction = field
        }

    var onSeek: ((Float) -> Unit)? = null

    private val trackPaint = Paint().apply {
        isAntiAlias = true
        color = COL_DIVIDER
    }
    private val progressPaint = Paint().apply {
        isAntiAlias = true
        color = COL_ACCENT
    }
    private val thumbPaint = Paint().apply {
        isAntiAlias = true
        color = COL_ACCENT
    }

    private val thumbR = 7f
    private val trackH = 4f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val f = (event.x / width.toFloat()).coerceIn(0f, 1f)
                fraction = f
                onSeek?.invoke(f)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cy = height / 2f
        val left = thumbR
        val right = width - thumbR
        // Дорожка.
        canvas.drawRoundRect(left, cy - trackH / 2, right, cy + trackH / 2, trackH / 2, trackPaint)
        // Прогресс.
        val x = left + (right - left) * fraction
        canvas.drawRoundRect(left, cy - trackH / 2, x, cy + trackH / 2, trackH / 2, progressPaint)
        // Ползунок.
        canvas.drawCircle(x, cy, thumbR, thumbPaint)
    }
}