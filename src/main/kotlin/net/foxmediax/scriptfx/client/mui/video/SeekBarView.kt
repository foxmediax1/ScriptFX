package net.foxmediax.scriptfx.client.mui.video

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.Canvas
import icyllis.modernui.graphics.Paint
import icyllis.modernui.view.MotionEvent
import icyllis.modernui.view.View
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_ACCENT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_DIVIDER

/**
 * Слайдер: во время drag — onScrub (только UI/кадр),
 * при отпускании — onSeekCommit (один раз, с аудио).
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

    /** Во время перетаскивания / клика (без аудио-seek). */
    var onScrub: ((Float) -> Unit)? = null

    /** Один раз при отпускании — финальная позиция (с аудио). */
    var onSeekCommit: ((Float) -> Unit)? = null

    /** Совместимость: если onSeekCommit не задан, вызывается на каждый move. */
    var onSeek: ((Float) -> Unit)? = null

    private var dragging = false

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
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                applyFractionFromEvent(event, commit = false)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    applyFractionFromEvent(event, commit = false)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    applyFractionFromEvent(event, commit = true)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun applyFractionFromEvent(event: MotionEvent, commit: Boolean) {
        val f = (event.x / width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
        fraction = f
        onScrub?.invoke(f)
        if (commit) {
            onSeekCommit?.invoke(f) ?: onSeek?.invoke(f)
        } else if (onSeekCommit == null && onScrub == null) {
            // старое поведение, если колбэки не разделены
            onSeek?.invoke(f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cy = height / 2f
        val left = thumbR
        val right = width - thumbR
        canvas.drawRoundRect(left, cy - trackH / 2, right, cy + trackH / 2, trackH / 2, trackPaint)
        val x = left + (right - left) * fraction
        canvas.drawRoundRect(left, cy - trackH / 2, x, cy + trackH / 2, trackH / 2, progressPaint)
        canvas.drawCircle(x, cy, thumbR, thumbPaint)
    }
}