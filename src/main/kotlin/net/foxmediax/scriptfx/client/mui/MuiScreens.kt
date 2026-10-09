package net.foxmediax.scriptfx.client.mui

import icyllis.modernui.mc.MuiModApi
import icyllis.modernui.mc.ScreenCallback
import icyllis.modernui.view.KeyEvent
import net.foxmediax.scriptfx.client.mui.panel.ControlPanelFragment
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.glfw.GLFW

object MuiScreens {

    /** Чат: без затемнения и размытия фона, без паузы игры. */
    private object ChatCallback : ScreenCallback {
        override fun hasDefaultBackground(): Boolean = false
        override fun shouldBlurBackground(): Boolean = false
        override fun isPauseScreen(): Boolean = false
    }

    /** Настройки: в мире мир виден без затемнения (цвета видны честно), в меню фон обычный. */
    private object SettingsCallback : ScreenCallback {
        override fun hasDefaultBackground(): Boolean = Minecraft.getInstance().level == null
        override fun shouldBlurBackground(): Boolean = false
        override fun isPauseScreen(): Boolean = true
    }

    /** Диалог NPC: без затемнения, без паузы, Esc не закрывает. */
    private object NpcDialogCallback : ScreenCallback {
        override fun hasDefaultBackground(): Boolean = false
        override fun shouldBlurBackground(): Boolean = false
        override fun isPauseScreen(): Boolean = false
        override fun isBackKey(keyCode: Int, event: KeyEvent): Boolean = false
    }

    /** Панель: пока есть что защищать (диалог, несохранённые правки), Esc обрабатывает сама панель. */
    private class PanelCallback(private val fragment: ControlPanelFragment) : ScreenCallback {
        override fun hasDefaultBackground(): Boolean = true
        override fun shouldBlurBackground(): Boolean = false
        override fun isPauseScreen(): Boolean = true

        override fun isBackKey(keyCode: Int, event: KeyEvent): Boolean {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE && fragment.interceptsEscape()) {
                fragment.handleEscape()
                return false          // экран не закрываем, решает страница
            }
            return super.isBackKey(keyCode, event)
        }
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

    @JvmStatic
    fun openControlPanel() {
        val mc = Minecraft.getInstance()
        val fragment = ControlPanelFragment()
        mc.setScreen(
            MuiModApi.get().createScreen(fragment, PanelCallback(fragment), null, "ScriptFX")
        )
    }

    /** Экран настроек мода. Возвращает Screen (как раньше Cloth), открывает вызывающий. */
    @JvmStatic
    fun createSettings(parent: Screen?): Screen =
        MuiModApi.get().createScreen(
            MuiSettingsFragment(parent),
            SettingsCallback,
            parent,
            "ScriptFX"
        )

    /** Настройки с произвольным возвратом (для панели: открыть её заново). */
    @JvmStatic
    fun createSettings(onBack: () -> Unit): Screen =
        MuiModApi.get().createScreen(
            MuiSettingsFragment(null, onBack),
            SettingsCallback,
            null,
            "ScriptFX"
        )

    @JvmStatic
    fun createNpcDialog(model: NpcDialogModel): Screen =
        MuiModApi.get().createScreen(NpcDialogFragment(model), NpcDialogCallback, null, "NPC")
}