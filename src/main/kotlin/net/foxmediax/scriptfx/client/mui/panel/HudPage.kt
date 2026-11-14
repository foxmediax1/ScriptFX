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
import icyllis.modernui.view.MotionEvent
import icyllis.modernui.view.ViewGroup

/**
 * Редактор кастомного HUD:
 * слева — список панелей, по центру — превью и слои, справа — свойства.
 */
class HudPage(host: PanelHost) : PanelPage(host) {

    override val breadcrumb: String
        get() = selected?.let { "HUD → $project / ${it.id}" } ?: "HUD → $project"

    private lateinit var ctx: Context
    private lateinit var listBox: LinearLayout
    private lateinit var canvasBox: LinearLayout
    private lateinit var layersBox: LinearLayout
    private lateinit var propsHost: LinearLayout
    private lateinit var projectLabel: TextView
    private lateinit var stageMeta: TextView
    private lateinit var overlays: OverlayHost

    private var project: String = "default"
    private var selected: HudDefinition? = null
    private var selectedEl: HudElement? = null
    private var propsTab: Int = 0 // 0 = панель, 1 = элемент

    /** Масштаб превью: 1 HUD-пиксель ≈ density * stageScale dp */
    private val stageScale = 1f

    private var dragEl: HudElement? = null
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragOrigElX = 0f
    private var dragOrigElY = 0f
    private var dragMoved = false
    private var dragPanel: FrameLayout? = null
    private var dragWrap: View? = null

    private lateinit var coordsHint: TextView

    private val browser: FileBrowser get() = ControlPanelFragment.fileBrowser

