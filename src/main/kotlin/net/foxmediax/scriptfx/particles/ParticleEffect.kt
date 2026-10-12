package net.foxmediax.scriptfx.particles

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class ParticleEffect(
    val identifier: String,
    val material: String = "particles_alpha",
    val texture: String = "minecraft:textures/particle/particles",
    val spawnRate: Float = 10f,
    val maxParticles: Int = 200,
    val lifetimeMode: LifetimeMode = LifetimeMode.ONCE,
    val activeTime: Float = 1f,
    val shape: EmitterShape = EmitterShape.Point,
    val particleLifetime: Float = 1f,
    val lifetimeExpr: String? = null,
    val initialSpeed: Float = 0f,
    val speedExpr: String? = null,
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val accelExprX: String? = null,
    val accelExprY: String? = null,
    val accelExprZ: String? = null,
    val sizeX: Float = 0.2f,
    val sizeY: Float = 0.2f,
    val sizeExprX: String? = null,
    val sizeExprY: String? = null,
    val uvX: Float = 0f,
    val uvY: Float = 0f,
    val uvW: Float = 8f,
    val uvH: Float = 8f,
    val texW: Float = 128f,
    val texH: Float = 128f,
    val colorR: Float = 1f,
    val colorG: Float = 1f,
    val colorB: Float = 1f,
    val colorA: Float = 1f,
    val flipbook: Flipbook? = null,
    val rateMode: RateMode = RateMode.STEADY
) {
    enum class LifetimeMode { ONCE, LOOPING }
    enum class RateMode { STEADY, INSTANT }

    sealed class EmitterShape {
        data object Point : EmitterShape()
        data class Box(
            val ox: Float, val oy: Float, val oz: Float,
            val hx: Float, val hy: Float, val hz: Float
        ) : EmitterShape()
        data class Sphere(
            val ox: Float, val oy: Float, val oz: Float,
            val radius: Float
        ) : EmitterShape()
        data class Disc(
            val ox: Float, val oy: Float, val oz: Float,
            val radius: Float
        ) : EmitterShape()
    }

    fun sampleSpawnOffset(rng: Random): Triple<Float, Float, Float> = when (val s = shape) {
        is EmitterShape.Point -> Triple(0f, 0f, 0f)
        is EmitterShape.Box -> Triple(
            s.ox + (rng.nextFloat() * 2f - 1f) * s.hx,
            s.oy + (rng.nextFloat() * 2f - 1f) * s.hy,
            s.oz + (rng.nextFloat() * 2f - 1f) * s.hz
        )
        is EmitterShape.Sphere -> {
            val theta = rng.nextFloat() * (Math.PI * 2).toFloat()
            val phi = rng.nextFloat() * Math.PI.toFloat()
            val r = rng.nextFloat() * s.radius
            Triple(
                s.ox + r * sin(phi) * cos(theta),
                s.oy + r * cos(phi),
                s.oz + r * sin(phi) * sin(theta)
            )
        }
        is EmitterShape.Disc -> {
            val angle = rng.nextFloat() * (Math.PI * 2).toFloat()
            val r = sqrt(rng.nextFloat()) * s.radius
            Triple(
                s.ox + r * cos(angle),
                s.oy,
                s.oz + r * sin(angle)
            )
        }
    }

    companion object {
        fun parse(jsonText: String): ParticleEffect? {
            return runCatching {
                val root = JsonParser.parseString(jsonText).asJsonObject
                val effect = root.getAsJsonObject("particle_effect") ?: return null
                val desc = effect.getAsJsonObject("description") ?: return null
                val id = desc.get("identifier")?.asString ?: return null
                val render = desc.getAsJsonObject("basic_render_parameters")
                val material = render?.get("material")?.asString ?: "particles_alpha"
                val texture = render?.get("texture")?.asString
                    ?: "minecraft:textures/particle/particles"

                val comps = effect.getAsJsonObject("components") ?: JsonObject()

                var spawnRate = 10f
                var maxParticles = 200
                var rateMode = RateMode.STEADY
                comps.getAsJsonObject("minecraft:emitter_rate_steady")?.let {
                    spawnRate = num(it, "spawn_rate", 10f)
                    maxParticles = num(it, "max_particles", 200f).toInt()
                    rateMode = RateMode.STEADY
                }
                comps.getAsJsonObject("minecraft:emitter_rate_instant")?.let {
                    spawnRate = num(it, "particle_count", 10f)
                    maxParticles = spawnRate.toInt().coerceAtLeast(1)
                    rateMode = RateMode.INSTANT
                }

                var lifetimeMode = LifetimeMode.ONCE
                var activeTime = 1f
                comps.getAsJsonObject("minecraft:emitter_lifetime_once")?.let {
                    activeTime = num(it, "active_time", 1f)
                    lifetimeMode = LifetimeMode.ONCE
                }
                comps.getAsJsonObject("minecraft:emitter_lifetime_looping")?.let {
                    activeTime = num(it, "active_time", 1f)
                    lifetimeMode = LifetimeMode.LOOPING
                }

                var shape: EmitterShape = EmitterShape.Point
                comps.getAsJsonObject("minecraft:emitter_shape_point")?.let { o ->
                    val off = vec3(o, "offset")
                    shape = EmitterShape.Box(off[0], off[1], off[2], 0f, 0f, 0f)
                }
                comps.getAsJsonObject("minecraft:emitter_shape_box")?.let { o ->
                    val off = vec3(o, "offset")
                    val half = vec3(o, "half_dimensions")
                    shape = EmitterShape.Box(off[0], off[1], off[2], half[0], half[1], half[2])
                }
                comps.getAsJsonObject("minecraft:emitter_shape_sphere")?.let { o ->
                    val off = vec3(o, "offset")
                    shape = EmitterShape.Sphere(off[0], off[1], off[2], num(o, "radius", 1f))
                }
                comps.getAsJsonObject("minecraft:emitter_shape_disc")?.let { o ->
                    val off = vec3(o, "offset")
                    shape = EmitterShape.Disc(off[0], off[1], off[2], num(o, "radius", 1f))
                }

                val lifeEl = comps.getAsJsonObject("minecraft:particle_lifetime_expression")
                    ?.get("max_lifetime")
                val particleLifetime = numValue(lifeEl, 1f)
                val lifetimeExpr = exprOrNull(lifeEl)

                val speedEl = comps.get("minecraft:particle_initial_speed")
                val initialSpeed = numValue(speedEl, 0f)
                val speedExpr = exprOrNull(speedEl)

                var ax = 0f
                var ay = 0f
                var az = 0f
                var accelExprX: String? = null
                var accelExprY: String? = null
                var accelExprZ: String? = null
                comps.getAsJsonObject("minecraft:particle_motion_dynamic")?.let { o ->
                    val arr = o.get("linear_acceleration")
                    if (arr != null && arr.isJsonArray) {
                        val a = arr.asJsonArray
                        ax = numValue(a.getOrNull(0), 0f)
                        ay = numValue(a.getOrNull(1), 0f)
                        az = numValue(a.getOrNull(2), 0f)
                        accelExprX = exprOrNull(a.getOrNull(0))
                        accelExprY = exprOrNull(a.getOrNull(1))
                        accelExprZ = exprOrNull(a.getOrNull(2))
                    }
                }

                var sizeX = 0.2f
                var sizeY = 0.2f
                var sizeExprX: String? = null
                var sizeExprY: String? = null
                var uvX = 0f
                var uvY = 0f
                var uvW = 8f
                var uvH = 8f
                var texW = 128f
                var texH = 128f
                comps.getAsJsonObject("minecraft:particle_appearance_billboard")?.let { o ->
                    var flipbook: Flipbook? = null
                    val sz = o.get("size")
                    if (sz != null && sz.isJsonArray) {
                        val a = sz.asJsonArray
                        sizeX = numValue(a.getOrNull(0), 0.2f)
                        sizeY = numValue(a.getOrNull(1), 0.2f)
                        sizeExprX = exprOrNull(a.getOrNull(0))
                        sizeExprY = exprOrNull(a.getOrNull(1))
                    }
                    o.getAsJsonObject("uv")?.let { uv ->
                        texW = num(uv, "texture_width", 128f)
                        texH = num(uv, "texture_height", 128f)
                        val u = vec2(uv, "uv")
                        val us = vec2(uv, "uv_size")
                        uvX = u[0]
                        uvY = u[1]
                        uvW = if (us[0] != 0f) us[0] else 8f
                        uvH = if (us[1] != 0f) us[1] else 8f

                        uv.getAsJsonObject("flipbook")?.let { fb ->
                            val sizeUv = vec2(fb, "size_UV")
                            val stepUv = vec2(fb, "step_UV")
                            flipbook = Flipbook(
                                sizeU = if (sizeUv[0] != 0f) sizeUv[0] else 16f,
                                sizeV = if (sizeUv[1] != 0f) sizeUv[1] else 16f,
                                stepU = stepUv[0],
                                stepV = stepUv[1],
                                maxFrame = num(fb, "max_frame", 1f).toInt().coerceAtLeast(1),
                                stretchToLifetime = fb.get("stretch_to_lifetime")?.asBoolean ?: true
                            )
                            // базовый размер кадра для UV
                            uvW = flipbook!!.sizeU
                            uvH = flipbook!!.sizeV
                        }
                    }
                }

                var cr = 1f
                var cg = 1f
                var cb = 1f
                var ca = 1f
                comps.getAsJsonObject("minecraft:particle_appearance_tinting")?.let { o ->
                    o.get("color")?.let { c ->
                        if (c.isJsonArray) {
                            val arr = c.asJsonArray
                            cr = numValue(arr.getOrNull(0), 1f)
                            cg = numValue(arr.getOrNull(1), 1f)
                            cb = numValue(arr.getOrNull(2), 1f)
                            ca = numValue(arr.getOrNull(3), 1f)
                        }
                    }
                }

                ParticleEffect(
                    identifier = id,
                    material = material,
                    texture = texture,
                    spawnRate = spawnRate,
                    maxParticles = maxParticles,
                    lifetimeMode = lifetimeMode,
                    activeTime = activeTime,
                    shape = shape,
                    particleLifetime = particleLifetime,
                    lifetimeExpr = lifetimeExpr,
                    initialSpeed = initialSpeed,
                    speedExpr = speedExpr,
                    accelX = ax,
                    accelY = ay,
                    accelZ = az,
                    accelExprX = accelExprX,
                    accelExprY = accelExprY,
                    accelExprZ = accelExprZ,
                    sizeX = sizeX,
                    sizeY = sizeY,
                    sizeExprX = sizeExprX,
                    sizeExprY = sizeExprY,
                    uvX = uvX,
                    uvY = uvY,
                    uvW = uvW,
                    uvH = uvH,
                    texW = texW,
                    texH = texH,
                    colorR = cr,
                    colorG = cg,
                    colorB = cb,
                    colorA = ca,
                    rateMode = rateMode
                )
            }.getOrNull()
        }

        private fun JsonArray.getOrNull(i: Int): JsonElement? =
            if (i in 0 until size()) get(i) else null

        private fun num(obj: JsonObject, key: String, def: Float): Float =
            numValue(obj.get(key), def)

        private fun numValue(el: JsonElement?, def: Float): Float {
            if (el == null || el.isJsonNull) return def
            if (el.isJsonPrimitive) {
                val p = el.asJsonPrimitive
                if (p.isNumber) return p.asFloat
                if (p.isString) return p.asString.toFloatOrNull() ?: def
            }
            return def
        }

        /** null = использовать числовое поле; иначе Molang-строка */
        private fun exprOrNull(el: JsonElement?): String? {
            if (el == null || el.isJsonNull) return null
            if (!el.isJsonPrimitive) return null
            val p = el.asJsonPrimitive
            if (p.isNumber) return null
            if (p.isString) {
                val s = p.asString.trim()
                if (s.toFloatOrNull() != null) return null
                return s
            }
            return null
        }

        private fun vec3(obj: JsonObject, key: String): FloatArray {
            val el = obj.get(key) ?: return floatArrayOf(0f, 0f, 0f)
            if (!el.isJsonArray) return floatArrayOf(0f, 0f, 0f)
            val a = el.asJsonArray
            return floatArrayOf(
                numValue(a.getOrNull(0), 0f),
                numValue(a.getOrNull(1), 0f),
                numValue(a.getOrNull(2), 0f)
            )
        }

        private fun vec2(obj: JsonObject, key: String): FloatArray {
            val el = obj.get(key) ?: return floatArrayOf(0f, 0f)
            if (!el.isJsonArray) return floatArrayOf(0f, 0f)
            val a = el.asJsonArray
            return floatArrayOf(
                numValue(a.getOrNull(0), 0f),
                numValue(a.getOrNull(1), 0f)
            )
        }
    }
}

data class Flipbook(
    val sizeU: Float,
    val sizeV: Float,
    val stepU: Float,
    val stepV: Float,
    val maxFrame: Int,
    val stretchToLifetime: Boolean = true
)