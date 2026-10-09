package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.View
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.CutsceneClient
import net.foxmediax.scriptfx.client.NpcPreviewHelper
import net.foxmediax.scriptfx.gui.Documentation
import net.foxmediax.scriptfx.gui.PanelSection
import net.foxmediax.scriptfx.npc.NpcDefinition
import net.foxmediax.scriptfx.npc.NpcRegistry
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.Minecraft

interface PanelHost {
    fun navigate(section: PanelSection?)
    fun requestLeave(proceed: () -> Unit)
    fun refreshBreadcrumb()
    fun closePanel()
    fun openSettings()
    fun openDocumentation()
    fun openScriptHints()
}

abstract class PanelPage(protected val host: PanelHost) {
    abstract fun createView(ctx: Context): View
    open val breadcrumb: String? = null
    open fun goUp(): Boolean = false
    open fun requestLeave(proceed: () -> Unit) = proceed()
    open fun onDestroy() {}
    open val interceptsEscape: Boolean get() = false
    open fun onEscape() {}
}

class HomePage(host: PanelHost) : PanelPage(host) {
    override fun createView(ctx: Context): View {
        val root = FrameLayout(ctx)
        root.background = ColorDrawable(0xFF000000.toInt())
        val tv = TextView(ctx).apply {
            text = "Это главное меню мода, для\nработы с проектом откройте\nсоответствующий раздел,\nкоторый находится слева.\n\n←"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(PanelUi.COL_MUTED)
        }
        root.addView(
            tv,
            FrameLayout.LayoutParams(PanelUi.WRAP, PanelUi.WRAP, Gravity.CENTER)
        )
        return root
    }
}

class PlaceholderPage(host: PanelHost, private val name: String) : PanelPage(host) {
    override val breadcrumb: String? get() = name

    override fun createView(ctx: Context): View {
        val root = FrameLayout(ctx)
        root.background = ColorDrawable(0xFF000000.toInt())
        val tv = TextView(ctx).apply {
            text = "Раздел «$name» — в разработке"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(PanelUi.COL_MUTED)
        }
        root.addView(
            tv,
            FrameLayout.LayoutParams(PanelUi.WRAP, PanelUi.WRAP, Gravity.CENTER)
        )
        return root
    }
}

class LogsPage(host: PanelHost) : PanelPage(host) {

    private lateinit var box: LinearLayout
    private lateinit var scroll: ScrollView

    override val breadcrumb: String? get() = "Логи"

    override fun createView(ctx: Context): View {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(0xFF000000.toInt())
        }

        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(PanelUi.dp(ctx, 4), PanelUi.dp(ctx, 4), PanelUi.dp(ctx, 4), PanelUi.dp(ctx, 4))
        }
        top.addView(
            TextView(ctx).apply {
                text = "Логи ScriptFX"
                textSize = 13f
                setTextColor(PanelUi.COL_TEXT)
            },
            LinearLayout.LayoutParams(0, PanelUi.WRAP, 1f)
        )
        top.addView(
            PanelUi.flatButton(ctx, "Обновить") { refresh() },
            PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP)
        )
        top.addView(
            PanelUi.flatButton(ctx, "Очистить") {
                ScriptFXLog.clear()
                refresh()
            },
            PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP).apply {
                leftMargin = PanelUi.dp(ctx, 6)
            }
        )
        root.addView(top, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))

        box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PanelUi.dp(ctx, 6), PanelUi.dp(ctx, 4), PanelUi.dp(ctx, 6), PanelUi.dp(ctx, 4))
        }
        scroll = ScrollView(ctx)
        scroll.addView(box, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))
        root.addView(scroll, LinearLayout.LayoutParams(PanelUi.MATCH, 0, 1f))

        refresh()
        return root
    }

    private fun refresh() {
        if (!this::box.isInitialized) return
        box.removeAllViews()
        val ctx = box.context
        val entries = ScriptFXLog.snapshot()
        if (entries.isEmpty()) {
            box.addView(
                TextView(ctx).apply {
                    text = "Лог пуст"
                    textSize = 13f
                    setTextColor(PanelUi.COL_MUTED)
                    gravity = Gravity.CENTER
                    setPadding(0, PanelUi.dp(ctx, 24), 0, 0)
                },
                PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
            )
            return
        }
        for (entry in entries.takeLast(200)) {
            val color = when (entry.level) {
                ScriptFXLog.Level.ERROR -> 0xFFFF5555.toInt()
                ScriptFXLog.Level.WARN -> 0xFFFFAA00.toInt()
                ScriptFXLog.Level.INFO -> PanelUi.COL_TEXT
            }
            val levelTag = when (entry.level) {
                ScriptFXLog.Level.ERROR -> "ERROR"
                ScriptFXLog.Level.WARN -> "WARN"
                ScriptFXLog.Level.INFO -> "INFO"
            }
            box.addView(
                TextView(ctx).apply {
                    text = "[$levelTag] ${entry.message}"
                    textSize = 12f
                    setTextColor(color)
                    setPadding(0, PanelUi.dp(ctx, 1), 0, PanelUi.dp(ctx, 1))
                },
                PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
            )
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }
}

