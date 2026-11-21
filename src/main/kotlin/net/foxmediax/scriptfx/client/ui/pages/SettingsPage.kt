package net.foxmediax.scriptfx.client.ui.pages

import net.foxmediax.scriptfx.client.ui.StudioPage
import net.foxmediax.scriptfx.client.ui.UiButton
import net.foxmediax.scriptfx.client.ui.UiLabel
import net.foxmediax.scriptfx.client.ui.UiPanel
import net.foxmediax.scriptfx.client.ui.UiTextField
import net.foxmediax.scriptfx.client.ui.UiTheme
import net.foxmediax.scriptfx.security.StudioSecurity
import net.minecraft.client.gui.GuiGraphicsExtractor

class SettingsPage : StudioPage {
    override val title = "Настройки"

    private val root = UiPanel(0)
    private val status = UiLabel("", UiTheme.TEXT_MUTED)
    private val msg = UiLabel("", UiTheme.TEXT_MUTED)
    private val pwd1 = UiTextField(hint = "Новый пароль")
    private val pwd2 = UiTextField(hint = "Повтор пароля")

    override fun onShow() {
        StudioSecurity.load()
        refreshStatus()
        msg.text = ""
    }

    override fun layout(x: Int, y: Int, w: Int, h: Int) {
        root.clearChildren()
        root.setBounds(x, y, w, h)
        val pad = UiTheme.PAD
        var cy = y + pad

        root.add(UiLabel("Настройки ScriptFX Studio", UiTheme.TEXT).also {
            it.setBounds(x + pad, cy, w - pad * 2, 18)
        })
        cy += 28

        root.add(UiLabel("Безопасность и шифрование", UiTheme.ACCENT).also {
            it.setBounds(x + pad, cy, w - pad * 2, 16)
        })
        cy += 22

        status.setBounds(x + pad, cy, w - pad * 2, 14)
        root.add(status)
        cy += 24

        root.add(UiLabel(
            "Пароль спрашивается при открытии студии. В файле хранится hash.",
            UiTheme.TEXT_MUTED
        ).also { it.setBounds(x + pad, cy, w - pad * 2, 14) })
        cy += 28

        pwd1.setBounds(x + pad, cy, 280, 26)
        root.add(pwd1)
        cy += 32
        pwd2.setBounds(x + pad, cy, 280, 26)
        root.add(pwd2)
        cy += 36

        val btnSet = UiButton("Установить / сменить пароль") {
            val a = pwd1.value
            val b = pwd2.value
            when {
                a.length < 4 -> {
                    msg.text = "Минимум 4 символа"
                    msg.color = UiTheme.WARNING
                }
                a != b -> {
                    msg.text = "Пароли не совпадают"
                    msg.color = UiTheme.WARNING
                }
                else -> {
                    StudioSecurity.setPassword(a)
                    pwd1.value = ""
                    pwd2.value = ""
                    msg.text = "Пароль сохранён"
                    msg.color = UiTheme.SUCCESS
                    refreshStatus()
                }
            }
        }
        btnSet.style = UiButton.Style.PRIMARY
        btnSet.setBounds(x + pad, cy, 220, 28)
        root.add(btnSet)

        val btnClear = UiButton("Снять пароль") {
            StudioSecurity.clearPassword()
            msg.text = "Пароль отключён"
            msg.color = UiTheme.SUCCESS
            refreshStatus()
        }
        btnClear.style = UiButton.Style.DANGER
        btnClear.setBounds(x + pad + 230, cy, 140, 28)
        root.add(btnClear)
        cy += 40

        msg.setBounds(x + pad, cy, w - pad * 2, 14)
        root.add(msg)
        cy += 32

        root.add(UiLabel("Режим студии", UiTheme.ACCENT).also {
            it.setBounds(x + pad, cy, w - pad * 2, 16)
        })
        cy += 22
        root.add(UiLabel(
            "Spectator при входе в студию (если включено в клиенте).",
            UiTheme.TEXT_MUTED
        ).also { it.setBounds(x + pad, cy, w - pad * 2, 14) })
    }

    private fun refreshStatus() {
        status.text = if (StudioSecurity.isEnabled()) "Статус: пароль ВКЛЮЧЁН"
        else "Статус: пароль выключен"
        status.color = if (StudioSecurity.isEnabled()) UiTheme.SUCCESS else UiTheme.TEXT_MUTED
    }

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) =
        root.render(g, mouseX, mouseY, pt)

    override fun mouseClicked(mx: Int, my: Int, button: Int) = root.mouseClicked(mx, my, button)
    override fun mouseReleased(mx: Int, my: Int, button: Int) = root.mouseReleased(mx, my, button)
    override fun keyPressed(key: Int, scan: Int, modifiers: Int) = root.keyPressed(key, scan, modifiers)
    override fun charTyped(codePoint: Int, modifiers: Int) = root.charTyped(codePoint, modifiers)
}