package net.foxmediax.scriptfx.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.foxmediax.scriptfx.ScriptFX
import net.foxmediax.scriptfx.ScriptFXKeybinds
import net.foxmediax.scriptfx.client.mui.MuiScreens
import net.foxmediax.scriptfx.client.particles.ParticleRuntime
import net.foxmediax.scriptfx.client.particles.ParticleWorldRenderer
import net.foxmediax.scriptfx.config.ScriptFXConfig
import net.foxmediax.scriptfx.network.CameraPayload
import net.foxmediax.scriptfx.network.MediaPayload
import net.foxmediax.scriptfx.network.NpcDialogOpenPayload
import net.foxmediax.scriptfx.network.NpcInteractListenPayload
import net.foxmediax.scriptfx.network.ParticlePayload
import net.foxmediax.scriptfx.network.QuestSyncPayload
import net.foxmediax.scriptfx.network.QuestToastPayload
import net.foxmediax.scriptfx.network.ScriptMessagePayload
import net.foxmediax.scriptfx.npc.NpcEntities
import net.foxmediax.scriptfx.npc.NpcInteractClient
import net.foxmediax.scriptfx.particles.ParticleRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.server.permissions.Permissions

object ScriptFXClient : ClientModInitializer {

    private val REQUIRED_PERMISSION = Permissions.COMMANDS_GAMEMASTER
    private var triedEnableCustomsPack = false

    override fun onInitializeClient() {

        ScriptFXKeybinds
        ScriptFXConfig.load()
        ParticleRegistry.reload()
        ParticleWorldRenderer.init()
        net.foxmediax.scriptfx.media.MediaRegistry.reload()

        LocalNpcAssets.clear()
        LocalNpcAssets.tryEnableResourcePack()

        ClientPlayNetworking.registerGlobalReceiver(CameraPayload.TYPE) { payload, _ ->
            CameraOverlay.receive(payload)
        }

        HudElementRegistry.addLast(
            ScriptFX.id("camera_effects"),
            HudElement { graphics, _ -> CameraOverlay.render(graphics) }
        )

        HudElementRegistry.addLast(
            ScriptFX.id("npc_interact_prompt"),
            HudElement { graphics, _ -> NpcInteractClient.render(graphics) }
        )

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

        HudElementRegistry.addLast(
            ScriptFX.id("center_message"),
            HudElement { graphics, _ -> CenterMessageOverlay.render(graphics) }
        )

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            // После входа в мир: попробовать включить customs-пак один раз
            if (!triedEnableCustomsPack) {
                triedEnableCustomsPack = true
                Minecraft.getInstance().execute {
                    LocalNpcAssets.tryEnableResourcePack()
                }
            }
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            CenterMessageOverlay.clear()
            CameraOverlay.clear()
            CutsceneClient.clear()
            NpcPreviewHelper.clear()
            QuestClient.clear()
            ParticleRuntime.clear()
            LocalNpcAssets.clear()
            net.foxmediax.scriptfx.client.media.MediaClient.clear()
            triedEnableCustomsPack = false
        }

        EntityRendererRegistry.register(NpcEntities.SCRIPT_NPC, ::ScriptNpcRenderer)

        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            ScreenEvents.afterExtract(screen).register { _, graphics, _, _, _ ->
                NpcPreviewHelper.renderInGui(graphics)
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            ParticleRuntime.tick(1f / 20f)

            NpcInteractClient.tick()
            NpcPreviewHelper.clientTick()
            NpcDialogClient.tick()

            while (ScriptFXKeybinds.openControlPanel.consumeClick()) {
                val player = client.player ?: continue
                if (client.screen == null &&
                    player.permissions().hasPermission(REQUIRED_PERMISSION)
                ) {
                    MuiScreens.openControlPanel()
                }
            }

            while (ScriptFXKeybinds.openQuestJournal.consumeClick()) {
                if (client.player != null && client.screen == null) {
                    MuiScreens.openQuestJournal()
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

        ClientPlayNetworking.registerGlobalReceiver(QuestSyncPayload.TYPE) { payload, _ ->
            QuestClient.receiveSync(payload.json)
        }
        ClientPlayNetworking.registerGlobalReceiver(QuestToastPayload.TYPE) { payload, _ ->
            QuestClient.receiveToast(payload)
        }

        HudElementRegistry.addLast(
            ScriptFX.id("quest_toast"),
            HudElement { graphics, _ -> QuestClient.renderToast(graphics) }
        )

        ClientPlayNetworking.registerGlobalReceiver(ParticlePayload.TYPE) { payload, _ ->
            ParticleRuntime.handlePayload(payload)
        }

        ClientPlayNetworking.registerGlobalReceiver(MediaPayload.TYPE) { payload, _ ->
            net.foxmediax.scriptfx.client.media.MediaClient.handle(payload)
        }

        HudElementRegistry.addLast(
            ScriptFX.id("scriptfx_video"),
            HudElement { graphics, _ ->
                val mc = Minecraft.getInstance()
                val sw = mc.window.guiScaledWidth
                val sh = mc.window.guiScaledHeight
                net.foxmediax.scriptfx.client.media.VideoPlayer.render(sw, sh) { tex, x, y, w, h ->
                    graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        tex,
                        x, y,
                        0f, 0f,
                        w, h,
                        w, h,
                        w, h,
                        0xFFFFFFFF.toInt()
                    )
                }
            }
        )
    }
}