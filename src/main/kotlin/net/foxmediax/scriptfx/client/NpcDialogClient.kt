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
    private var closing = false

    val isActive: Boolean
        get() = model != null

    /**
     * Блокировать WASD/мышь только пока идёт подлёт или открыт диалог.
     * Во время отлёта (closing) и после — не блокировать.
     */
    val locksInput: Boolean
        get() {
            if (model == null || closing) return false
            return screen != null || CutsceneClient.dialogCam
        }

    fun open(payload: NpcDialogOpenPayload) {
        val buttons = listOf(
            payload.button1,
            payload.button2,
            payload.button3,
            payload.button4,
            payload.button5
        ).filter { it.isNotBlank() }

        focusEntityId = payload.npcEntityId
        closing = false

        val focus = findFocus()
        val name = focus?.customName?.string?.takeIf { it.isNotBlank() } ?: payload.npcId

        val m = NpcDialogModel(name, payload.text, buttons) { choose(it) }
        model = m
        applyOutline()

        if (focus != null) {
            CutsceneClient.startDialogApproach(
                npcX = focus.x,
                npcY = focus.eyeY,
                npcZ = focus.z,
                durationTicks = 12,
                targetFov = 40f,
                distance = 1.55
            ) {
                if (model === m && !closing) {
                    Minecraft.getInstance().execute { show(m) }
                }
            }
        } else {
            show(m)
        }
    }

    fun close() {
        if (model == null || closing) return
        closing = true   // ← сразу locksInput = false → мышь снова живая

        val s = screen
        screen = null
        val mc = Minecraft.getInstance()
        if (mc.screen === s) mc.setScreen(null)
        clearOutline()

        if (!CutsceneClient.dialogCam && !CutsceneClient.dialogFovActive) {
            CutsceneClient.clearDialogCamera()
            model = null
            focusEntityId = -1
            closing = false
            return
        }

        CutsceneClient.startDialogReturn(durationTicks = 12) {
            model = null
            focusEntityId = -1
            closing = false
        }
    }

    /** Закрыть сразу, без анимации отлёта. */
    fun forceClose() {
        closing = true
        val s = screen
        screen = null
        val mc = Minecraft.getInstance()
        if (mc.screen === s) {
            mc.setScreen(null)
        }
        clearOutline()
        CutsceneClient.clearDialogCamera()
        model = null
        focusEntityId = -1
        closing = false
    }

    fun tick() {
        val m = model ?: return
        if (closing) return

        val mc = Minecraft.getInstance()

        // ещё летим к NPC — экран не трогаем
        if (CutsceneClient.dialogCam && screen == null) {
            applyOutline()
            return
        }

        // экран закрыли извне
        if (mc.screen == null && screen != null) {
            close()
            return
        }

        // диалог активен, экрана нет, камера свободна — показать снова
        if (mc.screen == null && screen == null && !CutsceneClient.dialogCam) {
            show(m)
        }

        applyOutline()
    }

    private fun show(m: NpcDialogModel) {
        val s = MuiScreens.createNpcDialog(m)
        screen = s
        Minecraft.getInstance().setScreen(s)
    }

    private fun findFocus(): Entity? {
        if (focusEntityId < 0) return null
        return Minecraft.getInstance().level?.getEntity(focusEntityId)
    }

    private fun applyOutline() {
        // Обводка через glowing на GeckoLib ненадёжна — отключена.
        // Включить позже через ScriptFXConfig.npcOutline + кастомный outline-pass.
        if (!ScriptFXConfig.npcOutline) {
            clearOutline()
            return
        }
        val e = findFocus()
        if (e == null) {
            clearOutline()
            return
        }
        if (outlined !== e) {
            clearOutline()
            outlined = e
        }
        try {
            e.setGlowingTag(true)
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun clearOutline() {
        try {
            outlined?.setGlowingTag(false)
        } catch (_: Throwable) {
            // ignore
        }
        outlined = null
    }

    private fun choose(button: Int) {
        Minecraft.getInstance().execute {
            if (ClientPlayNetworking.canSend(NpcDialogChoicePayload.TYPE)) {
                ClientPlayNetworking.send(NpcDialogChoicePayload(button))
            }
            close()
        }
    }

}