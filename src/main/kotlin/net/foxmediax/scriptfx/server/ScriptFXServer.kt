package net.foxmediax.scriptfx.server

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.foxmediax.scriptfx.scriptengine.ScriptManager

object ScriptFXServer : ModInitializer {

    private var tickCounter = 0L

    override fun onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register { ScriptManager.reload() }

        ServerTickEvents.END_SERVER_TICK.register {
            tickCounter++
            ScriptManager.tickAll(tickCounter)
        }
    }
}