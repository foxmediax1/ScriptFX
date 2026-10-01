package net.foxmediax.scriptfx.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.foxmediax.scriptfx.ScriptFX
import net.foxmediax.scriptfx.ScriptFXKeybinds
import net.foxmediax.scriptfx.gui.ControlPanelScreen
import net.foxmediax.scriptfx.config.ScriptFXConfig
import net.foxmediax.scriptfx.network.CameraPayload
import net.foxmediax.scriptfx.network.ScriptMessagePayload
import net.minecraft.server.permissions.Permissions

object ScriptFXClient : ClientModInitializer {
    private val REQUIRED_PERMISSION = Permissions.COMMANDS_GAMEMASTER

    override fun onInitializeClient() {
        ScriptFXKeybinds
        ScriptFXConfig.load()

        // Эффекты камеры (чёрный экран, крупный/мелкий текст).
        ClientPlayNetworking.registerGlobalReceiver(CameraPayload.TYPE) { payload, _ ->
            CameraOverlay.receive(payload)
        }
        HudElementRegistry.addLast(ScriptFX.id("camera_effects"), HudElement { graphics, _ ->
            CameraOverlay.render(graphics)
        })

        // Сообщения скриптов: чат или над хотбаром — зависит от настройки.
        ClientPlayNetworking.registerGlobalReceiver(ScriptMessagePayload.TYPE) { payload, _ ->
            CenterMessageOverlay.receive(payload)
        }
        HudElementRegistry.addLast(ScriptFX.id("center_message"), HudElement { graphics, _ ->
            CenterMessageOverlay.render(graphics)
        })

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            CenterMessageOverlay.clear()
            CameraOverlay.clear()
        }

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