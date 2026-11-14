package net.foxmediax.scriptfx.client.mui.video

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.Canvas
import icyllis.modernui.graphics.Paint
import icyllis.modernui.view.View
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_ACCENT

class LoadingSpinnerView(context: Context) : View(context) {

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.STROKE
        strokeWidth = 6f
        color = COL_ACCENT
    }

    private var angle = 0f
    private var running = true

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            angle = (angle + 6f) % 360f
            invalidate()
            postDelayed(this, 16L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        post(ticker)
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - paint.strokeWidth
        // Modern UI: drawArc(cx, cy, radius, startAngle, sweepAngle, paint)
        canvas.drawArc(cx, cy, r, angle, 300f, paint)
    }
}