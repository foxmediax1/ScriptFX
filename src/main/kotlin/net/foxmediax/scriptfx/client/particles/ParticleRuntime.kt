package net.foxmediax.scriptfx.client.particles

import net.foxmediax.scriptfx.network.ParticlePayload
import net.foxmediax.scriptfx.particles.ParticleEffect
import kotlin.random.Random
import net.minecraft.client.Minecraft
import net.foxmediax.scriptfx.particles.molang.SimpleMolang
import net.foxmediax.scriptfx.particles.ParticleRegistry
import net.foxmediax.scriptfx.particles.Flipbook

private val rng = kotlin.random.Random.Default

data class LiveParticle(
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var vz: Float,
    var age: Float,
    val maxAge: Float,
    val sizeX: Float,
    val sizeY: Float,
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float,
    var uvU0: Float,
    var uvV0: Float,
    var uvU1: Float,
    var uvV1: Float,
    val ax: Float = 0f,
    val ay: Float = 0f,
    val az: Float = 0f,
    val flipbook: Flipbook? = null,
    val baseUvX: Float = 0f,
    val baseUvY: Float = 0f,
    val texW: Float = 128f,
    val texH: Float = 128f
)

class ActiveEmitter(
    val effect: ParticleEffect,
    var originX: Double,
    var originY: Double,
    var originZ: Double,
    /** Если не null — каждый тик тянем origin с этого NPC */
    var followNpcId: String? = null,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val offsetZ: Double = 0.0,
    var ageSec: Float = 0f,
    var spawnAcc: Float = 0f,
    val particles: ArrayList<LiveParticle> = ArrayList(),
    val rng: Random = Random.Default
) {
    val finished: Boolean
        get() {
            // при attach не гасим эмиттер по activeTime, пока NPC жив
            if (followNpcId != null) {
                return particles.isEmpty() && ageSec > 0.5f && !isNpcPresent()
            }
            val emitterDone = when (effect.lifetimeMode) {
                ParticleEffect.LifetimeMode.ONCE -> ageSec >= effect.activeTime
                ParticleEffect.LifetimeMode.LOOPING -> false
            }
            return emitterDone && particles.isEmpty()
        }

    private fun isNpcPresent(): Boolean {
        val id = followNpcId ?: return false
        return findNpc(id) != null
    }

    // --- helpers ---

    private fun findNpc(npcId: String): net.foxmediax.scriptfx.npc.ScriptNpcEntity? {
        val level = Minecraft.getInstance().level ?: return null
        for (e in level.entitiesForRendering()) {
            if (e is net.foxmediax.scriptfx.npc.ScriptNpcEntity && e.npcId == npcId) {
                return e
            }
        }
        return null
    }
}

object ParticleRuntime {

    private val emitters = ArrayList<ActiveEmitter>()

    class ActiveEmitter(
        val effect: ParticleEffect,
        var originX: Double,
        var originY: Double,
        var originZ: Double,
        /** Если не null — каждый тик тянем origin с этого NPC */
        var followNpcId: String? = null,
        val offsetX: Double = 0.0,
        val offsetY: Double = 0.0,
        val offsetZ: Double = 0.0,
        var ageSec: Float = 0f,
        var spawnAcc: Float = 0f,
        val particles: ArrayList<LiveParticle> = ArrayList(),
        val rng: Random = Random.Default
    ) {
        val finished: Boolean
            get() {
                // при attach не гасим эмиттер по activeTime, пока NPC жив
                if (followNpcId != null) {
                    return particles.isEmpty() && ageSec > 0.5f && !isNpcPresent()
                }
                val emitterDone = when (effect.lifetimeMode) {
                    ParticleEffect.LifetimeMode.ONCE -> ageSec >= effect.activeTime
                    ParticleEffect.LifetimeMode.LOOPING -> false
                }
                return emitterDone && particles.isEmpty()
            }

        private fun isNpcPresent(): Boolean {
            val id = followNpcId ?: return false
            return findNpc(id) != null
        }
    }

// --- helpers ---

    private fun findNpc(npcId: String): net.foxmediax.scriptfx.npc.ScriptNpcEntity? {
        val level = Minecraft.getInstance().level ?: return null
        for (e in level.entitiesForRendering()) {
            if (e is net.foxmediax.scriptfx.npc.ScriptNpcEntity && e.npcId == npcId) {
                return e
            }
        }
        return null
    }

