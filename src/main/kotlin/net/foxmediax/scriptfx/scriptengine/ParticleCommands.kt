package net.foxmediax.scriptfx.scriptengine

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.foxmediax.scriptfx.network.ParticlePayload
import net.foxmediax.scriptfx.particles.ParticleRegistry
import net.minecraft.server.level.ServerPlayer

object ParticleCommands {

    fun install(register: (String, CommandHandler) -> Unit) {

        // particle_play <id> [x y z]  — координаты опциональны, по умолчанию ~ ~1 ~ (чуть над головой)
        register("particle_play") { ctx, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            if (id.isEmpty()) {
                ScriptFXLog.warn("particle_play: нужен id (файл .particle.json в projects/*/particles/)")
                return@register CommandResult.Continue
            }
            ParticleRegistry.reload()
            if (ParticleRegistry.get(id) == null) {
                ScriptFXLog.warn("particle_play: эффект '$id' не найден (положите .particle.json в projects/*/particles/)")
                return@register CommandResult.Continue
            }
            val ref = ctx.player
            // Если координаты не указаны — используем позицию игрока с небольшим смещением вверх
            val x = args.getOrNull(1)?.let { parseCoord(it, ref?.x) } ?: (ref?.x ?: 0.0)
            val y = args.getOrNull(2)?.let { parseCoord(it, ref?.y) } ?: ((ref?.y ?: 0.0) + 1.0)
            val z = args.getOrNull(3)?.let { parseCoord(it, ref?.z) } ?: (ref?.z ?: 0.0)

            broadcast(ctx, ParticlePayload(ParticlePayload.PLAY, id, "", x, y, z))
            CommandResult.Continue
        }

        // particle_stop <id>
        register("particle_stop") { ctx, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            if (id.isEmpty()) {
                ScriptFXLog.warn("particle_stop: нужен id")
                return@register CommandResult.Continue
            }
            broadcast(ctx, ParticlePayload(ParticlePayload.STOP, id, "", 0.0, 0.0, 0.0))
            CommandResult.Continue
        }

        register("particle_stop_all") { ctx, _ ->
            broadcast(ctx, ParticlePayload(ParticlePayload.STOP_ALL, "", "", 0.0, 0.0, 0.0))
            CommandResult.Continue
        }

        // particle_attach <effectId> <npcId> [ox oy oz]
        register("particle_attach") { ctx, args ->
            val effectId = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            val npcId = args.getOrNull(1)?.removeSurrounding("\"")?.trim().orEmpty()
            if (effectId.isEmpty() || npcId.isEmpty()) {
                ScriptFXLog.warn("particle_attach: нужны effectId npcId [ox oy oz]")
                return@register CommandResult.Continue
            }
            ParticleRegistry.reload()
            if (ParticleRegistry.get(effectId) == null) {
                ScriptFXLog.warn("particle_attach: эффект '$effectId' не найден")
                return@register CommandResult.Continue
            }
            val ox = args.getOrNull(2)?.toDoubleOrNull() ?: 0.0
            val oy = args.getOrNull(3)?.toDoubleOrNull() ?: 1.0
            val oz = args.getOrNull(4)?.toDoubleOrNull() ?: 0.0
            broadcast(
                ctx,
                ParticlePayload(ParticlePayload.ATTACH, effectId, npcId, ox, oy, oz)
            )
            CommandResult.Continue
        }

        // particle_detach <npcId> [effectId]
        register("particle_detach") { ctx, args ->
            val npcId = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            if (npcId.isEmpty()) {
                ScriptFXLog.warn("particle_detach: нужен npcId [effectId]")
                return@register CommandResult.Continue
            }
            val effectId = args.getOrNull(1)?.removeSurrounding("\"")?.trim().orEmpty()
            broadcast(
                ctx,
                ParticlePayload(ParticlePayload.DETACH, effectId, npcId, 0.0, 0.0, 0.0)
            )
            CommandResult.Continue
        }
    }

    private fun broadcast(ctx: ScriptContext, payload: ParticlePayload) {
        val players: Collection<ServerPlayer> =
            ctx.player?.let { listOf(it) } ?: ctx.server.playerList.players
        for (p in players) {
            if (ServerPlayNetworking.canSend(p, ParticlePayload.TYPE)) {
                ServerPlayNetworking.send(p, payload)
            }
        }
    }

    private fun parseCoord(raw: String?, base: Double?): Double? {
        if (raw == null) return null
        val t = raw.trim()
        if (t == "~") return base
        if (t.startsWith("~") && base != null) {
            val off = t.substring(1).toDoubleOrNull() ?: 0.0
            return base + off
        }
        return t.toDoubleOrNull()
    }
}