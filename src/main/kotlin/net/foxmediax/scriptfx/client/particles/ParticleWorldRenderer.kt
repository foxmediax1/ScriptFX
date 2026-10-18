package net.foxmediax.scriptfx.client.particles

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog

object ParticleWorldRenderer {

    private var registered = false

    fun init() {
        if (registered) return
        registered = true
        try {
            LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register { _ ->
                // визуал — ParticleRuntime.emitVanilla
            }
            ScriptFXLog.info("ParticleWorldRenderer: registered (vanilla via ParticleRuntime)")
        } catch (t: Throwable) {
            ScriptFXLog.warn("ParticleWorldRenderer: LevelRenderEvents unavailable: ${t.message}")
        }
    }
}