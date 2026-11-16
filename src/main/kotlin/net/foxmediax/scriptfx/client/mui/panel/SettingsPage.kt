package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.view.View
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.mui.MuiScreens
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_ACCENT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_CONTENT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_MUTED
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_TEXT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.MATCH
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.WRAP
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.dp
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.lp
import net.foxmediax.scriptfx.security.StudioSecurity
import net.minecraft.client.Minecraft

class SettingsPage(host: PanelHost) : PanelPage(host) {

    override val breadcrumb: String get() = "Настройки"

    override fun createView(ctx: Context): View {
        StudioSecurity.load()

        val root = FrameLayout(ctx).apply {
            background = ColorDrawable(COL_CONTENT)
        }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 24), dp(ctx, 20), dp(ctx, 24), dp(ctx, 20))
        }

        col.addView(TextView(ctx).apply {
            text = "Настройки ScriptFX Studio"
            textSize = 16f
            setTextColor(COL_TEXT)
        }, lp(MATCH, WRAP))

        col.addView(TextView(ctx).apply {
            text = "Клиентские опции панели и безопасности."
            textSize = 12f
            setTextColor(COL_MUTED)
            setPadding(0, dp(ctx, 6), 0, dp(ctx, 16))
        }, lp(MATCH, WRAP))

        col.addView(TextView(ctx).apply {
            text = "Безопасность"
            textSize = 13f
            setTextColor(COL_ACCENT)
        }, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) })

        val status = if (StudioSecurity.isEnabled()) "пароль ВКЛ" else "пароль выкл"
        col.addView(TextView(ctx).apply {
            text = "Статус: $status"
            textSize = 12f
            setTextColor(COL_MUTED)
            setPadding(0, 0, 0, dp(ctx, 10))
        }, lp(MATCH, WRAP))

        col.addView(
            PanelUi.accentButton(ctx, "Настройки безопасности и шифрования") {
                // безопасный вариант: документация / статус
                // если есть отдельный Fragment — вызови его здесь
                host.openDocumentation()
            },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 16) }
        )

        col.addView(TextView(ctx).apply {
            text = "Режим студии"
            textSize = 13f
            setTextColor(COL_ACCENT)
        }, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) })

        col.addView(TextView(ctx).apply {
            text =
                "При входе в студию включается spectator (gamemode 3), " +
                        "при выходе возвращается прежний режим."
            textSize = 12f
            setTextColor(COL_MUTED)
        }, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 16) })

        col.addView(TextView(ctx).apply {
            text = "Документация"
            textSize = 13f
            setTextColor(COL_ACCENT)
        }, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) })

        col.addView(
            PanelUi.flatButton(ctx, "Открыть документацию") {
                host.openDocumentation()
            },
            lp(MATCH, WRAP)
        )

        val scroll = ScrollView(ctx)
        scroll.addView(col, lp(MATCH, WRAP))
        root.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))
        return root
    }
}