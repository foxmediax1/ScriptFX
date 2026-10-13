package net.foxmediax.scriptfx.client.particles

import net.foxmediax.scriptfx.network.ParticlePayload
import net.foxmediax.scriptfx.npc.ScriptNpcEntity
import net.foxmediax.scriptfx.particles.Flipbook
import net.foxmediax.scriptfx.particles.ParticleEffect
import net.foxmediax.scriptfx.particles.ParticleRegistry
import net.foxmediax.scriptfx.particles.molang.SimpleMolang
import net.minecraft.client.Minecraft
import kotlin.math.sqrt
import kotlin.random.Random

data class LiveParticle(
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var vz: Float,
    var age: Float,
    val maxAge: Float,
    var sizeX: Float,
    var sizeY: Float,
    var r: Float,
    var g: Float,
    var b: Float,
    var a: Float,
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
    val texH: Float = 128f,
    val baseR: Float = 1f,
    val baseG: Float = 1f,
    val baseB: Float = 1f,
    val baseA: Float = 1f,
    val rand1: Float = 0f,
    val rand2: Float = 0f,
    val rand3: Float = 0f,
    val rand4: Float = 0f
)

object ParticleRuntime {

    private val emitters = ArrayList<ActiveEmitter>()

    class ActiveEmitter(
        val effect: ParticleEffect,
        var originX: Double,
        var originY: Double,
        var originZ: Double,
        var followNpcId: String? = null,
        val offsetX: Double = 0.0,
        val offsetY: Double = 0.0,
        val offsetZ: Double = 0.0,
        var ageSec: Float = 0f,
        var spawnAcc: Float = 0f,
        /** Для INSTANT: отслеживаем, когда нужно следующую порцию (при LOOPING) */
        var lastBurstTime: Float = 0f,
        val particles: ArrayList<LiveParticle> = ArrayList(),
        val rng: Random = Random.Default
    ) {
        val finished: Boolean
            get() {
                if (followNpcId != null) {
                    return particles.isEmpty() && ageSec > 0.5f && findNpc(followNpcId!!) == null
                }
                val emitterDone = when (effect.lifetimeMode) {
                    ParticleEffect.LifetimeMode.ONCE -> ageSec >= effect.activeTime
                    ParticleEffect.LifetimeMode.LOOPING -> false
                }
                return emitterDone && particles.isEmpty()
            }
    }

    private fun findNpc(npcId: String): ScriptNpcEntity? {
        val level = Minecraft.getInstance().level ?: return null
        for (e in level.entitiesForRendering()) {
            if (e is ScriptNpcEntity && e.npcId == npcId) return e
        }
        return null
    }

    fun clear() {
        emitters.clear()
    }

    fun forEachParticle(block: (ActiveEmitter, LiveParticle) -> Unit) {
        for (em in emitters) {
            for (p in em.particles) block(em, p)
        }
    }

