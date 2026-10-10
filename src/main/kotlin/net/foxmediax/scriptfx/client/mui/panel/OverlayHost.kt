package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.KeyEvent
import icyllis.modernui.view.View
import icyllis.modernui.widget.EditText
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.MATCH
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.WRAP
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.dp
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.lp
import org.lwjgl.glfw.GLFW

/** Стопка модальных окон поверх страницы. Работает только в UI-потоке Modern UI. */
class OverlayHost(private val ctx: Context, private val layer: FrameLayout) {

    private class Entry(val view: View, val onCancel: (() -> Unit)?)

    private val stack = ArrayList<Entry>()

    /** Читается из другого потока (isBackKey), поэтому volatile. */
    @Volatile var isOpen = false
        private set

    private fun push(box: View, onCancel: (() -> Unit)? = null, widthDp: Int = 300, maxHeightDp: Int = 0) {
        val scrim = FrameLayout(ctx).apply {
            background = ColorDrawable(0x99000000.toInt())
            isClickable = true
            setOnClickListener { dismissTop() }
        }
        box.isClickable = true

        val content: View = if (maxHeightDp > 0) {
            icyllis.modernui.widget.ScrollView(ctx).apply {
                addView(box, FrameLayout.LayoutParams(MATCH, WRAP))
            }
        } else {
            box
        }

        val h = if (maxHeightDp > 0) dp(ctx, maxHeightDp) else WRAP
        scrim.addView(
            content,
            FrameLayout.LayoutParams(dp(ctx, widthDp), h, Gravity.CENTER)
        )
        layer.addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))
        stack.add(Entry(scrim, onCancel))
        isOpen = true
    }

    /** cancelled = true вызывает onCancel окна (Esc или клик мимо). */
    fun dismissTop(cancelled: Boolean = true) {
        val e = stack.removeLastOrNull() ?: return
        layer.removeView(e.view)
        isOpen = stack.isNotEmpty()
        if (cancelled) e.onCancel?.invoke()
    }

    fun clear() {
        while (stack.isNotEmpty()) dismissTop(false)
    }

    private fun box(title: String, titleColor: Int): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 12))
        background = ColorDrawable(0xFF1E1E1E.toInt())
        addView(
            TextView(ctx).apply { text = title; textSize = 14f; setTextColor(titleColor) },
            lp(MATCH, WRAP)
        )
    }

    private fun text(message: String) = TextView(ctx).apply {
        text = message
        textSize = 13f
        setTextColor(0xFFCCCCCC.toInt())
        setPadding(0, dp(ctx, 6), 0, dp(ctx, 10))
    }

    /** Окно с двумя кнопками. Esc/клик мимо вызывают onCancel. */
    fun confirm(
        message: String,
        yes: String,
        no: String,
        onYes: () -> Unit,
        onNo: () -> Unit = {},
        onCancel: (() -> Unit)? = null
    ) {
        val b = box("Внимание!", 0xFFFF5555.toInt())
        b.addView(text(message), lp(MATCH, WRAP))
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(
            PanelUi.flatButton(ctx, yes) { dismissTop(false); onYes() },
            LinearLayout.LayoutParams(0, WRAP, 1f)
        )
        row.addView(
            PanelUi.flatButton(ctx, no) { dismissTop(false); onNo() },
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(ctx, 8) }
        )
        b.addView(row, lp(MATCH, WRAP))
        push(b, onCancel)
    }

    /** Ввод строки (создать/переименовать). Enter = OK. */
    fun input(title: String, initial: String, onConfirm: (String) -> Unit) {
        val b = box(title, 0xFFFFFFFF.toInt())
        val edit = EditText(ctx).apply {
            setSingleLine(true)
            textSize = 14f
            setText(initial)
        }
        fun ok() {
            val value = edit.text?.toString().orEmpty()
            dismissTop(false)
            onConfirm(value)
        }
        edit.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
            ) { ok(); true } else false
        }
        b.addView(edit, lp(MATCH, WRAP).apply { topMargin = dp(ctx, 6); bottomMargin = dp(ctx, 10) })

        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(PanelUi.flatButton(ctx, "OK") { ok() }, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(
            PanelUi.flatButton(ctx, "Отмена") { dismissTop() },
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(ctx, 8) }
        )
        b.addView(row, lp(MATCH, WRAP))
        push(b)
        edit.post {
            edit.requestFocus()
            edit.setSelection(0, initial.length)
        }
    }

    /** Список действий (замена контекстного меню). */
    fun actions(title: String, items: List<Pair<String, () -> Unit>>) {
        val b = box(title, 0xFFFFFFFF.toInt())
        for ((label, action) in items) {
            b.addView(
                PanelUi.flatButton(ctx, label) {
                    dismissTop(false)
                    action()
                }.apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
                },
                lp(MATCH, WRAP).apply { topMargin = dp(ctx, 4) }
            )
        }
        // шире + ограничение высоты + скролл
        push(b, widthDp = 420, maxHeightDp = 360)
    }
}