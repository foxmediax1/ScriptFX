package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.View
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
import net.foxmediax.scriptfx.triggers.TriggerDefinition
import net.foxmediax.scriptfx.triggers.TriggerKind
import net.foxmediax.scriptfx.triggers.TriggerRegistry
import net.minecraft.client.Minecraft
import java.io.File

class TriggersPage(host: PanelHost) : PanelPage(host) {

    override val breadcrumb: String get() = "Триггеры"

    private lateinit var ctx: Context
    private lateinit var listBox: LinearLayout
    private lateinit var detailHost: LinearLayout
    private lateinit var overlays: OverlayHost

    private var project: String = "default"
    private var selected: TriggerDefinition? = null

    private val browser: FileBrowser
        get() = ControlPanelFragment.fileBrowser

    override fun createView(ctx: Context): View {
        this.ctx = ctx
        ensureProject()
        TriggerRegistry.reload()

        val root = FrameLayout(ctx).apply {
            background = ColorDrawable(COL_CONTENT)
        }
        val overlayLayer = FrameLayout(ctx)
        overlays = OverlayHost(ctx, overlayLayer)

        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        body.addView(buildList(ctx), lp(dp(ctx, 260), MATCH))
        body.addView(View(ctx).apply { background = ColorDrawable(COL_DIVIDER) }, lp(px1(ctx), MATCH))
        body.addView(buildDetail(ctx), LinearLayout.LayoutParams(0, MATCH, 1f))

        root.addView(body, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(overlayLayer, FrameLayout.LayoutParams(MATCH, MATCH))

        selected = currentItems().firstOrNull()
        refreshList()
        renderDetail()
        return root
    }

    override fun onDestroy() {
        if (this::overlays.isInitialized) overlays.clear()
    }

    private fun ensureProject() {
        val projects = TriggerRegistry.listProjects()
        project = when {
            browser.isInsideProjectRoot() -> browser.currentDir.name
            projects.isNotEmpty() -> projects.first()
            else -> {
                File(TriggerRegistry.projectsRoot(), "default").also {
                    it.mkdirs()
                    File(it, "triggers").mkdirs()
                }
                "default"
            }
        }
    }

    private fun currentItems(): List<TriggerDefinition> =
        TriggerRegistry.list(project)

    // ------------------------------------------------------------------
    // LEFT
    // ------------------------------------------------------------------

    private fun buildList(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_SIDEBAR)
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
        }

        col.addView(TextView(ctx).apply {
            text = "Триггеры"
            textSize = 13f
            setTextColor(COL_ACCENT)
        }, lp(MATCH, WRAP))

