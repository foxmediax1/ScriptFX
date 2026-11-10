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
import kotlin.math.abs

/**
 * Превью NPC:
 * - drag = поворот
 * - click = следующая анимация из реального списка модели
 * - кнопки анимаций снизу
 */
class NpcPreviewView(
    ctx: Context
) : FrameLayout(ctx) {

    private val titleView: TextView
    private val hintView: TextView
    private val animView: TextView
    private val errorView: TextView
    private val animBar: LinearLayout

    private var dragging = false
    private var moved = false
    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f

    var onAnimChanged: ((String) -> Unit)? = null

    var definition: NpcDefinition? = null
        set(value) {
            field = value
            NpcPreviewHelper.setDefinition(value)

            titleView.text = when {
                value == null -> "Превью"
                value.displayName.isNotBlank() -> value.displayName
                else -> value.id
            }

            val anim = value?.defaultAnim?.ifBlank { "idle" } ?: "idle"
            NpcPreviewHelper.playAnim(anim)
            updateAnimLabel(anim)
            rebuildAnimButtons(context)
            highlightAnimButton(anim)

            post {
                updateBounds()
                refreshErrorState()
            }
            postDelayed({ refreshErrorState() }, 50)
            postDelayed({ refreshErrorState() }, 200)
        }

    init {
        background = ColorDrawable(0xFF1A1028.toInt())
        isClickable = true
        isFocusable = true

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

        val spacer = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        hintView = TextView(ctx).apply {
            text = "Тяни — поворот · Клик — анимация"
            textSize = 10f
            setTextColor(0xFF9CA3AF.toInt())
            gravity = Gravity.CENTER
        }

        animView = TextView(ctx).apply {
            text = "anim: idle"
            textSize = 11f
            setTextColor(PanelUi.COL_ACCENT)
            gravity = Gravity.CENTER
            setPadding(0, PanelUi.dp(ctx, 2), 0, PanelUi.dp(ctx, 4))
        }

        animBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        rebuildAnimButtons(ctx)

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
        column.addView(
            animView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        column.addView(
            animBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        addView(
            column,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        addView(
            errorView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )
    }

    private fun rebuildAnimButtons(ctx: Context) {
        animBar.removeAllViews()
        for (anim in NpcPreviewHelper.previewAnims) {
            val btn = TextView(ctx).apply {
                text = anim
                textSize = 10f
                setTextColor(PanelUi.COL_TEXT)
                background = ColorDrawable(PanelUi.COL_BTN)
                setPadding(
                    PanelUi.dp(ctx, 8),
                    PanelUi.dp(ctx, 5),
                    PanelUi.dp(ctx, 8),
                    PanelUi.dp(ctx, 5)
                )
                isClickable = true
                setOnClickListener { play(anim) }
            }
            animBar.addView(
                btn,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = PanelUi.dp(ctx, 4)
                    topMargin = PanelUi.dp(ctx, 2)
                }
            )
        }
    }

    private fun play(anim: String) {
        NpcPreviewHelper.playAnim(anim)
        updateAnimLabel(anim)
        highlightAnimButton(anim)
        onAnimChanged?.invoke(anim)
    }

    private fun updateAnimLabel(anim: String) {
        animView.text = "anim: $anim"
    }

    private fun highlightAnimButton(anim: String) {
        for (i in 0 until animBar.childCount) {
            val child = animBar.getChildAt(i) as? TextView ?: continue
            val selected = child.text?.toString() == anim
            child.background = ColorDrawable(
                if (selected) PanelUi.COL_BTN_SELECTED else PanelUi.COL_BTN
            )
        }
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

        val padTop = (PanelUi.dp(context, 28) / guiScale).toInt().coerceAtLeast(14)
        val padBottom = (PanelUi.dp(context, 52) / guiScale).toInt().coerceAtLeast(28)
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
                    if (abs(event.x - downX) > 4f || abs(event.y - downY) > 4f) {
                        moved = true
                    }
                    if (moved) {
                        NpcPreviewHelper.drag(dx, dy)
                    }
                    lastX = event.x
                    lastY = event.y
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (dragging && !moved) {
                    val next = NpcPreviewHelper.cycleAnim()
                    updateAnimLabel(next)
                    highlightAnimButton(next)
                    onAnimChanged?.invoke(next)
                }
                dragging = false
                moved = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                moved = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}