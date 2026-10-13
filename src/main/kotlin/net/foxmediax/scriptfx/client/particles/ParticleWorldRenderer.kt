package net.foxmediax.scriptfx.client.particles

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog

/**
 * Хук мирового рендера частиц (Fabric 26.1).
 *
 * Кастомные билборды в 26.1 через getBuffer дают IllegalArgumentException,
 * поэтому здесь только регистрация события. Визуал частиц — в ParticleRuntime
 * (vanilla DustParticleOptions / END_ROD).
 */
object ParticleWorldRenderer {

    private var registered = false

    fun init() {
        if (registered) return
        registered = true
        try {
            LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register { _ ->
                // no-op: отрисовка через ParticleRuntime
            }
            ScriptFXLog.info("ParticleWorldRenderer: registered (vanilla fallback)")
        } catch (t: Throwable) {
            ScriptFXLog.warn("ParticleWorldRenderer: LevelRenderEvents unavailable: ${t.message}")
        }
    }
}