        col.addView(TextView(ctx).apply {
            text = "Проект: $project"
            textSize = 11f
            setTextColor(COL_MUTED)
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 8))
        }, lp(MATCH, WRAP))

        col.addView(
            PanelUi.accentButton(ctx, "+ Создать триггер") { createTriggerDialog() },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 10) }
        )

        listBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx)
        scroll.addView(listBox, lp(MATCH, WRAP))
        col.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return col
    }

    private fun refreshList() {
        listBox.removeAllViews()
        val items = currentItems()
        if (items.isEmpty()) {
            listBox.addView(
                PanelUi.mutedText(ctx, "Нет триггеров.\nНажми «Создать триггер»"),
                lp(MATCH, WRAP)
            )
            return
        }
        for (item in items) {
            val active = selected?.id == item.id
            val icon = when (item.kind) {
                TriggerKind.ZONE -> "🟣"
                TriggerKind.INTERACT -> "👆"
                TriggerKind.TIMER -> "⏱"
                TriggerKind.WORLD_START -> "🌍"
            }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = ColorDrawable(if (active) COL_BTN_SELECTED else COL_BTN)
                setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
                isClickable = true
                setOnClickListener {
                    selected = item
                    refreshList()
                    renderDetail()
                }
            }
            row.addView(TextView(ctx).apply {
                text = "$icon  ${item.title}"
                textSize = 13f
                setTextColor(COL_TEXT)
            }, lp(MATCH, WRAP))
            row.addView(TextView(ctx).apply {
                text = item.id
                textSize = 11f
                setTextColor(COL_MUTED)
                setPadding(0, dp(ctx, 2), 0, 0)
            }, lp(MATCH, WRAP))
            listBox.addView(row, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 6) })
        }
    }

    // ------------------------------------------------------------------
    // DETAIL
    // ------------------------------------------------------------------

    private fun buildDetail(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_CONTENT)
            setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 16))
        }
        detailHost = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx)
        scroll.addView(detailHost, lp(MATCH, WRAP))
        col.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return col
    }

    private fun renderDetail() {
        detailHost.removeAllViews()
        val item = selected
        if (item == null) {
            detailHost.addView(PanelUi.mutedText(ctx, "Выбери триггер слева"), lp(MATCH, WRAP))
            return
        }

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_SIDEBAR)
            setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
        }

        card.addView(TextView(ctx).apply {
            text = "⚡  Триггер: ${item.title}"
            textSize = 15f
            setTextColor(COL_TEXT)
        }, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 14) })

        card.addView(label("Тип"), lp(MATCH, WRAP))
        card.addView(valueBox(kindLabel(item.kind)), fieldLp())

        if (item.kind == TriggerKind.ZONE) {
            card.addView(label("Область"), lp(MATCH, WRAP))
            val areaRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            areaRow.addView(TextView(ctx).apply {
                text = "Радиус"
                textSize = 13f
                setTextColor(COL_TEXT)
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            areaRow.addView(TextView(ctx).apply {
                text = item.radius.toInt().toString()
                textSize = 13f
                setTextColor(COL_ACCENT)
                background = ColorDrawable(COL_BTN)
                setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6))
                isClickable = true
                setOnClickListener {
                    overlays.input("Радиус", item.radius.toInt().toString()) { raw ->
                        item.radius = raw.trim().toDoubleOrNull()?.coerceIn(1.0, 128.0) ?: item.radius
                        persist(item)
                    }
                }
            }, lp(WRAP, WRAP))
            card.addView(areaRow, fieldLp())

            card.addView(label("Координаты (x y z)"), lp(MATCH, WRAP))
            card.addView(TextView(ctx).apply {
                text = "%.1f  %.1f  %.1f".format(item.x, item.y, item.z)
                textSize = 13f
                setTextColor(COL_TEXT)
                background = ColorDrawable(COL_BTN)
                setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
                isClickable = true
                setOnClickListener {
                    val p = Minecraft.getInstance().player
                    if (p != null) {
                        item.x = p.x
                        item.y = p.y
                        item.z = p.z
                        persist(item)
                    } else {
                        overlays.actions("Нужен мир / игрок", listOf("ОК" to {}))
                    }
                }
            }, fieldLp())
            card.addView(PanelUi.mutedText(ctx, "Клик по координатам = позиция игрока"), lp(MATCH, WRAP))
        }

        if (item.kind == TriggerKind.INTERACT) {
            card.addView(label("Предмет (item id)"), lp(MATCH, WRAP))
            card.addView(TextView(ctx).apply {
                text = item.itemId ?: "minecraft:stick"
                textSize = 13f
                setTextColor(COL_TEXT)
                background = ColorDrawable(COL_BTN)
                setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
                isClickable = true
                setOnClickListener {
                    overlays.input("Item id", item.itemId ?: "minecraft:stick") { raw ->
                        item.itemId = raw.trim().ifBlank { "minecraft:stick" }
                        persist(item)
                    }
                }
            }, fieldLp())
        }

        card.addView(label("Действия (скрипт / команда)"), lp(MATCH, WRAP))
        card.addView(TextView(ctx).apply {
            text = item.actionScript.ifBlank { "Запустить сценарий" }
            textSize = 13f
            setTextColor(COL_TEXT)
            background = ColorDrawable(COL_BTN)
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
            isClickable = true
            setOnClickListener {
                overlays.input("Скрипт или команда", item.actionScript) { raw ->
                    item.actionScript = raw.trim()
                    persist(item)
                }
            }
        }, fieldLp())

        card.addView(checkRow("Один раз", item.once) {
            item.once = it
            persist(item)
        }, lp(MATCH, WRAP).apply { topMargin = dp(ctx, 4) })

        card.addView(checkRow("Активен", item.active) {
            item.active = it
            persist(item)
        }, lp(MATCH, WRAP).apply { topMargin = dp(ctx, 6) })

        detailHost.addView(card, lp(MATCH, WRAP))

        detailHost.addView(
            PanelUi.accentButton(ctx, "Сохранить") { persist(item) },
            lp(WRAP, WRAP).apply { topMargin = dp(ctx, 16); rightMargin = dp(ctx, 8) }
        )

        detailHost.addView(
            PanelUi.dangerButton(ctx, "Удалить триггер") {
                overlays.confirm(
                    "Удалить «${item.title}» и файл?",
                    "Удалить",
                    "Отмена",
                    onYes = {
                        TriggerRegistry.delete(item.id)
                        selected = currentItems().firstOrNull()
                        refreshList()
                        renderDetail()
                        Unit
                    }
                )
            },
            lp(WRAP, WRAP).apply { topMargin = dp(ctx, 10) }
        )
    }

    private fun persist(item: TriggerDefinition) {
        item.project = project
        TriggerRegistry.save(item)
        selected = TriggerRegistry.get(item.id) ?: item
        refreshList()
        renderDetail()
    }

    private fun createTriggerDialog() {
        overlays.actions(
            "Тип триггера",
            listOf(
                "Вход в зону" to {
                    create(TriggerKind.ZONE, "При входе в зону", "zone_trigger")
                    Unit
                },
                "Взаимодействие" to {
                    create(TriggerKind.INTERACT, "При взаимодействии", "interact_trigger")
                    Unit
                },
                "Старт мира" to {
                    create(TriggerKind.WORLD_START, "При входе в мир", "world_start")
                    Unit
                },
                "По таймеру (скоро)" to {
                    create(TriggerKind.TIMER, "По таймеру", "timer_trigger")
                    Unit
                }
            )
        )
    }

    private fun create(kind: TriggerKind, title: String, baseId: String) {
        var id = baseId
        var n = 2
        while (TriggerRegistry.get(id) != null) {
            id = "${baseId}_$n"
            n++
        }
        val p = Minecraft.getInstance().player
        val def = TriggerDefinition(
            id = id,
            title = title,
            kind = kind,
            project = project,
            x = p?.x ?: 0.0,
            y = p?.y ?: 64.0,
            z = p?.z ?: 0.0,
            radius = 10.0,
            actionScript = "",
            once = true,
            active = true,
            itemId = if (kind == TriggerKind.INTERACT) "minecraft:stick" else null
        )
        TriggerRegistry.save(def)
        selected = def
        refreshList()
        renderDetail()
    }

    private fun kindLabel(k: TriggerKind) = when (k) {
        TriggerKind.ZONE -> "Вход в зону"
        TriggerKind.INTERACT -> "Взаимодействие"
        TriggerKind.TIMER -> "По таймеру"
        TriggerKind.WORLD_START -> "Старт мира"
    }

    private fun label(t: String) = PanelUi.mutedText(ctx, t, 11f)

    private fun valueBox(t: String) = TextView(ctx).apply {
        text = t
        textSize = 13f
        setTextColor(COL_TEXT)
        background = ColorDrawable(COL_BTN)
        setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
    }

    private fun fieldLp() = lp(MATCH, WRAP).apply {
        topMargin = dp(ctx, 4)
        bottomMargin = dp(ctx, 12)
    }

    private fun checkRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setOnClickListener { onToggle(!checked) }
        }
        row.addView(TextView(ctx).apply {
            text = if (checked) "☑" else "☐"
            textSize = 16f
            setTextColor(if (checked) COL_ACCENT else COL_MUTED)
        }, lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 10) })
        row.addView(TextView(ctx).apply {
            text = label
            textSize = 13f
            setTextColor(COL_TEXT)
        }, lp(WRAP, WRAP))
        return row
    }
}