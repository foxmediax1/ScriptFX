package net.foxmediax.scriptfx.client.vfx

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.loader.api.FabricLoader
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.resources.Identifier

@Environment(EnvType.CLIENT)
object VfxBridge {

    val available: Boolean
        get() = FabricLoader.getInstance().isModLoaded("vfxweaver")

    fun play(
        effectId: String,
        ticks: Int = 20,
        params: Map<String, Float> = emptyMap()
    ) {
        if (!available) {
            ScriptFXLog.warn("VFXWeaver не загружен — пропуск '$effectId'")
            return
        }
        val id = toId(effectId)
        try {
            val api = Class.forName("dev.vfxweaver.api.VFXAPI")
            val candidates = api.methods.filter { it.name == "playEffect" }

            // Лог один раз — чтобы видеть реальные сигнатуры
            if (candidates.isNotEmpty()) {
                candidates.forEach { m ->
                    val sig = m.parameterTypes.joinToString { it.simpleName }
                    ScriptFXLog.info("VFXAPI.playEffect($sig)")
                }
            }

            val boxed: Map<String, Any> = HashMap(params.mapValues { it.value })
            var ok = false

            for (m in candidates) {
                try {
                    when (m.parameterCount) {
                        2 -> m.invoke(null, id, ticks)
                        3 -> m.invoke(null, id, ticks, boxed)
                        4 -> m.invoke(null, id, ticks, boxed, null)
                        5 -> {
                            // Часто: (Identifier, int, Map, Object, Object)
                            // или (Identifier, int, float, Map, Object)
                            val types = m.parameterTypes
                            if (types.size == 5 && types[2] == Float::class.javaPrimitiveType) {
                                m.invoke(null, id, ticks, 1f, boxed, null)
                            } else {
                                m.invoke(null, id, ticks, boxed, null, null)
                            }
                        }
                        else -> continue
                    }
                    ok = true
                    ScriptFXLog.info("VFX play OK: $effectId via ${m.parameterCount} args")
                    break
                } catch (e: IllegalArgumentException) {
                    // пробуем следующий overload
                } catch (e: java.lang.reflect.InvocationTargetException) {
                    ScriptFXLog.warn("VFX play invoke error: ${e.cause?.message ?: e.message}")
                }
            }

            if (!ok) {
                ScriptFXLog.warn("VFX play('$effectId'): ни один overload не подошёл")
            }
        } catch (t: Throwable) {
            ScriptFXLog.warn("VFX play('$effectId') failed: ${t.message}")
            t.printStackTrace()
        }
    }

    fun stop(effectId: String) {
        if (!available) return
        val id = toId(effectId)
        try {
            val api = Class.forName("dev.vfxweaver.api.VFXAPI")
            // на клиенте может быть stopEffect / sendStop — пробуем оба
            val stop = api.methods.firstOrNull {
                (it.name == "stopEffect" || it.name == "sendStop" || it.name == "stop") &&
                        it.parameterCount in 1..2
            } ?: return
            if (stop.parameterCount == 1) stop.invoke(null, id)
            else stop.invoke(null, id, null)
        } catch (t: Throwable) {
            ScriptFXLog.warn("VFX stop failed: ${t.message}")
        }
    }

    fun shake(amplitude: Float = 0.25f, ticks: Int = 20) =
        play(
            "camera_shake",
            ticks,
            mapOf("amplitude_x" to amplitude, "amplitude_y" to amplitude)
        )

    fun roll(ticks: Int = 40) = play("camera_roll", ticks)
    fun letterbox(ticks: Int = 60) = play("letterbox", ticks)
    fun flash(ticks: Int = 12) = play("screen_flash", ticks)

    fun handlePayload(action: String, effectId: String, ticks: Int, p0: Float, p1: Float) {
        when (action) {
            "stop" -> stop(effectId)
            "shake" -> shake(if (p0 > 0f) p0 else 0.25f, ticks)
            "roll" -> roll(ticks)
            "letterbox" -> letterbox(ticks)
            "flash" -> flash(ticks)
            else -> {
                val params = mutableMapOf<String, Float>()
                if (p0 != 0f) params["amplitude_x"] = p0
                if (p1 != 0f) params["amplitude_y"] = p1
                play(effectId.ifBlank { "camera_shake" }, ticks, params)
            }
        }
    }

    private fun toId(raw: String): Identifier {
        val clean = raw.trim().removeSurrounding("\"")
        return if (clean.contains(':')) Identifier.parse(clean)
        else Identifier.fromNamespaceAndPath("vfxweaver", clean)
    }
}