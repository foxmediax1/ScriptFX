package net.foxmediax.scriptfx.config

import net.foxmediax.scriptfx.client.mui.MuiScreens
import net.minecraft.client.gui.screens.Screen

object ScriptFXConfigScreen {
    fun build(parent: Screen): Screen = MuiScreens.createSettings(parent)
}