class CutscenesPage(host: PanelHost) : PanelPage(host) {

    override val breadcrumb: String? get() = "Кат-сцены"

    override fun createView(ctx: Context): View {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(0xFF000000.toInt())
            setPadding(PanelUi.dp(ctx, 12), PanelUi.dp(ctx, 12), PanelUi.dp(ctx, 12), PanelUi.dp(ctx, 12))
        }

        root.addView(
            TextView(ctx).apply {
                text = "Кат-сцены"
                textSize = 15f
                setTextColor(PanelUi.COL_TEXT)
            },
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
        )

        val status = TextView(ctx).apply {
            textSize = 13f
            setTextColor(PanelUi.COL_MUTED)
            setPadding(0, PanelUi.dp(ctx, 12), 0, PanelUi.dp(ctx, 8))
        }
        root.addView(status, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))

        fun refreshStatus() {
            val mc = Minecraft.getInstance()
            mc.execute {
                val active = CutsceneClient.active
                val locked = CutsceneClient.locked
                val text = buildString {
                    append(if (active) "● Катсцена активна" else "○ Катсцена не идёт")
                    append("\n")
                    append(if (locked) "Управление игроком заблокировано" else "Управление свободно")
                    append("\n\n")
                    append("Запуск — командами cutscene_* в скриптах.\n")
                    append("Прерывание — клавиша, заданная на сервере / в скрипте.")
                }
                status.post { status.text = text }
            }
        }
        refreshStatus()

        root.addView(
            PanelUi.flatButton(ctx, "Обновить статус") { refreshStatus() },
            PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP).apply {
                topMargin = PanelUi.dp(ctx, 8)
            }
        )

        root.addView(
            TextView(ctx).apply {
                text = "Редактор таймлайна кат-сцен будет в следующей итерации."
                textSize = 12f
                setTextColor(0xFF666666.toInt())
                setPadding(0, PanelUi.dp(ctx, 16), 0, 0)
            },
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
        )

        return root
    }
}

class DocsPage(host: PanelHost) : PanelPage(host) {

    override val breadcrumb: String? get() = "Документация"

    private data class Node(val section: String, val subsection: String)

    private var current: Node? = null
    private lateinit var docsBody: LinearLayout

    override fun createView(ctx: Context): View {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ColorDrawable(0xFF000000.toInt())
        }

