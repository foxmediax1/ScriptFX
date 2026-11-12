package net.foxmediax.scriptfx.client.particles

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.loader.api.FabricLoader
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.world.level.Level

/**
 * Мост к AAA Particles (Effekseer).
 * assets/<ns>/effeks/<name>.efkefc  →  id <ns>:<name>
 */
@Environment(EnvType.CLIENT)
object AaaParticleBridge {

    val available: Boolean
        get() = FabricLoader.getInstance().isModLoaded("aaa_particles")

    fun play(effekId: String, x: Double, y: Double, z: Double, scale: Float = 1f) {
        if (!available) {
            ScriptFXLog.warn("AAA Particles не загружен — пропуск '$effekId'")
            return
        }
        val level = Minecraft.getInstance().level ?: run {
            ScriptFXLog.warn("AAA play: нет Level")
            return
        }
        try {
            spawn(level, toId(effekId), x, y, z, scale)
            ScriptFXLog.info("AAA play: $effekId @ $x $y $z scale=$scale")
        } catch (t: Throwable) {
            ScriptFXLog.warn("AAA play failed: ${t.message}")
            t.printStackTrace()
        }
    }

    private fun spawn(
        level: Level,
        id: Identifier,
        x: Double, y: Double, z: Double,
        scale: Float
    ) {
        val infoClass = loadClass(
            "mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo",
            "cn.chloeprime.aaaparticles.api.common.ParticleEmitterInfo"
        ) ?: error("ParticleEmitterInfo not found")

        val levelClass = loadClass(
            "mod.chloeprime.aaaparticles.api.common.AAALevel",
            "cn.chloeprime.aaaparticles.api.common.AAALevel"
        ) ?: error("AAALevel not found")

        val info = infoClass.getConstructor(Identifier::class.java).newInstance(id)
        val clone = infoClass.getMethod("clone").invoke(info)

        val positioned = infoClass.methods.first { m ->
            m.name == "position" && m.parameterCount == 3
        }.invoke(clone, x, y, z)

        val scaled = run {
            val s3 = infoClass.methods.firstOrNull { it.name == "scale" && it.parameterCount == 3 }
            val s1 = infoClass.methods.firstOrNull { it.name == "scale" && it.parameterCount == 1 }
            when {
                s3 != null -> s3.invoke(positioned, scale, scale, scale)
                s1 != null -> s1.invoke(positioned, scale)
                else -> positioned
            }
        }

        val add = levelClass.methods.first { m ->
            m.name == "addParticle" && m.parameterCount == 3
        }
        add.invoke(null, level, false, scaled)
    }

    private fun toId(raw: String): Identifier {
        val clean = raw.trim().removeSurrounding("\"")
        return if (clean.contains(':')) Identifier.parse(clean)
        else Identifier.fromNamespaceAndPath("scriptfx", clean)
    }

    private fun loadClass(vararg names: String): Class<*>? {
        for (n in names) {
            try {
                return Class.forName(n)
            } catch (_: ClassNotFoundException) {
            }
        }
        ScriptFXLog.warn("AAA class not found, tried: ${names.joinToString()}")
        return null
    }
}