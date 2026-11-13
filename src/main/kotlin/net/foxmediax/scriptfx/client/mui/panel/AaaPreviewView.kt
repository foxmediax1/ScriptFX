package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.MotionEvent
import icyllis.modernui.view.View
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.particles.AaaPreviewHelper
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import kotlin.math.abs

class AaaPreviewView(ctx: Context) : FrameLayout(ctx) {

    private val titleView: TextView
    private val statusView: TextView
    private val hintView: TextView
    private val errorView: TextView

    private var dragging = false
    private var moved = false
    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f

    var currentId: Identifier? = null
        private set

    init {
        background = ColorDrawable(0xFF12081F.toInt())
        isClickable = true
        isFocusable = true

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                PanelUi.dp(ctx, 6),
                PanelUi.dp(ctx, 6),
                PanelUi.dp(ctx, 6),
                PanelUi.dp(ctx, 6)
            )
        }

        titleView = TextView(ctx).apply {
            text = "AAA Preview"
            textSize = 12f
            setTextColor(PanelUi.COL_TEXT)
            gravity = Gravity.CENTER
        }
        statusView = TextView(ctx).apply {
            text = ""
            textSize = 10f
            setTextColor(PanelUi.COL_ACCENT)
            gravity = Gravity.CENTER
        }
        val spacer = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }
        hintView = TextView(ctx).apply {
            text = "Тяни — orbit · Play в панели"
            textSize = 10f
            setTextColor(0xFF9CA3AF.toInt())
            gravity = Gravity.CENTER
        }
        errorView = TextView(ctx).apply {
            text = ""
            textSize = 12f
            setTextColor(0xFFFF5555.toInt())
            gravity = Gravity.CENTER
            visibility = View.GONE
        }

        col.addView(titleView, LinearLayout.LayoutParams(MATCH, WRAP))
        col.addView(statusView, LinearLayout.LayoutParams(MATCH, WRAP))
        col.addView(spacer)
        col.addView(hintView, LinearLayout.LayoutParams(MATCH, WRAP))
        addView(col, LayoutParams(MATCH, MATCH))
        addView(errorView, LayoutParams(MATCH, MATCH, Gravity.CENTER))
    }

    fun setEffect(id: Identifier?) {
        currentId = id
        titleView.text = id?.toString() ?: "AAA Preview"
        if (id == null) {
            AaaPreviewHelper.stop()
            statusView.text = ""
            refreshError()
            return
        }
        AaaPreviewHelper.play(id)
        statusView.text = "playing · scale=${"%.2f".format(AaaPreviewHelper.scale)}"
        post { updateBounds(); refreshError() }
        postDelayed({ refreshError() }, 120)
        postDelayed({ refreshError() }, 500)
    }

    fun play() {
        val id = currentId ?: return
        AaaPreviewHelper.play(id)
        statusView.text = "playing"
        refreshError()
    }

    fun stop() {
        AaaPreviewHelper.stop()
        statusView.text = "stopped"
    }

    fun setScale(v: Float) {
        AaaPreviewHelper.setScale(v)
        statusView.text = "scale=${"%.2f".format(AaaPreviewHelper.scale)}"
    }

    fun refreshError() {
        val err = AaaPreviewHelper.loadError
        if (err.isNullOrBlank()) {
            errorView.visibility = View.GONE
            errorView.text = ""
        } else {
            errorView.text = err
            errorView.visibility = View.VISIBLE
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { updateBounds() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { updateBounds() }
    }

    override fun onDetachedFromWindow() {
        AaaPreviewHelper.clearBounds()
        AaaPreviewHelper.stop()
        super.onDetachedFromWindow()
    }

    private fun updateBounds() {
        if (width <= 0 || height <= 0) {
            AaaPreviewHelper.clearBounds()
            return
        }
        val loc = IntArray(2)
        getLocationInWindow(loc)
        val mc = Minecraft.getInstance()
        val guiScale = mc.window.guiScale.toFloat().coerceAtLeast(1f)

        val x0 = (loc[0] / guiScale).toInt()
        val y0 = (loc[1] / guiScale).toInt()
        val x1 = ((loc[0] + width) / guiScale).toInt()
        val y1 = ((loc[1] + height) / guiScale).toInt()

        val padTop = (PanelUi.dp(context, 28) / guiScale).toInt().coerceAtLeast(12)
        val padBottom = (PanelUi.dp(context, 22) / guiScale).toInt().coerceAtLeast(10)

        val px0 = x0 + 4
        val py0 = y0 + padTop
        val px1 = x1 - 4
        val py1 = y1 - padBottom
        if (px1 - px0 < 24 || py1 - py0 < 24) {
            AaaPreviewHelper.clearBounds()
            return
        }
        AaaPreviewHelper.setBounds(px0, py0, px1, py1)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                moved = false
                lastX = event.x
                lastY = event.y
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (abs(event.x - downX) > 4f || abs(event.y - downY) > 4f) moved = true
                    if (moved) AaaPreviewHelper.drag(dx, dy)
                    lastX = event.x
                    lastY = event.y
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                moved = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    }
}