    fun clear() {
        emitters.clear()
    }

    fun tick(dtSec: Float) {
        val it = emitters.iterator()
        while (it.hasNext()) {
            val em = it.next()
            em.ageSec += dtSec

            // следовать за NPC
            em.followNpcId?.let { id ->
                val npc = findNpc(id)
                if (npc != null) {
                    em.originX = npc.x + em.offsetX
                    em.originY = npc.y + em.offsetY
                    em.originZ = npc.z + em.offsetZ
                }
            }

            // для attach всегда считаем эмиттер «активным» (looping-поведение)
            val stillActive = if (em.followNpcId != null) {
                findNpc(em.followNpcId!!) != null
            } else {
                when (em.effect.lifetimeMode) {
                    ParticleEffect.LifetimeMode.ONCE -> em.ageSec < em.effect.activeTime
                    ParticleEffect.LifetimeMode.LOOPING -> true
                }
            }

            // spawn steady
            if (em.effect.rateMode == ParticleEffect.RateMode.STEADY) {
                val stillActive = when (em.effect.lifetimeMode) {
                    ParticleEffect.LifetimeMode.ONCE -> em.ageSec < em.effect.activeTime
                    ParticleEffect.LifetimeMode.LOOPING -> true
                }
                if (stillActive) {
                    em.spawnAcc += em.effect.spawnRate * dtSec
                    while (em.spawnAcc >= 1f && em.particles.size < em.effect.maxParticles) {
                        em.spawnAcc -= 1f
                        spawnOne(em)
                    }
                }
            }

            // integrate particles
            val pit = em.particles.iterator()
            while (pit.hasNext()) {
                val p = pit.next()
                p.age += dtSec
                p.flipbook?.let { fb ->
                    val t = if (fb.stretchToLifetime && p.maxAge > 0f) {
                        (p.age / p.maxAge).coerceIn(0f, 0.999f)
                    } else {
                        p.age
                    }
                    val frame = if (fb.stretchToLifetime) {
                        (t * fb.maxFrame).toInt().coerceIn(0, (fb.maxFrame - 1).coerceAtLeast(0))
                    } else {
                        (p.age * fb.maxFrame).toInt().coerceIn(0, (fb.maxFrame - 1).coerceAtLeast(0))
                    }
                    val u = p.baseUvX + fb.stepU * frame
                    val v = p.baseUvY + fb.stepV * frame
                    p.uvU0 = u / p.texW
                    p.uvV0 = v / p.texH
                    p.uvU1 = (u + fb.sizeU) / p.texW
                    p.uvV1 = (v + fb.sizeV) / p.texH
                }
                if (p.age >= p.maxAge) {
                    pit.remove()
                    continue
                }
                p.vx += em.effect.accelX * dtSec
                p.vy += em.effect.accelY * dtSec
                p.vz += em.effect.accelZ * dtSec
                p.x += p.vx * dtSec
                p.y += p.vy * dtSec
                p.z += p.vz * dtSec
                p.vx += p.ax * dtSec
                p.vy += p.ay * dtSec
                p.vz += p.az * dtSec
            }

            if (em.finished) it.remove()
        }

        val level = Minecraft.getInstance().level ?: return
        forEachParticle { _, p ->
            if (rng.nextFloat() < 0.4f) {
                val ri = (p.r.coerceIn(0f, 1f) * 255).toInt()
                val gi = (p.g.coerceIn(0f, 1f) * 255).toInt()
                val bi = (p.b.coerceIn(0f, 1f) * 255).toInt()
                // packed RGB (без alpha) — формат 26.1
                val color = (ri shl 16) or (gi shl 8) or bi
                val scale = (p.sizeX * 2f).coerceIn(0.3f, 1.5f)

                level.addParticle(
                    net.minecraft.core.particles.DustParticleOptions(color, scale),
                    p.x.toDouble(),
                    p.y.toDouble(),
                    p.z.toDouble(),
                    0.0, 0.0, 0.0
                )
            }
        }
    }

    fun forEachParticle(block: (ActiveEmitter, LiveParticle) -> Unit) {
        for (em in emitters) {
            for (p in em.particles) block(em, p)
        }
    }

