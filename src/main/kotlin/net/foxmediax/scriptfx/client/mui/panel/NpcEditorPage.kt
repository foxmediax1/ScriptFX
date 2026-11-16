package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.View
import icyllis.modernui.widget.EditText
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.NpcPreviewHelper
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
import net.foxmediax.scriptfx.npc.NpcDefinition
import net.foxmediax.scriptfx.npc.NpcModelPreset
import net.foxmediax.scriptfx.npc.NpcRegistry
import net.foxmediax.scriptfx.npc.NpcAnimationCatalog
import java.io.File
import net.foxmediax.scriptfx.gui.PanelSection

/**
 * Раздел NPC в стиле студии:
 * список | 3D-превью | вкладки полей + параметры
 */
class NpcEditorPage(host: PanelHost) : PanelPage(host) {

    override val breadcrumb: String
        get() = when {
            selected != null && project != null -> "NPC -> $project / ${selected!!.id}"
            project != null -> "NPC -> $project"
            else -> "NPC"
        }

    private lateinit var ctx: Context
    private lateinit var overlays: OverlayHost
    private lateinit var listBox: LinearLayout
    private lateinit var previewView: NpcPreviewView
    private lateinit var formHost: LinearLayout
    private lateinit var projectLabel: TextView

    private lateinit var tabMain: TextView
    private lateinit var tabDialogs: TextView
    private lateinit var tabBehavior: TextView
    private lateinit var tabAnims: TextView

    private lateinit var idEdit: EditText
    private lateinit var nameEdit: EditText
    private lateinit var modelEdit: EditText
    private lateinit var textureEdit: EditText
    private lateinit var animationEdit: EditText
    private lateinit var defaultAnimEdit: EditText
    private lateinit var modeEdit: EditText
    private lateinit var interactScriptEdit: EditText
    private lateinit var interactKeyEdit: EditText

    private var project: String? = null
    private var selected: NpcDefinition? = null
    private var currentTab = 0

    private val flagFriendly = ToggleState(true)
    private val flagTrader = ToggleState(false)
    private val flagAggressive = ToggleState(false)
    private val flagAi = ToggleState(true)

    private data class ToggleState(var on: Boolean)

    override fun onDestroy() {
        NpcPreviewHelper.clear()
        if (this::overlays.isInitialized) overlays.clear()
    }

    override fun createView(ctx: Context): View {
        this.ctx = ctx
        NpcRegistry.reload()

        val root = FrameLayout(ctx).apply {
            background = ColorDrawable(COL_CONTENT)
        }
        val overlayLayer = FrameLayout(ctx)
        overlays = OverlayHost(ctx, overlayLayer)

        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        body.addView(buildListColumn(ctx), lp(dp(ctx, 240), MATCH))
        body.addView(vLine(ctx), lp(px1(ctx), MATCH))
        body.addView(buildPreviewColumn(ctx), LinearLayout.LayoutParams(0, MATCH, 1.15f))
        body.addView(vLine(ctx), lp(px1(ctx), MATCH))
        body.addView(buildRightColumn(ctx), lp(dp(ctx, 430), MATCH))

        root.addView(body, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(overlayLayer, FrameLayout.LayoutParams(MATCH, MATCH))

        ensureProject()
        refreshList()
        val first = currentNpcs().firstOrNull()
        if (first != null) selectNpc(first) else clearSelection()
        return root
    }

    // ------------------------------------------------------------------
    // LEFT
    // ------------------------------------------------------------------

    private fun buildListColumn(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_SIDEBAR)
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
        }

        col.addView(TextView(ctx).apply {
            text = "NPC"
            textSize = 13f
            setTextColor(COL_ACCENT)
        }, lp(MATCH, WRAP))

