package net.foxmediax.scriptfx.client.mui

import icyllis.modernui.core.Context
import icyllis.modernui.fragment.Fragment
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.util.DataSet
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.LayoutInflater
import icyllis.modernui.view.View
import icyllis.modernui.view.ViewGroup
import icyllis.modernui.widget.EditText
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.mui.panel.PanelUi
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.MATCH
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.WRAP
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.dp
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.lp
import net.foxmediax.scriptfx.security.StudioSecurity

/**
 * Затемнение/blur (через ScreenCallback) + UI ввода пароля перед студией.
 */
class StudioPasswordFragment(
    private val onSuccess: () -> Unit,
    private val onCancel: () -> Unit
) : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: DataSet?
    ): View {
        val ctx = requireContext()
        val root = FrameLayout(ctx).apply {
            background = ColorDrawable(0xCC0A0A12.toInt())
        }

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(0xF2181824.toInt())
            setPadding(dp(ctx, 28), dp(ctx, 24), dp(ctx, 28), dp(ctx, 24))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        card.addView(
            TextView(ctx).apply {
                text = "ScriptFX Studio"
                textSize = 16f
                setTextColor(PanelUi.COL_ACCENT)
                gravity = Gravity.CENTER
            },
            lp(MATCH, WRAP)
        )

        card.addView(
            TextView(ctx).apply {
                text = "Введите пароль"
                textSize = 14f
                setTextColor(PanelUi.COL_TEXT)
                gravity = Gravity.CENTER
                setPadding(0, dp(ctx, 16), 0, dp(ctx, 12))
            },
            lp(MATCH, WRAP)
        )

        val input = EditText(ctx).apply {
            hint = "••••••••"
            textSize = 14f
            setTextColor(PanelUi.COL_TEXT)
            setHintTextColor(PanelUi.COL_MUTED)
            setSingleLine(true)
            background = ColorDrawable(0xFF101018.toInt())
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
        }
        card.addView(input, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) })

        val error = TextView(ctx).apply {
            text = ""
            textSize = 12f
            setTextColor(PanelUi.COL_ERROR)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        card.addView(error, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 12) })

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        row.addView(
            PanelUi.flatButton(ctx, "Отменить ввод") {
                onCancel()
            },
            lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 10) }
        )

        row.addView(
            PanelUi.accentButton(ctx, "Подтвердить") {
                val pwd = input.text?.toString().orEmpty()
                if (StudioSecurity.verify(pwd)) {
                    StudioSecurity.markUnlocked()
                    onSuccess()
                } else {
                    error.text = "Неверный пароль"
                    error.visibility = View.VISIBLE
                    input.setText("")
                }
            },
            lp(WRAP, WRAP)
        )

        card.addView(row, lp(MATCH, WRAP))

        val dm = ctx.resources.displayMetrics
        val w = (dm.widthPixels * 0.42f).toInt().coerceIn(dp(ctx, 320), dp(ctx, 480))
        root.addView(
            card,
            FrameLayout.LayoutParams(w, WRAP, Gravity.CENTER)
        )
        return root
    }
}