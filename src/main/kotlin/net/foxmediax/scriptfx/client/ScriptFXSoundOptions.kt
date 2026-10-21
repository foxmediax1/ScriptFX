package net.foxmediax.scriptfx.client

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.foxmediax.scriptfx.client.media.MediaClient
import net.foxmediax.scriptfx.config.ScriptFXConfig
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

object ScriptFXSoundOptions {

    @Volatile
    private var slider: ScriptFXVideoVolumeSlider? = null

    fun init() {
        ScreenEvents.AFTER_INIT.register { _, screen, scaledWidth, scaledHeight ->
            slider = null
            if (!isSoundOptionsScreen(screen)) return@register
            try {
                injectSlider(screen, scaledWidth, scaledHeight)
                wireMouse(screen)
            } catch (t: Throwable) {
                ScriptFXLog.warn("ScriptFXSoundOptions: ${t.message}")
                t.printStackTrace()
            }
        }
        ScriptFXLog.info("ScriptFXSoundOptions: registered")
    }

    private fun isSoundOptionsScreen(screen: Screen): Boolean {
        val n = screen.javaClass.name
        val simple = screen.javaClass.simpleName
        val title = runCatching { screen.title.string.lowercase() }.getOrDefault("")
        val titleOk = listOf("sound", "music", "audio", "звук", "музык").any { title.contains(it) }
        return simple.contains("SoundOptions", true) ||
                n.contains("SoundOptions", true) ||
                simple.contains("SoundSettings", true) ||
                (simple.contains("Options", true) && titleOk) ||
                titleOk
    }

    private fun injectSlider(screen: Screen, scaledWidth: Int, scaledHeight: Int) {
        val w = 150
        val h = 20
        val x = scaledWidth / 2 - 155
        val y = (scaledHeight - 60).coerceAtLeast(8)

        val s = ScriptFXVideoVolumeSlider(
            x, y, w, h,
            ScriptFXConfig.videoVolume.toDouble().coerceIn(0.0, 1.0)
        )
        slider = s
        addWidgetToScreen(screen, s)
        runCatching {
            val list = Screens.getWidgets(screen)
            if (list.none { it is ScriptFXVideoVolumeSlider }) list.add(s)
        }
        ScriptFXLog.info("ScriptFXSoundOptions: slider at $x,$y")
    }

    private fun wireMouse(screen: Screen) {
        // Забрать клик у списка опций, если попали в наш слайдер
        ScreenMouseEvents.allowMouseClick(screen).register { _, event ->
            val s = slider ?: return@register true
            if (isOver(s, event)) false else true
        }

        ScreenMouseEvents.beforeMouseClick(screen).register { _, event ->
            val s = slider ?: return@register
            if (!isOver(s, event)) return@register
            if (event.button() != 0) return@register
            applyMouseX(s, event.x())
        }

        ScreenMouseEvents.beforeMouseDrag(screen).register { _, event, _, _ ->
            val s = slider ?: return@register
            if (event.button() != 0) return@register
            // drag только если начали на слайдере или курсор над ним
            if (isOver(s, event) || isNear(s, event.x(), event.y())) {
                applyMouseX(s, event.x())
            }
        }

        ScreenMouseEvents.afterMouseDrag(screen).register { _, event, _, _, _ ->
            val s = slider
            if (s != null && event.button() == 0 && isNear(s, event.x(), event.y())) {
                applyMouseX(s, event.x())
                true
            } else {
                false
            }
        }
    }

    private fun isOver(s: ScriptFXVideoVolumeSlider, event: MouseButtonEvent): Boolean {
        val mx = event.x()
        val my = event.y()
        return mx >= s.wx() && mx <= s.wx() + s.ww() &&
                my >= s.wy() && my <= s.wy() + s.wh()
    }

    private fun isNear(s: ScriptFXVideoVolumeSlider, mx: Double, my: Double): Boolean {
        // чуть шире по Y, чтобы drag не срывался
        return mx >= s.wx() - 4 && mx <= s.wx() + s.ww() + 4 &&
                my >= s.wy() - 8 && my <= s.wy() + s.wh() + 8
    }

    private fun applyMouseX(s: ScriptFXVideoVolumeSlider, mouseX: Double) {
        val rel = ((mouseX - (s.wx() + 4)) / (s.ww() - 8).toDouble()).coerceIn(0.0, 1.0)
        s.setSliderValue(rel)
    }

    private fun addWidgetToScreen(screen: Screen, widget: AbstractWidget): Boolean {
        for (m in screen.javaClass.methods + Screen::class.java.declaredMethods) {
            if (m.parameterCount != 1) continue
            if (!m.name.contains("addRenderable", ignoreCase = true) && m.name != "addWidget") continue
            try {
                m.isAccessible = true
                m.invoke(screen, widget)
                return true
            } catch (_: Throwable) {
            }
        }
        return false
    }

    class ScriptFXVideoVolumeSlider(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        initial: Double
    ) : AbstractSliderButton(x, y, width, height, Component.empty(), initial) {

        init {
            updateMessage()
            active = true
            visible = true
        }

        fun wx(): Int = try { getX() } catch (_: Throwable) { x }
        fun wy(): Int = try { getY() } catch (_: Throwable) { y }
        fun ww(): Int = try { getWidth() } catch (_: Throwable) { width }
        fun wh(): Int = try { getHeight() } catch (_: Throwable) { height }

        fun setSliderValue(v: Double) {
            val nv = v.coerceIn(0.0, 1.0)
            if (nv == value) return
            value = nv
            applyValue()
            updateMessage()
        }

        override fun updateMessage() {
            message = Component.literal("ScriptFX Video: ${(value * 100.0).toInt()}%")
        }

        override fun applyValue() {
            ScriptFXConfig.videoVolume = value.toFloat().coerceIn(0f, 1f)
            ScriptFXConfig.save()
            MediaClient.applyVideoVolumeToPlaying()
        }
    }
}