    fun tick(dtSec: Float) {
        if (dtSec <= 0f) return
        val it = emitters.iterator()
        while (it.hasNext()) {
            val em = it.next()
            em.ageSec += dtSec

            em.followNpcId?.let { id ->
                val npc = findNpc(id)
                if (npc != null) {
                    em.originX = npc.x + em.offsetX
                    em.originY = npc.y + em.offsetY
                    em.originZ = npc.z + em.offsetZ
                }
            }

            val stillActive = when {
                em.followNpcId != null -> true
                else -> when (em.effect.lifetimeMode) {
                    ParticleEffect.LifetimeMode.ONCE -> em.ageSec < em.effect.activeTime
                    ParticleEffect.LifetimeMode.LOOPING -> true
                }
            }

            when (em.effect.rateMode) {
                ParticleEffect.RateMode.STEADY -> {
                    if (stillActive) {
                        em.spawnAcc += em.effect.spawnRate * dtSec
                        while (em.spawnAcc >= 1f && em.particles.size < em.effect.maxParticles) {
                            em.spawnAcc -= 1f
                            spawnOne(em)
                        }
                    }
                }
                ParticleEffect.RateMode.INSTANT -> {
                    // Для INSTANT: спавним порцию сразу при старте и далее каждые activeTime секунд (если LOOPING)
                    val timeSinceLastBurst = em.ageSec - em.lastBurstTime
                    val shouldBurst = when (em.effect.lifetimeMode) {
                        ParticleEffect.LifetimeMode.ONCE ->
                            em.lastBurstTime == 0f && em.ageSec < em.effect.activeTime
                        ParticleEffect.LifetimeMode.LOOPING ->
                            timeSinceLastBurst >= em.effect.activeTime
                    }
                    if (shouldBurst && stillActive) {
                        val count = em.effect.spawnRate.toInt().coerceIn(1, em.effect.maxParticles)
                        repeat(count) { spawnOne(em) }
                        em.lastBurstTime = em.ageSec
                    }
                }
            }

            val pit = em.particles.iterator()
            while (pit.hasNext()) {
                val p = pit.next()
                p.age += dtSec

                // flipbook UV
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
                    p.uvU0 = (u / p.texW).coerceIn(0f, 1f)
                    p.uvV0 = (v / p.texH).coerceIn(0f, 1f)
                    p.uvU1 = ((u + fb.sizeU) / p.texW).coerceIn(0f, 1f)
                    p.uvV1 = ((v + fb.sizeV) / p.texH).coerceIn(0f, 1f)
                }

                if (p.age >= p.maxAge) {
                    pit.remove()
                    continue
                }

                // fade alpha
                val life = if (p.maxAge > 1e-6f) (p.age / p.maxAge).coerceIn(0f, 1f) else 1f
                p.a = p.baseA * (1f - life)
                p.r = p.baseR
                p.g = p.baseG
                p.b = p.baseB

                // size by age (Molang)
                if (em.effect.sizeExprX != null || em.effect.sizeExprY != null) {
                    val c = SimpleMolang.Ctx(
                        random1 = p.rand1,
                        random2 = p.rand2,
                        random3 = p.rand3,
                        random4 = p.rand4,
                        particleAge = p.age,
                        particleLifetime = p.maxAge,
                        emitterAge = em.ageSec,
                        emitterLifetime = em.effect.activeTime
                    )
                    if (em.effect.sizeExprX != null) {
                        p.sizeX = SimpleMolang.eval(em.effect.sizeExprX, p.sizeX, c)
                            .coerceIn(0.02f, 2f)
                    }
                    if (em.effect.sizeExprY != null) {
                        p.sizeY = SimpleMolang.eval(em.effect.sizeExprY, p.sizeY, c)
                            .coerceIn(0.02f, 2f)
                    }
                }

                // motion
                p.vx += (em.effect.accelX + p.ax) * dtSec
                p.vy += (em.effect.accelY + p.ay) * dtSec
                p.vz += (em.effect.accelZ + p.az) * dtSec
                p.x += p.vx * dtSec
                p.y += p.vy * dtSec
                p.z += p.vz * dtSec
            }

            if (em.finished) it.remove()
        }
    }

    private fun spawnOne(em: ActiveEmitter) {
        try {
            if (em.particles.size >= em.effect.maxParticles) return
            val e = em.effect
            val r1 = em.rng.nextFloat()
            val r2 = em.rng.nextFloat()
            val r3 = em.rng.nextFloat()
            val r4 = em.rng.nextFloat()

            val ctx = SimpleMolang.Ctx(
                random1 = r1,
                random2 = r2,
                random3 = r3,
                random4 = r4,
                particleAge = 0f,
                particleLifetime = e.particleLifetime
            )

            val lifetime = SimpleMolang.eval(e.lifetimeExpr, e.particleLifetime, ctx)
                .coerceAtLeast(0.05f)
            val speed = SimpleMolang.eval(e.speedExpr, e.initialSpeed, ctx)
            val ax = SimpleMolang.eval(e.accelExprX, e.accelX, ctx)
            val ay = SimpleMolang.eval(e.accelExprY, e.accelY, ctx)
            val az = SimpleMolang.eval(e.accelExprZ, e.accelZ, ctx)
            val sx = SimpleMolang.eval(e.sizeExprX, e.sizeX, ctx).coerceIn(0.02f, 2f)
            val sy = SimpleMolang.eval(e.sizeExprY, e.sizeY, ctx).coerceIn(0.02f, 2f)

            val (ox, oy, oz) = e.sampleSpawnOffset(em.rng)
            val (dx, dy, dz) = directionFor(e, ox, oy, oz, em.rng)
            val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-4f)

            val u0 = (e.uvX / e.texW).coerceIn(0f, 1f)
            val v0 = (e.uvY / e.texH).coerceIn(0f, 1f)
            val u1 = ((e.uvX + e.uvW) / e.texW).coerceIn(0f, 1f)
            val v1 = ((e.uvY + e.uvH) / e.texH).coerceIn(0f, 1f)

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
                    texH = e.texH,
                    baseR = e.colorR,
                    baseG = e.colorG,
                    baseB = e.colorB,
                    baseA = e.colorA,
                    rand1 = r1,
                    rand2 = r2,
                    rand3 = r3,
                    rand4 = r4
                )
            )
        } catch (t: Throwable) {
            t.printStackTrace()
            net.foxmediax.scriptfx.scriptengine.ScriptFXLog.warn(
                "spawnOne failed: ${t.message}"
            )
        }
    }

    private fun directionFor(
        e: ParticleEffect,
        ox: Float,
        oy: Float,
        oz: Float,
        rng: Random
    ): Triple<Float, Float, Float> {
        return when (e.shape) {
            is ParticleEffect.EmitterShape.Sphere,
            is ParticleEffect.EmitterShape.Disc -> {
                val len = sqrt(ox * ox + oy * oy + oz * oz)
                if (len > 1e-4f) Triple(ox / len, oy / len, oz / len)
                else randomDir(rng)
            }
            else -> randomDir(rng)
        }
    }

    private fun randomDir(rng: Random): Triple<Float, Float, Float> {
        val dx = rng.nextFloat() * 2f - 1f
        val dy = (rng.nextFloat() * 2f - 1f) * 0.5f + 0.5f
        val dz = rng.nextFloat() * 2f - 1f
        return Triple(dx, dy, dz)
    }

    fun handlePayload(payload: ParticlePayload) {
        when (payload.action) {
            ParticlePayload.PLAY -> {
                ParticleRegistry.reload()
                val effect = ParticleRegistry.get(payload.effectId)
                if (effect == null) {
                    net.foxmediax.scriptfx.scriptengine.ScriptFXLog.warn(
                        "particle_play: эффект не найден: ${payload.effectId}"
                    )
                    return
                }
                // Удаляем старые эмиттеры с тем же эффектом в той же точке (перезапуск)
                emitters.removeAll {
                    it.effect.identifier == effect.identifier &&
                            it.followNpcId == null &&
                            kotlin.math.abs(it.originX - payload.x) < 0.1 &&
                            kotlin.math.abs(it.originY - payload.y) < 0.1 &&
                            kotlin.math.abs(it.originZ - payload.z) < 0.1
                }
                val em = ActiveEmitter(effect, payload.x, payload.y, payload.z)
                when (effect.rateMode) {
                    ParticleEffect.RateMode.INSTANT -> {
                        // Первую порцию спавним сразу
                        val count = effect.spawnRate.toInt().coerceIn(1, effect.maxParticles)
                        repeat(count) { spawnOne(em) }
                        em.lastBurstTime = 0f
                    }
                    ParticleEffect.RateMode.STEADY -> {
                        // Небольшой стартовый burst, чтобы было видно сразу
                        val burst = effect.spawnRate.toInt().coerceIn(1, 8)
                        repeat(burst) { spawnOne(em) }
                    }
                }
                emitters.add(em)
                net.foxmediax.scriptfx.scriptengine.ScriptFXLog.info(
                    "particle_play: ${effect.identifier} particles=${em.particles.size}"
                )
            }
            ParticlePayload.ATTACH -> {
                ParticleRegistry.reload()
                val effect = ParticleRegistry.get(payload.effectId) ?: return
                val npcId = payload.targetNpcId
                emitters.removeAll {
                    it.followNpcId == npcId && it.effect.identifier == effect.identifier
                }
                val npc = findNpc(npcId)
                val em = ActiveEmitter(
                    effect = effect,
                    originX = (npc?.x ?: 0.0) + payload.x,
                    originY = (npc?.y ?: 0.0) + payload.y,
                    originZ = (npc?.z ?: 0.0) + payload.z,
                    followNpcId = npcId,
                    offsetX = payload.x,
                    offsetY = payload.y,
                    offsetZ = payload.z
                )
                // Для INSTANT тоже спавним сразу
                if (effect.rateMode == ParticleEffect.RateMode.INSTANT) {
                    val count = effect.spawnRate.toInt().coerceIn(1, effect.maxParticles)
                    repeat(count) { spawnOne(em) }
                    em.lastBurstTime = 0f
                }
                emitters.add(em)
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