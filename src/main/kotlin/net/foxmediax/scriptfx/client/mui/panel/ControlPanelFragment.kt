package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.fragment.Fragment
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.util.DataSet
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.LayoutInflater
import icyllis.modernui.view.View
import icyllis.modernui.view.ViewGroup
import icyllis.modernui.widget.Button
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.mui.MuiScreens
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_ACCENT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_BTN
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_BTN_SELECTED
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_HEADER
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_LOGO
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_MUTED
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_PANEL
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_SIDEBAR
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.MATCH
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.WRAP
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.dp
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.lp
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.px1
import net.foxmediax.scriptfx.gui.FileBrowser
import net.foxmediax.scriptfx.gui.PanelSection
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.Minecraft

/**
 * Панель управления на Modern UI (замена ControlPanelScreen).
 * Всё, что трогает Minecraft (экраны), идёт через Minecraft.execute.
 */
class ControlPanelFragment : Fragment() {

    companion object {
        /** Раздел, который был открыт; нужен, чтобы вернуться в него из настроек. */
        @JvmStatic var lastSection: PanelSection? = null

        /** Один браузер на сессию: возвращаясь из настроек, остаёмся в той же папке. */
        val fileBrowser: FileBrowser by lazy { FileBrowser() }
    }

    private var selected: PanelSection? = null
    private var page: PanelPage? = null
    private var content: FrameLayout? = null
    private var breadcrumbView: TextView? = null
    private val sidebarButtons = LinkedHashMap<PanelSection, Button>()

    @Volatile private var rootView: View? = null

    private val host = object : PanelHost {
        override fun navigate(section: PanelSection?) = requestLeave { show(section) }

        override fun requestLeave(proceed: () -> Unit) {
            val p = page
            if (p == null) proceed() else p.requestLeave(proceed)
        }

        override fun refreshBreadcrumb() = this@ControlPanelFragment.refreshBreadcrumb()

        override fun closePanel() = requestLeave {
            val mc = Minecraft.getInstance()
            mc.execute { mc.setScreen(null) }
        }

        override fun openSettings() = requestLeave {
            val mc = Minecraft.getInstance()
            // из настроек возвращаемся в новую панель на том же разделе
            mc.execute { mc.setScreen(MuiScreens.createSettings { MuiScreens.openControlPanel() }) }
        }

        override fun openDocumentation() {
            ScriptFXLog.info("Документация будет перенесена на этапе 5")
        }

        override fun openScriptHints() {
            ScriptFXLog.info("Подсказки по командам будут перенесены на этапе 5")
        }
    }

