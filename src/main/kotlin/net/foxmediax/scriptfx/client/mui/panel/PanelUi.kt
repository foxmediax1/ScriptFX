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

    const val COL_PANEL = 0xE6161616.toInt()
    const val COL_LOGO = 0xFF101010.toInt()
    const val COL_HEADER = 0xFF1E1E1E.toInt()
    const val COL_SIDEBAR = 0xFF141414.toInt()
    const val COL_DIVIDER = 0xFF3A3A3A.toInt()
    const val COL_BTN = 0xFF101010.toInt()
    const val COL_BTN_SELECTED = 0xFF2A2140.toInt()
    const val COL_ACCENT = 0xFFB026FF.toInt()
    const val COL_TEXT = 0xFFEDEDED.toInt()
    const val COL_MUTED = 0xFFAAAAAA.toInt()

    fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun px1(ctx: Context): Int = max(1, dp(ctx, 1))

    fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)

    fun divider(ctx: Context, vertical: Boolean): View = View(ctx).apply {
        background = ColorDrawable(COL_DIVIDER)
    }.also {
        // размеры задаёт вызывающий через lp(px1, MATCH) / lp(MATCH, px1)
    }

    fun flatButton(ctx: Context, label: String, onClick: () -> Unit): Button = Button(ctx).apply {
        text = label
        textSize = 13f
        setTextColor(COL_TEXT)
        background = ColorDrawable(COL_BTN)
        setOnClickListener { onClick() }
    }

    /** Маленькая квадратная кнопка-иконка для шапки. */
    fun iconButton(ctx: Context, glyph: String, onClick: () -> Unit): TextView = TextView(ctx).apply {
        text = glyph
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(COL_TEXT)
        background = ColorDrawable(COL_BTN)
        setOnClickListener { onClick() }
    }
}