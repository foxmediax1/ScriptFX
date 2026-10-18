package net.foxmediax.scriptfx.npc

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.foxmediax.scriptfx.mixin.client.WindowAccessor
import net.foxmediax.scriptfx.network.NpcInteractKeyPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.lwjgl.glfw.GLFW

object NpcInteractClient {
    private const val REACH = 4.0
    private var alpha = 0f
    private var shownKeyLabel = "X"
    private var keyWasDown = false
    /** Какую физ. клавишу слушаем (по умолчанию X). Меняется с сервера через begin на клиенте не нужно — шлём имя нажатой. */
    private var listenGlfwKey: Int = GLFW.GLFW_KEY_X
    private var listenKeyName: String = "X"

    /** Буквы → GLFW (латиница; на RU раскладке физика та же). */
    private val KEY_MAP: Map<String, Int> = mapOf(
        "A" to GLFW.GLFW_KEY_A, "B" to GLFW.GLFW_KEY_B, "C" to GLFW.GLFW_KEY_C,
        "D" to GLFW.GLFW_KEY_D, "E" to GLFW.GLFW_KEY_E, "F" to GLFW.GLFW_KEY_F,
        "G" to GLFW.GLFW_KEY_G, "H" to GLFW.GLFW_KEY_H, "I" to GLFW.GLFW_KEY_I,
        "J" to GLFW.GLFW_KEY_J, "K" to GLFW.GLFW_KEY_K, "L" to GLFW.GLFW_KEY_L,
        "M" to GLFW.GLFW_KEY_M, "N" to GLFW.GLFW_KEY_N, "O" to GLFW.GLFW_KEY_O,
        "P" to GLFW.GLFW_KEY_P, "Q" to GLFW.GLFW_KEY_Q, "R" to GLFW.GLFW_KEY_R,
        "S" to GLFW.GLFW_KEY_S, "T" to GLFW.GLFW_KEY_T, "U" to GLFW.GLFW_KEY_U,
        "V" to GLFW.GLFW_KEY_V, "W" to GLFW.GLFW_KEY_W, "X" to GLFW.GLFW_KEY_X,
        "Y" to GLFW.GLFW_KEY_Y, "Z" to GLFW.GLFW_KEY_Z,
        "SPACE" to GLFW.GLFW_KEY_SPACE, "ENTER" to GLFW.GLFW_KEY_ENTER,
        "F" to GLFW.GLFW_KEY_F // уже есть
    )

    fun nearestInteractNpc(): ScriptNpcEntity? {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return null
        val level = mc.level ?: return null
        return level.entitiesForRendering()
            .filterIsInstance<ScriptNpcEntity>()
            .filter { it.distanceTo(player) <= REACH }
            .filter { it.currentMode() == NpcMode.INTERACT }
            .minByOrNull { it.distanceTo(player) }
    }

    /** Вызывать, когда скрипт ждёт клавишу (опционально с сервера позже). Пока — с локального default. */
    fun setListenKey(keyName: String) {
        val upper = keyName.uppercase()
        listenKeyName = upper
        listenGlfwKey = KEY_MAP[upper] ?: GLFW.GLFW_KEY_X
        if (KEY_MAP[upper] == null) listenKeyName = "X"
    }

    fun tick() {
        val near = nearestInteractNpc() != null
        val target = if (near) 1f else 0f
        alpha += (target - alpha) * 0.15f
        if (alpha < 0.01f) alpha = 0f
        if (alpha > 0.99f) alpha = 1f

        shownKeyLabel = layoutLetter(listenGlfwKey)

        val down = isKeyDown(listenGlfwKey)
        if (near && down && !keyWasDown) {
            if (ClientPlayNetworking.canSend(NpcInteractKeyPayload.TYPE)) {
                ClientPlayNetworking.send(NpcInteractKeyPayload(listenKeyName))
            }
        }
        keyWasDown = down
    }

    private fun windowHandle(): Long =
        (Minecraft.getInstance().window as WindowAccessor).handle

    private fun layoutLetter(glfwKey: Int): String {
        val name = GLFW.glfwGetKeyName(glfwKey, 0) ?: return listenKeyName
        return name.uppercase()
    }

    private fun isKeyDown(glfwKey: Int): Boolean =
        GLFW.glfwGetKey(windowHandle(), glfwKey) == GLFW.GLFW_PRESS

    fun render(graphics: GuiGraphicsExtractor) {
        if (alpha <= 0.01f) return
        val mc = Minecraft.getInstance()
        val line = "Взаимодействовать: клавиша \"$shownKeyLabel\""
        val font = mc.font
        val w = font.width(line)
        val x = 12
        val y = mc.window.guiScaledHeight - 40
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        if (a < 8) return
        graphics.fill(x - 4, y - 4, x + w + 4, y + 12, a shl 24)
        graphics.text(font, line, x, y, (a shl 24) or 0xFFFFFF, false)
    }
}