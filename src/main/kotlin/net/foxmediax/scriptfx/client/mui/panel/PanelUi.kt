package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.View
import icyllis.modernui.view.ViewGroup
import icyllis.modernui.widget.Button
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.TextView
import kotlin.math.max
import icyllis.modernui.graphics.drawable.Drawable
import net.foxmediax.scriptfx.client.mui.UiStyle

/**
 * Общий стиль и фабрики виджетов панели ScriptFX (Modern UI).
 * Все цвета и отступы — отсюда; страницы не дублируют «магические» числа.
 */
object PanelUi {

    // -------------------------------------------------------------------------
    // Layout constants
    // -------------------------------------------------------------------------

    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

    // -------------------------------------------------------------------------
    // Colors (ARGB)
    // -------------------------------------------------------------------------

    const val COL_PANEL = 0xF01E1E1E.toInt()
    const val COL_LOGO = 0xFF1A1A1A.toInt()
    const val COL_HEADER = 0xFF252525.toInt()
    const val COL_SIDEBAR = 0xFF1A1A1A.toInt()
    const val COL_DIVIDER = 0xFF3A3A3A.toInt()
    const val COL_BTN = 0xFF101010.toInt()
    const val COL_BTN_SELECTED = 0xFF2A2140.toInt()
    const val COL_BTN_DANGER = 0xFF3A1515.toInt()
    const val COL_ACCENT = 0xFFB026FF.toInt()
    const val COL_TEXT = 0xFFEDEDED.toInt()
    const val COL_MUTED = 0xFFAAAAAA.toInt()
    const val COL_DIM = 0xFF666666.toInt()
    const val COL_CONTENT = 0xFF000000.toInt()
    const val COL_CARD = 0xFF101014.toInt()
    const val COL_NAV = 0xFF141418.toInt()
    const val COL_SUCCESS = 0xFF55FF88.toInt()
    const val COL_WARNING = 0xFFFFAA55.toInt()
    const val COL_ERROR = 0xFFFF5555.toInt()
    const val COL_DANGER_TEXT = 0xFFFF8888.toInt()

    // -------------------------------------------------------------------------
    // Metrics
    // -------------------------------------------------------------------------

    fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun dp(ctx: Context, value: Float): Int =
        (value * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun px1(ctx: Context): Int = max(1, dp(ctx, 1))

    // -------------------------------------------------------------------------
    // LayoutParams helpers
    // -------------------------------------------------------------------------

    fun lp(w: Int, h: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h)

    fun lpWeight(weight: Float, w: Int = MATCH, h: Int = MATCH): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, weight)

    fun lpMargin(
        w: Int,
        h: Int,
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h).apply {
            leftMargin = left
            topMargin = top
            rightMargin = right
            bottomMargin = bottom
        }

    // -------------------------------------------------------------------------
    // Buttons (скруглённые, с состояниями наведения и нажатия)
    // -------------------------------------------------------------------------

    /** Фон со скруглением и состояниями, производными от базового цвета. */
    fun btnBg(ctx: Context, base: Int, radiusDp: Float = 6f): Drawable =
        UiStyle.buttonBg(
            ctx,
            base,
            hover = UiStyle.mix(base, 0xFFFFFFFF.toInt(), 0.10f),
            pressed = UiStyle.mix(base, 0xFF000000.toInt(), 0.30f),
            radiusDp = radiusDp
            // hoverStroke по умолчанию = UiStyle.ACCENT
        )

    private fun styled(
        ctx: Context, label: String, size: Float, textColor: Int, base: Int,
        padH: Int, padV: Int, onClick: () -> Unit
    ): Button = Button(ctx).apply {
        text = label
        this.textSize = size
        setTextColor(textColor)
        background = btnBg(ctx, base)
        setPadding(dp(ctx, padH), dp(ctx, padV), dp(ctx, padH), dp(ctx, padV))
        setOnClickListener { onClick() }
    }

    fun sidebarButton(ctx: Context, label: String, onClick: () -> Unit): Button =
        styled(ctx, label, 15f, COL_TEXT, COL_BTN, 10, 12, onClick).apply {
            gravity = Gravity.CENTER
            minHeight = dp(ctx, 48)
        }

    fun flatButton(ctx: Context, label: String, onClick: () -> Unit): Button =
        styled(ctx, label, 13f, COL_TEXT, COL_BTN, 10, 8, onClick)

    /** Кнопка с акцентом (сохранить, применить). */
    fun accentButton(ctx: Context, label: String, onClick: () -> Unit): Button =
        styled(ctx, label, 13f, COL_TEXT, COL_BTN_SELECTED, 12, 8, onClick)