    // ------------------------------------------------------------------
    // Жизненный цикл

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: DataSet?
    ): View {
        val ctx = requireContext()
        val root = FrameLayout(ctx)

        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_PANEL)
        }
        panel.addView(buildHeader(ctx), lp(MATCH, dp(ctx, 50)))
        panel.addView(divider(ctx), lp(MATCH, px1(ctx)))

        val body = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        body.addView(buildSidebar(ctx), lp(dp(ctx, 140), MATCH))
        body.addView(divider(ctx), lp(px1(ctx), MATCH))

        val pad = dp(ctx, 20)
        val c = FrameLayout(ctx).apply { setPadding(pad, pad, pad, pad) }
        content = c
        body.addView(c, LinearLayout.LayoutParams(0, MATCH, 1f))
        panel.addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val m = dp(ctx, 20)
        root.addView(
            panel,
            FrameLayout.LayoutParams(MATCH, MATCH).apply {
                leftMargin = m; topMargin = m; rightMargin = m; bottomMargin = m
            }
        )

        rootView = root
        show(lastSection)
        return root
    }

    override fun onDestroyView() {
        rootView = null
        page?.onDestroy()
        page = null
        content = null
        breadcrumbView = null
        sidebarButtons.clear()
        super.onDestroyView()
    }

    // ------------------------------------------------------------------
    // Esc (вызывается из PanelCallback)

    /** Из isBackKey (другой поток): надо ли перехватить Esc. Только чтение флагов. */
    fun interceptsEscape(): Boolean = page?.interceptsEscape == true

    /** Перехваченный Esc обрабатывается уже в UI-потоке. */
    fun handleEscape() {
        rootView?.post { page?.onEscape() }
    }

    // ------------------------------------------------------------------
    // Страницы

    private fun show(section: PanelSection?) {
        val ctx = context ?: return
        page?.onDestroy()
        selected = section
        lastSection = section

        val p = createPage(section)
        page = p
        content?.apply {
            removeAllViews()
            addView(p.createView(ctx), FrameLayout.LayoutParams(MATCH, MATCH))
        }
        refreshSidebar()
        refreshBreadcrumb()
    }

    /** Сюда на следующих этапах подключаются настоящие страницы. */
    private fun createPage(section: PanelSection?): PanelPage = when (section) {
        null -> HomePage(host)
        PanelSection.PROJECTS -> ProjectsPage(host, fileBrowser)
        else -> PlaceholderPage(host, section.displayName)
    }

    private fun refreshSidebar() {
        sidebarButtons.forEach { (section, btn) ->
            btn.background = ColorDrawable(if (section == selected) COL_BTN_SELECTED else COL_BTN)
        }
    }

    private fun refreshBreadcrumb() {
        val tail = page?.breadcrumb ?: selected?.displayName ?: "Главное меню"
        breadcrumbView?.text = "Панель управления -> $tail"
    }

    // ------------------------------------------------------------------
    // Шапка и боковое меню

    private fun buildHeader(ctx: Context): View {
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ColorDrawable(COL_HEADER)
        }

        // логотип
        val logo = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 10), 0, dp(ctx, 10), 0)
            background = ColorDrawable(COL_LOGO)
        }
        val title = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        title.addView(TextView(ctx).apply { text = "Script"; textSize = 16f; setTextColor(0xFFFFFFFF.toInt()) }, lp(WRAP, WRAP))
        title.addView(TextView(ctx).apply { text = "FX"; textSize = 16f; setTextColor(COL_ACCENT) }, lp(WRAP, WRAP))
        logo.addView(title, lp(WRAP, WRAP))
        logo.addView(TextView(ctx).apply { text = "Панель управления"; textSize = 11f; setTextColor(COL_MUTED) }, lp(WRAP, WRAP))
        header.addView(logo, lp(dp(ctx, 140), MATCH))
        header.addView(divider(ctx), lp(px1(ctx), MATCH))

        // слева: домой / вверх
        val leftNav = navColumn(ctx)
        leftNav.addView(PanelUi.iconButton(ctx, "⌂") { host.navigate(null) }, navLp(ctx, true))
        leftNav.addView(
            PanelUi.iconButton(ctx, "▲") {
                host.requestLeave {
                    val p = page
                    if (p == null || !p.goUp()) show(null)
                }
            },
            navLp(ctx, false)
        )
        header.addView(leftNav, lp(WRAP, MATCH))
        header.addView(divider(ctx), lp(px1(ctx), MATCH))

        // хлебные крошки
        val crumbs = TextView(ctx).apply {
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 12), 0, dp(ctx, 12), 0)
        }
        breadcrumbView = crumbs
        header.addView(crumbs, LinearLayout.LayoutParams(0, MATCH, 1f))
        header.addView(divider(ctx), lp(px1(ctx), MATCH))

        // справа: закрыть / справка
        val rightNav = navColumn(ctx)
        rightNav.addView(PanelUi.iconButton(ctx, "X") { host.closePanel() }, navLp(ctx, true))
        rightNav.addView(PanelUi.iconButton(ctx, "?") { host.openDocumentation() }, navLp(ctx, false))
        header.addView(rightNav, lp(WRAP, MATCH))
        return header
    }

    private fun navColumn(ctx: Context) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(ctx, 8), 0, dp(ctx, 8), 0)
    }

    private fun navLp(ctx: Context, first: Boolean) =
        lp(dp(ctx, 22), dp(ctx, 22)).apply { if (!first) topMargin = dp(ctx, 4) }

    private fun buildSidebar(ctx: Context): View {
        val side = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
            background = ColorDrawable(COL_SIDEBAR)
        }
        for (section in PanelSection.entries) {
            val btn = PanelUi.flatButton(ctx, section.displayName) {
                if (section == PanelSection.SETTINGS) host.openSettings() else host.navigate(section)
            }
            if (section != PanelSection.SETTINGS) sidebarButtons[section] = btn
            side.addView(btn, lp(MATCH, WRAP).apply { topMargin = dp(ctx, 6) })
        }
        return side
    }

    private fun divider(ctx: Context): View = View(ctx).apply {
        background = ColorDrawable(PanelUi.COL_DIVIDER)
    }
}