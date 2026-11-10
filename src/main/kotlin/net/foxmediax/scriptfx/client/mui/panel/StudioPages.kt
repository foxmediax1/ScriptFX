package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.View
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.gui.PanelSection
import icyllis.modernui.widget.Button
import icyllis.modernui.widget.ScrollView
import net.foxmediax.scriptfx.gui.FileBrowser
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

        // ----- LEFT: menu -----
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

        continueBtn = PanelUi.accentButton(ctx, "Продолжить") {
            openSelectedProject()
        }
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

        left.addView(TextView(ctx).apply {
            text = "Version: v2.0"
            textSize = 11f
            setTextColor(PanelUi.COL_DIM)
        }, PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP).apply {
            topMargin = PanelUi.dp(ctx, 24)
        })

        // ----- RIGHT: project list -----
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

        val rightHeader = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        rightHeader.addView(TextView(ctx).apply {
            text = "Проекты"
            textSize = 14f
            setTextColor(PanelUi.COL_ACCENT)
        }, LinearLayout.LayoutParams(0, PanelUi.WRAP, 1f))

        rightHeader.addView(
            PanelUi.iconButton(ctx, "🗑") { deleteSelectedProject() },
            PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP)
        )

        right.addView(
            rightHeader,
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP).apply {
                bottomMargin = PanelUi.dp(ctx, 12)
            }
        )

        listBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx)
        scroll.addView(listBox, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))
        right.addView(scroll, LinearLayout.LayoutParams(PanelUi.MATCH, 0, 1f))

        body.addView(left, LinearLayout.LayoutParams(0, PanelUi.MATCH, 1f))
        body.addView(
            View(ctx).apply { background = ColorDrawable(PanelUi.COL_DIVIDER) },
            PanelUi.lp(PanelUi.px1(ctx), PanelUi.MATCH)
        )
        body.addView(right, PanelUi.lp(PanelUi.dp(ctx, 320), PanelUi.MATCH))

        root.addView(body, FrameLayout.LayoutParams(PanelUi.MATCH, PanelUi.MATCH))
        root.addView(overlayLayer, FrameLayout.LayoutParams(PanelUi.MATCH, PanelUi.MATCH))

        refreshProjects()
        return root
    }

    override fun onDestroy() {
        if (this::overlays.isInitialized) overlays.clear()
    }

    private fun listProjectNames(): List<String> {
        val dir = browser.projectsDir
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
    }

    private fun refreshProjects() {
        listBox.removeAllViews()
        val names = listProjectNames()
        if (names.isEmpty()) {
            listBox.addView(
                PanelUi.mutedText(ctx, "Нет проектов.\nНажми «Создать проект»"),
                PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
            )
            selectedProject = null
            setContinueEnabled(false)
            return
        }

        if (selectedProject != null && selectedProject !in names) {
            selectedProject = null
            setContinueEnabled(false)
        }

        for (name in names) {
            val active = name == selectedProject
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ColorDrawable(
                    if (active) PanelUi.COL_BTN_SELECTED else PanelUi.COL_BTN
                )
                setPadding(
                    PanelUi.dp(ctx, 12),
                    PanelUi.dp(ctx, 12),
                    PanelUi.dp(ctx, 12),
                    PanelUi.dp(ctx, 12)
                )
                isClickable = true
                setOnClickListener {
                    selectedProject = name
                    setContinueEnabled(true)
                    refreshProjects()
                }
            }
            row.addView(TextView(ctx).apply {
                text = "📁  $name"
                textSize = 13f
                setTextColor(PanelUi.COL_TEXT)
            }, LinearLayout.LayoutParams(0, PanelUi.WRAP, 1f))
            listBox.addView(
                row,
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
        continueBtn.background = PanelUi.btnBg(
            ctx,
            if (enabled) PanelUi.COL_BTN_SELECTED else PanelUi.COL_BTN
        )
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
        overlays.input("Имя проекта", "new_projects") { raw ->
            val name = raw.trim()
            if (name.isEmpty()) return@input
            browser.createProject(name)
            selectedProject = name
            setContinueEnabled(true)
            refreshProjects()
        }
    }

    private fun deleteSelectedProject() {
        val name = selectedProject
        if (name == null) {
            overlays.actions(
                "Сначала выбери проект в списке",
                listOf("ОК" to { })
            )
            return
        }

        overlays.confirm(
            "Удалить проект «$name»?\nПапка и все файлы будут удалены.",
            "Удалить",
            "Отмена",
            onYes = {
                val dir = File(browser.projectsDir, name)
                if (dir.isDirectory) {
                    dir.deleteRecursively()
                }
                if (browser.currentDir.path.startsWith(dir.path)) {
                    browser.goTo(browser.projectsDir)
                }
                selectedProject = null
                setContinueEnabled(false)
                refreshProjects()
                Unit
            }
        )
    }
}

/** Универсальная страница-заглушка в стиле студии (3 колонки). */
class StudioSectionPage(
    host: PanelHost,
    private val title: String,
    private val leftTitle: String,
    private val centerHint: String,
    private val rightTitle: String
) : PanelPage(host) {

    override val breadcrumb: String get() = title

    override fun createView(ctx: Context): View {
        val left = PanelUi.verticalColumn(ctx).apply {
            background = ColorDrawable(PanelUi.COL_SIDEBAR)
            setPadding(PanelUi.dp(ctx, 12), PanelUi.dp(ctx, 12), PanelUi.dp(ctx, 12), PanelUi.dp(ctx, 12))
            addView(PanelUi.sectionTitle(ctx, leftTitle))
            addView(PanelUi.spacer(ctx, 8))
            addView(PanelUi.accentButton(ctx, "+ Создать") {})
            addView(PanelUi.spacer(ctx, 8))
            addView(PanelUi.flatButton(ctx, "Элемент 1") {})
            addView(PanelUi.spacer(ctx, 6))
            addView(PanelUi.flatButton(ctx, "Элемент 2") {})
        }

        val center = PanelUi.pageRoot(ctx).apply {
            addView(PanelUi.pageTitle(ctx, title))
            addView(PanelUi.spacer(ctx, 12))
            addView(PanelUi.mutedText(ctx, centerHint, 14f))
        }

        val right = PanelUi.verticalColumn(ctx).apply {
            background = ColorDrawable(PanelUi.COL_SIDEBAR)
            setPadding(PanelUi.dp(ctx, 12), PanelUi.dp(ctx, 12), PanelUi.dp(ctx, 12), PanelUi.dp(ctx, 12))
            addView(PanelUi.sectionTitle(ctx, rightTitle))
            addView(PanelUi.spacer(ctx, 8))
            addView(PanelUi.bodyText(ctx, "Свойства выбранного элемента появятся здесь."))
        }

        return PanelUi.studioTriple(ctx, left, center, right)
    }
}