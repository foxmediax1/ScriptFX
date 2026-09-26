package net.foxmediax.scriptfx.scriptengine

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/** player — конкретный игрок, если скрипт привязан к нему (например, сработал у него checkpoint). */
class ScriptContext(
    val server: MinecraftServer,
    val player: ServerPlayer? = null
)