package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.graphics.drawable.Drawable
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.View
import icyllis.modernui.view.ViewGroup
import icyllis.modernui.widget.Button
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.mui.UiStyle
import kotlin.math.max

object PanelUi {

    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

    // ---- Studio palette (под макет) ----
    const val COL_PANEL = 0xF00B0614.toInt()
    const val COL_LOGO = 0xFF12081F.toInt()
    const val COL_HEADER = 0xFF140A22.toInt()
    const val COL_SIDEBAR = 0xFF10081C.toInt()
    const val COL_DIVIDER = 0xFF3B2760.toInt()

    const val COL_BTN = 0xFF1A102E.toInt()
    const val COL_BTN_SELECTED = 0xFF6D28D9.toInt()
    const val COL_BTN_DANGER = 0xFF3F1220.toInt()

    const val COL_ACCENT = 0xFFA855F7.toInt()
    const val COL_TEXT = 0xFFF5F3FF.toInt()
    const val COL_MUTED = 0xFFC4B5FD.toInt()
    const val COL_DIM = 0xFF8B7CB3.toInt()

    const val COL_CONTENT = 0xFF0A0612.toInt()
    const val COL_CARD = 0xFF16102A.toInt()
    const val COL_NAV = 0xFF120A1E.toInt()
    const val COL_STATUS = 0xFF0E0A18.toInt()

    const val COL_SUCCESS = 0xFF34D399.toInt()
    const val COL_WARNING = 0xFFFBBF24.toInt()
    const val COL_ERROR = 0xFFF87171.toInt()
    const val COL_DANGER_TEXT = 0xFFFCA5A5.toInt()

    fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun dp(ctx: Context, value: Float): Int =
        (value * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun px1(ctx: Context): Int = max(1, dp(ctx, 1))

    fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)

    fun lpWeight(weight: Float, w: Int = MATCH, h: Int = MATCH) =
        LinearLayout.LayoutParams(w, h, weight)

    fun lpMargin(
        w: Int, h: Int,
        left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0
    ) = LinearLayout.LayoutParams(w, h).apply {
        leftMargin = left; topMargin = top; rightMargin = right; bottomMargin = bottom
    }

    fun btnBg(ctx: Context, base: Int, radiusDp: Float = 8f): Drawable =
        UiStyle.buttonBg(
            ctx,
            base,
            hover = UiStyle.mix(base, 0xFFFFFFFF.toInt(), 0.12f),
            pressed = UiStyle.mix(base, 0xFF000000.toInt(), 0.28f),
            radiusDp = radiusDp
        )

    private fun styled(
        ctx: Context, label: String, size: Float, textColor: Int, base: Int,
        padH: Int, padV: Int, onClick: () -> Unit
    ): Button = Button(ctx).apply {
        text = label
        textSize = size
        setTextColor(textColor)
        background = btnBg(ctx, base)
        setPadding(dp(ctx, padH), dp(ctx, padV), dp(ctx, padH), dp(ctx, padV))
        setOnClickListener { onClick() }
    }

    fun sidebarButton(ctx: Context, label: String, onClick: () -> Unit): Button =
        styled(ctx, label, 14f, COL_TEXT, COL_BTN, 12, 12, onClick).apply {
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(ctx, 44)
        }

    fun flatButton(ctx: Context, label: String, onClick: () -> Unit): Button =
        styled(ctx, label, 13f, COL_TEXT, COL_BTN, 14, 10, onClick)

    fun accentButton(ctx: Context, label: String, onClick: () -> Unit): Button =
        styled(ctx, label, 14f, COL_TEXT, COL_BTN_SELECTED, 16, 12, onClick)

    fun dangerButton(ctx: Context, label: String, onClick: () -> Unit): Button =
        styled(ctx, label, 13f, COL_DANGER_TEXT, COL_BTN_DANGER, 12, 10, onClick)

    fun tabButton(ctx: Context, label: String, selected: Boolean, onClick: () -> Unit): Button =
        styled(
            ctx, label, 13f, COL_TEXT,
            if (selected) COL_BTN_SELECTED else COL_BTN,
            14, 10, onClick
        )

    fun iconButton(ctx: Context, glyph: String, onClick: () -> Unit): TextView {
        val size = dp(ctx, 32)
        return TextView(ctx).apply {
            text = glyph
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(COL_TEXT)
            background = btnBg(ctx, COL_BTN, 8f)
            minWidth = size
            minHeight = size
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    fun pageTitle(ctx: Context, text: String): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = 16f
            setTextColor(COL_TEXT)
        }

    fun sectionTitle(ctx: Context, text: String): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = 13f
            setTextColor(COL_ACCENT)
        }

    fun bodyText(ctx: Context, text: String, size: Float = 13f): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = size
            setTextColor(COL_TEXT)
        }

    fun mutedText(ctx: Context, text: String, size: Float = 12f): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = size
            setTextColor(COL_MUTED)
        }

    fun pageRoot(ctx: Context): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_CONTENT)
            setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
        }

    fun card(ctx: Context): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_CARD)
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
        }

    fun horizontalRow(ctx: Context): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

    fun verticalColumn(ctx: Context): LinearLayout =
        LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    fun divider(ctx: Context): View =
        View(ctx).apply { background = ColorDrawable(COL_DIVIDER) }

    fun spacer(ctx: Context, heightDp: Int = 8): View =
        View(ctx).apply { minimumHeight = dp(ctx, heightDp) }

    fun scrollColumn(ctx: Context, content: View): ScrollView =
        ScrollView(ctx).apply { addView(content, lp(MATCH, WRAP)) }

    /** Трёхколоночный layout: left | center | right */
    fun studioTriple(
        ctx: Context,
        left: View,
        center: View,
        right: View,
        leftDp: Int = 260,
        rightDp: Int = 300
    ): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ColorDrawable(COL_CONTENT)
            addView(left, lp(dp(ctx, leftDp), MATCH))
            addView(divider(ctx), lp(px1(ctx), MATCH))
            addView(center, LinearLayout.LayoutParams(0, MATCH, 1f))
            addView(divider(ctx), lp(px1(ctx), MATCH))
            addView(right, lp(dp(ctx, rightDp), MATCH))
        }
    }

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

    fun navColumn(ctx: Context): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_NAV)
            setPadding(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6))
        }

    fun matchFrame(ctx: Context): FrameLayout =
        FrameLayout(ctx).apply {
            background = ColorDrawable(COL_CONTENT)
        }
}