        val nav = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PanelUi.dp(ctx, 6), PanelUi.dp(ctx, 6), PanelUi.dp(ctx, 6), PanelUi.dp(ctx, 6))
            background = ColorDrawable(0xFF141418.toInt())
        }

        val sections = listOf(
            "Скрипты" to listOf("Переменные", "Команды", "Триггеры"),
            "NPC" to listOf("Определения", "Спавн", "Режимы"),
            "Кат-сцены" to listOf("Обзор")
        )

        for ((section, subs) in sections) {
            nav.addView(
                TextView(ctx).apply {
                    text = section
                    textSize = 13f
                    setTextColor(PanelUi.COL_ACCENT)
                    setPadding(0, PanelUi.dp(ctx, 8), 0, PanelUi.dp(ctx, 2))
                },
                PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
            )
            for (sub in subs) {
                nav.addView(
                    PanelUi.flatButton(ctx, "  · $sub") {
                        current = Node(section, sub)
                        renderDocsBody()
                    },
                    PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP).apply {
                        topMargin = PanelUi.dp(ctx, 2)
                    }
                )
            }
        }
        root.addView(nav, PanelUi.lp(PanelUi.dp(ctx, 160), PanelUi.MATCH))

        val scroll = ScrollView(ctx)
        docsBody = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PanelUi.dp(ctx, 10), PanelUi.dp(ctx, 8), PanelUi.dp(ctx, 10), PanelUi.dp(ctx, 8))
        }
        scroll.addView(docsBody, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))
        root.addView(scroll, LinearLayout.LayoutParams(0, PanelUi.MATCH, 1f))

        current = Node("Скрипты", "Переменные")
        renderDocsBody()
        return root
    }

    private fun renderDocsBody() {
        if (!this::docsBody.isInitialized) return
        val ctx = docsBody.context
        docsBody.removeAllViews()
        val node = current ?: return
        val lines = Documentation.page(node.section, node.subsection)
        if (lines.isEmpty()) {
            docsBody.addView(
                TextView(ctx).apply {
                    text = "Нет текста для «${node.section}» / «${node.subsection}»"
                    textSize = 13f
                    setTextColor(PanelUi.COL_MUTED)
                },
                PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
            )
            return
        }
        for (line in lines) {
            if (line.trim() == Documentation.DIVIDER) {
                docsBody.addView(
                    View(ctx).apply { background = ColorDrawable(PanelUi.COL_DIVIDER) },
                    PanelUi.lp(PanelUi.MATCH, PanelUi.px1(ctx)).apply {
                        topMargin = PanelUi.dp(ctx, 6)
                        bottomMargin = PanelUi.dp(ctx, 6)
                    }
                )
            } else {
                docsBody.addView(
                    TextView(ctx).apply {
                        text = line
                        textSize = 12f
                        setTextColor(PanelUi.COL_TEXT)
                        setPadding(0, PanelUi.dp(ctx, 2), 0, PanelUi.dp(ctx, 2))
                    },
                    PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
                )
            }
        }
    }
}

class NpcEditorPage(host: PanelHost) : PanelPage(host) {

    override val breadcrumb: String?
        get() = when {
            selectedDef != null -> "NPC-редактор -> ${selectedDef!!.id}"
            selectedProject != null -> "NPC-редактор -> $selectedProject"
            else -> "NPC-редактор"
        }

    private var selectedProject: String? = null
    private var selectedDef: NpcDefinition? = null

    private lateinit var ctx: Context
    private lateinit var overlays: OverlayHost
    private lateinit var pathView: TextView
    private lateinit var rowsBox: LinearLayout
    private lateinit var detailBox: LinearLayout
    private lateinit var previewView: NpcPreviewView

