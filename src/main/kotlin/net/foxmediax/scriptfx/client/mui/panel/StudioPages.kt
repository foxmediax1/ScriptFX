package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.View
import icyllis.modernui.widget.Button
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.gui.FileBrowser
import net.foxmediax.scriptfx.gui.PanelSection
import java.io.File

class HomePage(host: PanelHost) : PanelPage(host) {

    override val breadcrumb: String? get() = "Главное меню"

    private lateinit var ctx: Context
    private lateinit var listBox: LinearLayout
    private lateinit var continueBtn: Button
    private lateinit var overlays: OverlayHost
    private var selectedProject: String? = null

    private val browser: FileBrowser
        get() = ControlPanelFragment.fileBrowser

    override fun createView(ctx: Context): View {
        this.ctx = ctx
        val root = FrameLayout(ctx).apply {
            background = ColorDrawable(PanelUi.COL_CONTENT)
        }
        val overlayLayer = FrameLayout(ctx)
        overlays = OverlayHost(ctx, overlayLayer)

        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val left = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                PanelUi.dp(ctx, 56),
                PanelUi.dp(ctx, 40),
                PanelUi.dp(ctx, 40),
                PanelUi.dp(ctx, 40)
            )
        }

        left.addView(TextView(ctx).apply {
            text = "ScriptFX"
            textSize = 44f
            setTextColor(PanelUi.COL_TEXT)
        }, PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP))

        left.addView(TextView(ctx).apply {
            text = "Script Engine by FoxMediaX"
            textSize = 15f
            setTextColor(PanelUi.COL_MUTED)
        }, PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP).apply {
            topMargin = PanelUi.dp(ctx, 6)
            bottomMargin = PanelUi.dp(ctx, 32)
        })

        continueBtn = PanelUi.accentButton(ctx, "Продолжить") { openSelectedProject() }
        setContinueEnabled(false)
        left.addView(
            continueBtn,
            PanelUi.lp(PanelUi.dp(ctx, 260), PanelUi.WRAP).apply {
                bottomMargin = PanelUi.dp(ctx, 10)
            }
        )
        left.addView(
            PanelUi.flatButton(ctx, "Создать проект") { createProjectDialog() },
            PanelUi.lp(PanelUi.dp(ctx, 260), PanelUi.WRAP).apply {
                bottomMargin = PanelUi.dp(ctx, 10)
            }
        )
        left.addView(
            PanelUi.flatButton(ctx, "Настройки") { host.openSettings() },
            PanelUi.lp(PanelUi.dp(ctx, 260), PanelUi.WRAP).apply {
                bottomMargin = PanelUi.dp(ctx, 10)
            }
        )
        left.addView(
            PanelUi.flatButton(ctx, "Документация") { host.openDocumentation() },
            PanelUi.lp(PanelUi.dp(ctx, 260), PanelUi.WRAP).apply {
                bottomMargin = PanelUi.dp(ctx, 10)
            }
        )
        left.addView(
            PanelUi.flatButton(ctx, "Выход") { host.closePanel() },
            PanelUi.lp(PanelUi.dp(ctx, 260), PanelUi.WRAP)
        )

        val right = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(PanelUi.COL_SIDEBAR)
            setPadding(
                PanelUi.dp(ctx, 16),
                PanelUi.dp(ctx, 20),
                PanelUi.dp(ctx, 16),
                PanelUi.dp(ctx, 16)
            )
        }
        right.addView(TextView(ctx).apply {
            text = "Проекты"
            textSize = 14f
            setTextColor(PanelUi.COL_ACCENT)
        }, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP).apply {
            bottomMargin = PanelUi.dp(ctx, 12)
        })

        listBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx)
        scroll.addView(listBox, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))
        right.addView(scroll, LinearLayout.LayoutParams(PanelUi.MATCH, 0, 1f))

        body.addView(left, LinearLayout.LayoutParams(0, PanelUi.MATCH, 1f))
        body.addView(right, PanelUi.lp(PanelUi.dp(ctx, 320), PanelUi.MATCH))
        root.addView(body, FrameLayout.LayoutParams(PanelUi.MATCH, PanelUi.MATCH))
        root.addView(overlayLayer, FrameLayout.LayoutParams(PanelUi.MATCH, PanelUi.MATCH))

        refreshProjects()
        return root
    }

    override fun onDestroy() {
        if (this::overlays.isInitialized) overlays.clear()
    }

    private fun refreshProjects() {
        listBox.removeAllViews()
        val root = browser.projectsDir
        val projects = root.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
        if (projects.isEmpty()) {
            listBox.addView(
                PanelUi.mutedText(ctx, "Нет проектов"),
                PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
            )
            return
        }
        for (dir in projects) {
            val active = selectedProject == dir.name
            listBox.addView(
                PanelUi.flatButton(ctx, dir.name) {
                    selectedProject = dir.name
                    setContinueEnabled(true)
                    refreshProjects()
                }.also {
                    it.background = ColorDrawable(
                        if (active) PanelUi.COL_BTN_SELECTED else PanelUi.COL_BTN
                    )
                },
                PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP).apply {
                    bottomMargin = PanelUi.dp(ctx, 6)
                }
            )
        }
    }

    private fun setContinueEnabled(enabled: Boolean) {
        if (!this::continueBtn.isInitialized) return
        continueBtn.isEnabled = enabled
        continueBtn.alpha = if (enabled) 1f else 0.45f
    }

    private fun openSelectedProject() {
        val name = selectedProject ?: return
        val dir = File(browser.projectsDir, name)
        if (!dir.isDirectory) {
            refreshProjects()
            return
        }
        browser.goTo(dir)
        host.navigate(PanelSection.PROJECTS)
    }

    private fun createProjectDialog() {
        overlays.input("Имя проекта", "new_project") { raw ->
            val name = raw.trim()
            if (name.isEmpty()) return@input
            browser.createProject(name)
            selectedProject = name
            setContinueEnabled(true)
            refreshProjects()
        }
    }
}