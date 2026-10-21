package net.foxmediax.scriptfx.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.foxmediax.scriptfx.ScriptFX
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen

/**
 * Пока CutsceneClient.hidePlayerHud (диалоговая катсцена / диалог):
 * options.hideGui = true → ванильный HUD (хотбар, хп, прицел…) скрыт,
 * чат рисуем поверх через этот слой.
 *
 * Диалоговое окно — Screen (Modern UI), hideGui на него не влияет.
 */
object DialogHudSupport {

    fun init() {
        HudElementRegistry.addLast(
            ScriptFX.id("dialog_force_chat"),
            HudElement { graphics, tickCounter ->
                renderChatIfNeeded(graphics, tickCounter)
            }
        )
    }

    private fun renderChatIfNeeded(graphics: GuiGraphicsExtractor, tickCounter: Any?) {
        if (!CutsceneClient.hidePlayerHud) return

        val mc = Minecraft.getInstance()
        if (!mc.options.hideGui) return
        // Окно чата (T) — ваниль сама рисует
        if (mc.screen is ChatScreen) return

        try {
            val chat = mc.gui.chat
            val mouseX = (mc.mouseHandler.xpos() * mc.window.guiScaledWidth / mc.window.width).toInt()
            val mouseY = (mc.mouseHandler.ypos() * mc.window.guiScaledHeight / mc.window.height).toInt()
            val tick = mc.gui.guiTicks

            if (!invokeChatRender(chat, graphics, tick, mouseX, mouseY)) {
                invokeChatRenderFallback(chat, graphics, tickCounter, mouseX, mouseY)
            }
        } catch (_: Throwable) {
            // не роняем кадр
        }
    }

    private fun invokeChatRender(
        chat: Any,
        graphics: GuiGraphicsExtractor,
        tick: Int,
        mouseX: Int,
        mouseY: Int
    ): Boolean {
        val gfxClass = graphics.javaClass

        // 1) render(GuiGraphicsExtractor, int, int, int)
        runCatching {
            val m = chat.javaClass.getMethod(
                "render",
                gfxClass,
                Integer.TYPE,
                Integer.TYPE,
                Integer.TYPE
            )
            m.invoke(chat, graphics, tick, mouseX, mouseY)
            return true
        }

        // 2) render(GuiGraphicsExtractor, int, int, int, boolean)
        runCatching {
            val m = chat.javaClass.getMethod(
                "render",
                gfxClass,
                Integer.TYPE,
                Integer.TYPE,
                Integer.TYPE,
                java.lang.Boolean.TYPE
            )
            m.invoke(chat, graphics, tick, mouseX, mouseY, false)
            return true
        }

        // 3) любой render, первый аргумент — тот же тип, что у graphics
        for (m in chat.javaClass.methods) {
            if (m.name != "render" || m.parameterCount < 1) continue
            if (!m.parameterTypes[0].isInstance(graphics)) continue
            runCatching {
                when (m.parameterCount) {
                    1 -> m.invoke(chat, graphics)
                    2 -> {
                        val p1 = m.parameterTypes[1]
                        when {
                            p1 == Integer.TYPE -> m.invoke(chat, graphics, tick)
                            else -> m.invoke(chat, graphics, tickCounterOrNull())
                        }
                    }
                    3 -> m.invoke(chat, graphics, tick, mouseX)
                    4 -> {
                        val last = m.parameterTypes[3]
                        if (last == java.lang.Boolean.TYPE) {
                            m.invoke(chat, graphics, tick, mouseX, mouseY, false)
                        } else {
                            m.invoke(chat, graphics, tick, mouseX, mouseY)
                        }
                    }
                    5 -> m.invoke(chat, graphics, tick, mouseX, mouseY, false)
                    else -> return@runCatching
                }
                return true
            }
        }
        return false
    }

    private fun invokeChatRenderFallback(
        chat: Any,
        graphics: GuiGraphicsExtractor,
        tickCounter: Any?,
        mouseX: Int,
        mouseY: Int
    ) {
        if (tickCounter == null) return
        for (m in chat.javaClass.methods) {
            if (m.name != "render" || m.parameterCount !in 2..5) continue
            if (!m.parameterTypes[0].isInstance(graphics)) continue
            runCatching {
                when (m.parameterCount) {
                    2 -> m.invoke(chat, graphics, tickCounter)
                    3 -> m.invoke(chat, graphics, tickCounter, mouseX)
                    4 -> m.invoke(chat, graphics, tickCounter, mouseX, mouseY)
                    5 -> m.invoke(chat, graphics, tickCounter, mouseX, mouseY, false)
                }
                return
            }
        }
    }

    private fun tickCounterOrNull(): Any? = null
}