    private fun spawnOne(em: ActiveEmitter) {
        if (em.particles.size >= em.effect.maxParticles) return
        val e = em.effect
        val ctx = SimpleMolang.Ctx()

        val lifetime = SimpleMolang.eval(e.lifetimeExpr, e.particleLifetime, ctx)
            .coerceAtLeast(0.05f)
        val speed = SimpleMolang.eval(e.speedExpr, e.initialSpeed, ctx)
        val ax = SimpleMolang.eval(e.accelExprX, e.accelX, ctx)
        val ay = SimpleMolang.eval(e.accelExprY, e.accelY, ctx)
        val az = SimpleMolang.eval(e.accelExprZ, e.accelZ, ctx)
        val sx = SimpleMolang.eval(e.sizeExprX, e.sizeX, ctx)
        val sy = SimpleMolang.eval(e.sizeExprY, e.sizeY, ctx)

        val (ox, oy, oz) = e.sampleSpawnOffset(em.rng)
        val dx = (em.rng.nextFloat() * 2f - 1f)
        val dy = (em.rng.nextFloat() * 2f - 1f) * 0.5f + 0.5f
        val dz = (em.rng.nextFloat() * 2f - 1f)
        val len = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-4f)

        val u0 = e.uvX / e.texW
        val v0 = e.uvY / e.texH
        val u1 = (e.uvX + e.uvW) / e.texW
        val v1 = (e.uvY + e.uvH) / e.texH

        em.particles.add(
            LiveParticle(
                x = (em.originX + ox).toFloat(),
                y = (em.originY + oy).toFloat(),
                z = (em.originZ + oz).toFloat(),
                vx = dx / len * speed,
                vy = dy / len * speed,
                vz = dz / len * speed,
                age = 0f,
                maxAge = lifetime,
                sizeX = sx,
                sizeY = sy,
                r = e.colorR,
                g = e.colorG,
                b = e.colorB,
                a = e.colorA,
                uvU0 = u0,
                uvV0 = v0,
                uvU1 = u1,
                uvV1 = v1,
                ax = ax,
                ay = ay,
                az = az,
                flipbook = e.flipbook,
                baseUvX = e.uvX,
                baseUvY = e.uvY,
                texW = e.texW,
                texH = e.texH
            )
        )
    }

    fun handlePayload(payload: ParticlePayload) {
        when (payload.action) {
            ParticlePayload.PLAY -> {
                ParticleRegistry.reload()
                val effect = ParticleRegistry.get(payload.effectId) ?: return
                val em = ActiveEmitter(effect, payload.x, payload.y, payload.z)
                if (effect.rateMode == ParticleEffect.RateMode.INSTANT) {
                    val count = effect.spawnRate.toInt().coerceIn(1, effect.maxParticles)
                    repeat(count) { spawnOne(em) }
                }
                emitters.add(em)
            }
            ParticlePayload.ATTACH -> {
                ParticleRegistry.reload()
                val effect = ParticleRegistry.get(payload.effectId) ?: return
                val npcId = payload.targetNpcId
                emitters.removeAll {
                    it.followNpcId == npcId && it.effect.identifier == effect.identifier
                }
                val npc = findNpc(npcId)
                emitters.add(
                    ActiveEmitter(
                        effect = effect,
                        originX = (npc?.x ?: 0.0) + payload.x,
                        originY = (npc?.y ?: 0.0) + payload.y,
                        originZ = (npc?.z ?: 0.0) + payload.z,
                        followNpcId = npcId,
                        offsetX = payload.x,
                        offsetY = payload.y,
                        offsetZ = payload.z
                    )
                )
            }
            ParticlePayload.DETACH -> {
                val npcId = payload.targetNpcId
                if (payload.effectId.isBlank()) {
                    emitters.removeAll { it.followNpcId == npcId }
                } else {
                    emitters.removeAll {
                        it.followNpcId == npcId &&
                                (it.effect.identifier == payload.effectId ||
                                        it.effect.identifier.substringAfter(':') == payload.effectId)
                    }
                }
            }
            ParticlePayload.STOP -> {
                emitters.removeAll {
                    it.effect.identifier == payload.effectId ||
                            it.effect.identifier.substringAfter(':') == payload.effectId
                }
            }
            ParticlePayload.STOP_ALL -> emitters.clear()
        }
    }
}