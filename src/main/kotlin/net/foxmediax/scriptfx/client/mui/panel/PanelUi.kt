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
    // Buttons
    // -------------------------------------------------------------------------

    fun sidebarButton(ctx: Context, label: String, onClick: () -> Unit): Button =
        Button(ctx).apply {
            text = label
            textSize = 14f
            setTextColor(COL_TEXT)
            background = ColorDrawable(COL_BTN)
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 8), dp(ctx, 10), dp(ctx, 8), dp(ctx, 10))
            minHeight = dp(ctx, 42)
            setOnClickListener { onClick() }
        }

    fun flatButton(ctx: Context, label: String, onClick: () -> Unit): Button =
        Button(ctx).apply {
            text = label
            textSize = 12f
            setTextColor(COL_TEXT)
            background = ColorDrawable(COL_BTN)
            setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
            setOnClickListener { onClick() }
        }

    /** Кнопка с акцентом (сохранить, применить). */
    fun accentButton(ctx: Context, label: String, onClick: () -> Unit): Button =
        Button(ctx).apply {
            text = label
            textSize = 12f
            setTextColor(COL_TEXT)
            background = ColorDrawable(COL_BTN_SELECTED)
            setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6))
            setOnClickListener { onClick() }
        }

    /** Опасное действие (удалить, прервать). */
    fun dangerButton(ctx: Context, label: String, onClick: () -> Unit): Button =
        Button(ctx).apply {
            text = label
            textSize = 12f
            setTextColor(COL_DANGER_TEXT)
            background = ColorDrawable(COL_BTN_DANGER)
            setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
            setOnClickListener { onClick() }
        }

    fun iconButton(ctx: Context, glyph: String, onClick: () -> Unit): TextView {
        val size = dp(ctx, 28)
        return TextView(ctx).apply {
            text = glyph
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(COL_TEXT)
            background = ColorDrawable(COL_BTN)
            // фиксированный квадрат — без «прыгающей» высоты от глифа
            minWidth = size
            minHeight = size
            width = size   // если API позволяет; иначе только через LayoutParams
            height = size
            setPadding(0, 0, 0, 0)
            setOnClickListener { onClick() }
        }
    }

    fun iconLp(ctx: Context, marginStartDp: Int = 0): LinearLayout.LayoutParams {
        val s = dp(ctx, 28)
        return LinearLayout.LayoutParams(s, s).apply {
            if (marginStartDp != 0) leftMargin = dp(ctx, marginStartDp)
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    // -------------------------------------------------------------------------
    // Text
    // -------------------------------------------------------------------------

    fun sectionTitle(ctx: Context, text: String): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = 13f
            setTextColor(COL_ACCENT)
            setPadding(0, dp(ctx, 8), 0, dp(ctx, 2))
        }

    fun pageTitle(ctx: Context, text: String): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = 15f
            setTextColor(COL_TEXT)
        }

    fun bodyText(ctx: Context, text: String, size: Float = 12f): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = size
            setTextColor(COL_TEXT)
            setPadding(0, dp(ctx, 2), 0, dp(ctx, 2))
        }

    fun mutedText(ctx: Context, text: String, size: Float = 12f): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = size
            setTextColor(COL_MUTED)
            setPadding(0, dp(ctx, 2), 0, dp(ctx, 2))
        }

    fun dimText(ctx: Context, text: String, size: Float = 11f): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = size
            setTextColor(COL_DIM)
            setPadding(0, dp(ctx, 2), 0, dp(ctx, 2))
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
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
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
            background = ColorDrawable(COL_CARD)
            setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
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
            background = ColorDrawable(COL_BTN)
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