package net.foxmediax.scriptfx.client.hud

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.foxmediax.scriptfx.hud.HudDefinition
import net.foxmediax.scriptfx.hud.HudRegistry
import net.foxmediax.scriptfx.network.HudPayload

@Environment(EnvType.CLIENT)
object HudRuntime {

    data class Active(
        val def: HudDefinition,
        var alpha: Float = 0f,
        var closing: Boolean = false,
        var animStartMs: Long = System.currentTimeMillis()
    )

    private val active = linkedMapOf<String, Active>()

    fun handlePayload(payload: HudPayload) {
        when (payload.action) {
            HudPayload.ON -> show(payload.hudId)
            HudPayload.OFF -> hide(payload.hudId)
            HudPayload.OFF_ALL -> hideAll()
        }
    }

    fun show(id: String) {
        var def = HudRegistry.get(id)
        if (def == null) {
            HudRegistry.reload()
            def = HudRegistry.get(id)
        }
        if (def == null) return
        active[id] = Active(def, alpha = 0f, closing = false, animStartMs = System.currentTimeMillis())
    }

    fun hide(id: String) {
        val a = active[id] ?: return
        a.closing = true
        a.animStartMs = System.currentTimeMillis()
    }

    fun hideAll() {
        active.keys.toList().forEach { hide(it) }
    }

    fun clear() {
        active.clear()
    }

    fun tick() {
        val now = System.currentTimeMillis()
        val remove = mutableListOf<String>()
        for ((id, a) in active) {
            val anim = if (a.closing) a.def.hideAnim else a.def.showAnim
            val dur = anim.durationMs.coerceAtLeast(1)
            val t = ((now - a.animStartMs).toFloat() / dur).coerceIn(0f, 1f)
            a.alpha = anim.from + (anim.to - anim.from) * ease(t, anim.type)
            if (a.closing && t >= 1f) remove += id
            if (!a.closing && t >= 1f) a.alpha = anim.to
        }
        remove.forEach { active.remove(it) }
    }

    private fun ease(t: Float, type: String): Float = when (type) {
        "slide", "scale" -> t * t * (3f - 2f * t)
        else -> t
    }

    fun visible(): Collection<Active> = active.values
}