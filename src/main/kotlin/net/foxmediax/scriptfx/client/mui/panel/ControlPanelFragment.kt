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
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_CONTENT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_DIVIDER
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
        @JvmStatic var lastSection: PanelSection? = null
        val fileBrowser: FileBrowser by lazy { FileBrowser() }
    }

    private var selected: PanelSection? = null
    private var page: PanelPage? = null
    private var content: FrameLayout? = null
    private var breadcrumbView: TextView? = null
    private val sidebarButtons = LinkedHashMap<PanelSection, Button>()

    @Volatile private var rootView: View? = null

    private val host: PanelHost = object : PanelHost {
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
            mc.execute {
                mc.setScreen(MuiScreens.createSettings { MuiScreens.openControlPanel() })
            }
        }

        override fun openDocumentation() {
            requestLeave { openDocsPage() }
        }

        override fun openScriptHints() {
            ScriptFXLog.info("Откройте подсказки из редактора скрипта")
        }
    }

    // ------------------------------------------------------------------

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

        // шапка повыше
        panel.addView(buildHeader(ctx), lp(MATCH, dp(ctx, 58)))
        panel.addView(divider(ctx), lp(MATCH, px1(ctx)))

        val body = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }

        // сайдбар пошире
        body.addView(buildSidebar(ctx), lp(dp(ctx, 156), MATCH))
        body.addView(divider(ctx), lp(px1(ctx), MATCH))

        // контент: серая рамка + внутри чёрный экран
        val contentPad = dp(ctx, 14)
        val c = FrameLayout(ctx).apply {
            setPadding(contentPad, contentPad, contentPad, contentPad)
            background = ColorDrawable(COL_HEADER)
        }
        content = c
        body.addView(c, LinearLayout.LayoutParams(0, MATCH, 1f))
        panel.addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val dm = ctx.resources.displayMetrics
        val panelW = (dm.widthPixels * 0.78f).toInt().coerceIn(dp(ctx, 560), dp(ctx, 960))
        val panelH = (dm.heightPixels * 0.72f).toInt().coerceIn(dp(ctx, 380), dp(ctx, 640))

        root.addView(
            panel,
            FrameLayout.LayoutParams(panelW, panelH, Gravity.CENTER)
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

    fun interceptsEscape(): Boolean = page?.interceptsEscape == true

    fun handleEscape() {
        rootView?.post { page?.onEscape() }
    }

    // ------------------------------------------------------------------

    private fun show(section: PanelSection?) {
        val ctx = context ?: return
        page?.onDestroy()
        selected = section
        lastSection = section

        val p = createPage(section)
        page = p

        val black = FrameLayout(ctx).apply {
            background = ColorDrawable(COL_CONTENT)
        }
        black.addView(p.createView(ctx), FrameLayout.LayoutParams(MATCH, MATCH))

        content?.apply {
            removeAllViews()
            addView(black, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        refreshSidebar()
        refreshBreadcrumb()
    }

    private fun createPage(section: PanelSection?): PanelPage = when (section) {
        null -> HomePage(host)
        PanelSection.PROJECTS -> ProjectsPage(host, fileBrowser)
        PanelSection.CUTSCENES -> CutscenesPage(host)
        PanelSection.NPC_EDITOR -> NpcEditorPage(host)
        PanelSection.LOGS -> LogsPage(host)
        else -> PlaceholderPage(host, section.displayName)
    }

    private fun refreshSidebar() {
        sidebarButtons.forEach { (section, btn) ->
            btn.background = ColorDrawable(
                if (section == selected) COL_BTN_SELECTED else COL_BTN
            )
        }
    }

    private fun refreshBreadcrumb() {
        val tail = page?.breadcrumb ?: selected?.displayName ?: "Главное меню"
        breadcrumbView?.text = "Панель управления -> $tail"
    }

    // ------------------------------------------------------------------

    private fun buildHeader(ctx: Context): View {
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ColorDrawable(COL_HEADER)
        }

        val logo = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 10), dp(ctx, 4), dp(ctx, 10), dp(ctx, 4))
            background = ColorDrawable(COL_LOGO)
        }

        val title = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        title.addView(
            TextView(ctx).apply {
                text = "Script"
                textSize = 16f
                setTextColor(0xFFFFFFFF.toInt())
            },
            lp(WRAP, WRAP)
        )
        title.addView(
            TextView(ctx).apply {
                text = "FX"
                textSize = 16f
                setTextColor(COL_ACCENT)
            },
            lp(WRAP, WRAP)
        )
        logo.addView(title, lp(WRAP, WRAP))

        logo.addView(
            View(ctx).apply { background = ColorDrawable(COL_DIVIDER) },
            lp(MATCH, px1(ctx)).apply {
                topMargin = dp(ctx, 3)
                bottomMargin = dp(ctx, 2)
            }
        )
        logo.addView(
            TextView(ctx).apply {
                text = "Панель управления"
                textSize = 11f
                setTextColor(COL_MUTED)
            },
            lp(WRAP, WRAP)
        )
        header.addView(logo, lp(dp(ctx, 156), MATCH))
        header.addView(divider(ctx), lp(px1(ctx), MATCH))

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

        val crumbs = TextView(ctx).apply {
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 12), 0, dp(ctx, 12), 0)
        }
        breadcrumbView = crumbs
        header.addView(crumbs, LinearLayout.LayoutParams(0, MATCH, 1f))
        header.addView(divider(ctx), lp(px1(ctx), MATCH))

        val rightNav = navColumn(ctx)
        rightNav.addView(PanelUi.iconButton(ctx, "X") { host.closePanel() }, navLp(ctx, true))
        rightNav.addView(PanelUi.iconButton(ctx, "?") { host.openDocumentation() }, navLp(ctx, false))
        header.addView(rightNav, lp(WRAP, MATCH))
        return header
    }

    private fun navColumn(ctx: Context) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(ctx, 6), 0, dp(ctx, 6), 0)
    }

    private fun navLp(ctx: Context, first: Boolean) =
        lp(dp(ctx, 26), dp(ctx, 24)).apply { if (!first) topMargin = dp(ctx, 4) }

    private fun buildSidebar(ctx: Context): View {
        val side = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 10), dp(ctx, 10), dp(ctx, 10), dp(ctx, 10))
            background = ColorDrawable(COL_SIDEBAR)
        }
        val order = listOf(
            PanelSection.PROJECTS,
            PanelSection.CUTSCENES,
            PanelSection.NPC_EDITOR,
            PanelSection.LOGS,
            PanelSection.SETTINGS
        )
        for (section in order) {
            val btn = PanelUi.sidebarButton(ctx, section.displayName) {
                if (section == PanelSection.SETTINGS) host.openSettings()
                else host.navigate(section)
            }
            if (section != PanelSection.SETTINGS) sidebarButtons[section] = btn
            side.addView(
                btn,
                lp(MATCH, WRAP).apply {
                    topMargin = if (side.childCount == 0) 0 else dp(ctx, 8)
                }
            )
        }
        return side
    }

    private fun divider(ctx: Context): View = View(ctx).apply {
        background = ColorDrawable(COL_DIVIDER)
    }

    private fun openDocsPage() {
        val ctx = context ?: return
        page?.onDestroy()
        selected = null
        lastSection = null
        val p = DocsPage(host)
        page = p
        val black = FrameLayout(ctx).apply {
            background = ColorDrawable(COL_CONTENT)
        }
        black.addView(p.createView(ctx), FrameLayout.LayoutParams(MATCH, MATCH))
        content?.apply {
            removeAllViews()
            addView(black, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        refreshSidebar()
        breadcrumbView?.text = "Панель управления -> Документация"
    }
}