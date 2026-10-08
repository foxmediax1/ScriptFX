package net.foxmediax.scriptfx.client.mui

import icyllis.modernui.mc.MuiModApi
import icyllis.modernui.mc.ScreenCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

object MuiScreens {

    /** Чат: без затемнения и размытия фона, без паузы игры. */
    private object ChatCallback : ScreenCallback {
        override fun hasDefaultBackground(): Boolean = false
        override fun shouldBlurBackground(): Boolean = false
        override fun isPauseScreen(): Boolean = false
    }

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
            ChatCallback,
            null,
            "Chat"
        )
        mc.setScreen(screen)
    }
}