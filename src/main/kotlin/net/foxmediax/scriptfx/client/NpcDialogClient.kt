package net.foxmediax.scriptfx.client

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.foxmediax.scriptfx.config.ScriptFXConfig
import net.foxmediax.scriptfx.network.NpcDialogChoicePayload
import net.foxmediax.scriptfx.network.NpcDialogOpenPayload
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity

object NpcDialogClient {
    private var screen: NpcDialogScreen? = null
    private var focusEntityId = -1
    private var outlined: Entity? = null

    val isActive: Boolean get() = screen != null   // используют миксины

    fun open(payload: NpcDialogOpenPayload) {
        val mc = Minecraft.getInstance()
        val buttons = listOf(
            payload.button1, payload.button2, payload.button3, payload.button4, payload.button5
        ).filter { it.isNotBlank() }

        focusEntityId = payload.npcEntityId
        val entity = findFocus()
        val name = entity?.customName?.string?.takeIf { it.isNotBlank() } ?: payload.npcId

        val s = NpcDialogScreen(name, payload.text, buttons) { choose(it) }
        screen = s
        mc.setScreen(s)
        applyOutline()
    }

    fun close() {
        val s = screen ?: return
        screen = null
        clearOutline()
        focusEntityId = -1
        val mc = Minecraft.getInstance()
        if (mc.screen === s) mc.setScreen(null)
    }

    /** Если экран кто-то закрыл, пока диалог активен, открываем его снова. */
    fun tick() {
        val s = screen ?: return
        val mc = Minecraft.getInstance()
        if (mc.screen == null) mc.setScreen(s)
        applyOutline()
    }

    private fun findFocus(): Entity? =
        if (focusEntityId >= 0) Minecraft.getInstance().level?.getEntity(focusEntityId) else null

    /** Белая обводка: ванильное свечение, выставленное только на клиенте. */
    private fun applyOutline() {
        val e = findFocus()
        if (e == null || !ScriptFXConfig.npcOutline) {
            clearOutline()
            return
        }
        if (outlined !== e) {
            clearOutline()
            outlined = e
        }
        e.setGlowingTag(true)
    }

    private fun clearOutline() {
        outlined?.setGlowingTag(false)
        outlined = null
    }

    private fun choose(button: Int) {
        if (ClientPlayNetworking.canSend(NpcDialogChoicePayload.TYPE)) {
            ClientPlayNetworking.send(NpcDialogChoicePayload(button))
        }
        close()
    }
}