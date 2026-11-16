package net.foxmediax.scriptfx.client

import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.Minecraft
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType

/**
 * При открытии студии → SPECTATOR, при закрытии → прежний режим.
 * Только singleplayer (через server.playerList).
 */
object StudioGameMode {

    @Volatile
    private var saved: GameType? = null

    fun enterStudio() {
        val mc = Minecraft.getInstance()
        val clientPlayer = mc.player ?: return
        if (saved != null) return

        val server = mc.singleplayerServer
        if (server == null) {
            ScriptFXLog.info("Studio: spectator skip (не singleplayer)")
            return
        }

        server.execute {
            val sp = server.playerList.getPlayer(clientPlayer.uuid) ?: return@execute
            if (saved != null) return@execute

            val current = readGameType(sp) ?: GameType.SURVIVAL
            saved = current
            if (current != GameType.SPECTATOR) {
                sp.setGameMode(GameType.SPECTATOR)
                ScriptFXLog.info("Studio: ${sp.scoreboardName} → SPECTATOR (was $current)")
            } else {
                ScriptFXLog.info("Studio: уже SPECTATOR")
            }
        }
    }

    fun leaveStudio() {
        val prev = saved ?: return
        saved = null
        if (prev == GameType.SPECTATOR) return

        val mc = Minecraft.getInstance()
        val clientPlayer = mc.player ?: return
        val server = mc.singleplayerServer ?: return

        server.execute {
            val sp = server.playerList.getPlayer(clientPlayer.uuid) ?: return@execute
            sp.setGameMode(prev)
            ScriptFXLog.info("Studio: ${sp.scoreboardName} → $prev (restore)")
        }
    }

    private fun readGameType(player: ServerPlayer): GameType? {
        return try {
            val gm = player.gameMode()
            val m = gm.javaClass.methods.firstOrNull {
                it.parameterCount == 0 && (
                        it.name == "getGameModeForPlayer" ||
                                it.name == "getGameMode" ||
                                it.name == "gameModeForPlayer" ||
                                it.name.equals("getPlayerMode", ignoreCase = true)
                        )
            }
            when {
                m != null -> m.invoke(gm) as? GameType
                else -> GameType.SURVIVAL
            }
        } catch (_: Throwable) {
            try {
                val f = player.javaClass.methods.firstOrNull {
                    it.parameterCount == 0 && it.returnType == GameType::class.java
                }
                f?.invoke(player) as? GameType
            } catch (_: Throwable) {
                GameType.SURVIVAL
            }
        }
    }
}