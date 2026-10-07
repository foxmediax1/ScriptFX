package net.foxmediax.scriptfx.npc

import net.foxmediax.scriptfx.mixin.client.WindowAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

object NpcInteractClient {
    private const val REACH = 4.0
    private var alpha = 0f
    private var shownKeyLabel = "X"

    fun nearestInteractNpc(): ScriptNpcEntity? {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return null
        val level = mc.level ?: return null
        return level.entitiesForRendering()
            .filterIsInstance<ScriptNpcEntity>()
            .filter { it.distanceTo(player) <= REACH }
            .minByOrNull { it.distanceTo(player) }
    }

    fun tick() {
        val npc = nearestInteractNpc()
        val target = if (npc != null) 1f else 0f
        alpha += (target - alpha) * 0.15f
        if (alpha < 0.01f) alpha = 0f
        if (alpha > 0.99f) alpha = 1f
        shownKeyLabel = layoutLetterForPhysicalX()
    }

    private fun layoutLetterForPhysicalX(): String {
        val handle = (Minecraft.getInstance().window as WindowAccessor).handle
        val name = GLFW.glfwGetKeyName(GLFW.GLFW_KEY_X, 0) ?: return "X"
        return name.uppercase()
    }

    fun render(graphics: GuiGraphicsExtractor) {
        if (alpha <= 0.01f) return
        val mc = Minecraft.getInstance()
        val text = Component.literal("Взаимодействовать: клавиша \"$shownKeyLabel\"")
        val font = mc.font
        val w = font.width(text)
        val x = 12
        val y = mc.window.guiScaledHeight - 40
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        graphics.fill(x - 4, y - 4, x + w + 4, y + 12, (a shl 24))
        // как в CenterMessageOverlay: text(), не drawString
        graphics.text(font, text.string, x, y, (a shl 24) or 0xFFFFFF, false)
    }

    /** true, если физ. клавиша X зажата */
    fun isKeyXDown(): Boolean {
        val handle = (Minecraft.getInstance().window as WindowAccessor).handle
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_X) == GLFW.GLFW_PRESS
    }
}