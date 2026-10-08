package net.foxmediax.scriptfx.client.mui

import icyllis.modernui.mc.MuiModApi
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

object MuiScreens {

    @JvmStatic
    fun openTest() {
        val mc = Minecraft.getInstance()
        val screen: Screen = MuiModApi.get().createScreen(
            TestMuiFragment(),
            null,
            mc.screen,
            "ScriptFX Test"
        )
        mc.setScreen(screen)
    }

    @JvmStatic
    fun openCenteredChat() {
        val mc = Minecraft.getInstance()
        val screen: Screen = MuiModApi.get().createScreen(
            CenteredChatFragment(),
            null,
            null,
            "Chat"
        )
        mc.setScreen(screen)
    }
}