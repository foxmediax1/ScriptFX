package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.MotionEvent
import icyllis.modernui.view.View
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.NpcPreviewHelper
import net.foxmediax.scriptfx.npc.NpcDefinition

/**
 * Правая колонка: зона под 3D-модель.
 * Модель рисуется через ScreenEvents.afterExtract + InventoryScreen.
 * View только отдаёт bounds и drag.
 */
class NpcPreviewView(ctx: Context) : FrameLayout(ctx) {

    private val titleView: TextView
    private val hintView: TextView

    private var dragging = false
    private var lastX = 0f
    private var lastY = 0f

    var definition: NpcDefinition? = null
        set(value) {
            field = value
            NpcPreviewHelper.setDefinition(value)
            titleView.text = when {
                value == null -> "Превью"
                value.displayName.isNotBlank() -> value.displayName
                else -> value.id
            }
            post { updateBounds() }
        }

    init {
        // почти прозрачный фон — модель рисуется поверх в screen-pass
        background = ColorDrawable(0xFF0A0A0C.toInt())

        val column = LinearLayout(ctx).apply {
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
            text = "Превью"
            textSize = 12f
            setTextColor(PanelUi.COL_TEXT)
            gravity = Gravity.CENTER
        }
        hintView = TextView(ctx).apply {
            text = "Тяни — поворот"
            textSize = 10f
            setTextColor(0xFF555555.toInt())
            gravity = Gravity.CENTER
        }

        column.addView(titleView, LinearLayout.LayoutParams(PanelUi.MATCH, PanelUi.WRAP))
        column.addView(
            View(ctx),
            LinearLayout.LayoutParams(PanelUi.MATCH, 0, 1f)
        )
        column.addView(hintView, LinearLayout.LayoutParams(PanelUi.MATCH, PanelUi.WRAP))

        addView(column, LayoutParams(PanelUi.MATCH, PanelUi.MATCH))

        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateBounds()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { updateBounds() }
    }

    override fun onDetachedFromWindow() {
        NpcPreviewHelper.clearBounds()
        super.onDetachedFromWindow()
    }

    private fun updateBounds() {
        if (width <= 0 || height <= 0) {
            NpcPreviewHelper.clearBounds()
            return
        }

        val loc = IntArray(2)
        try {
            getLocationInWindow(loc)
        } catch (_: Throwable) {
            // fallback: накопить offset по иерархии
            var x = left
            var y = top
            var p = parent
            while (p is View) {
                x += p.left - p.scrollX
                y += p.top - p.scrollY
                p = p.parent as? View
            }
            loc[0] = x
            loc[1] = y
        }

        val x0 = loc[0]
        val y0 = loc[1]
        val x1 = loc[0] + width
        val y1 = loc[1] + height

        val padTop = PanelUi.dp(context, 28)
        val padBottom = PanelUi.dp(context, 22)
        NpcPreviewHelper.setBounds(
            x0 + 4,
            y0 + padTop,
            x1 - 4,
            y1 - padBottom
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    NpcPreviewHelper.drag(event.x - lastX, event.y - lastY)
                    lastX = event.x
                    lastY = event.y
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}