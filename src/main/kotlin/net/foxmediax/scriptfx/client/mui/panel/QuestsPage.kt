package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.text.Editable
import icyllis.modernui.text.TextWatcher
import icyllis.modernui.view.View
import icyllis.modernui.widget.EditText
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_ACCENT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_BTN
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_BTN_SELECTED
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_CONTENT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_DIVIDER
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_MUTED
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_SIDEBAR
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_TEXT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.MATCH
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.WRAP
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.dp
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.lp
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.px1
import net.foxmediax.scriptfx.gui.FileBrowser
import net.foxmediax.scriptfx.npc.NpcRegistry
import net.foxmediax.scriptfx.quest.QuestDefinition
import net.foxmediax.scriptfx.quest.QuestEdge
import net.foxmediax.scriptfx.quest.QuestObjective
import net.foxmediax.scriptfx.quest.QuestRegistry

/**
 * Квесты: список .fxquest | граф целей | свойства.
 */
class QuestsPage(host: PanelHost) : PanelPage(host) {

    override val breadcrumb: String
        get() = selected?.let { "Квесты -> ${it.id}" } ?: "Квесты"

    private lateinit var ctx: Context
    private lateinit var overlays: OverlayHost
    private lateinit var listBox: LinearLayout
    private lateinit var formHost: LinearLayout
    private lateinit var projectLabel: TextView
    private lateinit var questGraph: QuestGraphView

    private var project: String = "default"
    private var selected: QuestDefinition? = null
    private var suppress = false

    private val browser: FileBrowser
        get() = ControlPanelFragment.fileBrowser

    override fun createView(ctx: Context): View {
        this.ctx = ctx
        QuestRegistry.reload()
        ensureProject()

        val root = FrameLayout(ctx).apply { background = ColorDrawable(COL_CONTENT) }
        val overlayLayer = FrameLayout(ctx)
        overlays = OverlayHost(ctx, overlayLayer)

        val body = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        body.addView(buildList(ctx), lp(dp(ctx, 250), MATCH))
        body.addView(View(ctx).apply { background = ColorDrawable(COL_DIVIDER) }, lp(px1(ctx), MATCH))
        body.addView(buildCenter(ctx), LinearLayout.LayoutParams(0, MATCH, 1.1f))
        body.addView(View(ctx).apply { background = ColorDrawable(COL_DIVIDER) }, lp(px1(ctx), MATCH))
        body.addView(buildEditor(ctx), LinearLayout.LayoutParams(0, MATCH, 1f))

        root.addView(body, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(overlayLayer, FrameLayout.LayoutParams(MATCH, MATCH))

        selected = QuestRegistry.list(project).firstOrNull()
        refreshList()
        bindGraph()
        renderForm()
        return root
    }

    override fun onDestroy() {
        if (this::overlays.isInitialized) overlays.clear()
    }

    private fun ensureProject() {
        val projects = NpcRegistry.projectsRoot()
            .listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            .orEmpty()
        project = when {
            projects.isEmpty() -> "default"
            else -> {
                val cur = browser.currentDir
                val hit = projects.firstOrNull { cur.path.replace('\\', '/').contains("/$it") }
                hit ?: projects.first()
            }
        }
        if (this::projectLabel.isInitialized) {
            projectLabel.text = "Проект: $project"
        }
    }

    // ------------------------------------------------------------------ list
    private fun buildList(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_SIDEBAR)
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
        }
        col.addView(TextView(ctx).apply {
            text = "Квесты"
            textSize = 13f
            setTextColor(COL_ACCENT)
        }, lp(MATCH, WRAP))

