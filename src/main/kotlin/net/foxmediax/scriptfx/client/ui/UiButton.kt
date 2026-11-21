package net.foxmediax.scriptfx.client.ui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class UiButton(
    var text: String = "",
    var onClick: (() -> Unit)? = null
) : UiWidget() {

    enum class Style {
        NORMAL,
        PRIMARY,
        DANGER,
        GHOST
    }

    var active = false
    var style = Style.NORMAL

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        if (!visible) return
        val hover = contains(mouseX, mouseY) && enabled

        val bg = when {
            !enabled -> 0xFF0A0A0A.toInt()
            active -> UiTheme.BG_ACTIVE
            style == Style.PRIMARY && hover -> 0xFF3B1F5C.toInt()
            style == Style.PRIMARY -> 0xFF2A1540.toInt()
            style == Style.DANGER && hover -> 0xFF4A1A1A.toInt()
            style == Style.DANGER -> UiTheme.DANGER_BG
            style == Style.GHOST && hover -> UiTheme.BG_HOVER
            style == Style.GHOST -> 0x00000000
            hover -> UiTheme.BG_HOVER
            else -> UiTheme.BG_BTN
        }
        if (bg != 0) g.fill(x, y, right, bottom, bg)

        val border = when {
            !enabled -> UiTheme.BORDER
            active || style == Style.PRIMARY -> UiTheme.ACCENT
            style == Style.DANGER -> UiTheme.DANGER
            hover -> UiTheme.ACCENT_DIM
            else -> UiTheme.BORDER
        }
        g.fill(x, y, right, y + 1, border)
        g.fill(x, bottom - 1, right, bottom, border)
        g.fill(x, y, x + 1, bottom, border)
        g.fill(right - 1, y, right, bottom, border)

        if (enabled && (active || style == Style.PRIMARY)) {
            g.fill(x, y, x + 2, bottom, UiTheme.ACCENT)
        }

        val tw = font.width(text)
        val tx = x + (w - tw) / 2
        val ty = y + (h - font.lineHeight) / 2
        val col = when {
            !enabled -> UiTheme.TEXT_DIM
            active || style == Style.PRIMARY -> UiTheme.ACCENT_SOFT
            style == Style.DANGER -> UiTheme.DANGER_TEXT
            else -> UiTheme.TEXT
        }
        g.text(font, Component.literal(text), tx, ty, col, false)
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (!visible || !enabled || button != 0 || !contains(mx, my)) return false
        onClick?.invoke()
        return true
    }
}

fun primaryButton(text: String, onClick: () -> Unit) =
    UiButton(text, onClick).also { it.style = UiButton.Style.PRIMARY }

fun dangerButton(text: String, onClick: () -> Unit) =
    UiButton(text, onClick).also { it.style = UiButton.Style.DANGER }

fun ghostButton(text: String, onClick: () -> Unit) =
    UiButton(text, onClick).also { it.style = UiButton.Style.GHOST }