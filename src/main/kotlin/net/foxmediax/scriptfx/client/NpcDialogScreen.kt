package net.foxmediax.scriptfx.client

import net.foxmediax.scriptfx.client.ui.UiButton
import net.foxmediax.scriptfx.client.ui.UiLabel
import net.foxmediax.scriptfx.client.ui.UiPanel
import net.foxmediax.scriptfx.client.ui.UiTheme
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class NpcDialogScreen(
    private val model: NpcDialogModel
) : Screen(Component.literal(model.npcName)) {

    private val root = UiPanel(0)
    private val body = UiLabel("", UiTheme.TEXT)
    private val nameLabel = UiLabel(model.npcName, UiTheme.ACCENT)
    private val buttons = mutableListOf<UiButton>()

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        super.init()
        layoutUi()
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        layoutUi()
    }

    private fun layoutUi() {
        root.clearChildren()
        buttons.clear()

        val panelW = (width * 0.55f).toInt().coerceIn(320, 520)
        val panelH = 160 + model.buttons.size * 30
        val px = (width - panelW) / 2
        val py = height - panelH - 40

        root.setBounds(0, 0, width, height)

        val panel = UiPanel(0xE016161F.toInt())
        panel.setBounds(px, py, panelW, panelH)
        root.add(panel)

        nameLabel.setBounds(px + 16, py + 12, panelW - 32, 16)
        root.add(nameLabel)

        body.setBounds(px + 16, py + 36, panelW - 32, 70)
        root.add(body)

        model.buttons.forEachIndexed { i, label ->
            val btn = UiButton(label) {
                if (model.picked) return@UiButton
                if (!model.isTextComplete()) {
                    model.skipTypewriter()
                    return@UiButton
                }
                model.picked = true
                model.onChoose(i)
            }
            btn.setBounds(px + 16, py + 110 + i * 30, panelW - 32, 26)
            buttons += btn
            root.add(btn)
        }
    }

    override fun extractBackground(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        // полупрозрачный фон
        val a = (model.fade() * 0x60).toInt().coerceIn(0, 0x60)
        g.fill(0, 0, width, height, (a shl 24))
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val n = model.revealedChars()
        body.text = model.bodyText.take(n)
        if (n >= model.bodyText.length) model.textDone = true
        root.render(g, mouseX, mouseY, partialTick)
        super.extractRenderState(g, mouseX, mouseY, partialTick)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()
        if (!model.isTextComplete()) {
            model.skipTypewriter()
            return true
        }
        if (root.mouseClicked(mx, my, event.button())) return true
        return super.mouseClicked(event, doubleClick)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_SPACE || event.key() == GLFW.GLFW_KEY_ENTER) {
            if (!model.isTextComplete()) {
                model.skipTypewriter()
                return true
            }
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            // закрытие через сервер/клиент close — не глотаем, если нужно
            return true
        }
        return super.keyPressed(event)
    }
}