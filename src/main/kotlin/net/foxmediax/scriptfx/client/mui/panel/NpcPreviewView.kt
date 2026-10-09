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
 * Правая колонка NPC-редактора.
 *
 * Само 3D-превью рисуется через
 * ScreenEvents.afterExtract + InventoryScreen.
 *
 * Этот View отвечает только за:
 *
 * - отображение заголовка;
 * - bounds области модели;
 * - обработку drag мышью.
 */
class NpcPreviewView(
    ctx: Context
) : FrameLayout(ctx) {

    private val titleView: TextView
    private val hintView: TextView

    private var dragging = false

    private var lastX = 0f
    private var lastY = 0f

    var definition: NpcDefinition? = null
        set(value) {

            field = value

            NpcPreviewHelper.setDefinition(
                value
            )

            titleView.text =
                when {

                    value == null ->
                        "Превью"

                    value.displayName.isNotBlank() ->
                        value.displayName

                    else ->
                        value.id
                }

            post {
                updateBounds()
            }
        }

    init {

        background =
            ColorDrawable(
                0xFF0A0A0C.toInt()
            )

        val column =
            LinearLayout(ctx).apply {

                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER_HORIZONTAL

                setPadding(
                    PanelUi.dp(ctx, 6),
                    PanelUi.dp(ctx, 6),
                    PanelUi.dp(ctx, 6),
                    PanelUi.dp(ctx, 6)
                )
            }

        titleView =
            TextView(ctx).apply {

                text = "Превью"

                textSize = 12f

                setTextColor(
                    PanelUi.COL_TEXT
                )

                gravity =
                    Gravity.CENTER
            }

        hintView =
            TextView(ctx).apply {

                text = "Тяни — поворот"

                textSize = 10f

                setTextColor(
                    0xFF555555.toInt()
                )

                gravity =
                    Gravity.CENTER
            }

        column.addView(
            titleView,
            LinearLayout.LayoutParams(
                PanelUi.MATCH,
                PanelUi.WRAP
            )
        )

        /*
         * Центральная область,
         * где будет находиться модель.
         */
        column.addView(
            View(ctx),
            LinearLayout.LayoutParams(
                PanelUi.MATCH,
                0,
                1f
            )
        )

        column.addView(
            hintView,
            LinearLayout.LayoutParams(
                PanelUi.MATCH,
                PanelUi.WRAP
            )
        )

        addView(
            column,
            LayoutParams(
                PanelUi.MATCH,
                PanelUi.MATCH
            )
        )

        addOnLayoutChangeListener {
                _, _, _, _, _, _, _, _, _ ->

            updateBounds()
        }
    }

    override fun onAttachedToWindow() {

        super.onAttachedToWindow()

        post {
            updateBounds()
        }
    }

    override fun onDetachedFromWindow() {

        NpcPreviewHelper.clearBounds()

        super.onDetachedFromWindow()
    }

    private fun updateBounds() {

        if (
            width <= 0 ||
            height <= 0
        ) {

            NpcPreviewHelper.clearBounds()

            return
        }

        /*
         * ModernUI уже предоставляет нам
         * абсолютные координаты окна.
         *
         * Никакого ручного обхода parent hierarchy
         * здесь не требуется.
         */
        val location =
            IntArray(2)

        getLocationInWindow(
            location
        )

        val x0 =
            location[0]

        val y0 =
            location[1]

        val x1 =
            x0 + width

        val y1 =
            y0 + height

        /*
         * Высота заголовка.
         */
        val padTop =
            PanelUi.dp(
                context,
                28
            )

        /*
         * Высота нижней подсказки.
         */
        val padBottom =
            PanelUi.dp(
                context,
                22
            )

        val previewX0 =
            x0 + 4

        val previewY0 =
            y0 + padTop

        val previewX1 =
            x1 - 4

        val previewY1 =
            y1 - padBottom

        /*
         * Защита от некорректных bounds.
         */
        if (
            previewX1 <= previewX0 ||
            previewY1 <= previewY0
        ) {

            NpcPreviewHelper.clearBounds()

            return
        }

        NpcPreviewHelper.setBounds(
            previewX0,
            previewY0,
            previewX1,
            previewY1
        )
    }

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        when (event.action) {

            MotionEvent.ACTION_DOWN -> {

                dragging = true

                lastX = event.x
                lastY = event.y

                return true
            }

            MotionEvent.ACTION_MOVE -> {

                if (dragging) {

                    val dx =
                        event.x - lastX

                    val dy =
                        event.y - lastY

                    NpcPreviewHelper.drag(
                        dx,
                        dy
                    )

                    lastX = event.x
                    lastY = event.y

                    return true
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {

                dragging = false

                return true
            }
        }

        return super.onTouchEvent(
            event
        )
    }
}