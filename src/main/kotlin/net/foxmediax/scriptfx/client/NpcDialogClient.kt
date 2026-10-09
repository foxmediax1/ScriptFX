package net.foxmediax.scriptfx.client

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.foxmediax.scriptfx.client.mui.MuiScreens
import net.foxmediax.scriptfx.client.mui.NpcDialogModel
import net.foxmediax.scriptfx.config.ScriptFXConfig
import net.foxmediax.scriptfx.network.NpcDialogChoicePayload
import net.foxmediax.scriptfx.network.NpcDialogOpenPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.Entity

object NpcDialogClient {
    private var model: NpcDialogModel? = null
    private var screen: Screen? = null
    private var focusEntityId = -1
    private var outlined: Entity? = null

    val isActive: Boolean get() = model != null   // используют миксины

    fun open(payload: NpcDialogOpenPayload) {
        val buttons = listOf(
            payload.button1, payload.button2, payload.button3, payload.button4, payload.button5
        ).filter { it.isNotBlank() }

        focusEntityId = payload.npcEntityId
        val name = findFocus()?.customName?.string?.takeIf { it.isNotBlank() } ?: payload.npcId

        val m = NpcDialogModel(name, payload.text, buttons) { choose(it) }
        model = m
        show(m)
        applyOutline()
    }

    fun close() {
        if (model == null) return
        val s = screen
        model = null
        screen = null
        clearOutline()
        focusEntityId = -1
        val mc = Minecraft.getInstance()
        if (mc.screen === s) mc.setScreen(null)
    }

    /** Если экран кто-то закрыл, пока диалог активен, создаём его заново (модель та же). */
    fun tick() {
        val m = model ?: return
        if (Minecraft.getInstance().screen == null) show(m)
        applyOutline()
    }

    private fun show(m: NpcDialogModel) {
        val s = MuiScreens.createNpcDialog(m)
        screen = s
        Minecraft.getInstance().setScreen(s)
    }

    private fun findFocus(): Entity? =
        if (focusEntityId >= 0) Minecraft.getInstance().level?.getEntity(focusEntityId) else null

    private fun applyOutline() {
        val e = findFocus()
        if (e == null || !ScriptFXConfig.npcOutline) { clearOutline(); return }
        if (outlined !== e) { clearOutline(); outlined = e }
        e.setGlowingTag(true)
    }

    private fun clearOutline() {
        outlined?.setGlowingTag(false)
        outlined = null
    }

    /** Вызывается из UI-потока Modern UI, поэтому сеть и смена экрана идут через execute. */
    private fun choose(button: Int) {
        Minecraft.getInstance().execute {
            if (ClientPlayNetworking.canSend(NpcDialogChoicePayload.TYPE)) {
                ClientPlayNetworking.send(NpcDialogChoicePayload(button))
            }
            close()
        }
    }
}