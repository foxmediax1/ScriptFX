package net.foxmediax.scriptfx.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.foxmediax.scriptfx.ScriptFX
import net.foxmediax.scriptfx.ScriptFXKeybinds
import net.foxmediax.scriptfx.client.mui.MuiScreens
import net.foxmediax.scriptfx.config.ScriptFXConfig
import net.foxmediax.scriptfx.network.CameraPayload
import net.foxmediax.scriptfx.network.NpcDialogOpenPayload
import net.foxmediax.scriptfx.network.NpcInteractListenPayload
import net.foxmediax.scriptfx.network.ScriptMessagePayload
import net.foxmediax.scriptfx.npc.NpcEntities
import net.foxmediax.scriptfx.npc.NpcInteractClient
import net.minecraft.server.permissions.Permissions
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents

object ScriptFXClient : ClientModInitializer {
    private val REQUIRED_PERMISSION = Permissions.COMMANDS_GAMEMASTER

    override fun onInitializeClient() {
        ScriptFXKeybinds
        ScriptFXConfig.load()

        ClientPlayNetworking.registerGlobalReceiver(CameraPayload.TYPE) { payload, _ ->
            CameraOverlay.receive(payload)
        }
        HudElementRegistry.addLast(ScriptFX.id("camera_effects"), HudElement { graphics, _ ->
            CameraOverlay.render(graphics)
        })

        HudElementRegistry.addLast(ScriptFX.id("npc_interact_prompt"), HudElement { graphics, _ ->
            NpcInteractClient.render(graphics)
        })

        ClientReceiveMessageEvents.CHAT.register { message, signed, _, params, _ ->
            if (signed == null) {
                CenterMessageOverlay.receiveSystem(message.string)
                return@register
            }
            CenterMessageOverlay.receivePlayerChat(
                params.name().string,
                signed.sender(),
                signed.decoratedContent().string
            )
        }

        ClientPlayNetworking.registerGlobalReceiver(ScriptMessagePayload.TYPE) { payload, _ ->
            CenterMessageOverlay.receive(payload)
        }
        HudElementRegistry.addLast(ScriptFX.id("center_message"), HudElement { graphics, _ ->
            CenterMessageOverlay.render(graphics)
        })

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            CenterMessageOverlay.clear()
            CameraOverlay.clear()
            CutsceneClient.clear()
        }

        EntityRendererRegistry.register(NpcEntities.SCRIPT_NPC, ::ScriptNpcRenderer)

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            NpcInteractClient.tick()
            NpcDialogClient.tick()

            while (ScriptFXKeybinds.openControlPanel.consumeClick()) {
                val player = client.player ?: continue
                if (client.screen == null && player.permissions().hasPermission(REQUIRED_PERMISSION)) {
                    MuiScreens.openControlPanel()
                }
            }
        }

        CutsceneClient.init()

        ClientPlayNetworking.registerGlobalReceiver(NpcInteractListenPayload.TYPE) { payload, _ ->
            NpcInteractClient.setListenKey(payload.key)
        }

        ClientPlayNetworking.registerGlobalReceiver(NpcDialogOpenPayload.TYPE) { payload, _ ->
            NpcDialogClient.open(payload)
        }
    }
}