    /** Опасное действие (удалить, прервать). */
    fun dangerButton(ctx: Context, label: String, onClick: () -> Unit): Button =
        styled(ctx, label, 13f, COL_DANGER_TEXT, COL_BTN_DANGER, 10, 8, onClick)

    fun iconButton(ctx: Context, glyph: String, onClick: () -> Unit): TextView {
        val size = dp(ctx, 32)
        return TextView(ctx).apply {
            text = glyph
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(COL_TEXT)
            background = btnBg(ctx, COL_BTN, 6f)
            minWidth = size
            minHeight = size
            setPadding(0, 0, 0, 0)
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    // -------------------------------------------------------------------------
    // Text
    // -------------------------------------------------------------------------

    fun sectionTitle(ctx: Context, text: String): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = 14f
            setTextColor(COL_ACCENT)
            setPadding(0, dp(ctx, 10), 0, dp(ctx, 4))
        }

    fun pageTitle(ctx: Context, text: String): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = 16f
            setTextColor(COL_TEXT)
        }

    fun bodyText(ctx: Context, text: String, size: Float = 13f): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = size
            setTextColor(COL_TEXT)
            setPadding(0, dp(ctx, 3), 0, dp(ctx, 3))
        }

    fun mutedText(ctx: Context, text: String, size: Float = 13f): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = size
            setTextColor(COL_MUTED)
            setPadding(0, dp(ctx, 3), 0, dp(ctx, 3))
        }

    fun dimText(ctx: Context, text: String, size: Float = 12f): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = size
            setTextColor(COL_DIM)
            setPadding(0, dp(ctx, 3), 0, dp(ctx, 3))
        }
    fun errorText(ctx: Context, text: String, size: Float = 12f): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = size
            setTextColor(COL_ERROR)
            setPadding(0, dp(ctx, 2), 0, dp(ctx, 2))
        }

    // -------------------------------------------------------------------------
    // Containers / surfaces
    // -------------------------------------------------------------------------

    fun pageRoot(ctx: Context): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_CONTENT)
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
        }

    fun navColumn(ctx: Context): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_NAV)
            setPadding(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6))
        }

    fun card(ctx: Context): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_CARD) // или UiStyle.rounded, если уже так
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
        }

    fun horizontalRow(ctx: Context): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

    fun verticalColumn(ctx: Context): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }

    fun divider(ctx: Context): View =
        View(ctx).apply {
            background = ColorDrawable(COL_DIVIDER)
            minimumHeight = px1(ctx)
        }

    fun spacer(ctx: Context, heightDp: Int = 8): View =
        View(ctx).apply {
            minimumHeight = dp(ctx, heightDp)
        }

    /** Вертикальный ScrollView на всю ширину с контентом. */
    fun scrollColumn(ctx: Context, content: View): ScrollView =
        ScrollView(ctx).apply {
            addView(content, lp(MATCH, WRAP))
        }

    /** Frame на весь родитель. */
    fun matchFrame(ctx: Context): FrameLayout =
        FrameLayout(ctx).apply {
            background = ColorDrawable(COL_CONTENT)
        }

    // -------------------------------------------------------------------------
    // Composite rows
    // -------------------------------------------------------------------------

    /**
     * Строка списка: слева текст, справа опциональная кнопка.
     */
    fun listRow(
        ctx: Context,
        label: String,
        labelColor: Int = COL_TEXT,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        val row = horizontalRow(ctx).apply {
            setPadding(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6))
            background = btnBg(ctx, COL_BTN, 4f)
            if (onClick != null) {
                isClickable = true
                setOnClickListener { onClick() }
            }
        }
        row.addView(
            TextView(ctx).apply {
                text = label
                textSize = 12f
                setTextColor(labelColor)
            },
            LinearLayout.LayoutParams(0, WRAP, 1f)
        )
        return row
    }

    /**
     * Заголовок страницы + ряд кнопок справа.
     */
    fun toolbar(
        ctx: Context,
        title: String,
        vararg actions: Pair<String, () -> Unit>
    ): LinearLayout {
        val bar = horizontalRow(ctx)
        bar.addView(
            pageTitle(ctx, title),
            LinearLayout.LayoutParams(0, WRAP, 1f)
        )
        for ((label, click) in actions) {
            bar.addView(
                flatButton(ctx, label, click),
                lpMargin(WRAP, WRAP, left = dp(ctx, 6))
            )
        }
        return bar
    }
}