        projectLabel = TextView(ctx).apply {
            text = "Проект: $project"
            textSize = 11f
            setTextColor(COL_MUTED)
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 8))
        }
        col.addView(projectLabel, lp(MATCH, WRAP))

        col.addView(
            PanelUi.accentButton(ctx, "+ Создать квест") { createDialog() },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 6) }
        )
        col.addView(
            PanelUi.flatButton(ctx, "Обновить") {
                QuestRegistry.reload()
                selected = QuestRegistry.get(project, selected?.id ?: "")
                    ?: QuestRegistry.list(project).firstOrNull()
                refreshList()
                bindGraph()
                renderForm()
            },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 10) }
        )

        listBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx)
        scroll.addView(listBox, lp(MATCH, WRAP))
        col.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return col
    }

    private fun refreshList() {
        if (!this::listBox.isInitialized) return
        listBox.removeAllViews()
        val items = QuestRegistry.list(project)
        if (items.isEmpty()) {
            listBox.addView(
                PanelUi.mutedText(ctx, "Нет .fxquest\nНажми «Создать квест»"),
                lp(MATCH, WRAP)
            )
            return
        }
        for (q in items) {
            val active = selected?.id == q.id
            listBox.addView(
                LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    background = ColorDrawable(if (active) COL_BTN_SELECTED else COL_BTN)
                    setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
                    isClickable = true
                    setOnClickListener {
                        selected = q
                        refreshList()
                        bindGraph()
                        renderForm()
                        host.refreshBreadcrumb()
                    }
                    addView(TextView(ctx).apply {
                        text = q.title.ifBlank { q.id }
                        textSize = 13f
                        setTextColor(COL_TEXT)
                    }, lp(MATCH, WRAP))
                    addView(TextView(ctx).apply {
                        text = q.id
                        textSize = 11f
                        setTextColor(COL_MUTED)
                    }, lp(MATCH, WRAP))
                },
                lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 6) }
            )
        }
    }

    // ------------------------------------------------------------------ graph center
    private fun buildCenter(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
        }
        col.addView(TextView(ctx).apply {
            text = "Граф целей"
            textSize = 13f
            setTextColor(COL_ACCENT)
        }, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) })

        questGraph = QuestGraphView(ctx).apply {
            onSelect = { /* optional */ }
            onChanged = { /* позиции — сохраняются кнопкой Сохранить */ }
        }
        col.addView(questGraph, LinearLayout.LayoutParams(MATCH, 0, 1f).apply {
            bottomMargin = dp(ctx, 8)
        })

        col.addView(
            PanelUi.flatButton(ctx, "Цепочка на графе") {
                questGraph.layoutChain()
            },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 6) }
        )
        col.addView(
            PanelUi.mutedText(
                ctx,
                "Перетаскивай цели. «Цепочка» строит рёбра 1→2→3 и раскладку."
            ),
            lp(MATCH, WRAP)
        )
        return col
    }

    private fun bindGraph() {
        if (!this::questGraph.isInitialized) return
        questGraph.definition = selected
    }

    // ------------------------------------------------------------------ form
    private fun buildEditor(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_SIDEBAR)
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
        }
        formHost = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx)
        scroll.addView(formHost, lp(MATCH, WRAP))
        col.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return col
    }

    private fun renderForm() {
        if (!this::formHost.isInitialized) return
        formHost.removeAllViews()
        val q = selected
        if (q == null) {
            formHost.addView(PanelUi.mutedText(ctx, "Выбери или создай квест"), lp(MATCH, WRAP))
            return
        }

        formHost.addView(label("ID"), lp(MATCH, WRAP))
        formHost.addView(value(q.id), fieldLp())

        formHost.addView(label("Название"), lp(MATCH, WRAP))
        val titleEdit = edit(q.title)
        formHost.addView(titleEdit, fieldLp())

        formHost.addView(label("Описание"), lp(MATCH, WRAP))
        val descEdit = edit(q.description)
        formHost.addView(descEdit, fieldLp())

        formHost.addView(label("Цели"), lp(MATCH, WRAP).apply {
            topMargin = dp(ctx, 8)
            bottomMargin = dp(ctx, 6)
        })

        val objBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        formHost.addView(objBox, lp(MATCH, WRAP))

        fun redrawObjectives() {
            objBox.removeAllViews()
            if (q.objectives.isEmpty()) {
                objBox.addView(PanelUi.mutedText(ctx, "Нет целей"), lp(MATCH, WRAP))
            }
            q.objectives.forEachIndexed { index, obj ->
                objBox.addView(
                    objectiveRow(q, index, obj) {
                        redrawObjectives()
                        bindGraph()
                    },
                    lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) }
                )
            }
        }
        redrawObjectives()

        formHost.addView(
            PanelUi.flatButton(ctx, "+ Цель") {
                val n = q.objectives.size + 1
                q.objectives.add(
                    QuestObjective(
                        id = "obj_$n",
                        text = "Цель $n",
                        target = 1,
                        x = 40f + q.objectives.size * 160f,
                        y = 60f
                    )
                )
                redrawObjectives()
                bindGraph()
            },
            lp(MATCH, WRAP).apply { topMargin = dp(ctx, 4); bottomMargin = dp(ctx, 10) }
        )

        formHost.addView(label("Рёбра графа"), lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 4) })
        q.edges.toList().forEachIndexed { index, edge ->
            formHost.addView(
                PanelUi.flatButton(ctx, "${edge.from} → ${edge.to}  ✕") {
                    if (index in q.edges.indices) q.edges.removeAt(index)
                    renderForm()
                    bindGraph()
                },
                lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 4) }
            )
        }
        formHost.addView(
            PanelUi.flatButton(ctx, "+ Ребро вручную") {
                val ids = q.objectives.map { it.id }.filter { it.isNotBlank() }
                if (ids.size < 2) {
                    overlays.actions("Нужно ≥2 цели", listOf("ОК" to {}))
                    return@flatButton
                }
                overlays.actions(
                    "Откуда",
                    ids.map { from ->
                        from to {
                            overlays.actions(
                                "Куда",
                                ids.filter { it != from }.map { to ->
                                    to to {
                                        q.edges.add(QuestEdge(from, to))
                                        renderForm()
                                        bindGraph()
                                        Unit
                                    }
                                }
                            )
                            Unit
                        }
                    }
                )
            },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 12) }
        )

        formHost.addView(
            PanelUi.accentButton(ctx, "Сохранить") {
                q.title = titleEdit.text?.toString().orEmpty().ifBlank { q.id }
                q.description = descEdit.text?.toString().orEmpty()
                if (QuestRegistry.save(project, q)) {
                    QuestRegistry.reload()
                    selected = QuestRegistry.get(project, q.id)
                    refreshList()
                    bindGraph()
                    renderForm()
                    overlays.actions("Сохранено: ${q.id}.fxquest", listOf("ОК" to {}))
                } else {
                    overlays.actions("Не удалось сохранить", listOf("ОК" to {}))
                }
            },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) }
        )

        formHost.addView(
            PanelUi.flatButton(ctx, "Удалить квест") {
                overlays.confirm(
                    "Удалить «${q.id}»?",
                    "Удалить",
                    "Отмена",
                    onYes = {
                        QuestRegistry.delete(project, q.id)
                        selected = QuestRegistry.list(project).firstOrNull()
                        refreshList()
                        bindGraph()
                        renderForm()
                    }
                )
            },
            lp(MATCH, WRAP)
        )

        formHost.addView(
            PanelUi.mutedText(
                ctx,
                "Скрипт: quest_start \"${q.id}\" · quest_progress \"${q.id}\" \"obj_1\""
            ),
            lp(MATCH, WRAP).apply { topMargin = dp(ctx, 12) }
        )
    }

    private fun objectiveRow(
        q: QuestDefinition,
        index: Int,
        obj: QuestObjective,
        onChanged: () -> Unit
    ): View {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_BTN)
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
        }
        box.addView(label("id"), lp(MATCH, WRAP))
        val idEdit = edit(obj.id)
        idEdit.addTextChangedListener(simpleWatcher { obj.id = it })
        box.addView(idEdit, fieldLp())

        box.addView(label("текст"), lp(MATCH, WRAP))
        val textEdit = edit(obj.text)
        textEdit.addTextChangedListener(simpleWatcher { obj.text = it })
        box.addView(textEdit, fieldLp())

        box.addView(label("target"), lp(MATCH, WRAP))
        val targetEdit = edit(obj.target.toString())
        targetEdit.addTextChangedListener(simpleWatcher {
            obj.target = it.toIntOrNull() ?: 1
        })
        box.addView(targetEdit, fieldLp())

        box.addView(
            PanelUi.flatButton(ctx, "Удалить цель") {
                q.objectives.removeAt(index)
                q.edges.removeAll { it.from == obj.id || it.to == obj.id }
                onChanged()
            },
            lp(MATCH, WRAP)
        )
        return box
    }

    private fun createDialog() {
        overlays.input("ID квеста", "tutorial") { raw ->
            val id = raw.trim()
                .lowercase()
                .replace(Regex("[^a-z0-9_]+"), "_")
                .trim('_')
                .ifEmpty { "quest" }
            if (QuestRegistry.get(project, id) != null) {
                overlays.actions("Уже есть: $id", listOf("ОК" to {}))
                return@input
            }
            val def = QuestDefinition(
                id = id,
                title = id.replace('_', ' '),
                description = "",
                objectives = mutableListOf(
                    QuestObjective(id = "obj_1", text = "Первая цель", target = 1, x = 40f, y = 60f),
                    QuestObjective(id = "obj_2", text = "Вторая цель", target = 1, x = 220f, y = 60f)
                ),
                edges = mutableListOf(QuestEdge("obj_1", "obj_2"))
            )
            QuestRegistry.save(project, def)
            selected = def
            refreshList()
            bindGraph()
            renderForm()
        }
    }

    private fun label(t: String) = PanelUi.mutedText(ctx, t, 11f)

    private fun value(t: String) = TextView(ctx).apply {
        text = t
        textSize = 13f
        setTextColor(COL_TEXT)
        setPadding(0, dp(ctx, 4), 0, 0)
    }

    private fun edit(text: String) = EditText(ctx).apply {
        setText(text)
        textSize = 13f
        setTextColor(COL_TEXT)
        setHintTextColor(COL_MUTED)
        background = ColorDrawable(0xFF1A1A22.toInt())
        setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
    }

    private fun fieldLp() = lp(MATCH, WRAP).apply {
        topMargin = dp(ctx, 2)
        bottomMargin = dp(ctx, 8)
    }

    private fun simpleWatcher(on: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (suppress) return
            on(s?.toString().orEmpty())
        }
    }
}