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
import net.minecraft.client.Minecraft

/**
 * Центральная колонка NPC-редактора: заголовок, bounds для 3D-превью, drag, ошибка загрузки.
 * Модель рисуется снаружи (ScreenEvents + NpcPreviewHelper.renderInGui).
 */
class NpcPreviewView(
    ctx: Context
) : FrameLayout(ctx) {

    private val titleView: TextView
    private val hintView: TextView
    private val errorView: TextView

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

            post {
                updateBounds()
                refreshErrorState()
            }
            // второй кадр — после apply в renderInGui
            postDelayed({ refreshErrorState() }, 50)
            postDelayed({ refreshErrorState() }, 200)
        }

    init {
        background = ColorDrawable(0xFF3A3A42.toInt())

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

        // Растягиваемая середина — зона модели
        val spacer = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        hintView = TextView(ctx).apply {
            text = "Тяни — поворот"
            textSize = 10f
            setTextColor(0xFF888888.toInt())
            gravity = Gravity.CENTER
        }

        errorView = TextView(ctx).apply {
            text = ""
            textSize = 13f
            setTextColor(0xFFFF5555.toInt())
            gravity = Gravity.CENTER
            visibility = View.GONE
        }

        column.addView(
            titleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        column.addView(spacer)
        column.addView(
            hintView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        addView(
            column,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        // Ошибка поверх центра
        addView(
            errorView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )
    }

    fun refreshErrorState() {
        val err = NpcPreviewHelper.loadError
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
        post {
            updateBounds()
            refreshErrorState()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post {
            updateBounds()
            refreshErrorState()
        }
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

        val location = IntArray(2)
        getLocationInWindow(location)

        val mc = Minecraft.getInstance()
        val guiScale = mc.window.guiScale.toFloat().coerceAtLeast(1f)

        val x0 = (location[0] / guiScale).toInt()
        val y0 = (location[1] / guiScale).toInt()
        val x1 = ((location[0] + width) / guiScale).toInt()
        val y1 = ((location[1] + height) / guiScale).toInt()

        val padTop = (PanelUi.dp(context, 26) / guiScale).toInt().coerceAtLeast(14)
        val padBottom = (PanelUi.dp(context, 20) / guiScale).toInt().coerceAtLeast(12)
        val padX = 4

        val previewX0 = x0 + padX
        val previewY0 = y0 + padTop
        val previewX1 = x1 - padX
        val previewY1 = y1 - padBottom

        if (previewX1 - previewX0 < 24 || previewY1 - previewY0 < 24) {
            NpcPreviewHelper.clearBounds()
            return
        }

        NpcPreviewHelper.setBounds(previewX0, previewY0, previewX1, previewY1)
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