    override fun createView(ctx: Context): View {
        this.ctx = ctx
        ensureProject()
        HudRegistry.reload()

        val root = FrameLayout(ctx).apply { background = ColorDrawable(COL_CONTENT) }
        val overlayLayer = FrameLayout(ctx)
        overlays = OverlayHost(ctx, overlayLayer)

        val body = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        body.addView(buildList(ctx), lp(dp(ctx, 230), MATCH))
        body.addView(View(ctx).apply { background = ColorDrawable(COL_DIVIDER) }, lp(px1(ctx), MATCH))
        body.addView(buildCenter(ctx), LinearLayout.LayoutParams(0, MATCH, 1.25f))
        body.addView(View(ctx).apply { background = ColorDrawable(COL_DIVIDER) }, lp(px1(ctx), MATCH))
        body.addView(buildProps(ctx), lp(dp(ctx, 310), MATCH))

        root.addView(body, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(overlayLayer, FrameLayout.LayoutParams(MATCH, MATCH))

        selected = HudRegistry.list(project).firstOrNull()
        selectedEl = selected?.elements?.firstOrNull()
        refreshAll()
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
                    it.mkdirs()
                    File(it, "hud").mkdirs()
                }
                "default"
            }
        }
    }

    private fun refreshAll() {
        refreshList()
        renderCanvas()
        renderLayers()
        renderProps()
    }

    // ------------------------------------------------------------------
    // LEFT — список HUD
    // ------------------------------------------------------------------

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

        projectLabel = TextView(ctx).apply {
            textSize = 11f
            setTextColor(COL_MUTED)
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 8))
        }
        col.addView(projectLabel, lp(MATCH, WRAP))

        col.addView(
            PanelUi.accentButton(ctx, "+ Создать HUD") { createHudDialog() },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 6) }
        )
        col.addView(
            PanelUi.flatButton(ctx, "Сменить проект") { pickProject() },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 6) }
        )
        col.addView(
            PanelUi.flatButton(ctx, "Обновить") {
                HudRegistry.reload()
                selected = HudRegistry.get(selected?.id ?: "") ?: HudRegistry.list(project).firstOrNull()
                selectedEl = selected?.elements?.find { it.id == selectedEl?.id }
                    ?: selected?.elements?.firstOrNull()
                refreshAll()
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
        projectLabel.text = "Проект: $project"
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
                    propsTab = 0
                    refreshAll()
                }
            }
            row.addView(TextView(ctx).apply {
                text = "🖥  ${item.title.ifBlank { item.id }}"
                textSize = 13f
                setTextColor(COL_TEXT)
            }, lp(MATCH, WRAP))
            row.addView(TextView(ctx).apply {
                text = "${item.id} · ${item.elements.size} эл."
                textSize = 11f
                setTextColor(COL_MUTED)
            }, lp(MATCH, WRAP))
            listBox.addView(row, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 6) })
        }
    }

    // ------------------------------------------------------------------
    // CENTER — превью + слои
    // ------------------------------------------------------------------

    private fun buildCenter(ctx: Context): View {
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
        }, lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 6) })
        bar.addView(PanelUi.flatButton(ctx, "Off all") {
            HudRuntime.hideAll()
        }, lp(WRAP, WRAP))
        col.addView(bar, lp(MATCH, WRAP))

        // верх: сцена
        val stageScroll = ScrollView(ctx)
        canvasBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 8))
        }
        stageScroll.addView(canvasBox, lp(MATCH, WRAP))
        col.addView(stageScroll, LinearLayout.LayoutParams(MATCH, 0, 1.1f))

        col.addView(View(ctx).apply { background = ColorDrawable(COL_DIVIDER) }, lp(MATCH, px1(ctx)))

        // низ: слои + add
        val layersHeader = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ColorDrawable(COL_SIDEBAR)
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
        }
        layersHeader.addView(TextView(ctx).apply {
            text = "Слои"
            textSize = 13f
            setTextColor(COL_TEXT)
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        layersHeader.addView(PanelUi.flatButton(ctx, "+ Text") {
            addElement(HudElementType.TEXT)
        }, lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 4) })
        layersHeader.addView(PanelUi.flatButton(ctx, "+ Rect") {
            addElement(HudElementType.RECT)
        }, lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 4) })
        layersHeader.addView(PanelUi.flatButton(ctx, "+ Image") {
            addElement(HudElementType.IMAGE)
        }, lp(WRAP, WRAP))
        col.addView(layersHeader, lp(MATCH, WRAP))

        layersBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 10))
        }
        val layersScroll = ScrollView(ctx)
        layersScroll.addView(layersBox, lp(MATCH, WRAP))
        col.addView(layersScroll, LinearLayout.LayoutParams(MATCH, 0, 0.9f))
        return col
    }

    private fun renderCanvas() {
        canvasBox.removeAllViews()
        val def = selected
        if (def == null) {
            canvasBox.addView(PanelUi.mutedText(ctx, "Выбери или создай HUD слева"), lp(MATCH, WRAP))
            return
        }

        stageMeta = TextView(ctx).apply {
            text = "${def.title}  ·  ${def.width.toInt()}×${def.height.toInt()}  ·  ${def.anchor.name}"
            textSize = 12f
            setTextColor(COL_MUTED)
        }
        canvasBox.addView(stageMeta, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 6) })

        coordsHint = TextView(ctx).apply {
            text = "Перетащи элемент мышью · координаты обновятся"
            textSize = 11f
            setTextColor(COL_MUTED)
        }
        canvasBox.addView(coordsHint, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) })

        val density = ctx.resources.displayMetrics.density
        val scale = density * stageScale

        val stageFrame = FrameLayout(ctx).apply {
            background = ColorDrawable(0xFF1A1028.toInt())
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
            minimumHeight = dp(ctx, (def.height + 32).toInt().coerceIn(120, 400))
        }

        val panelW = (def.width * scale).toInt().coerceAtLeast(dp(ctx, 80))
        val panelH = (def.height * scale).toInt().coerceAtLeast(dp(ctx, 48))

        val panel = FrameLayout(ctx).apply {
            background = ColorDrawable(def.background)
            // клип по размеру HUD
            minimumWidth = panelW
            minimumHeight = panelH
        }
        dragPanel = panel

        for (el in def.elements) {
            if (!el.visible) continue
            val active = selectedEl?.id == el.id
            val borderColor = if (active) 0xFFA855F7.toInt() else 0x33FFFFFF

            val cell = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = ColorDrawable(
                    when (el.type) {
                        HudElementType.RECT -> el.color
                        HudElementType.IMAGE -> 0x55404060
                        HudElementType.TEXT -> if (active) 0x446D28D9 else 0x33000000
                    }
                )
                setPadding(dp(ctx, 4), dp(ctx, 3), dp(ctx, 4), dp(ctx, 3))
            }

            cell.addView(TextView(ctx).apply {
                text = when (el.type) {
                    HudElementType.TEXT -> el.text.ifBlank { el.id }.take(36)
                    HudElementType.RECT -> "■ ${el.w.toInt()}×${el.h.toInt()}"
                    HudElementType.IMAGE -> "🖼 ${el.imagePath.ifBlank { el.id }}"
                }
                textSize = 11f
                setTextColor(COL_TEXT)
            }, lp(WRAP, WRAP))

            val wrap = FrameLayout(ctx).apply {
                background = ColorDrawable(borderColor)
                setPadding(dp(ctx, 1), dp(ctx, 1), dp(ctx, 1), dp(ctx, 1))
                isClickable = true
            }
            wrap.addView(
                cell,
                FrameLayout.LayoutParams(MATCH, MATCH)
            )

            val elW = (el.w * scale).toInt().coerceAtLeast(dp(ctx, 24))
            val elH = (el.h * scale).toInt().coerceAtLeast(dp(ctx, 18))
            val lpEl = FrameLayout.LayoutParams(elW, elH).apply {
                leftMargin = (el.x * scale).toInt().coerceAtLeast(0)
                topMargin = (el.y * scale).toInt().coerceAtLeast(0)
            }
            panel.addView(wrap, lpEl)

            attachDrag(wrap, el, scale, panelW, panelH)
        }

        stageFrame.addView(
            panel,
            FrameLayout.LayoutParams(panelW, panelH, Gravity.CENTER_HORIZONTAL)
        )
        canvasBox.addView(stageFrame, lp(MATCH, WRAP))
        canvasBox.addView(
            PanelUi.dimText(ctx, "Клик = выбор · drag = позиция · отпусти = сохранить"),
            lp(MATCH, WRAP).apply { topMargin = dp(ctx, 8) }
        )
    }

    private fun attachDrag(
        wrap: View,
        el: HudElement,
        scale: Float,
        panelW: Int,
        panelH: Int
    ) {
        wrap.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    selectedEl = el
                    propsTab = 1
                    dragEl = el
                    dragWrap = v
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    dragOrigElX = el.x
                    dragOrigElY = el.y
                    dragMoved = false
                    // подсветка без полного rebuild
                    updateCoordsHint(el)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dragging = dragEl
                    val node = dragWrap
                    if (dragging == null || node !== v || dragging.id != el.id) return@setOnTouchListener true

                    val dxPx = event.rawX - dragStartX
                    val dyPx = event.rawY - dragStartY
                    if (!dragMoved && (kotlin.math.abs(dxPx) + kotlin.math.abs(dyPx) < 4f)) {
                        return@setOnTouchListener true
                    }
                    dragMoved = true

                    val newX = (dragOrigElX + dxPx / scale).coerceIn(0f, (selected?.width ?: 9999f) - el.w.coerceAtLeast(1f))
                    val newY = (dragOrigElY + dyPx / scale).coerceIn(0f, (selected?.height ?: 9999f) - el.h.coerceAtLeast(1f))
                    el.x = newX
                    el.y = newY

                    val lp = node.layoutParams as? FrameLayout.LayoutParams ?: return@setOnTouchListener true
                    lp.leftMargin = (el.x * scale).toInt().coerceAtLeast(0)
                    lp.topMargin = (el.y * scale).toInt().coerceAtLeast(0)
                    // не вылезать за панель
                    lp.leftMargin = lp.leftMargin.coerceAtMost((panelW - node.width).coerceAtLeast(0))
                    lp.topMargin = lp.topMargin.coerceAtMost((panelH - node.height).coerceAtLeast(0))
                    node.layoutParams = lp
                    // пересчитать layout у родителя
                    (node.parent as? View)?.requestLayout()

                    updateCoordsHint(el)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val wasDrag = dragMoved && dragEl?.id == el.id
                    dragEl = null
                    dragWrap = null
                    if (wasDrag) {
                        // финальный clamp в HUD-координатах
                        val maxX = (selected?.width ?: el.x + el.w) - el.w
                        val maxY = (selected?.height ?: el.y + el.h) - el.h
                        el.x = el.x.coerceIn(0f, maxX.coerceAtLeast(0f))
                        el.y = el.y.coerceIn(0f, maxY.coerceAtLeast(0f))
                        persist()
                    } else {
                        // простой клик — только выбор
                        selectedEl = el
                        propsTab = 1
                        renderLayers()
                        renderProps()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun updateCoordsHint(el: HudElement) {
        if (!this::coordsHint.isInitialized) return
        coordsHint.text = "${el.id}: x=${el.x.toInt()}  y=${el.y.toInt()}  (${el.w.toInt()}×${el.h.toInt()})"
        coordsHint.setTextColor(COL_ACCENT)
    }

    private fun renderLayers() {
        layersBox.removeAllViews()
        val def = selected
        if (def == null) {
            layersBox.addView(PanelUi.mutedText(ctx, "Нет слоёв"), lp(MATCH, WRAP))
            return
        }
        if (def.elements.isEmpty()) {
            layersBox.addView(PanelUi.mutedText(ctx, "Добавь Text / Rect / Image"), lp(MATCH, WRAP))
            return
        }
        def.elements.forEachIndexed { index, el ->
            val active = selectedEl?.id == el.id
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ColorDrawable(if (active) COL_BTN_SELECTED else COL_BTN)
                setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8))
                isClickable = true
                setOnClickListener {
                    selectedEl = el
                    propsTab = 1
                    refreshAll()
                }
            }
            val icon = when (el.type) {
                HudElementType.TEXT -> "T"
                HudElementType.RECT -> "■"
                HudElementType.IMAGE -> "🖼"
            }
            row.addView(TextView(ctx).apply {
                text = "$icon  ${el.id}"
                textSize = 12f
                setTextColor(COL_TEXT)
            }, LinearLayout.LayoutParams(0, WRAP, 1f))

            // visibility
            row.addView(PanelUi.iconButton(ctx, if (el.visible) "👁" else "✕") {
                el.visible = !el.visible
                persist()
            }, lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 4) })
            // up
            row.addView(PanelUi.iconButton(ctx, "↑") {
                if (index > 0) {
                    def.elements.removeAt(index)
                    def.elements.add(index - 1, el)
                    persist()
                }
            }, lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 2) })
            // down
            row.addView(PanelUi.iconButton(ctx, "↓") {
                if (index < def.elements.lastIndex) {
                    def.elements.removeAt(index)
                    def.elements.add(index + 1, el)
                    persist()
                }
            }, lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 2) })
            // delete
            row.addView(PanelUi.iconButton(ctx, "🗑") {
                overlays.confirm("Удалить слой «${el.id}»?", "Удалить", "Отмена", onYes = {
                    def.elements.remove(el)
                    if (selectedEl?.id == el.id) selectedEl = def.elements.firstOrNull()
                    persist()
                })
            }, lp(WRAP, WRAP))

            layersBox.addView(row, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 4) })
        }
    }

    // ------------------------------------------------------------------
    // RIGHT — свойства
    // ------------------------------------------------------------------

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

        // вкладки
        val tabs = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tabs.addView(tabBtn("Панель", 0), lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 6) })
        tabs.addView(tabBtn("Элемент", 1), lp(WRAP, WRAP))
        propsHost.addView(tabs, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 12) })

        if (propsTab == 0) renderPanelProps(def) else renderElementProps(def)
    }

    private fun tabBtn(title: String, index: Int): TextView =
        TextView(ctx).apply {
            text = title
            textSize = 12f
            setTextColor(COL_TEXT)
            background = ColorDrawable(if (propsTab == index) COL_BTN_SELECTED else COL_BTN)
            setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8))
            isClickable = true
            setOnClickListener {
                propsTab = index
                renderProps()
            }
        }

    private fun renderPanelProps(def: HudDefinition) {
        propsHost.addView(label("ID"), lp(MATCH, WRAP))
        propsHost.addView(value(def.id), fieldLp())

        propsHost.addView(label("Название"), lp(MATCH, WRAP))
        propsHost.addView(editable(def.title) {
            def.title = it
            persist()
        }, fieldLp())

        propsHost.addView(label("Якорь (клик — следующий)"), lp(MATCH, WRAP))
        propsHost.addView(cycleBtn(def.anchor.name) {
            val order = HudAnchor.entries
            def.anchor = order[(order.indexOf(def.anchor) + 1) % order.size]
            persist()
        }, fieldLp())

        propsHost.addView(label("Offset X Y"), lp(MATCH, WRAP))
        propsHost.addView(editable("${def.offsetX.toInt()} ${def.offsetY.toInt()}") { raw ->
            val p = raw.trim().split(Regex("\\s+"))
            def.offsetX = p.getOrNull(0)?.toFloatOrNull() ?: def.offsetX
            def.offsetY = p.getOrNull(1)?.toFloatOrNull() ?: def.offsetY
            persist()
        }, fieldLp())

        propsHost.addView(label("Размер W H"), lp(MATCH, WRAP))
        propsHost.addView(editable("${def.width.toInt()} ${def.height.toInt()}") { raw ->
            val p = raw.trim().split(Regex("\\s+"))
            def.width = p.getOrNull(0)?.toFloatOrNull()?.coerceAtLeast(16f) ?: def.width
            def.height = p.getOrNull(1)?.toFloatOrNull()?.coerceAtLeast(16f) ?: def.height
            persist()
        }, fieldLp())

        propsHost.addView(label("Фон ARGB (hex, напр. CC12081F)"), lp(MATCH, WRAP))
        propsHost.addView(editable(argbToHex(def.background)) { raw ->
            parseArgb(raw)?.let {
                def.background = it
                persist()
            }
        }, fieldLp())

        propsHost.addView(label("Show anim ms"), lp(MATCH, WRAP))
        propsHost.addView(editable(def.showAnim.durationMs.toString()) {
            def.showAnim.durationMs = it.toIntOrNull()?.coerceAtLeast(0) ?: def.showAnim.durationMs
            persist()
        }, fieldLp())

        propsHost.addView(label("Hide anim ms"), lp(MATCH, WRAP))
        propsHost.addView(editable(def.hideAnim.durationMs.toString()) {
            def.hideAnim.durationMs = it.toIntOrNull()?.coerceAtLeast(0) ?: def.hideAnim.durationMs
            persist()
        }, fieldLp())

        propsHost.addView(
            PanelUi.accentButton(ctx, "Сохранить") { persist() },
            lp(MATCH, WRAP).apply { topMargin = dp(ctx, 4) }
        )
        propsHost.addView(
            PanelUi.flatButton(ctx, "Дублировать HUD") { duplicateHud(def) },
            lp(MATCH, WRAP).apply { topMargin = dp(ctx, 8) }
        )
        propsHost.addView(
            PanelUi.dangerButton(ctx, "Удалить HUD") {
                overlays.confirm("Удалить «${def.title}»?", "Удалить", "Отмена", onYes = {
                    HudRegistry.delete(def.id)
                    selected = HudRegistry.list(project).firstOrNull()
                    selectedEl = selected?.elements?.firstOrNull()
                    refreshAll()
                })
            },
            lp(MATCH, WRAP).apply { topMargin = dp(ctx, 8) }
        )
    }

    private fun renderElementProps(def: HudDefinition) {
        val el = selectedEl
        if (el == null) {
            propsHost.addView(PanelUi.mutedText(ctx, "Выбери слой в списке или на сцене"), lp(MATCH, WRAP))
            return
        }

        propsHost.addView(TextView(ctx).apply {
            text = "Элемент · ${el.type.name}"
            textSize = 13f
            setTextColor(COL_ACCENT)
            setPadding(0, 0, 0, dp(ctx, 8))
        }, lp(MATCH, WRAP))

        propsHost.addView(label("ID"), lp(MATCH, WRAP))
        propsHost.addView(editable(el.id) { raw ->
            val clean = raw.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            if (clean.isNotEmpty() && def.elements.none { it !== el && it.id == clean }) {
                el.id = clean
                persist()
            }
        }, fieldLp())

        when (el.type) {
            HudElementType.TEXT -> {
                propsHost.addView(label("Текст"), lp(MATCH, WRAP))
                propsHost.addView(editable(el.text) {
                    el.text = it
                    persist()
                }, fieldLp())
                propsHost.addView(label("Размер шрифта"), lp(MATCH, WRAP))
                propsHost.addView(editable(el.fontSize.toInt().toString()) {
                    el.fontSize = it.toFloatOrNull()?.coerceIn(8f, 48f) ?: el.fontSize
                    persist()
                }, fieldLp())
            }
            HudElementType.IMAGE -> {
                propsHost.addView(label("Путь PNG (hud/ или от проекта)"), lp(MATCH, WRAP))
                propsHost.addView(editable(el.imagePath.ifBlank { "icon.png" }) {
                    el.imagePath = it.trim()
                    persist()
                }, fieldLp())
                propsHost.addView(PanelUi.dimText(ctx, "Файл: projects/$project/hud/… или projects/$project/…"), lp(MATCH, WRAP).apply {
                    bottomMargin = dp(ctx, 8)
                })
            }
            HudElementType.RECT -> { /* color ниже */ }
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
            el.w = p.getOrNull(0)?.toFloatOrNull()?.coerceAtLeast(1f) ?: el.w
            el.h = p.getOrNull(1)?.toFloatOrNull()?.coerceAtLeast(1f) ?: el.h
            persist()
        }, fieldLp())

        propsHost.addView(label("Цвет ARGB hex"), lp(MATCH, WRAP))
        propsHost.addView(editable(argbToHex(el.color)) { raw ->
            parseArgb(raw)?.let {
                el.color = it
                persist()
            }
        }, fieldLp())

        propsHost.addView(label("Видимость"), lp(MATCH, WRAP))
        propsHost.addView(cycleBtn(if (el.visible) "visible" else "hidden") {
            el.visible = !el.visible
            persist()
        }, fieldLp())

        propsHost.addView(
            PanelUi.accentButton(ctx, "Сохранить") { persist() },
            lp(MATCH, WRAP).apply { topMargin = dp(ctx, 4) }
        )
        propsHost.addView(
            PanelUi.flatButton(ctx, "Дублировать слой") {
                val copy = el.copy(id = uniqueId(def, el.id + "_copy"))
                def.elements.add(copy)
                selectedEl = copy
                persist()
            },
            lp(MATCH, WRAP).apply { topMargin = dp(ctx, 8) }
        )
        propsHost.addView(
            PanelUi.dangerButton(ctx, "Удалить слой") {
                def.elements.remove(el)
                selectedEl = def.elements.firstOrNull()
                persist()
            },
            lp(MATCH, WRAP).apply { topMargin = dp(ctx, 8) }
        )
    }

    // ------------------------------------------------------------------
    // actions
    // ------------------------------------------------------------------

    private fun createHudDialog() {
        overlays.input("ID HUD (латиница)", "quest_tracker") { raw ->
            val id = raw.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            if (id.isEmpty()) return@input
            if (HudRegistry.get(id) != null) {
                overlays.confirm(
                    "HUD с id «$id» уже есть",
                    "OK",
                    "Закрыть",
                    onYes = {},
                    onNo = {}
                )
                return@input
            }
            val def = HudRegistry.createStub(project, id, id)
            selected = def
            selectedEl = def.elements.firstOrNull()
            propsTab = 0
            refreshAll()
        }
    }

    private fun pickProject() {
        val projects = HudRegistry.listProjects().ifEmpty { listOf("default") }
        // простой цикл по проектам
        val i = (projects.indexOf(project) + 1) % projects.size
        project = projects[i]
        File(File(HudRegistry.projectsRoot(), project), "hud").mkdirs()
        HudRegistry.reload()
        selected = HudRegistry.list(project).firstOrNull()
        selectedEl = selected?.elements?.firstOrNull()
        refreshAll()
    }

    private fun duplicateHud(def: HudDefinition) {
        overlays.input("ID копии", def.id + "_copy") { raw ->
            val id = raw.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            if (id.isEmpty() || HudRegistry.get(id) != null) return@input
            val copy = def.copy(
                id = id,
                title = def.title + " copy",
                elements = def.elements.map { it.copy() }.toMutableList()
            )
            copy.project = project
            HudRegistry.save(copy)
            selected = copy
            selectedEl = copy.elements.firstOrNull()
            refreshAll()
        }
    }

    private fun addElement(type: HudElementType) {
        val def = selected ?: return
        val id = uniqueId(def, type.name.lowercase())
        val el = when (type) {
            HudElementType.TEXT -> HudElement(
                id, type, 12f, 48f, 180f, 22f,
                text = "Новый текст", fontSize = 12f, color = 0xFFE0D4FF.toInt()
            )
            HudElementType.RECT -> HudElement(
                id, type, 0f, 0f, def.width, 28f,
                color = 0x66FFFFFF
            )
            HudElementType.IMAGE -> HudElement(
                id, type, 8f, 8f, 32f, 32f,
                imagePath = "icon.png"
            )
        }
        def.elements.add(el)
        selectedEl = el
        propsTab = 1
        persist()
    }

    private fun uniqueId(def: HudDefinition, base: String): String {
        var id = base
        var n = 1
        while (def.elements.any { it.id == id }) id = "${base}_${++n}"
        return id
    }

    private fun persist() {
        val def = selected ?: return
        def.project = project
        HudRegistry.save(def)
        selected = HudRegistry.get(def.id) ?: def
        selectedEl = selected?.elements?.find { it.id == selectedEl?.id }
            ?: selected?.elements?.firstOrNull()
        refreshAll()
    }

    // ------------------------------------------------------------------
    // UI helpers
    // ------------------------------------------------------------------

    private fun label(t: String) = PanelUi.mutedText(ctx, t, 11f)

    private fun value(t: String) = TextView(ctx).apply {
        text = t
        textSize = 13f
        setTextColor(COL_TEXT)
    }

    private fun fieldLp() = lp(MATCH, WRAP).apply {
        topMargin = dp(ctx, 2)
        bottomMargin = dp(ctx, 10)
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

    private fun cycleBtn(label: String, onClick: () -> Unit): View =
        TextView(ctx).apply {
            text = label
            textSize = 13f
            setTextColor(COL_TEXT)
            background = ColorDrawable(COL_BTN)
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun argbToHex(c: Int): String =
        String.format("%08X", c)

    private fun parseArgb(raw: String): Int? {
        val s = raw.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
        if (s.length !in 6..8) return null
        return runCatching {
            val v = s.toLong(16)
            if (s.length == 6) (0xFF000000 or v).toInt() else v.toInt()
        }.getOrNull()
    }
}