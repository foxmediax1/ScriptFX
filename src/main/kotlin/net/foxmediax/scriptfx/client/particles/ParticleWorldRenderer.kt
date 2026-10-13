package net.foxmediax.scriptfx.client.particles

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog

/**
 * Хук мирового рендера частиц (Fabric / Minecraft 26.1).
 *
 * Кастомный getBuffer(PARTICLE) / билборды в 26.1 падают с IllegalArgumentException,
 * поэтому здесь только регистрация события.
 * Симуляция частиц — в ParticleRuntime; визуал пока через vanilla-фолбэк
 * (см. ParticleRuntime / Dust / END_ROD), либо отдельный RenderType позже.
 */
object ParticleWorldRenderer {

    private var registered = false

    fun init() {
        if (registered) return
        registered = true
        try {
            LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register { _ ->
                // no-op: кастомный batch частиц в 26.1 нестабилен
            }
            ScriptFXLog.info("ParticleWorldRenderer: registered (vanilla fallback)")
        } catch (t: Throwable) {
            ScriptFXLog.warn("ParticleWorldRenderer: LevelRenderEvents unavailable: ${t.message}")
        }
    }
}