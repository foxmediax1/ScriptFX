package net.foxmediax.scriptfx.scriptengine

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

class ScriptContext(
    val server: MinecraftServer,
    val player: ServerPlayer? = null
) {
    /** Переменные рантайма скрипта (dialog_button и т.д.). */
    val vars: MutableMap<String, String> = mutableMapOf()
}