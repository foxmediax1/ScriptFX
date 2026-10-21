package net.foxmediax.scriptfx.client

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.foxmediax.scriptfx.client.media.MediaClient
import net.foxmediax.scriptfx.config.ScriptFXConfig
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Добавляет ползунок «ScriptFX Video» на экран «Музыка и звуки».
 */
object ScriptFXSoundOptions {

    fun init() {
        ScreenEvents.AFTER_INIT.register { client, screen, scaledWidth, scaledHeight ->
            if (!isSoundOptionsScreen(screen)) return@register
            try {
                injectSlider(screen, scaledWidth, scaledHeight)
            } catch (t: Throwable) {
                ScriptFXLog.warn("ScriptFXSoundOptions: ${t.message}")
            }
        }
    }

    private fun isSoundOptionsScreen(screen: Screen): Boolean {
        val n = screen.javaClass.name
        val simple = screen.javaClass.simpleName
        return simple.contains("SoundOptions", ignoreCase = true) ||
                n.contains("SoundOptions", ignoreCase = true) ||
                simple.contains("SoundSettings", ignoreCase = true) ||
                // 26.x иногда OptionsSubScreen + title
                (simple.contains("Options", ignoreCase = true) &&
                        screen.title.string.contains("sound", ignoreCase = true))
    }

    private fun injectSlider(screen: Screen, scaledWidth: Int, scaledHeight: Int) {
        // Не дублировать при повторном init
        val buttons = Screens.getButtons(screen)
        if (buttons.any { it is ScriptFXVideoVolumeSlider }) return

        val w = 150
        val h = 20
        // Нижняя зона экрана звуков (под стандартными слайдерами / над Done)
        val x = scaledWidth / 2 - w - 5
        val y = scaledHeight - 52

        val slider = ScriptFXVideoVolumeSlider(x, y, w, h, ScriptFXConfig.videoVolume.toDouble())
        buttons.add(slider)
        ScriptFXLog.info("ScriptFXSoundOptions: slider injected")
    }

    private class ScriptFXVideoVolumeSlider(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        initial: Double
    ) : AbstractSliderButton(x, y, width, height, Component.empty(), initial.coerceIn(0.0, 1.0)) {

        init {
            updateMessage()
        }

        override fun updateMessage() {
            val pct = (value * 100.0).toInt()
            message = Component.literal("ScriptFX Video: $pct%")
        }

        override fun applyValue() {
            ScriptFXConfig.videoVolume = value.toFloat().coerceIn(0f, 1f)
            ScriptFXConfig.save()
            MediaClient.applyVideoVolumeToPlaying()
        }
    }
}