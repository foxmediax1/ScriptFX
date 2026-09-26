package net.foxmediax.scriptfx.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.foxmediax.scriptfx.ScriptFXKeybinds
import net.foxmediax.scriptfx.gui.ControlPanelScreen
import net.foxmediax.scriptfx.config.ScriptFXConfig
import net.minecraft.server.permissions.Permissions

object ScriptFXClient : ClientModInitializer {
    private val REQUIRED_PERMISSION = Permissions.COMMANDS_GAMEMASTER

    override fun onInitializeClient() {
        ScriptFXKeybinds
        ScriptFXConfig.load()

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (ScriptFXKeybinds.openControlPanel.consumeClick()) {
                val player = client.player ?: continue
                if (client.screen == null && player.permissions().hasPermission(REQUIRED_PERMISSION)) {
                    client.setScreen(ControlPanelScreen())
                }
            }
        }
    }
}