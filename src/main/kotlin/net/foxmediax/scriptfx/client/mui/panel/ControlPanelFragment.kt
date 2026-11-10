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
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_BTN
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_BTN_SELECTED
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_CONTENT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_HEADER
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_MUTED
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_PANEL
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_STATUS
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_TEXT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_ACCENT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.MATCH
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.WRAP
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.dp
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.lp
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.px1
import net.foxmediax.scriptfx.gui.FileBrowser
import net.foxmediax.scriptfx.gui.PanelSection
import net.minecraft.client.Minecraft

class ControlPanelFragment : Fragment() {

    companion object {
        @JvmStatic
        var lastSection: PanelSection? = null

        val fileBrowser: FileBrowser by lazy { FileBrowser() }
    }

    private var selected: PanelSection? = null
    private var page: PanelPage? = null
    private var content: FrameLayout? = null
    private var statusText: TextView? = null

    private var topBar: View? = null
    private var topBarDivider: View? = null

    private val tabButtons = LinkedHashMap<PanelSection, Button>()
    private var docsTabBtn: Button? = null
    private var docsOpen = false

    @Volatile
    private var rootView: View? = null

    private val host: PanelHost = object : PanelHost {
        override fun navigate(section: PanelSection?) = requestLeave { show(section) }

        override fun requestLeave(proceed: () -> Unit) {
            val p = page
            if (p == null) proceed() else p.requestLeave(proceed)
        }

        override fun refreshBreadcrumb() = updateStatus()

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
            requestLeave { openDocsPage() }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: DataSet?
    ): View {
        val ctx = requireContext()
        val root = FrameLayout(ctx)

        val shell = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_PANEL)
        }

        val top = buildTopBar(ctx)
        topBar = top
        shell.addView(top, lp(MATCH, dp(ctx, 52)))

        val div = PanelUi.divider(ctx)
        topBarDivider = div
        shell.addView(div, lp(MATCH, px1(ctx)))

        val c = FrameLayout(ctx).apply {
            background = ColorDrawable(COL_CONTENT)
        }
        content = c
        shell.addView(c, LinearLayout.LayoutParams(MATCH, 0, 1f))

        shell.addView(PanelUi.divider(ctx), lp(MATCH, px1(ctx)))
        shell.addView(buildStatusBar(ctx), lp(MATCH, dp(ctx, 28)))

        val dm = ctx.resources.displayMetrics
        root.addView(
            shell,
            FrameLayout.LayoutParams(dm.widthPixels, dm.heightPixels, Gravity.CENTER)
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
        statusText = null
        topBar = null
        topBarDivider = null
        docsTabBtn = null
        tabButtons.clear()
        super.onDestroyView()
    }

    fun interceptsEscape(): Boolean = page?.interceptsEscape == true

    fun handleEscape() {
        rootView?.post { page?.onEscape() }
    }

    private fun show(section: PanelSection?) {
        val ctx = context ?: return
        page?.onDestroy()
        selected = section
        lastSection = section
        docsOpen = false

        setChromeVisible(section != null)

        val p = createPage(section)
        page = p

        val holder = FrameLayout(ctx).apply {
            background = ColorDrawable(COL_CONTENT)
        }
        holder.addView(p.createView(ctx), FrameLayout.LayoutParams(MATCH, MATCH))
        content?.apply {
            removeAllViews()
            addView(holder, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        refreshTabs()
        updateStatus()
    }

    private fun createPage(section: PanelSection?): PanelPage = when (section) {
        null -> HomePage(host)
        PanelSection.PROJECTS -> ProjectsPage(host, fileBrowser)
        PanelSection.NPC_EDITOR -> NpcEditorPage(host)
        PanelSection.QUESTS -> QuestsPage(host)
        PanelSection.TRIGGERS -> TriggersPage(host)
        PanelSection.PARTICLES -> ParticlesPage(host)
        PanelSection.LOGS -> LogsPage(host)
        PanelSection.SETTINGS -> PlaceholderPage(host, "Настройки")
        else -> HomePage(host)
    }

    private fun openDocsPage() {
        val ctx = context ?: return
        page?.onDestroy()
        selected = null
        lastSection = null
        docsOpen = true
        setChromeVisible(true)

        val p = DocsPage(host)
        page = p

        val holder = FrameLayout(ctx).apply {
            background = ColorDrawable(COL_CONTENT)
        }
        holder.addView(p.createView(ctx), FrameLayout.LayoutParams(MATCH, MATCH))
        content?.apply {
            removeAllViews()
            addView(holder, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        refreshTabs()
        statusText?.text = "● Готово  ·  Документация"
    }

    private fun setChromeVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        topBar?.visibility = v
        topBarDivider?.visibility = v
    }

    private fun buildTopBar(ctx: Context): View {
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ColorDrawable(COL_HEADER)
            setPadding(dp(ctx, 12), 0, dp(ctx, 12), 0)
        }

        val logo = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setOnClickListener { host.navigate(null) }
        }
        logo.addView(TextView(ctx).apply {
            text = "▶"
            textSize = 16f
            setTextColor(COL_ACCENT)
        }, lp(WRAP, WRAP))
        logo.addView(TextView(ctx).apply {
            text = "  ScriptFX"
            textSize = 16f
            setTextColor(COL_TEXT)
        }, lp(WRAP, WRAP))
        bar.addView(logo, lp(WRAP, MATCH).apply { rightMargin = dp(ctx, 18) })

        val tabs = listOf(
            PanelSection.PROJECTS to "Сценарии",
            PanelSection.NPC_EDITOR to "NPC",
            PanelSection.QUESTS to "Квесты",
            PanelSection.TRIGGERS to "Триггеры",
            PanelSection.PARTICLES to "Частицы",
            PanelSection.LOGS to "Логи"
        )

        for ((section, label) in tabs) {
            val btn = PanelUi.tabButton(ctx, label, false) {
                host.navigate(section)
            }
            tabButtons[section] = btn
            bar.addView(btn, lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 8) })
        }

        val docsBtn = PanelUi.tabButton(ctx, "Документация", false) {
            host.openDocumentation()
        }
        docsTabBtn = docsBtn
        bar.addView(docsBtn, lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 8) })

        bar.addView(View(ctx), LinearLayout.LayoutParams(0, MATCH, 1f))

        bar.addView(
            PanelUi.iconButton(ctx, "⚙") { host.openSettings() },
            lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 6) }
        )
        bar.addView(
            PanelUi.iconButton(ctx, "✕") { host.closePanel() },
            lp(WRAP, WRAP)
        )

        return bar
    }

    private fun buildStatusBar(ctx: Context): View {
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ColorDrawable(COL_STATUS)
            setPadding(dp(ctx, 12), 0, dp(ctx, 12), 0)
        }
        val tv = TextView(ctx).apply {
            text = "● Готово"
            textSize = 12f
            setTextColor(PanelUi.COL_SUCCESS)
        }
        statusText = tv
        bar.addView(tv, lp(WRAP, WRAP))
        bar.addView(View(ctx), LinearLayout.LayoutParams(0, MATCH, 1f))
        bar.addView(TextView(ctx).apply {
            text = "ScriptFX Studio"
            textSize = 11f
            setTextColor(COL_MUTED)
        }, lp(WRAP, WRAP))
        return bar
    }

    private fun refreshTabs() {
        val ctx = context ?: return
        tabButtons.forEach { (section, btn) ->
            val sel = !docsOpen && section == selected
            btn.background = PanelUi.btnBg(
                ctx,
                if (sel) COL_BTN_SELECTED else COL_BTN
            )
        }
        docsTabBtn?.background = PanelUi.btnBg(
            ctx,
            if (docsOpen) COL_BTN_SELECTED else COL_BTN
        )
    }

    private fun updateStatus() {
        if (docsOpen) {
            statusText?.text = "● Готово  ·  Документация"
            return
        }
        val name = when (selected) {
            null -> "Главное меню"
            PanelSection.PROJECTS -> "Сценарии"
            PanelSection.NPC_EDITOR -> "NPC"
            PanelSection.QUESTS -> "Квесты"
            PanelSection.TRIGGERS -> "Триггеры"
            PanelSection.PARTICLES -> "Частицы"
            PanelSection.LOGS -> "Логи"
            else -> selected?.displayName ?: page?.breadcrumb ?: "Studio"
        }
        statusText?.text = "● Готово  ·  $name"
    }
}