        projectLabel = TextView(ctx).apply {
            textSize = 11f
            setTextColor(COL_MUTED)
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 8))
        }
        col.addView(projectLabel, lp(MATCH, WRAP))

        col.addView(
            PanelUi.accentButton(ctx, "+ Создать NPC") { createNpcDialog() },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) }
        )
        col.addView(
            PanelUi.flatButton(ctx, "Сменить проект") { pickProject() },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) }
        )
        col.addView(
            PanelUi.flatButton(ctx, "Обновить") {
                NpcRegistry.reload()
                refreshList()
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
        projectLabel.text = "Проект: ${project ?: "—"}"
        listBox.removeAllViews()
        val npcs = currentNpcs()
        if (npcs.isEmpty()) {
            listBox.addView(
                PanelUi.mutedText(ctx, "Нет NPC. Нажми «Создать NPC»"),
                lp(MATCH, WRAP).apply { topMargin = dp(ctx, 8) }
            )
            return
        }
        for (npc in npcs) {
            val active = selected?.id == npc.id
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ColorDrawable(if (active) COL_BTN_SELECTED else COL_BTN)
                setPadding(dp(ctx, 10), dp(ctx, 10), dp(ctx, 10), dp(ctx, 10))
                isClickable = true
                setOnClickListener { selectNpc(npc) }
            }
            row.addView(TextView(ctx).apply {
                text = "👤  ${npc.displayName.ifBlank { npc.id }}"
                textSize = 13f
                setTextColor(COL_TEXT)
                setSingleLine(true)
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            listBox.addView(row, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 6) })
        }
    }

    // ------------------------------------------------------------------
    // CENTER PREVIEW
    // ------------------------------------------------------------------

    private fun buildPreviewColumn(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_CONTENT)
        }

        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            background = ColorDrawable(COL_SIDEBAR)
            setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8))
        }
        bar.addView(PanelUi.iconButton(ctx, "⟲") {
            NpcPreviewHelper.clear()
            selected?.let { NpcPreviewHelper.setDefinition(it) }
        }, lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 6) })
        bar.addView(PanelUi.iconButton(ctx, "↻") {
            NpcRegistry.reload()
            selected?.let { id ->
                val p = project ?: return@let
                NpcRegistry.get(p, id.id)?.let { selectNpc(it) }
            }
        }, lp(WRAP, WRAP))
        col.addView(bar, lp(MATCH, WRAP))

        val wrap = FrameLayout(ctx).apply {
            background = ColorDrawable(0xFF1A1028.toInt())
            setPadding(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6))
        }
        previewView = NpcPreviewView(ctx)
        wrap.addView(previewView, FrameLayout.LayoutParams(MATCH, MATCH))
        col.addView(wrap, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return col
    }

    // ------------------------------------------------------------------
    // RIGHT
    // ------------------------------------------------------------------

    private fun buildRightColumn(ctx: Context): View {
        val outer = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ColorDrawable(COL_SIDEBAR)
        }

        val main = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
        }

        val tabs = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tabMain = makeTab(ctx, "Основное", 0)
        tabDialogs = makeTab(ctx, "Диалоги", 1)
        tabBehavior = makeTab(ctx, "Поведение", 2)
        tabAnims = makeTab(ctx, "Анимации", 3)
        tabs.addView(tabMain, lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 6) })
        tabs.addView(tabDialogs, lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 6) })
        tabs.addView(tabBehavior, lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 6) })
        tabs.addView(tabAnims, lp(WRAP, WRAP))
        main.addView(tabs, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 12) })

        formHost = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx)
        scroll.addView(formHost, lp(MATCH, WRAP))
        main.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        outer.addView(main, LinearLayout.LayoutParams(0, MATCH, 1f))
        outer.addView(vLine(ctx), lp(px1(ctx), MATCH))
        outer.addView(buildParams(ctx), lp(dp(ctx, 170), MATCH))
        renderForm()
        return outer
    }

    private fun makeTab(ctx: Context, title: String, index: Int): TextView =
        TextView(ctx).apply {
            text = title
            textSize = 12f
            setTextColor(COL_TEXT)
            background = ColorDrawable(if (currentTab == index) COL_BTN_SELECTED else COL_BTN)
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
            isClickable = true
            setOnClickListener {
                currentTab = index
                refreshTabs()
                renderForm()
            }
        }

    private fun refreshTabs() {
        fun style(tv: TextView, idx: Int) {
            tv.background = ColorDrawable(if (currentTab == idx) COL_BTN_SELECTED else COL_BTN)
        }
        if (this::tabMain.isInitialized) {
            style(tabMain, 0)
            style(tabDialogs, 1)
            style(tabBehavior, 2)
            style(tabAnims, 3)
        }
    }

    private fun renderForm() {
        formHost.removeAllViews()
        when (currentTab) {
            0 -> renderMainForm()
            1 -> renderDialogsForm()
            2 -> renderBehaviorForm()
            3 -> renderAnimsForm()
        }
    }

    private fun renderMainForm() {
        val def = selected
        formHost.addView(label("ID"), lp(MATCH, WRAP))
        idEdit = edit(def?.id ?: "", enabled = false)
        formHost.addView(idEdit, fieldLp())

        formHost.addView(label("Имя"), lp(MATCH, WRAP))
        nameEdit = edit(def?.displayName ?: "")
        formHost.addView(nameEdit, fieldLp())

        formHost.addView(label("Модель"), lp(MATCH, WRAP))
        modelEdit = edit(def?.model ?: NpcModelPreset.MALE.model)
        formHost.addView(modelEdit, fieldLp())

        formHost.addView(label("Пресет модели"), lp(MATCH, WRAP))
        val presetRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        for (preset in NpcModelPreset.entries) {
            presetRow.addView(
                PanelUi.flatButton(ctx, preset.label) {
                    modelEdit.setText(preset.model)
                    textureEdit.setText(preset.texture)
                    animationEdit.setText(preset.animation)
                    // defaultAnim обычно idle
                    if (this@NpcEditorPage::defaultAnimEdit.isInitialized) {
                        defaultAnimEdit.setText("idle")
                    }
                },
                lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 6) }
            )
        }
        formHost.addView(presetRow, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 10) })

        formHost.addView(label("Текстура"), lp(MATCH, WRAP))
        textureEdit = edit(def?.texture ?: NpcModelPreset.MALE.texture)
        formHost.addView(textureEdit, fieldLp())

        formHost.addView(label("Анимация (файл)"), lp(MATCH, WRAP))
        animationEdit = edit(def?.animation ?: NpcModelPreset.MALE.animation)
        formHost.addView(animationEdit, fieldLp())

        formHost.addView(label("Анимация idle"), lp(MATCH, WRAP))
        defaultAnimEdit = edit(def?.defaultAnim ?: "idle")
        formHost.addView(defaultAnimEdit, fieldLp())

        formHost.addView(
            PanelUi.flatButton(ctx, "Выбрать анимацию из файла…") {
                val fileId = textOr(animationEdit, def?.animation ?: "")
                val names = NpcAnimationCatalog.listNames(fileId)
                if (names.isEmpty()) {
                    overlays.actions(
                        "Нет анимаций",
                        listOf("ОК" to {})
                    )
                    return@flatButton
                }
                overlays.actions(
                    "Анимация",
                    names.map { full ->
                        val short = NpcAnimationCatalog.shortLabel(full)
                        short to {
                            defaultAnimEdit.setText(full)
                            Unit
                        }
                    }
                )
            },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) }
        )

        formHost.addView(label("Режим (dialog/traiding/interact)"), lp(MATCH, WRAP))
        modeEdit = edit(def?.defaultMode ?: "interact")
        formHost.addView(label("Режим (быстрый выбор)"), lp(MATCH, WRAP))
        val modeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        listOf("interact", "dialog", "traiding").forEach { m ->
            modeRow.addView(
                PanelUi.flatButton(ctx, m) { modeEdit.setText(m) },
                lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 6) }
            )
        }
        formHost.addView(modeRow, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 10) })
        formHost.addView(modeEdit, fieldLp())

        formHost.addView(
            PanelUi.accentButton(ctx, "Сохранить") { saveSelected() },
            lp(MATCH, WRAP).apply { topMargin = dp(ctx, 6) }
        )
        formHost.addView(
            PanelUi.dangerButton(ctx, "Удалить NPC") { deleteSelected() },
            lp(MATCH, WRAP).apply { topMargin = dp(ctx, 8) }
        )
    }

    private fun renderDialogsForm() {
        val def = selected

        formHost.addView(label("Скрипт при взаимодействии"), lp(MATCH, WRAP))
        interactScriptEdit = edit(def?.onInteractScript ?: "")
        formHost.addView(interactScriptEdit, fieldLp())

        formHost.addView(
            PanelUi.flatButton(ctx, "Выбрать .sfxs из проекта…") {
                val p = project ?: return@flatButton
                val root = File(net.foxmediax.scriptfx.npc.NpcRegistry.projectsRoot(), p)
                if (!root.isDirectory) {
                    overlays.actions("Папка проекта не найдена", listOf("ОК" to {}))
                    return@flatButton
                }
                val scripts = root.walkTopDown()
                    .filter { it.isFile && it.extension.equals("sfxs", true) }
                    .map { it.relativeTo(root).path.replace('\\', '/').removeSuffix(".sfxs") }
                    .sorted()
                    .toList()
                if (scripts.isEmpty()) {
                    overlays.actions(
                        "В проекте нет .sfxs\nСоздай скрипт в разделе «Сценарии»",
                        listOf("ОК" to {})
                    )
                    return@flatButton
                }
                overlays.actions(
                    "Скрипт NPC",
                    scripts.map { path ->
                        path to {
                            interactScriptEdit.setText(path.substringAfterLast('/'))
                            Unit
                        }
                    }
                )
            },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) }
        )

        formHost.addView(label("Клавиша взаимодействия"), lp(MATCH, WRAP))
        interactKeyEdit = edit(def?.interactKey ?: "X")
        formHost.addView(interactKeyEdit, fieldLp())

        val keyRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        for (k in listOf("X", "G", "F", "C", "V")) {
            keyRow.addView(
                PanelUi.flatButton(ctx, k) { interactKeyEdit.setText(k) },
                lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 6) }
            )
        }
        formHost.addView(keyRow, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 12) })

        formHost.addView(label("Шаблоны"), lp(MATCH, WRAP))
        formHost.addView(
            PanelUi.flatButton(ctx, "Шаблон: приветствие") {
                interactScriptEdit.setText("npc_hello")
                overlays.actions(
                    "Пример npc_hello.sfxs:\n\nprint \"Привет, путник!\"\nticktime 1.sec\nstopscript",
                    listOf("ОК" to {})
                )
            },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 6) }
        )
        formHost.addView(
            PanelUi.flatButton(ctx, "Шаблон: квест") {
                interactScriptEdit.setText("npc_quest")
                overlays.actions(
                    "Пример npc_quest.sfxs:\n\nquest_start \"tutorial\"\nprint \"Новый квест!\"\nstopscript",
                    listOf("ОК" to {})
                )
            },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) }
        )

        formHost.addView(
            PanelUi.accentButton(ctx, "Создать .sfxs и привязать") {
                val p = project ?: return@accentButton
                val name = textOr(interactScriptEdit, "npc_hello")
                    .substringAfterLast('/')
                    .removeSuffix(".sfxs")
                    .ifBlank { "npc_hello" }
                    .lowercase()
                    .replace(Regex("[^a-z0-9_]+"), "_")

                val dir = File(net.foxmediax.scriptfx.npc.NpcRegistry.projectsRoot(), "$p/scripts")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "$name.sfxs")
                if (!file.exists()) {
                    file.writeText(
                        "print \"Привет! Я $name.\"\n" +
                                "ticktime 1.sec\n" +
                                "stopscript\n"
                    )
                }
                interactScriptEdit.setText(name)
                saveSelected()
                overlays.actions(
                    "Скрипт: ${file.absolutePath}\nПривязан как «$name»",
                    listOf(
                        "Открыть Сценарии" to {
                            host.navigate(net.foxmediax.scriptfx.gui.PanelSection.PROJECTS)
                            Unit
                        },
                        "ОК" to {}
                    )
                )
            },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) }
        )

        formHost.addView(
            PanelUi.accentButton(ctx, "Сохранить диалог") { saveSelected() },
            lp(MATCH, WRAP)
        )
        formHost.addView(
            PanelUi.mutedText(
                ctx,
                "onInteractScript — имя без .sfxs. Режим NPC: dialog / interact."
            ),
            lp(MATCH, WRAP).apply { topMargin = dp(ctx, 10) }
        )
    }

    private fun renderBehaviorForm() {
        val def = selected ?: run {
            formHost.addView(PanelUi.mutedText(ctx, "NPC не выбран"), lp(MATCH, WRAP))
            return
        }

        fun toggle(label: String, value: Boolean, set: (Boolean) -> Unit): View {
            var on = value
            return PanelUi.flatButton(ctx, "${if (on) "☑" else "☐"} $label") {
                on = !on
                set(on)
                renderForm() // перерисует с актуальным selected после save; проще save ниже
            }.also {
                // храним через side-effect set
            }
        }

        // Проще: кнопки сразу пишут в selected через copy+select
        fun flagButton(title: String, current: Boolean, apply: (NpcDefinition) -> NpcDefinition) {
            formHost.addView(
                PanelUi.flatButton(ctx, "${if (current) "☑" else "☐"} $title") {
                    val p = project ?: return@flatButton
                    val updated = apply(def)
                    if (NpcRegistry.save(p, updated)) {
                        selected = updated
                        NpcPreviewHelper.setDefinition(updated)
                        renderForm()
                    }
                },
                lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 6) }
            )
        }

        flagButton("Смотреть на игрока", def.lookAtPlayer) { it.copy(lookAtPlayer = !it.lookAtPlayer) }
        flagButton("Бессмертный", def.invulnerable) { it.copy(invulnerable = !it.invulnerable) }
        flagButton("Гравитация", def.gravity) { it.copy(gravity = !it.gravity) }
        flagButton("Коллизия", def.collide) { it.copy(collide = !it.collide) }
        flagButton("Ник над головой", def.nametag) { it.copy(nametag = !it.nametag) }
        flagButton("Без звука", def.silent) { it.copy(silent = !it.silent) }

        formHost.addView(label("Скорость"), lp(MATCH, WRAP).apply { topMargin = dp(ctx, 8) })
        val speedEdit = edit(def.speed.toString())
        formHost.addView(speedEdit, fieldLp())

        formHost.addView(label("Max HP"), lp(MATCH, WRAP))
        val hpEdit = edit(def.maxHealth.toString())
        formHost.addView(hpEdit, fieldLp())

        formHost.addView(
            PanelUi.accentButton(ctx, "Сохранить поведение") {
                val p = project ?: return@accentButton
                val updated = def.copy(
                    speed = speedEdit.text?.toString()?.toFloatOrNull() ?: def.speed,
                    maxHealth = hpEdit.text?.toString()?.toFloatOrNull() ?: def.maxHealth
                )
                if (NpcRegistry.save(p, updated)) {
                    selected = updated
                    NpcPreviewHelper.setDefinition(updated)
                    renderForm()
                }
            },
            lp(MATCH, WRAP).apply { topMargin = dp(ctx, 8) }
        )
    }

    private fun renderAnimsForm() {
        val def = selected
        formHost.addView(label("Файл анимаций (GeckoLib)"), lp(MATCH, WRAP))
        animationEdit = edit(def?.animation ?: NpcModelPreset.MALE.animation)
        formHost.addView(animationEdit, fieldLp())

        formHost.addView(
            PanelUi.flatButton(ctx, "Обновить список из файла") {
                renderForm()
            },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) }
        )

        formHost.addView(label("Анимация по умолчанию"), lp(MATCH, WRAP))
        defaultAnimEdit = edit(def?.defaultAnim ?: "idle")
        formHost.addView(
            PanelUi.flatButton(ctx, "Выбрать idle-анимацию из файла…") {
                val fileId = textOr(animationEdit, def?.animation ?: "")
                val names = try {
                    net.foxmediax.scriptfx.npc.NpcAnimationCatalog.listNames(fileId)
                } catch (_: Throwable) {
                    emptyList()
                }
                if (names.isEmpty()) {
                    overlays.actions(
                        "Анимации не найдены для «$fileId»\n" +
                                "Нужен .animation.json в assets или config/scriptfx/animations/",
                        listOf("ОК" to {})
                    )
                    return@flatButton
                }
                overlays.actions(
                    "Анимация",
                    names.map { full ->
                        val short = try {
                            net.foxmediax.scriptfx.npc.NpcAnimationCatalog.shortLabel(full)
                        } catch (_: Throwable) {
                            full.substringAfterLast('.')
                        }
                        short to {
                            defaultAnimEdit.setText(full)
                            Unit
                        }
                    }
                )
            },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) }
        )
        formHost.addView(defaultAnimEdit, fieldLp())

        val fileId = textOr(animationEdit, def?.animation ?: "")
        val names = NpcAnimationCatalog.listNames(fileId)

        formHost.addView(label("Выбор из файла"), lp(MATCH, WRAP))
        if (names.isEmpty()) {
            formHost.addView(
                PanelUi.mutedText(
                    ctx,
                    "Анимации не найдены.\n" +
                            "Ожидается assets/<ns>/animations/<path>.animation.json\n" +
                            "или config/scriptfx/animations/<path>.animation.json"
                ),
                lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) }
            )
        } else {
            formHost.addView(
                PanelUi.mutedText(ctx, "Найдено: ${names.size}"),
                lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 6) }
            )
            for (name in names) {
                val labelText = NpcAnimationCatalog.shortLabel(name)
                val selectedNow = textOr(defaultAnimEdit, "") == name ||
                        textOr(defaultAnimEdit, "") == labelText
                formHost.addView(
                    PanelUi.flatButton(
                        ctx,
                        if (selectedNow) "✓ $labelText" else labelText
                    ) {
                        defaultAnimEdit.setText(name)
                        // если в игре ждут короткое имя — можно:
                        // defaultAnimEdit.setText(NpcAnimationCatalog.shortLabel(name))
                        saveSelected()
                        renderForm()
                    },
                    lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 4) }
                )
            }
        }

        formHost.addView(
            PanelUi.accentButton(ctx, "Сохранить анимации") { saveSelected() },
            lp(MATCH, WRAP).apply { topMargin = dp(ctx, 10) }
        )
    }

    private fun buildParams(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
            background = ColorDrawable(COL_SIDEBAR)
        }
        col.addView(TextView(ctx).apply {
            text = "Параметры"
            textSize = 13f
            setTextColor(COL_ACCENT)
        }, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 12) })

        col.addView(toggleRow("Дружелюбный", flagFriendly), rowLp())
        col.addView(toggleRow("Торговец", flagTrader), rowLp())
        col.addView(toggleRow("Агрессивный", flagAggressive), rowLp())
        col.addView(toggleRow("Использует ИИ", flagAi), rowLp())

        col.addView(PanelUi.spacer(ctx, 14))
        col.addView(PanelUi.mutedText(ctx, "Диалог"), lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) })
        col.addView(
            PanelUi.flatButton(ctx, "Открыть редактор") {
                currentTab = 1
                refreshTabs()
                renderForm()
            },
            lp(MATCH, WRAP)
        )
        return col
    }

    private fun toggleRow(title: String, state: ToggleState): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(ctx).apply {
            text = title
            textSize = 12f
            setTextColor(COL_TEXT)
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        val pill = TextView(ctx).apply {
            text = if (state.on) "ON" else "OFF"
            textSize = 11f
            setTextColor(COL_TEXT)
            background = ColorDrawable(if (state.on) COL_BTN_SELECTED else COL_BTN)
            setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6))
            isClickable = true
            setOnClickListener {
                state.on = !state.on
                text = if (state.on) "ON" else "OFF"
                background = ColorDrawable(if (state.on) COL_BTN_SELECTED else COL_BTN)
            }
        }
        row.addView(pill, lp(WRAP, WRAP))
        return row
    }

    // ------------------------------------------------------------------
    // data ops
    // ------------------------------------------------------------------

    private fun ensureProject() {
        val projects = NpcRegistry.listProjects()
        project = projects.firstOrNull()
        if (project == null) {
            // создаём дефолтный проект-папку через projects root
            val root = NpcRegistry.projectsRoot()
            val def = java.io.File(root, "default")
            def.mkdirs()
            java.io.File(def, "npc").mkdirs()
            NpcRegistry.reload()
            project = "default"
        }
    }

    private fun currentNpcs(): List<NpcDefinition> {
        val p = project ?: return emptyList()
        return NpcRegistry.listNpcs(p)
    }

    private fun selectNpc(def: NpcDefinition) {
        selected = def
        NpcPreviewHelper.setDefinition(def)
        refreshList()
        currentTab = 0
        refreshTabs()
        renderForm()
        host.refreshBreadcrumb()
    }

    private fun clearSelection() {
        selected = null
        NpcPreviewHelper.setDefinition(null)
        refreshList()
        currentTab = 0
        refreshTabs()
        renderForm()
        host.refreshBreadcrumb()
    }

    private fun createNpcDialog() {
        val p = project ?: return
        overlays.input("ID нового NPC", "guard") { raw ->
            val id = raw.trim()
            if (id.isEmpty()) return@input
            val def = NpcDefinition.create(id, NpcModelPreset.MALE)
            if (NpcRegistry.save(p, def)) {
                NpcRegistry.reload()
                refreshList()
                NpcRegistry.get(p, id)?.let { selectNpc(it) }
            }
        }
    }

    private fun pickProject() {
        val projects = NpcRegistry.listProjects()
        if (projects.isEmpty()) {
            overlays.actions(
                "Проекты",
                listOf("Нет проектов" to { })
            )
            return
        }
        val items: List<Pair<String, () -> Unit>> = projects.map { name ->
            name to {
                project = name
                clearSelection()
                refreshList()
                currentNpcs().firstOrNull()?.let { selectNpc(it) }
                Unit
            }
        }
        overlays.actions("Выбор проекта", items)
    }

    private fun saveSelected() {
        val p = project ?: return
        val current = selected ?: return

        // поля могут быть не инициализированы на других вкладках
        val newDef = current.copy(
            displayName = textOr(nameEdit, current.displayName),
            model = textOr(modelEdit, current.model),
            texture = textOr(textureEdit, current.texture),
            animation = textOr(animationEdit, current.animation),
            defaultAnim = textOr(defaultAnimEdit, current.defaultAnim),
            defaultMode = textOr(modeEdit, current.defaultMode),
            onInteractScript = textOr(interactScriptEdit, current.onInteractScript),
            interactKey = textOr(interactKeyEdit, current.interactKey)
        )

        if (NpcRegistry.save(p, newDef)) {
            selected = newDef
            NpcPreviewHelper.setDefinition(newDef)
            refreshList()
            renderForm()
        }
    }

    private fun deleteSelected() {
        val p = project ?: return
        val id = selected?.id ?: return
        overlays.confirm(
            "Удалить NPC \"$id\"?",
            "Удалить",
            "Отмена",
            onYes = {
                NpcRegistry.delete(p, id)
                NpcRegistry.reload()
                clearSelection()
            }
        )
    }

    private fun textOr(edit: EditText?, fallback: String): String {
        if (edit == null || !this::formHost.isInitialized) return fallback
        return try {
            edit.text?.toString()?.trim()?.ifBlank { fallback } ?: fallback
        } catch (_: Throwable) {
            fallback
        }
    }

    // ------------------------------------------------------------------
    // ui helpers
    // ------------------------------------------------------------------

    private fun label(t: String) = PanelUi.mutedText(ctx, t, 11f)

    private fun edit(value: String, enabled: Boolean = true): EditText =
        EditText(ctx).apply {
            setText(value)
            textSize = 13f
            setTextColor(COL_TEXT)
            setHintTextColor(COL_MUTED)
            background = ColorDrawable(COL_BTN)
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
            setSingleLine(true)
            isEnabled = enabled
        }

    private fun fieldLp() = lp(MATCH, WRAP).apply {
        topMargin = dp(ctx, 4)
        bottomMargin = dp(ctx, 10)
    }

    private fun rowLp() = lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) }

    private fun vLine(ctx: Context) =
        View(ctx).apply { background = ColorDrawable(COL_DIVIDER) }
}