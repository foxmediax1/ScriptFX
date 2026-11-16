package net.foxmediax.scriptfx.client.mui

import icyllis.modernui.core.Context
import icyllis.modernui.fragment.Fragment
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.util.DataSet
import icyllis.modernui.view.LayoutInflater
import icyllis.modernui.view.View
import icyllis.modernui.view.ViewGroup
import icyllis.modernui.widget.EditText
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.mui.panel.PanelUi
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.MATCH
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.WRAP
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.dp
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.lp
import net.foxmediax.scriptfx.security.StudioSecurity

/**
 * Настройки безопасности и шифрования: установка / смена / снятие пароля студии.
 */
class StudioSecurityFragment(
    private val onBack: () -> Unit
) : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: DataSet?
    ): View {
        val ctx = requireContext()
        StudioSecurity.load()

        val root = FrameLayout(ctx).apply {
            background = ColorDrawable(0xFF0E0E14.toInt())
        }

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 24), dp(ctx, 20), dp(ctx, 24), dp(ctx, 20))
        }

        col.addView(
            TextView(ctx).apply {
                text = "← Назад"
                textSize = 13f
                setTextColor(PanelUi.COL_ACCENT)
                isClickable = true
                setOnClickListener { onBack() }
                setPadding(0, 0, 0, dp(ctx, 16))
            },
            lp(WRAP, WRAP)
        )

        col.addView(
            TextView(ctx).apply {
                text = "Безопасность и шифрование"
                textSize = 18f
                setTextColor(PanelUi.COL_TEXT)
            },
            lp(MATCH, WRAP)
        )

        col.addView(
            TextView(ctx).apply {
                text = "Пароль запрашивается при открытии студии. " +
                        "В файле хранится только hash (SHA-256 + salt), не сам пароль. " +
                        "Копии: config/scriptfx-security.json и <мир>/scriptfx-studio.lock"
                textSize = 12f
                setTextColor(PanelUi.COL_MUTED)
                setPadding(0, dp(ctx, 8), 0, dp(ctx, 16))
            },
            lp(MATCH, WRAP)
        )

        val status = TextView(ctx).apply {
            text = if (StudioSecurity.isEnabled()) "Статус: пароль ВКЛЮЧЁН"
            else "Статус: пароль выключен"
            textSize = 13f
            setTextColor(
                if (StudioSecurity.isEnabled()) PanelUi.COL_SUCCESS else PanelUi.COL_MUTED
            )
            setPadding(0, 0, 0, dp(ctx, 12))
        }
        col.addView(status, lp(MATCH, WRAP))

        col.addView(
            TextView(ctx).apply {
                text = "Новый пароль"
                textSize = 12f
                setTextColor(PanelUi.COL_MUTED)
            },
            lp(MATCH, WRAP)
        )

        val pwd1 = EditText(ctx).apply {
            hint = "Введите пароль"
            textSize = 13f
            setTextColor(PanelUi.COL_TEXT)
            setHintTextColor(PanelUi.COL_MUTED)
            setSingleLine(true)
            background = ColorDrawable(0xFF1A1A22.toInt())
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
        }
        col.addView(pwd1, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) })

        col.addView(
            TextView(ctx).apply {
                text = "Повтор"
                textSize = 12f
                setTextColor(PanelUi.COL_MUTED)
            },
            lp(MATCH, WRAP)
        )

        val pwd2 = EditText(ctx).apply {
            hint = "Повторите пароль"
            textSize = 13f
            setTextColor(PanelUi.COL_TEXT)
            setHintTextColor(PanelUi.COL_MUTED)
            setSingleLine(true)
            background = ColorDrawable(0xFF1A1A22.toInt())
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
        }
        col.addView(pwd2, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 12) })

        val msg = TextView(ctx).apply {
            text = ""
            textSize = 12f
            setTextColor(PanelUi.COL_WARNING)
            setPadding(0, 0, 0, dp(ctx, 8))
        }
        col.addView(msg, lp(MATCH, WRAP))

        col.addView(
            PanelUi.accentButton(ctx, "Установить / сменить пароль") {
                val a = pwd1.text?.toString().orEmpty()
                val b = pwd2.text?.toString().orEmpty()
                when {
                    a.length < 4 -> {
                        msg.setTextColor(PanelUi.COL_WARNING)
                        msg.text = "Минимум 4 символа"
                    }
                    a != b -> {
                        msg.setTextColor(PanelUi.COL_WARNING)
                        msg.text = "Пароли не совпадают"
                    }
                    else -> {
                        StudioSecurity.setPassword(a)
                        msg.setTextColor(PanelUi.COL_SUCCESS)
                        msg.text = "Пароль сохранён"
                        status.text = "Статус: пароль ВКЛЮЧЁН"
                        status.setTextColor(PanelUi.COL_SUCCESS)
                        pwd1.setText("")
                        pwd2.setText("")
                    }
                }
            },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) }
        )

        col.addView(
            PanelUi.flatButton(ctx, "Снять пароль") {
                StudioSecurity.clearPassword()
                status.text = "Статус: пароль выключен"
                status.setTextColor(PanelUi.COL_MUTED)
                msg.setTextColor(PanelUi.COL_SUCCESS)
                msg.text = "Пароль отключён"
            },
            lp(MATCH, WRAP)
        )

        val scroll = ScrollView(ctx)
        scroll.addView(col, lp(MATCH, WRAP))
        root.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))
        return root
    }
}