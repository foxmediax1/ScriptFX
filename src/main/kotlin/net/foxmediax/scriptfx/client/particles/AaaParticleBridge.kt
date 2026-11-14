package net.foxmediax.scriptfx.client.particles

import mod.chloeprime.aaaparticles.api.client.EffectRegistry
import mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo
import mod.chloeprime.aaaparticles.client.installer.NativePlatform
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object AaaParticleBridge {

    private val LOG: Logger = LoggerFactory.getLogger("ScriptFX")

    val available: Boolean by lazy {
        try {
            !NativePlatform.isRunningOnUnsupportedPlatform()
        } catch (t: Throwable) {
            LOG.warn("AAA availability check failed: {}", t.toString())
            false
        }
    }

    private val knownEffects = LinkedHashSet<Identifier>()

    // ─────────────────────────────────────────────────────────────────
    // Совместимость со старым API (String overloads)
    // ─────────────────────────────────────────────────────────────────

    @JvmStatic
    @JvmOverloads
    fun play(effekId: String, x: Double, y: Double, z: Double, scale: Float = 1.0f) {
        val id = Identifier.tryParse(effekId) ?: run {
            LOG.warn("AAA play: невалидный id '{}'", effekId)
            return
        }
        play(id, x, y, z, scale)
    }

    @JvmStatic
    @JvmOverloads
    fun play(effekId: String, x: Float, y: Float, z: Float, scale: Float = 1.0f) =
        play(effekId, x.toDouble(), y.toDouble(), z.toDouble(), scale)

    @JvmStatic
    @JvmOverloads
    fun preload(effekId: String): Boolean {
        val id = Identifier.tryParse(effekId) ?: return false
        return preload(id)
    }

    // ─────────────────────────────────────────────────────────────────
    // Основной API (Identifier)
    // ─────────────────────────────────────────────────────────────────

    @JvmStatic
    @JvmOverloads
    fun play(
        effekId: Identifier,
        x: Double,
        y: Double,
        z: Double,
        scale: Float = 1.0f,
    ) {
        LOG.info("AAA play: {} @ {} {} {} scale={}", effekId, x, y, z, scale)
        val level = Minecraft.getInstance().level ?: return
        knownEffects.add(effekId)
        spawn(level, effekId, x, y, z, scale = scale)
    }

    @JvmStatic
    @JvmOverloads
    fun play(effekId: Identifier, x: Float, y: Float, z: Float, scale: Float = 1.0f) =
        play(effekId, x.toDouble(), y.toDouble(), z.toDouble(), scale)

    @JvmStatic
    fun preload(effekId: Identifier): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        return try {
            knownEffects.add(effekId)
            runOnRenderThread {
                EffectRegistry.load(effekId)
                    .thenAccept { LOG.info("AAA preload OK: {}", effekId) }
                    .exceptionally { t ->
                        LOG.error("AAA preload failed: {} — {}", effekId, t.toString())
                        null
                    }
            }
            true
        } catch (t: Throwable) {
            LOG.error("AAA preload exception: {} — {}", effekId, t.toString())
            false
        }
    }

    @JvmStatic
    fun preloadKnownEffects() {
        if (knownEffects.isEmpty()) {
            LOG.info("AAA preloadKnownEffects: список пуст — нечего грузить")
            return
        }
        LOG.info("AAA preloadKnownEffects: перезагрузка {} эффект(ов)", knownEffects.size)
        knownEffects.toList().forEach { preload(it) }
    }

    @JvmStatic
    fun clearPreloadCache() {
        val size = knownEffects.size
        knownEffects.clear()
        LOG.info("AAA clearPreloadCache: очищено {} записей", size)
    }

    // ─────────────────────────────────────────────────────────────────
    // Спавн — ТЕПЕРЬ С ПЕРЕНАПРАВЛЕНИЕМ НА RENDER THREAD
    // ─────────────────────────────────────────────────────────────────

    @JvmStatic
    @JvmOverloads
    fun spawn(
        level: Level,
        effekId: String,
        x: Double,
        y: Double,
        z: Double,
        scale: Float = 1.0f,
        speed: Float = 1.0f,
        player: Player? = null,
    ): Boolean {
        val id = Identifier.tryParse(effekId) ?: return false
        return spawn(level, id, x, y, z, scale, speed, player)
    }

    fun spawn(
        level: Level,
        effekId: Identifier,
        x: Double,
        y: Double,
        z: Double,
        scale: Float = 1.0f,
        speed: Float = 1.0f,
        player: Player? = null,
    ): Boolean {
        return try {
            val info = ParticleEmitterInfo.create(level, effekId)
                .position(Vec3(x, y, z))
                .scale(scale)
                .speed(speed)

            // spawnInWorld() вызывает EffectRegistry.load(), который внутри
            // делает glGetInteger() — это требует текущего OpenGL-контекста.
            // Поэтому всегда выполняем на render thread Minecraft.
            runOnRenderThread {
                info.spawnInWorld(level, player)
            }
            LOG.debug("AAA spawn queued: {} @ ({}, {}, {}) scale={}", effekId, x, y, z, scale)
            true
        } catch (t: Throwable) {
            LOG.warn("AAA spawn failed for {}: {}", effekId, t.toString())
            false
        }
    }

    fun spawnAdvanced(
        level: Level,
        effekId: Identifier,
        x: Double,
        y: Double,
        z: Double,
        scale: Float = 1.0f,
        speed: Float = 1.0f,
        rotX: Float = 0f,
        rotY: Float = 0f,
        rotZ: Float = 0f,
        parameters: Map<Int, Float> = emptyMap(),
        triggers: List<Int> = emptyList(),
        player: Player? = null,
    ): Boolean {
        return try {
            val info = ParticleEmitterInfo.create(level, effekId)
                .position(Vec3(x, y, z))
                .rotation(rotX, rotY, rotZ)
                .scale(scale)
                .speed(speed)

            parameters.forEach { (index, value) -> info.parameter(index, value) }
            triggers.forEach { info.trigger(it) }

            runOnRenderThread {
                info.spawnInWorld(level, player)
            }
            LOG.debug("AAA spawnAdvanced queued: {} @ ({}, {}, {}) scale={}", effekId, x, y, z, scale)
            true
        } catch (t: Throwable) {
            LOG.warn("AAA spawnAdvanced failed for {}: {}", effekId, t.toString())
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Утилита потоковой безопасности
    // ─────────────────────────────────────────────────────────────────

    /**
     * Если текущий поток — render thread, выполняем сразу.
     * Иначе ставим задачу в очередь render thread через Minecraft.execute().
     */
    // В классе AaaParticleBridge замените метод runOnRenderThread на следующий:

    private fun runOnRenderThread(block: () -> Unit) {
        val mc = Minecraft.getInstance()
        if (mc.isSameThread) {
            block()
        } else {
            mc.execute(block)
        }
    }
}