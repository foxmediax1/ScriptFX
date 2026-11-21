package net.foxmediax.scriptfx.config

import net.foxmediax.scriptfx.client.ui.StudioScreen
import net.minecraft.client.gui.screens.Screen

object ScriptFXConfigScreen {
    /** ModMenu / конфиг → наша студия (настройки ModMenu позже отдельным Screen). */
    fun build(parent: Screen): Screen = StudioScreen()
}