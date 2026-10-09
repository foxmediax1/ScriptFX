package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.View
import icyllis.modernui.view.ViewGroup
import icyllis.modernui.widget.Button
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.TextView
import kotlin.math.max

object PanelUi {
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

    const val COL_PANEL = 0xF01E1E1E.toInt()
    const val COL_LOGO = 0xFF1A1A1A.toInt()
    const val COL_HEADER = 0xFF252525.toInt()
    const val COL_SIDEBAR = 0xFF1A1A1A.toInt()
    const val COL_DIVIDER = 0xFF3A3A3A.toInt()
    const val COL_BTN = 0xFF101010.toInt()
    const val COL_BTN_SELECTED = 0xFF2A2140.toInt()
    const val COL_ACCENT = 0xFFB026FF.toInt()
    const val COL_TEXT = 0xFFEDEDED.toInt()
    const val COL_MUTED = 0xFFAAAAAA.toInt()
    const val COL_CONTENT = 0xFF000000.toInt()

    fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun px1(ctx: Context): Int = max(1, dp(ctx, 1))

    fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)

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

    fun iconButton(ctx: Context, glyph: String, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            text = glyph
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(COL_TEXT)
            background = ColorDrawable(COL_BTN)
            setOnClickListener { onClick() }
        }
}