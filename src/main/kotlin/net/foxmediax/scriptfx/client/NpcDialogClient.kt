package net.foxmediax.scriptfx.client

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.foxmediax.scriptfx.config.ScriptFXConfig
import net.foxmediax.scriptfx.network.NpcDialogChoicePayload
import net.foxmediax.scriptfx.network.NpcDialogOpenPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.lwjgl.glfw.GLFW

object NpcDialogClient {
    private var active = false
    private var fullText = ""
    private var buttons: List<String> = emptyList()
    private var shownAt = 0L
    private var panelAlpha = 0f
    private var mouseWasDown = false

    val isActive: Boolean get() = active

    fun open(payload: NpcDialogOpenPayload) {
        fullText = payload.text
        buttons = listOf(
            payload.button1, payload.button2, payload.button3,
            payload.button4, payload.button5
        ).filter { it.isNotBlank() }
        shownAt = System.currentTimeMillis()
        panelAlpha = 0f
        active = true
        mouseWasDown = false
        // курсор
        val mc = Minecraft.getInstance()
        GLFW.glfwSetInputMode((mc.window as net.foxmediax.scriptfx.mixin.client.WindowAccessor).handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL)
    }

    fun close() {
        if (!active) return
        active = false
        val mc = Minecraft.getInstance()
        GLFW.glfwSetInputMode((mc.window as net.foxmediax.scriptfx.mixin.client.WindowAccessor).handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED)
    }

    fun tick() {
        if (!active) return
        val mc = Minecraft.getInstance()
        val target = 1f
        if (mc.screen != null) {
            mc.setScreen(null)
        }
        val fadeMs = ScriptFXConfig.dialogFadeMs.coerceAtLeast(1).toFloat()
        val t = ((System.currentTimeMillis() - shownAt) / fadeMs).coerceIn(0f, 1f)
        panelAlpha += (target * t - panelAlpha) * 0.25f
        if (panelAlpha > 0.99f) panelAlpha = 1f
    }

    fun render(graphics: GuiGraphicsExtractor) {
        if (!active || panelAlpha < 0.02f) return
        val mc = Minecraft.getInstance()
        val font = mc.font
        val sw = mc.window.guiScaledWidth
        val sh = mc.window.guiScaledHeight
        val a = (panelAlpha * 220).toInt().coerceIn(0, 255)

        // фон затемнение
        graphics.fill(0, 0, sw, sh, (a / 2) shl 24)

        // текстовая панель слева
        val boxW = (sw * 0.38).toInt().coerceIn(160, 320)
        val boxH = (sh * 0.28).toInt().coerceIn(80, 160)
        val leftX = 24
        val leftY = sh / 2 - boxH / 2
        graphics.fill(leftX, leftY, leftX + boxW, leftY + boxH, (a shl 24) or 0x101018)

        val visible = typewriterText()
        val lines = wrap(font, visible, boxW - 16)
        lines.take(6).forEachIndexed { i, line ->
            graphics.text(font, line, leftX + 8, leftY + 8 + i * (font.lineHeight + 2),
                (a shl 24) or 0xFFFFFF, false)
        }

        // кнопки справа
        val btnW = (sw * 0.28).toInt().coerceIn(120, 240)
        val btnH = 18
        val gap = 6
        val totalH = buttons.size * (btnH + gap) - gap
        var by = sh / 2 - totalH / 2
        val bx = sw - btnW - 24

        val mx = (mc.mouseHandler.xpos() * sw / mc.window.width).toInt()
        val my = (mc.mouseHandler.ypos() * sh / mc.window.height).toInt()
        val mouseDown = GLFW.glfwGetMouseButton((mc.window as net.foxmediax.scriptfx.mixin.client.WindowAccessor).handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS

        buttons.forEachIndexed { idx, label ->
            val hovered = mx in bx until (bx + btnW) && my in by until (by + btnH)
            val bg = if (hovered) 0x304060 else 0x181828
            graphics.fill(bx, by, bx + btnW, by + btnH, (a shl 24) or bg)
            graphics.text(font, label, bx + 6, by + 5, (a shl 24) or 0xFFFFFF, false)

            if (hovered && mouseDown && !mouseWasDown && panelAlpha > 0.8f) {
                choose(idx + 1)
            }
            by += btnH + gap
        }
        mouseWasDown = mouseDown
    }

    private fun choose(button: Int) {
        if (ClientPlayNetworking.canSend(NpcDialogChoicePayload.TYPE)) {
            ClientPlayNetworking.send(NpcDialogChoicePayload(button))
        }
        close()
    }

    private fun typewriterText(): String {
        val perChar = ScriptFXConfig.dialogTypewriterMs.coerceAtLeast(1)
        val n = ((System.currentTimeMillis() - shownAt) / perChar).toInt().coerceIn(0, fullText.length)
        return fullText.take(n)
    }

    private fun wrap(font: net.minecraft.client.gui.Font, text: String, maxWidth: Int): List<String> {
        if (text.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var cur = StringBuilder()
        for (word in text.split(" ")) {
            val cand = if (cur.isEmpty()) word else "$cur $word"
            if (font.width(cand) > maxWidth && cur.isNotEmpty()) {
                lines.add(cur.toString())
                cur = StringBuilder(word)
            } else cur = StringBuilder(cand)
        }
        if (cur.isNotEmpty()) lines.add(cur.toString())
        return lines
    }
}