    override fun createView(ctx: Context): View {
        this.ctx = ctx
        NpcRegistry.reload()

        val root = FrameLayout(ctx).apply {
            background = ColorDrawable(0xFF000000.toInt())
        }
        val overlayLayer = FrameLayout(ctx)
        overlays = OverlayHost(ctx, overlayLayer)

        val page = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(PanelUi.dp(ctx, 4), PanelUi.dp(ctx, 4), PanelUi.dp(ctx, 4), PanelUi.dp(ctx, 4))
        }
        pathView = TextView(ctx).apply {
            textSize = 12f
            setTextColor(0xFF888888.toInt())
        }
        top.addView(pathView, LinearLayout.LayoutParams(0, PanelUi.WRAP, 1f))
        top.addView(
            PanelUi.flatButton(ctx, "Обновить") {
                NpcRegistry.reload()
                refresh()
            },
            PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP)
        )
        top.addView(
            PanelUi.flatButton(ctx, "Создать…") { openCreate() },
            PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP).apply {
                leftMargin = PanelUi.dp(ctx, 6)
            }
        )
        page.addView(top, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))

        // Три колонки: список | свойства | превью
        val npcColumns = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        rowsBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PanelUi.dp(ctx, 4), 0, PanelUi.dp(ctx, 4), PanelUi.dp(ctx, 4))
        }
        val leftScroll = ScrollView(ctx)
        leftScroll.addView(rowsBox, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))
        npcColumns.addView(
            leftScroll,
            LinearLayout.LayoutParams(0, PanelUi.MATCH, 0.9f)
        )

        detailBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                PanelUi.dp(ctx, 8),
                PanelUi.dp(ctx, 4),
                PanelUi.dp(ctx, 8),
                PanelUi.dp(ctx, 4)
            )
            background = ColorDrawable(0xFF101014.toInt())
        }
        val centerScroll = ScrollView(ctx)
        centerScroll.addView(detailBox, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))
        npcColumns.addView(
            centerScroll,
            LinearLayout.LayoutParams(0, PanelUi.MATCH, 1.2f)
        )

        previewView = NpcPreviewView(ctx)
        npcColumns.addView(
            previewView,
            LinearLayout.LayoutParams(PanelUi.dp(ctx, 180), PanelUi.MATCH).apply {
                leftMargin = PanelUi.dp(ctx, 6)
            }
        )

        page.addView(
            npcColumns,
            LinearLayout.LayoutParams(PanelUi.MATCH, 0, 1f)
        )
        root.addView(page, FrameLayout.LayoutParams(PanelUi.MATCH, PanelUi.MATCH))
        root.addView(overlayLayer, FrameLayout.LayoutParams(PanelUi.MATCH, PanelUi.MATCH))

        refresh()
        return root
    }

    override fun goUp(): Boolean {
        when {
            selectedDef != null -> {
                selectedDef = null
                refresh()
                return true
            }
            selectedProject != null -> {
                selectedProject = null
                refresh()
                return true
            }
            else -> return false
        }
    }

    override fun onDestroy() {
        if (this::overlays.isInitialized) overlays.clear()
        NpcPreviewHelper.clear()
    }

    private fun refresh() {
        if (!this::rowsBox.isInitialized) return
        rowsBox.removeAllViews()
        detailBox.removeAllViews()
        host.refreshBreadcrumb()

        val project = selectedProject
        val def = selectedDef

        pathView.text = when {
            def != null -> "/scriptfx/projects/$project/npc/${def.id}.fxnpc"
            project != null -> "/scriptfx/projects/$project/npc"
            else -> "/scriptfx/projects/*/npc"
        }

        if (this::previewView.isInitialized) {
            previewView.definition = def
        }

        if (project == null) {
            val projects = NpcRegistry.listProjects()
            if (projects.isEmpty()) {
                rowsBox.addRow(hint("Нет проектов. Создайте проект в разделе «Проекты»."))
            } else {
                for (name in projects) {
                    rowsBox.addRow(
                        listRow(name, PanelUi.COL_TEXT) {
                            selectedProject = name
                            selectedDef = null
                            refresh()
                        }
                    )
                }
            }
            detailBox.addView(
                hint("Выберите проект слева"),
                PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
            )
            return
        }

        if (def == null) {
            rowsBox.addRow(
                listRow("../", PanelUi.COL_MUTED) {
                    selectedProject = null
                    refresh()
                }
            )
            val npcs = NpcRegistry.listNpcs(project)
            if (npcs.isEmpty()) {
                rowsBox.addRow(hint("Нет .fxnpc — «Создать…»"))
            } else {
                for (n in npcs) {
                    rowsBox.addRow(
                        listRow("${n.id}  (${n.displayName})", PanelUi.COL_TEXT) {
                            selectedDef = n
                            refresh()
                        }
                    )
                }
            }
            detailBox.addView(
                hint("Выберите NPC слева"),
                PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
            )
            return
        }

        rowsBox.addRow(
            listRow("../", PanelUi.COL_MUTED) {
                selectedDef = null
                refresh()
            }
        )
        for (n in NpcRegistry.listNpcs(project)) {
            val selected = n.id == def.id
            rowsBox.addRow(
                listRow(
                    n.id,
                    if (selected) PanelUi.COL_ACCENT else PanelUi.COL_TEXT
                ) {
                    selectedDef = n
                    refresh()
                }
            )
        }
        renderDetail(project, def)
    }

    private fun renderDetail(project: String, def: NpcDefinition) {
        val ctx = this.ctx

        detailBox.addView(
            TextView(ctx).apply {
                text = "Редактирование: ${def.id}"
                textSize = 14f
                setTextColor(PanelUi.COL_TEXT)
            },
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
        )

        addReadonly(ctx, "id", def.id)

        addEditRow(ctx, "displayName", def.displayName) { v ->
            save(project, def.copy(displayName = v.ifBlank { def.id }))
        }
        addEditRow(ctx, "defaultMode", def.defaultMode) { v ->
            save(project, def.copy(defaultMode = v.ifBlank { "interact" }))
        }
        addEditRow(ctx, "defaultAnim", def.defaultAnim) { v ->
            save(project, def.copy(defaultAnim = v.ifBlank { "idle" }))
        }
        addEditRow(ctx, "model", def.model) { v ->
            save(project, def.copy(model = v))
        }
        addEditRow(ctx, "texture", def.texture) { v ->
            save(project, def.copy(texture = v))
        }
        addEditRow(ctx, "animation", def.animation) { v ->
            save(project, def.copy(animation = v))
        }
        addEditRow(ctx, "maxHealth", def.maxHealth.toString()) { v ->
            val h = v.toFloatOrNull() ?: def.maxHealth
            save(project, def.copy(maxHealth = h.coerceIn(1f, 1000f)))
        }
        addEditRow(ctx, "onInteractScript", def.onInteractScript) { v ->
            save(project, def.copy(onInteractScript = v.trim()))
        }
        addEditRow(ctx, "interactKey", def.interactKey) { v ->
            save(project, def.copy(interactKey = v.ifBlank { "X" }))
        }

        addToggle(ctx, "lookAtPlayer", def.lookAtPlayer) { on ->
            save(project, def.copy(lookAtPlayer = on))
        }
        addToggle(ctx, "gravity", def.gravity) { on ->
            save(project, def.copy(gravity = on))
        }
        addToggle(ctx, "invulnerable", def.invulnerable) { on ->
            save(project, def.copy(invulnerable = on))
        }
        addToggle(ctx, "collide", def.collide) { on ->
            save(project, def.copy(collide = on))
        }
        addToggle(ctx, "nametag", def.nametag) { on ->
            save(project, def.copy(nametag = on))
        }
        addToggle(ctx, "silent", def.silent) { on ->
            save(project, def.copy(silent = on))
        }

        detailBox.addView(
            TextView(ctx).apply {
                text = "Режим (быстрый выбор)"
                textSize = 12f
                setTextColor(PanelUi.COL_MUTED)
                setPadding(0, PanelUi.dp(ctx, 10), 0, PanelUi.dp(ctx, 4))
            },
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
        )
        val modes = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        for (m in listOf("dialog", "interact", "traiding")) {
            modes.addView(
                PanelUi.flatButton(ctx, m) {
                    save(project, def.copy(defaultMode = m))
                },
                PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP).apply {
                    rightMargin = PanelUi.dp(ctx, 6)
                }
            )
        }
        detailBox.addView(modes, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))

        detailBox.addView(
            PanelUi.flatButton(ctx, "Удалить NPC") {
                overlays.confirm(
                    "Удалить «${def.id}» безвозвратно?",
                    "Удалить",
                    "Отмена",
                    onYes = {
                        NpcRegistry.delete(project, def.id)
                        selectedDef = null
                        NpcRegistry.reload()
                        refresh()
                    }
                )
            },
            PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP).apply {
                topMargin = PanelUi.dp(ctx, 16)
            }
        )
    }

    private fun openCreate() {
        val project = selectedProject
        if (project == null) {
            overlays.actions(
                "Сначала выберите проект",
                NpcRegistry.listProjects().map { name ->
                    name to {
                        selectedProject = name
                        refresh()
                        openCreate()
                    }
                }
            )
            return
        }
        overlays.input("ID нового NPC", "npc_1") { id ->
            val clean = id.trim()
            if (clean.isEmpty()) return@input
            val def = NpcDefinition(id = clean, displayName = clean)
            if (NpcRegistry.save(project, def)) {
                NpcRegistry.reload()
                selectedDef = NpcRegistry.get(project, clean)
                refresh()
            } else {
                ScriptFXLog.warn("Не удалось сохранить NPC '$clean'")
            }
        }
    }

    private fun listRow(label: String, color: Int, onClick: () -> Unit): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ColorDrawable(0xFF16161C.toInt())
            setPadding(PanelUi.dp(ctx, 6), PanelUi.dp(ctx, 4), PanelUi.dp(ctx, 6), PanelUi.dp(ctx, 4))
            isClickable = true
            setOnClickListener { onClick() }
            addView(
                TextView(ctx).apply {
                    text = label
                    textSize = 12f
                    setTextColor(color)
                    setSingleLine(true)
                },
                LinearLayout.LayoutParams(0, PanelUi.WRAP, 1f)
            )
        }
    }

    /** Добавить строку списка с отступом (не путать с ViewGroup.addView). */
    private fun LinearLayout.addRow(child: View) {
        addView(
            child,
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP).apply {
                topMargin = PanelUi.dp(ctx, 2)
            }
        )
    }

    private fun hint(text: String): View =
        TextView(ctx).apply {
            this.text = text
            textSize = 12f
            setTextColor(PanelUi.COL_MUTED)
            gravity = Gravity.CENTER
            setPadding(0, PanelUi.dp(ctx, 16), 0, 0)
        }

    private fun save(project: String, def: NpcDefinition) {
        if (NpcRegistry.save(project, def)) {
            NpcRegistry.reload()
            selectedDef = NpcRegistry.get(project, def.id)
            refresh()
        } else {
            ScriptFXLog.warn("Не удалось сохранить NPC '${def.id}'")
        }
    }

    private fun addReadonly(ctx: Context, label: String, value: String) {
        detailBox.addView(
            TextView(ctx).apply {
                text = "$label: $value"
                textSize = 12f
                setTextColor(PanelUi.COL_MUTED)
                setPadding(0, PanelUi.dp(ctx, 3), 0, PanelUi.dp(ctx, 3))
            },
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
        )
    }

    private fun addEditRow(
        ctx: Context,
        label: String,
        value: String,
        onSave: (String) -> Unit
    ) {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, PanelUi.dp(ctx, 2), 0, PanelUi.dp(ctx, 2))
        }
        row.addView(
            TextView(ctx).apply {
                text = label
                textSize = 12f
                setTextColor(PanelUi.COL_MUTED)
            },
            LinearLayout.LayoutParams(0, PanelUi.WRAP, 1f)
        )
        row.addView(
            TextView(ctx).apply {
                text = value.ifBlank { "—" }
                textSize = 12f
                setTextColor(PanelUi.COL_TEXT)
                setSingleLine(true)
            },
            LinearLayout.LayoutParams(0, PanelUi.WRAP, 1.2f)
        )
        row.addView(
            PanelUi.flatButton(ctx, "✎") {
                overlays.input(label, value) { onSave(it) }
            },
            PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP)
        )
        detailBox.addView(row, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))
    }

    private fun addToggle(
        ctx: Context,
        label: String,
        value: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, PanelUi.dp(ctx, 2), 0, PanelUi.dp(ctx, 2))
        }
        row.addView(
            TextView(ctx).apply {
                text = label
                textSize = 12f
                setTextColor(PanelUi.COL_MUTED)
            },
            LinearLayout.LayoutParams(0, PanelUi.WRAP, 1f)
        )
        row.addView(
            PanelUi.flatButton(ctx, if (value) "ON" else "OFF") {
                onChange(!value)
            },
            PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP)
        )
        detailBox.addView(row, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))
    }
}