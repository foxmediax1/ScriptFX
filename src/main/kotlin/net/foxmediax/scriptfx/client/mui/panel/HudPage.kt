package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.View
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.hud.HudRuntime
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
import net.foxmediax.scriptfx.hud.HudAnchor
import net.foxmediax.scriptfx.hud.HudDefinition
import net.foxmediax.scriptfx.hud.HudElement
import net.foxmediax.scriptfx.hud.HudElementType
import net.foxmediax.scriptfx.hud.HudRegistry
import java.io.File

class HudPage(host: PanelHost) : PanelPage(host) {

    override val breadcrumb: String get() = "HUD"

    private lateinit var ctx: Context
    private lateinit var listBox: LinearLayout
    private lateinit var canvasBox: LinearLayout
    private lateinit var propsHost: LinearLayout
    private lateinit var overlays: OverlayHost

    private var project: String = "default"
    private var selected: HudDefinition? = null
    private var selectedEl: HudElement? = null

    private val browser: FileBrowser get() = ControlPanelFragment.fileBrowser

    override fun createView(ctx: Context): View {
        this.ctx = ctx
        ensureProject()
        HudRegistry.reload()

        val root = FrameLayout(ctx).apply { background = ColorDrawable(COL_CONTENT) }
        val overlayLayer = FrameLayout(ctx)
        overlays = OverlayHost(ctx, overlayLayer)

        val body = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        body.addView(buildList(ctx), lp(dp(ctx, 240), MATCH))
        body.addView(View(ctx).apply { background = ColorDrawable(COL_DIVIDER) }, lp(px1(ctx), MATCH))
        body.addView(buildCanvas(ctx), LinearLayout.LayoutParams(0, MATCH, 1.2f))
        body.addView(View(ctx).apply { background = ColorDrawable(COL_DIVIDER) }, lp(px1(ctx), MATCH))
        body.addView(buildProps(ctx), lp(dp(ctx, 300), MATCH))

        root.addView(body, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(overlayLayer, FrameLayout.LayoutParams(MATCH, MATCH))

        selected = HudRegistry.list(project).firstOrNull()
        selectedEl = selected?.elements?.firstOrNull()
        refreshList()
        renderCanvas()
        renderProps()
        return root
    }

    override fun onDestroy() {
        if (this::overlays.isInitialized) overlays.clear()
    }

    private fun ensureProject() {
        val projects = HudRegistry.listProjects()
        project = when {
            browser.isInsideProjectRoot() -> browser.currentDir.name
            projects.isNotEmpty() -> projects.first()
            else -> {
                File(HudRegistry.projectsRoot(), "default").also {
                    it.mkdirs(); File(it, "hud").mkdirs()
                }
                "default"
            }
        }
    }

    // ----- LEFT -----
    private fun buildList(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_SIDEBAR)
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
        }
        col.addView(TextView(ctx).apply {
            text = "HUD"
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
            PanelUi.accentButton(ctx, "+ Создать HUD") { createHudDialog() },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) }
        )
        listBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx)
        scroll.addView(listBox, lp(MATCH, WRAP))
        col.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return col
    }

    private fun refreshList() {
        listBox.removeAllViews()
        val items = HudRegistry.list(project)
        if (items.isEmpty()) {
            listBox.addView(PanelUi.mutedText(ctx, "Нет HUD.\nСоздай новый."), lp(MATCH, WRAP))
            return
        }
        for (item in items) {
            val active = selected?.id == item.id
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = ColorDrawable(if (active) COL_BTN_SELECTED else COL_BTN)
                setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
                isClickable = true
                setOnClickListener {
                    selected = item
                    selectedEl = item.elements.firstOrNull()
                    refreshList(); renderCanvas(); renderProps()
                }
            }
            row.addView(TextView(ctx).apply {
                text = "🖥  ${item.title}"
                textSize = 13f
                setTextColor(COL_TEXT)
            }, lp(MATCH, WRAP))
            row.addView(TextView(ctx).apply {
                text = item.id
                textSize = 11f
                setTextColor(COL_MUTED)
            }, lp(MATCH, WRAP))
            listBox.addView(row, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 6) })
        }
    }

    // ----- CENTER -----
    private fun buildCanvas(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_CONTENT)
        }
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ColorDrawable(COL_SIDEBAR)
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
        }
        bar.addView(TextView(ctx).apply {
            text = "Превью"
            textSize = 13f
            setTextColor(COL_TEXT)
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        bar.addView(PanelUi.flatButton(ctx, "▶ On") {
            selected?.let { HudRuntime.show(it.id) }
        }, lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 6) })
        bar.addView(PanelUi.flatButton(ctx, "■ Off") {
            selected?.let { HudRuntime.hide(it.id) }
        }, lp(WRAP, WRAP))
        col.addView(bar, lp(MATCH, WRAP))

        canvasBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 16))
        }
        val scroll = ScrollView(ctx)
        scroll.addView(canvasBox, lp(MATCH, WRAP))
        col.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return col
    }

    private fun renderCanvas() {
        canvasBox.removeAllViews()
        val def = selected
        if (def == null) {
            canvasBox.addView(PanelUi.mutedText(ctx, "Выбери HUD слева"), lp(MATCH, WRAP))
            return
        }
        canvasBox.addView(TextView(ctx).apply {
            text = "${def.title}  (${def.width.toInt()}×${def.height.toInt()})  ·  ${def.anchor.name}"
            textSize = 12f
            setTextColor(COL_MUTED)
        }, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 10) })

        val stage = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(def.background)
            setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8))
            minimumHeight = dp(ctx, def.height.toInt().coerceAtLeast(80))
        }
        for (el in def.elements) {
            val active = selectedEl?.id == el.id
            val row = TextView(ctx).apply {
                text = when (el.type) {
                    HudElementType.TEXT -> "T  ${el.text.ifBlank { el.id }}"
                    HudElementType.RECT -> "■  rect ${el.w.toInt()}×${el.h.toInt()}"
                    HudElementType.IMAGE -> "🖼  ${el.imagePath.ifBlank { el.id }}"
                }
                textSize = 12f
                setTextColor(COL_TEXT)
                background = ColorDrawable(if (active) COL_BTN_SELECTED else COL_BTN)
                setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
                isClickable = true
                setOnClickListener {
                    selectedEl = el
                    renderCanvas(); renderProps()
                }
            }
            stage.addView(row, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 4) })
        }
        canvasBox.addView(stage, lp(MATCH, WRAP))
        canvasBox.addView(
            PanelUi.flatButton(ctx, "+ Текст") { addElement(HudElementType.TEXT) },
            lp(WRAP, WRAP).apply { topMargin = dp(ctx, 12); rightMargin = dp(ctx, 6) }
        )
        canvasBox.addView(
            PanelUi.flatButton(ctx, "+ Rect") { addElement(HudElementType.RECT) },
            lp(WRAP, WRAP).apply { topMargin = dp(ctx, 6) }
        )
    }

    // ----- RIGHT -----
    private fun buildProps(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_SIDEBAR)
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
        }
        propsHost = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx)
        scroll.addView(propsHost, lp(MATCH, WRAP))
        col.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return col
    }

    private fun renderProps() {
        propsHost.removeAllViews()
        val def = selected
        if (def == null) {
            propsHost.addView(PanelUi.mutedText(ctx, "HUD не выбран"), lp(MATCH, WRAP))
            return
        }

        propsHost.addView(label("ID"), lp(MATCH, WRAP))
        propsHost.addView(value(def.id), fieldLp())

        propsHost.addView(label("Название"), lp(MATCH, WRAP))
        propsHost.addView(editable(def.title) {
            def.title = it; persist()
        }, fieldLp())

        propsHost.addView(label("Якорь"), lp(MATCH, WRAP))
        propsHost.addView(TextView(ctx).apply {
            text = def.anchor.name
            textSize = 13f
            setTextColor(COL_TEXT)
            background = ColorDrawable(COL_BTN)
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
            isClickable = true
            setOnClickListener {
                val order = HudAnchor.entries
                val i = (order.indexOf(def.anchor) + 1) % order.size
                def.anchor = order[i]
                persist()
            }
        }, fieldLp())

        propsHost.addView(label("Offset X / Y"), lp(MATCH, WRAP))
        propsHost.addView(editable("${def.offsetX.toInt()} ${def.offsetY.toInt()}") { raw ->
            val p = raw.trim().split(Regex("\\s+"))
            def.offsetX = p.getOrNull(0)?.toFloatOrNull() ?: def.offsetX
            def.offsetY = p.getOrNull(1)?.toFloatOrNull() ?: def.offsetY
            persist()
        }, fieldLp())

        propsHost.addView(label("Размер W H"), lp(MATCH, WRAP))
        propsHost.addView(editable("${def.width.toInt()} ${def.height.toInt()}") { raw ->
            val p = raw.trim().split(Regex("\\s+"))
            def.width = p.getOrNull(0)?.toFloatOrNull() ?: def.width
            def.height = p.getOrNull(1)?.toFloatOrNull() ?: def.height
            persist()
        }, fieldLp())

        val el = selectedEl
        if (el != null) {
            propsHost.addView(TextView(ctx).apply {
                text = "Элемент: ${el.id}"
                textSize = 13f
                setTextColor(COL_ACCENT)
                setPadding(0, dp(ctx, 12), 0, dp(ctx, 8))
            }, lp(MATCH, WRAP))

            if (el.type == HudElementType.TEXT) {
                propsHost.addView(label("Текст"), lp(MATCH, WRAP))
                propsHost.addView(editable(el.text) {
                    el.text = it; persist()
                }, fieldLp())
            }
            propsHost.addView(label("Позиция x y"), lp(MATCH, WRAP))
            propsHost.addView(editable("${el.x.toInt()} ${el.y.toInt()}") { raw ->
                val p = raw.trim().split(Regex("\\s+"))
                el.x = p.getOrNull(0)?.toFloatOrNull() ?: el.x
                el.y = p.getOrNull(1)?.toFloatOrNull() ?: el.y
                persist()
            }, fieldLp())
            propsHost.addView(label("Размер w h"), lp(MATCH, WRAP))
            propsHost.addView(editable("${el.w.toInt()} ${el.h.toInt()}") { raw ->
                val p = raw.trim().split(Regex("\\s+"))
                el.w = p.getOrNull(0)?.toFloatOrNull() ?: el.w
                el.h = p.getOrNull(1)?.toFloatOrNull() ?: el.h
                persist()
            }, fieldLp())
        }

        propsHost.addView(
            PanelUi.accentButton(ctx, "Сохранить") { persist() },
            lp(MATCH, WRAP).apply { topMargin = dp(ctx, 8) }
        )
        propsHost.addView(
            PanelUi.dangerButton(ctx, "Удалить HUD") {
                overlays.confirm("Удалить «${def.title}»?", "Удалить", "Отмена", onYes = {
                    HudRegistry.delete(def.id)
                    selected = HudRegistry.list(project).firstOrNull()
                    selectedEl = selected?.elements?.firstOrNull()
                    refreshList(); renderCanvas(); renderProps()
                    Unit
                })
            },
            lp(MATCH, WRAP).apply { topMargin = dp(ctx, 8) }
        )
    }

    private fun createHudDialog() {
        overlays.input("ID HUD (латиница)", "quest_tracker") { raw ->
            val id = raw.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            if (id.isEmpty()) return@input
            val def = HudRegistry.createStub(project, id, id)
            selected = def
            selectedEl = def.elements.firstOrNull()
            refreshList(); renderCanvas(); renderProps()
        }
    }

    private fun addElement(type: HudElementType) {
        val def = selected ?: return
        var id = type.name.lowercase()
        var n = 1
        while (def.elements.any { it.id == id }) { id = "${type.name.lowercase()}_${++n}" }
        val el = when (type) {
            HudElementType.TEXT -> HudElement(id, type, 12f, 60f, 180f, 20f, text = "Новый текст")
            HudElementType.RECT -> HudElement(id, type, 0f, 0f, def.width, 32f, color = 0x66FFFFFF)
            HudElementType.IMAGE -> HudElement(id, type, 8f, 8f, 32f, 32f)
        }
        def.elements.add(el)
        selectedEl = el
        persist()
    }

    private fun persist() {
        val def = selected ?: return
        def.project = project
        HudRegistry.save(def)
        selected = HudRegistry.get(def.id) ?: def
        selectedEl = selected?.elements?.find { it.id == selectedEl?.id }
            ?: selected?.elements?.firstOrNull()
        refreshList(); renderCanvas(); renderProps()
    }

    private fun label(t: String) = PanelUi.mutedText(ctx, t, 11f)
    private fun value(t: String) = TextView(ctx).apply {
        text = t; textSize = 13f; setTextColor(COL_TEXT)
    }
    private fun fieldLp() = lp(MATCH, WRAP).apply {
        topMargin = dp(ctx, 2); bottomMargin = dp(ctx, 10)
    }
    private fun editable(initial: String, onDone: (String) -> Unit): View =
        TextView(ctx).apply {
            text = initial
            textSize = 13f
            setTextColor(COL_TEXT)
            background = ColorDrawable(COL_BTN)
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
            isClickable = true
            setOnClickListener {
                overlays.input("Значение", initial) { onDone(it) }
            }
        }
}