package net.foxmediax.scriptfx.client.ui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/**
 * Модальный диалог поверх страницы (замена OverlayHost из Modern UI).
 * Типы: message / confirm / input.
 */
class UiDialog : UiWidget() {

    enum class Kind { NONE, MESSAGE, CONFIRM, INPUT }

    var kind: Kind = Kind.NONE
        private set

    private var title = ""
    private var body = ""
    private var onYes: (() -> Unit)? = null
    private var onNo: (() -> Unit)? = null
    private var onSubmit: ((String) -> Unit)? = null

    private val field = UiTextField(hint = "…")
    private val btnYes = UiButton("ОК") { handleYes() }
    private val btnNo = UiButton("Отмена") { handleNo() }

    val isOpen: Boolean get() = kind != Kind.NONE

    fun close() {
        kind = Kind.NONE
        onYes = null
        onNo = null
        onSubmit = null
        field.value = ""
    }

    fun message(title: String, body: String = "", onOk: (() -> Unit)? = null) {
        this.kind = Kind.MESSAGE
        this.title = title
        this.body = body
        this.onYes = onOk
        this.onNo = null
        this.onSubmit = null
        btnYes.text = "ОК"
    }

    fun confirm(
        title: String,
        body: String = "",
        yes: String = "Да",
        no: String = "Отмена",
        onYes: () -> Unit,
        onNo: (() -> Unit)? = null
    ) {
        this.kind = Kind.CONFIRM
        this.title = title
        this.body = body
        this.onYes = onYes
        this.onNo = onNo
        this.onSubmit = null
        btnYes.text = yes
        btnNo.text = no
    }

    fun input(
        title: String,
        hint: String = "",
        initial: String = "",
        onSubmit: (String) -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        this.kind = Kind.INPUT
        this.title = title
        this.body = ""
        this.onYes = null
        this.onNo = onCancel
        this.onSubmit = onSubmit
        field.hint = hint
        field.value = initial
        btnYes.text = "ОК"
        btnNo.text = "Отмена"
    }

    /** Вызвать из layout страницы на весь экран страницы. */
    fun layoutIn(pageX: Int, pageY: Int, pageW: Int, pageH: Int) {
        setBounds(pageX, pageY, pageW, pageH)
        if (!isOpen) return

        val boxW = pageW.coerceAtMost(420).coerceAtLeast(280)
        val boxH = when (kind) {
            Kind.INPUT -> 150
            Kind.CONFIRM -> 130
            else -> 110
        }
        val bx = pageX + (pageW - boxW) / 2
        val by = pageY + (pageH - boxH) / 2

        if (kind == Kind.INPUT) {
            field.setBounds(bx + 16, by + 48, boxW - 32, 26)
        }

        when (kind) {
            Kind.MESSAGE -> {
                btnYes.setBounds(bx + boxW - 100, by + boxH - 40, 84, 28)
            }
            Kind.CONFIRM, Kind.INPUT -> {
                btnNo.setBounds(bx + boxW - 200, by + boxH - 40, 84, 28)
                btnYes.setBounds(bx + boxW - 100, by + boxH - 40, 84, 28)
            }
            Kind.NONE -> {}
        }

        // store box for render
        this.boxX = bx
        this.boxY = by
        this.boxW = boxW
        this.boxH = boxH
    }

    private var boxX = 0
    private var boxY = 0
    private var boxW = 0
    private var boxH = 0

    private fun handleYes() {
        when (kind) {
            Kind.INPUT -> {
                val v = field.value
                val cb = onSubmit
                close()
                cb?.invoke(v)
            }
            else -> {
                val cb = onYes
                close()
                cb?.invoke()
            }
        }
    }

    private fun handleNo() {
        val cb = onNo
        close()
        cb?.invoke()
    }

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        if (!isOpen) return
        // dim
        g.fill(x, y, right, bottom, 0x99000000.toInt())
        // card
        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, UiTheme.BG_PANEL)
        g.fill(boxX, boxY, boxX + boxW, boxY + 1, UiTheme.ACCENT)
        g.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, UiTheme.BORDER)
        g.fill(boxX, boxY, boxX + 1, boxY + boxH, UiTheme.BORDER)
        g.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, UiTheme.BORDER)

        g.text(font, Component.literal(title), boxX + 16, boxY + 14, UiTheme.TEXT, false)
        if (body.isNotEmpty()) {
            g.text(font, Component.literal(body), boxX + 16, boxY + 34, UiTheme.TEXT_MUTED, false)
        }
        if (kind == Kind.INPUT) field.render(g, mouseX, mouseY, pt)
        btnYes.render(g, mouseX, mouseY, pt)
        if (kind == Kind.CONFIRM || kind == Kind.INPUT) {
            btnNo.render(g, mouseX, mouseY, pt)
        }
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (!isOpen) return false
        if (kind == Kind.INPUT && field.mouseClicked(mx, my, button)) return true
        if (btnYes.mouseClicked(mx, my, button)) return true
        if ((kind == Kind.CONFIRM || kind == Kind.INPUT) && btnNo.mouseClicked(mx, my, button)) return true
        // клик по dim — не закрываем (модальность)
        return true
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int): Boolean {
        if (!isOpen) return false
        field.mouseReleased(mx, my, button)
        btnYes.mouseReleased(mx, my, button)
        btnNo.mouseReleased(mx, my, button)
        return true
    }

    override fun keyPressed(key: Int, scan: Int, modifiers: Int): Boolean {
        if (!isOpen) return false
        // ESC
        if (key == 256) {
            handleNo()
            return true
        }
        // Enter
        if (key == 257 || key == 335) {
            handleYes()
            return true
        }
        if (kind == Kind.INPUT && field.keyPressed(key, scan, modifiers)) return true
        return true
    }

    override fun charTyped(codePoint: Int, modifiers: Int): Boolean {
        if (!isOpen) return false
        if (kind == Kind.INPUT && field.charTyped(codePoint, modifiers)) return true
        return true
    }
}