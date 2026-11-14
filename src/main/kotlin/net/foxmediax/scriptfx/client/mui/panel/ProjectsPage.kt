package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.core.Core
import icyllis.modernui.graphics.Bitmap
import icyllis.modernui.graphics.BitmapFactory
import icyllis.modernui.graphics.Image
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.text.Editable
import icyllis.modernui.text.TextWatcher
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.MotionEvent
import icyllis.modernui.view.View
import icyllis.modernui.widget.EditText
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.ImageView
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_ACCENT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_BTN
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_BTN_SELECTED
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_CARD
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
import net.foxmediax.scriptfx.gui.FileEntry
import net.foxmediax.scriptfx.nodes.NodeCanvasView
import net.foxmediax.scriptfx.nodes.NodeGraph
import net.foxmediax.scriptfx.nodes.NodeKind
import net.foxmediax.scriptfx.nodes.NodePalette
import net.foxmediax.scriptfx.nodes.ScriptNodeSync
import net.foxmediax.scriptfx.scriptengine.ScriptContext
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.foxmediax.scriptfx.scriptengine.ScriptManager
import net.minecraft.client.Minecraft
import java.io.File
import net.foxmediax.scriptfx.nodes.GraphNode

/**
 * Экран «Сценарии»:
 * проводник | редактор (ноды / текст) + консоль | свойства + палитра нод
 */
class ProjectsPage(host: PanelHost, private val browser: FileBrowser) : PanelPage(host) {

    private lateinit var ctx: Context
    private lateinit var overlays: OverlayHost

    private lateinit var treeBox: LinearLayout
    private lateinit var pathView: TextView

    private lateinit var tabTitle: TextView
    private lateinit var edit: EditText
    private lateinit var editorScroll: ScrollView
    private lateinit var imageView: ImageView
    private lateinit var contentHost: FrameLayout
    private lateinit var consoleBox: LinearLayout

    private lateinit var nodeCanvas: NodeCanvasView
    private lateinit var textEditorWrap: View
    private lateinit var nodeEditorWrap: FrameLayout
    private lateinit var modeBtn: TextView

    private lateinit var paletteSearch: EditText
    private lateinit var paletteBox: LinearLayout
    private lateinit var paletteSection: LinearLayout

    private var previewImage: Image? = null
    private var viewingImage = false
    private var videoOverlayOpen = false

    private lateinit var propName: TextView
    private lateinit var propPath: TextView
    private lateinit var propStatus: TextView

    private var file: File? = null
    private var original = ""
    private var last = ""
    private var suppress = false
    private val undoStack = ArrayDeque<String>()

    /** true = нодовый редактор, false = текст */
    private var nodeMode = true
    private var graph: NodeGraph = ScriptNodeSync.defaultTemplateGraph()
    private var syncing = false
    private var nodeIdSeq = 1

    private lateinit var nodeTitleView: TextView
    private lateinit var nodeParamEdit: EditText
    private var selectedNodeId: String? = null

    private lateinit var nodeParamsBox: LinearLayout

    @Volatile
    private var dirty = false

    override val breadcrumb: String?
        get() = file?.let { "Сценарии -> ${it.name}" } ?: "Сценарии"

    override val interceptsEscape: Boolean
        get() = overlays.isOpen || dirty

    override fun onEscape() {
        if (overlays.isOpen) overlays.dismissTop() else host.closePanel()
    }

    override fun createView(ctx: Context): View {
        this.ctx = ctx
        val root = FrameLayout(ctx).apply {
            background = ColorDrawable(COL_CONTENT)
        }
        val overlayLayer = FrameLayout(ctx)
        overlays = OverlayHost(ctx, overlayLayer)

        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        body.addView(buildExplorer(ctx), lp(dp(ctx, 250), MATCH))
        body.addView(dividerV(ctx), lp(px1(ctx), MATCH))
        body.addView(buildEditorColumn(ctx), LinearLayout.LayoutParams(0, MATCH, 1f))
        body.addView(dividerV(ctx), lp(px1(ctx), MATCH))
        body.addView(buildProperties(ctx), lp(dp(ctx, 280), MATCH))

        root.addView(body, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(overlayLayer, FrameLayout.LayoutParams(MATCH, MATCH))

        nodeCanvas.onSelectionChanged = { node ->
            selectedNodeId = node?.id
            showNodeProps(node)
        }

        refreshTree()
        refreshPalette("")
        showEmptyEditor()
        applyEditorVisibility()
        return root
    }

    override fun onDestroy() {
        clearImagePreview()
        if (this::overlays.isInitialized) overlays.clear()
    }

    private fun openVideoPreview(videoId: String) {
        if (videoOverlayOpen) return
        videoOverlayOpen = true
        try {
            overlays.showVideo(videoId) { videoOverlayOpen = false }
        } catch (t: Throwable) {
            videoOverlayOpen = false
            ScriptFXLog.warn("showVideo failed: ${t.message}")
        }
    }

    override fun goUp(): Boolean {
        if (!browser.canGoUp()) return false
        browser.goUp()
        refreshTree()
        return true
    }

    override fun requestLeave(proceed: () -> Unit) {
        val f = file
        if (f != null && isDirty()) {
            overlays.confirm(
                "Есть несохранённые изменения в ${f.name}. Сохранить?",
                "Сохранить",
                "Не сохранять",
                onYes = {
                    save()
                    proceed()
                },
                onNo = { proceed() }
            )
        } else {
            proceed()
        }
    }

    // ------------------------------------------------------------------
    // LEFT
    // ------------------------------------------------------------------

    private fun buildExplorer(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_SIDEBAR)
            setPadding(dp(ctx, 10), dp(ctx, 10), dp(ctx, 10), dp(ctx, 10))
        }

        col.addView(
            TextView(ctx).apply {
                text = "Проводник проекта"
                textSize = 13f
                setTextColor(COL_ACCENT)
            },
            lp(MATCH, WRAP)
        )

        pathView = TextView(ctx).apply {
            textSize = 11f
            setTextColor(COL_MUTED)
            setPadding(0, dp(ctx, 6), 0, dp(ctx, 8))
        }
        col.addView(pathView, lp(MATCH, WRAP))

        val actions = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(PanelUi.flatButton(ctx, "Создать") { openBackgroundMenu() }, lp(WRAP, WRAP))
        actions.addView(
            PanelUi.flatButton(ctx, "Обновить") { refreshTree() },
            lp(WRAP, WRAP).apply { leftMargin = dp(ctx, 6) }
        )
        col.addView(actions, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) })

        treeBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx)
        scroll.addView(treeBox, lp(MATCH, WRAP))
        col.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return col
    }

    private fun refreshTree() {
        pathView.text = "/scriptfx/${browser.breadcrumbPath()}"
        treeBox.removeAllViews()

        if (browser.canGoUp()) {
            treeBox.addView(treeRow("../", COL_MUTED, { goUp() }, null), treeLp())
        }

        for (entry in browser.entries) {
            val label = when {
                entry.isDirectory -> "📁 ${entry.name}"
                isVideoFile(entry.file) -> "🎬 ${entry.name}"
                isImageFile(entry.file) -> "🖼 ${entry.name}"
                else -> "📄 ${entry.name}"
            }
            val selected = file != null && entry.file.absolutePath == file!!.absolutePath
            treeBox.addView(
                treeRow(
                    label,
                    if (selected) COL_ACCENT else COL_TEXT,
                    { openEntry(entry) },
                    { openRowMenu(entry) },
                    selected
                ),
                treeLp()
            )
        }

        if (browser.entries.isEmpty()) {
            treeBox.addView(
                PanelUi.mutedText(ctx, "Пусто — нажми «Создать»"),
                lp(MATCH, WRAP).apply { topMargin = dp(ctx, 12) }
            )
        }
    }

    private fun treeLp() = lp(MATCH, WRAP).apply { topMargin = dp(ctx, 3) }

    private fun treeRow(
        label: String,
        color: Int,
        onClick: () -> Unit,
        onMenu: (() -> Unit)?,
        selected: Boolean = false
    ): View {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ColorDrawable(if (selected) COL_BTN_SELECTED else COL_BTN)
            setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8))
            isClickable = true
            setOnClickListener { onClick() }
            if (onMenu != null) {
                setOnTouchListener { _, e ->
                    if (e.action == MotionEvent.ACTION_DOWN &&
                        (e.buttonState and MotionEvent.BUTTON_SECONDARY) != 0
                    ) {
                        onMenu()
                        true
                    } else false
                }
            }
            addView(
                TextView(ctx).apply {
                    text = label
                    textSize = 12f
                    setTextColor(color)
                    setSingleLine(true)
                },
                LinearLayout.LayoutParams(0, WRAP, 1f)
            )
        }
    }

    // ------------------------------------------------------------------
    // CENTER
    // ------------------------------------------------------------------

    private fun buildEditorColumn(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_CONTENT)
        }

        val tabs = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ColorDrawable(COL_SIDEBAR)
            setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
        }

        tabTitle = TextView(ctx).apply {
            text = "— нет файла —"
            textSize = 13f
            setTextColor(COL_TEXT)
        }
        tabs.addView(tabTitle, LinearLayout.LayoutParams(0, WRAP, 1f))
        tabs.addView(PanelUi.flatButton(ctx, "▶") { runScript() }, lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 4) })
        tabs.addView(PanelUi.flatButton(ctx, "💾") { save() }, lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 4) })
        tabs.addView(PanelUi.flatButton(ctx, "✕") { closeFile() }, lp(WRAP, WRAP))
        col.addView(tabs, lp(MATCH, WRAP))
        col.addView(dividerH(ctx), lp(MATCH, px1(ctx)))

        contentHost = FrameLayout(ctx).apply {
            background = ColorDrawable(COL_CONTENT)
        }

        // --- TEXT ---
        edit = EditText(ctx).apply {
            textSize = 13f
            setTextColor(COL_TEXT)
            setHintTextColor(COL_MUTED)
            hint = "Открой файл слева или создай новый…"
            background = ColorDrawable(COL_CONTENT)
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
            isEnabled = false
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (suppress || syncing) return
                    val text = s?.toString() ?: return
                    if (text != last) {
                        undoStack.addLast(last)
                        if (undoStack.size > 80) undoStack.removeFirst()
                        last = text
                        dirty = text != original
                        updateProps()
                    }
                }
            })
        }
        editorScroll = ScrollView(ctx)
        editorScroll.addView(edit, lp(MATCH, WRAP))
        textEditorWrap = editorScroll

        // --- NODES ---
        nodeEditorWrap = FrameLayout(ctx)
        nodeCanvas = NodeCanvasView(ctx).apply {
            onGraphChanged = {
                dirty = true
                updateProps()
            }
        }
        nodeEditorWrap.addView(nodeCanvas, FrameLayout.LayoutParams(MATCH, MATCH))

        // кнопка режима >_ в правом нижнем углу центра
        modeBtn = PanelUi.flatButton(ctx, ">_") { toggleEditorMode() }
        nodeEditorWrap.addView(
            modeBtn,
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(ctx, 12), dp(ctx, 12))
            }
        )

        nodeCanvas.onSelectionChanged = { node ->
            selectedNodeId = node?.id
            showNodeProps(node)
        }



        // тот же modeBtn поверх текстового режима — через общий слой
        val editorStack = FrameLayout(ctx)
        editorStack.addView(textEditorWrap, FrameLayout.LayoutParams(MATCH, MATCH))
        editorStack.addView(nodeEditorWrap, FrameLayout.LayoutParams(MATCH, MATCH))

        // плавающая кнопка всегда видна
        val floatingMode = PanelUi.flatButton(ctx, ">_") { toggleEditorMode() }
        editorStack.addView(
            floatingMode,
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(ctx, 12), dp(ctx, 12))
            }
        )

        contentHost.addView(editorStack, FrameLayout.LayoutParams(MATCH, MATCH))

        imageView = ImageView(ctx).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
            background = ColorDrawable(0xFF0A0A12.toInt())
        }
        contentHost.addView(imageView, FrameLayout.LayoutParams(MATCH, MATCH))

        col.addView(contentHost, LinearLayout.LayoutParams(MATCH, 0, 1f))
        col.addView(dividerH(ctx), lp(MATCH, px1(ctx)))

        val consoleHeader = TextView(ctx).apply {
            text = "Консоль"
            textSize = 12f
            setTextColor(COL_ACCENT)
            setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 4))
            background = ColorDrawable(COL_SIDEBAR)
        }
        col.addView(consoleHeader, lp(MATCH, WRAP))

        consoleBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 10), dp(ctx, 4), dp(ctx, 10), dp(ctx, 8))
            background = ColorDrawable(COL_CARD)
        }
        val consoleScroll = ScrollView(ctx)
        consoleScroll.addView(consoleBox, lp(MATCH, WRAP))
        col.addView(consoleScroll, lp(MATCH, dp(ctx, 110)))

        applyEditorVisibility()
        logConsole("[INFO] Готов к работе")
        return col
    }

    private fun showNodeProps(node: GraphNode?) {
        if (!this::nodeTitleView.isInitialized || !this::nodeParamsBox.isInitialized) return
        nodeParamsBox.removeAllViews()
        if (node == null) {
            nodeTitleView.text = "—"
            nodeParamsBox.visibility = View.GONE
            return
        }
        nodeTitleView.text = "${node.title} (${node.kind})"
        nodeParamsBox.visibility = View.VISIBLE

        val editable = node.kind != NodeKind.START && node.kind != NodeKind.END
        val keys = when (node.kind) {
            NodeKind.COMMAND -> listOf("cmd", "args").filter { node.params.containsKey(it) }
                .ifEmpty { node.params.keys.toList() }
            NodeKind.WAIT, NodeKind.DELAY -> listOf("duration")
            NodeKind.SCRIPT -> listOf("script")
            NodeKind.QUEST_START, NodeKind.QUEST_COMPLETE, NodeKind.QUEST_FAIL -> listOf("quest")
            NodeKind.BRANCH -> listOf("var", "op", "value")
            NodeKind.CUTSCENE_SET -> listOf("x", "y", "z", "yaw", "pitch")
            NodeKind.CUTSCENE_MOVE -> listOf("x", "y", "z", "yaw", "pitch", "duration")
            NodeKind.CUTSCENE_START -> listOf("mode")
            else -> node.params.keys.toList()
        }.ifEmpty { node.params.keys.toList() }

        for (key in keys) {
            val label = TextView(ctx).apply {
                text = key
                textSize = 11f
                setTextColor(COL_MUTED)
                setPadding(0, dp(ctx, 6), 0, dp(ctx, 2))
            }
            nodeParamsBox.addView(label, lp(MATCH, WRAP))

            val field = EditText(ctx).apply {
                textSize = 12f
                setTextColor(COL_TEXT)
                setHintTextColor(COL_MUTED)
                hint = key
                background = ColorDrawable(COL_CARD)
                setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
                isEnabled = editable
                setText(node.params[key].orEmpty())
                tag = key
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        if (suppressNodeParam) return
                        val k = tag as? String ?: return
                        val id = selectedNodeId ?: return
                        val n = graph.node(id) ?: return
                        val v = s?.toString().orEmpty()
                        if (n.params[k] == v) return
                        n.params[k] = v
                        dirty = true
                        if (this@ProjectsPage::nodeCanvas.isInitialized) nodeCanvas.invalidate()
                        updateProps()
                    }
                })
            }
            nodeParamsBox.addView(field, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 4) })
        }
    }

    private fun toggleEditorMode() {
        if (viewingImage || file == null || !isScriptFile(file!!)) return
        if (nodeMode) {
            // ноды → текст с connect-мета
            syncing = true
            val text = ScriptNodeSync.graphToText(graph, includeConnectMeta = true)
            edit.setText(text)
            last = text
            syncing = false
            nodeMode = false
        } else {
            // текст → ноды
            val name = file?.nameWithoutExtension ?: "script"
            graph = ScriptNodeSync.textToGraph(edit.text?.toString().orEmpty(), name)
            nodeCanvas.graph = graph
            nodeMode = true
        }
        applyEditorVisibility()
        logConsole(if (nodeMode) "[INFO] Режим: ноды" else "[INFO] Режим: текст")
    }

    private fun applyEditorVisibility() {
        if (!this::nodeEditorWrap.isInitialized || !this::textEditorWrap.isInitialized) return

        if (viewingImage) {
            nodeEditorWrap.visibility = View.GONE
            textEditorWrap.visibility = View.GONE
            if (this::paletteSection.isInitialized) paletteSection.visibility = View.GONE
            return
        }

        val scriptOpen = file != null && isScriptFile(file!!)
        if (scriptOpen && nodeMode) {
            nodeEditorWrap.visibility = View.VISIBLE
            textEditorWrap.visibility = View.GONE
            if (this::paletteSection.isInitialized) paletteSection.visibility = View.VISIBLE
        } else {
            nodeEditorWrap.visibility = View.GONE
            textEditorWrap.visibility = View.VISIBLE
            if (this::paletteSection.isInitialized) {
                paletteSection.visibility = if (scriptOpen) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showEmptyEditor() {
        clearImagePreview()
        suppress = true
        edit.setText("")
        edit.isEnabled = false
        suppress = false
        file = null
        original = ""
        last = ""
        dirty = false
        nodeMode = true
        graph = ScriptNodeSync.defaultTemplateGraph()
        if (this::nodeCanvas.isInitialized) nodeCanvas.graph = graph
        tabTitle.text = "— нет файла —"
        applyEditorVisibility()
        updateProps()
    }

    private fun openEditor(f: File) {
        clearImagePreview()
        val text = try {
            f.readText()
        } catch (_: Exception) {
            logConsole("[ERROR] Не удалось прочитать ${f.name}")
            return
        }
        file = f
        original = text
        last = text
        dirty = false
        undoStack.clear()

        val script = isScriptFile(f)
        if (script) {
            val name = f.nameWithoutExtension
            graph = if (text.isBlank()) {
                ScriptNodeSync.defaultTemplateGraph(name)
            } else {
                ScriptNodeSync.textToGraph(text, name)
            }
            nodeCanvas.graph = graph
            // пустой новый файл — сразу ноды; иначе текст как в файле, режим нод по умолчанию
            nodeMode = true
            if (text.isBlank()) {
                val seeded = ScriptNodeSync.graphToText(graph, includeConnectMeta = true)
                suppress = true
                edit.isEnabled = true
                edit.setText(seeded)
                original = seeded
                last = seeded
                suppress = false
            } else {
                suppress = true
                edit.isEnabled = true
                edit.setText(text)
                suppress = false
            }
        } else {
            nodeMode = false
            suppress = true
            edit.isEnabled = true
            edit.setText(text)
            suppress = false
        }

        tabTitle.text = f.name
        applyEditorVisibility()
        updateProps()
        refreshTree()
        logConsole("[INFO] Открыт: ${f.name}")
    }

    private fun closeFile() {
        if (file != null && isDirty()) {
            overlays.confirm(
                "Сохранить изменения перед закрытием?",
                "Сохранить",
                "Закрыть",
                onYes = {
                    save()
                    showEmptyEditor()
                },
                onNo = { showEmptyEditor() }
            )
        } else {
            showEmptyEditor()
        }
    }

    private fun currentEditorText(): String {
        val f = file
        return if (f != null && isScriptFile(f) && nodeMode) {
            ScriptNodeSync.graphToText(graph, includeConnectMeta = true)
        } else {
            edit.text?.toString() ?: ""
        }
    }

    private fun save() {
        if (viewingImage) {
            logConsole("[WARN] Для изображения сохранение/запуск недоступны")
            return
        }
        val f = file ?: return
        try {
            val text = currentEditorText()
            f.writeText(text)
            // синхронизируем текстовый буфер
            syncing = true
            edit.setText(text)
            syncing = false
            original = text
            last = text
            dirty = false
            tabTitle.text = f.name
            updateProps()
            logConsole("[INFO] Сохранено: ${f.name}")
        } catch (e: Exception) {
            logConsole("[ERROR] Сохранение не удалось: ${e.message}")
        }
        if (nodeMode && isScriptFile(file!!)) {
            ScriptNodeSync.graphToText(graph, includeConnectMeta = true)
        } else {
            edit.text?.toString().orEmpty()
        }
    }

    private fun runScript() {
        if (viewingImage) {
            logConsole("[WARN] Для изображения сохранение/запуск недоступны")
            return
        }
        val f = file ?: run {
            logConsole("[WARN] Нет открытого файла")
            return
        }
        if (isDirty()) save()
        val name = f.nameWithoutExtension
        val mc = Minecraft.getInstance()
        val server = mc.singleplayerServer
        if (server == null) {
            logConsole("[WARN] Запуск только в одиночной игре / на сервере")
            return
        }
        server.execute {
            ScriptManager.reload()
            val player = server.playerList.players.firstOrNull()
            val ok = ScriptManager.startScript(name, ScriptContext(server, player))
            mc.execute {
                if (ok) logConsole("[INFO] Запущен скрипт: $name")
                else logConsole("[WARN] Не удалось запустить: $name")
            }
        }
    }

    private fun logConsole(line: String) {
        if (!this::consoleBox.isInitialized) return
        val apply = Runnable {
            if (!this::consoleBox.isInitialized) return@Runnable
            val color = when {
                line.startsWith("[ERROR]") -> PanelUi.COL_ERROR
                line.startsWith("[WARN]") -> PanelUi.COL_WARNING
                else -> COL_MUTED
            }
            consoleBox.addView(
                TextView(ctx).apply {
                    text = line
                    textSize = 11f
                    setTextColor(color)
                },
                lp(MATCH, WRAP)
            )
            while (consoleBox.childCount > 40) consoleBox.removeViewAt(0)
        }
        if (Core.isOnUiThread()) apply.run() else Core.getUiHandler().post(apply)
    }

    // ------------------------------------------------------------------
    // RIGHT: свойства + палитра нод
    // ------------------------------------------------------------------

    private fun buildProperties(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_SIDEBAR)
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
        }

        col.addView(
            TextView(ctx).apply {
                text = "Свойства сценария"
                textSize = 13f
                setTextColor(COL_ACCENT)
            },
            lp(MATCH, WRAP)
        )

        col.addView(PanelUi.spacer(ctx, 12))
        col.addView(PanelUi.mutedText(ctx, "Название"), lp(MATCH, WRAP))
        propName = TextView(ctx).apply {
            text = "—"
            textSize = 13f
            setTextColor(COL_TEXT)
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 10))
        }
        col.addView(propName, lp(MATCH, WRAP))

        col.addView(PanelUi.mutedText(ctx, "Путь"), lp(MATCH, WRAP))
        propPath = TextView(ctx).apply {
            text = "—"
            textSize = 12f
            setTextColor(COL_TEXT)
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 10))
        }
        col.addView(propPath, lp(MATCH, WRAP))

        col.addView(PanelUi.mutedText(ctx, "Статус"), lp(MATCH, WRAP))
        propStatus = TextView(ctx).apply {
            text = "Файл не выбран"
            textSize = 12f
            setTextColor(COL_MUTED)
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 10))
        }
        col.addView(propStatus, lp(MATCH, WRAP))

        col.addView(PanelUi.spacer(ctx, 8))
        col.addView(
            PanelUi.accentButton(ctx, "Сохранить") { save() },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) }
        )
        col.addView(
            PanelUi.flatButton(ctx, "Запустить") { runScript() },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 12) }
        )

        // --- выбранная нода ---
        val nodePropsTitle = TextView(ctx).apply {
            text = "Нода"
            textSize = 12f
            setTextColor(COL_ACCENT)
            setPadding(0, dp(ctx, 12), 0, dp(ctx, 4))
        }
        col.addView(nodePropsTitle, lp(MATCH, WRAP))

        nodeTitleView = TextView(ctx).apply {
            text = "—"
            textSize = 13f
            setTextColor(COL_TEXT)
        }
        col.addView(nodeTitleView, lp(MATCH, WRAP))

        nodeParamsBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        col.addView(nodeParamsBox, lp(MATCH, WRAP).apply { topMargin = dp(ctx, 6) })

        nodeParamEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (suppressNodeParam) return
            }
        })

        // --- палитра нод ---
        paletteSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        paletteSection.addView(
            TextView(ctx).apply {
                text = "Ноды"
                textSize = 13f
                setTextColor(COL_ACCENT)
            },
            lp(MATCH, WRAP)
        )
        paletteSection.addView(PanelUi.spacer(ctx, 6))

        paletteSearch = EditText(ctx).apply {
            hint = "Поиск нод…"
            textSize = 12f
            setTextColor(COL_TEXT)
            setHintTextColor(COL_MUTED)
            setSingleLine(true)
            background = ColorDrawable(0xFF1A1A22.toInt())
            setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    refreshPalette(s?.toString().orEmpty())
                }
            })
        }
        paletteSection.addView(paletteSearch, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) })

        paletteBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val paletteScroll = ScrollView(ctx)
        paletteScroll.addView(paletteBox, lp(MATCH, WRAP))
        paletteSection.addView(paletteScroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        col.addView(paletteSection, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return col
    }

    private var suppressNodeParam = false

    private fun refreshPalette(query: String) {
        if (!this::paletteBox.isInitialized) return
        paletteBox.removeAllViews()
        for (t in NodePalette.search(query)) {
            paletteBox.addView(
                PanelUi.flatButton(ctx, t.title) {
                    if (file == null || !isScriptFile(file!!) || viewingImage) {
                        logConsole("[WARN] Открой .sfxs, чтобы добавить ноду")
                        return@flatButton
                    }
                    if (!nodeMode) {
                        logConsole("[WARN] Переключись в режим нод (>_)")
                        return@flatButton
                    }
                    val id = "n${nodeIdSeq++}"
                    nodeCanvas.addNode(NodePalette.spawn(t, id, 0f, 0f))
                    dirty = true
                    updateProps()
                },
                lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 4) }
            )
        }
    }

    private fun updateProps() {
        val f = file
        if (f == null) {
            propName.text = "—"
            propPath.text = "—"
            propStatus.text = "Файл не выбран"
            propStatus.setTextColor(COL_MUTED)
            tabTitle.text = "— нет файла —"
            return
        }
        propName.text = f.name
        propPath.text = f.absolutePath
            .replace('\\', '/')
            .substringAfter("scriptfx/", "scriptfx/${f.name}")
        if (dirty) {
            propStatus.text = "Изменён (не сохранён)"
            propStatus.setTextColor(PanelUi.COL_WARNING)
            tabTitle.text = "${f.name} *"
        } else {
            propStatus.text = "Сохранён"
            propStatus.setTextColor(PanelUi.COL_SUCCESS)
            tabTitle.text = f.name
        }
    }

    private fun isDirty(): Boolean = dirty

    // ------------------------------------------------------------------
    // file ops
    // ------------------------------------------------------------------

    private fun openEntry(entry: FileEntry) {
        if (entry.isDirectory) {
            browser.goInto(entry)
            refreshTree()
            return
        }
        if (isVideoFile(entry.file)) {
            openVideoPreview(entry.file.nameWithoutExtension)
            return
        }
        if (isImageFile(entry.file)) openImagePreview(entry.file)
        else openEditor(entry.file)
    }

    private fun openRowMenu(entry: FileEntry) {
        val items = mutableListOf<Pair<String, () -> Unit>>()
        items += "Открыть" to { openEntry(entry) }
        items += "Переименовать" to {
            overlays.input("Переименовать", entry.name) { newName ->
                if (!browser.rename(entry, newName)) logConsole("[WARN] Не удалось переименовать")
                refreshTree()
            }
        }
        items += "Удалить" to {
            overlays.confirm(
                "Удалить \"${entry.name}\"?",
                "Удалить",
                "Отмена",
                onYes = {
                    browser.delete(entry)
                    if (file?.absolutePath == entry.file.absolutePath) showEmptyEditor()
                    refreshTree()
                }
            )
        }
        overlays.actions(entry.name, items)
    }

    private fun openBackgroundMenu() {
        val items = mutableListOf<Pair<String, () -> Unit>>()

        items += "Новый файл" to {
            overlays.input("Имя файла", "script.sfxs") { name ->
                val safe = name.trim()
                if (safe.isEmpty()) {
                    logConsole("[WARN] Пустое имя файла")
                    return@input
                }
                browser.createFile(safe)
                refreshTree()
                val created = File(browser.currentDir, safe)
                if (created.exists()) {
                    if (isScriptFile(created)) seedNewScript(created)
                    else openEditor(created)
                    logConsole("[INFO] Создан файл: $safe")
                } else logConsole("[WARN] Не удалось создать файл")
            }
        }

        items += "Новая папка" to {
            overlays.input("Имя папки", "folder") { name ->
                val safe = name.trim()
                if (safe.isEmpty()) {
                    logConsole("[WARN] Пустое имя папки")
                    return@input
                }
                browser.createFolder(safe)
                refreshTree()
                logConsole("[INFO] Создана папка: $safe")
            }
        }

        items += "Новый скрипт (.sfxs)" to {
            overlays.input("Имя скрипта", "main") { name ->
                browser.createScriptFile(name)
                refreshTree()
                val fileName = if (name.trim().endsWith(".sfxs")) name.trim() else "${name.trim()}.sfxs"
                val created = File(browser.currentDir, fileName)
                if (created.exists()) seedNewScript(created)
            }
        }

        overlays.actions("Создать", items)
    }

    /** Новый .sfxs: шаблон Start → print → End, режим нод. */
    private fun seedNewScript(f: File) {
        val name = f.nameWithoutExtension
        graph = ScriptNodeSync.defaultTemplateGraph(name)
        val text = ScriptNodeSync.graphToText(graph, includeConnectMeta = true)
        try {
            f.writeText(text)
        } catch (_: Exception) {}
        openEditor(f)
        nodeMode = true
        nodeCanvas.graph = graph
        applyEditorVisibility()
        logConsole("[INFO] Шаблон нод: Начало → Сообщение → Конец")
    }

    private fun isScriptFile(f: File): Boolean =
        f.name.lowercase().endsWith(".sfxs")

    private fun dividerV(ctx: Context) =
        View(ctx).apply { background = ColorDrawable(COL_DIVIDER) }

    private fun dividerH(ctx: Context) =
        View(ctx).apply { background = ColorDrawable(COL_DIVIDER) }

    private fun isImageFile(f: File): Boolean {
        val n = f.name.lowercase()
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".gif") || n.endsWith(".bmp") || n.endsWith(".webp")
    }

    private fun isVideoFile(f: File): Boolean {
        val n = f.name.lowercase()
        return n.endsWith(".mp4") || n.endsWith(".webm") || n.endsWith(".mkv")
                || n.endsWith(".mov") || n.endsWith(".avi") || n.endsWith(".m4v")
    }

    private fun clearImagePreview() {
        imageView.setImage(null as Image?)
        imageView.visibility = View.GONE
        previewImage?.close()
        previewImage = null
        viewingImage = false
        applyEditorVisibility()
    }

    private fun openImagePreview(f: File) {
        if (file != null && isDirty()) {
            overlays.confirm(
                "Сохранить изменения перед открытием картинки?",
                "Сохранить",
                "Не сохранять",
                onYes = {
                    save()
                    doOpenImage(f)
                },
                onNo = { doOpenImage(f) }
            )
        } else doOpenImage(f)
    }

    private fun doOpenImage(f: File) {
        clearImagePreview()
        try {
            val bmp: Bitmap = BitmapFactory.decodeFile(f)
            val img = Image.createTextureFromBitmap(bmp)
            bmp.close()
            if (img == null) {
                logConsole("[ERROR] Не удалось создать GPU-изображение: ${f.name}")
                return
            }
            previewImage = img
            imageView.setImage(img)
            imageView.visibility = View.VISIBLE
            nodeEditorWrap.visibility = View.GONE
            textEditorWrap.visibility = View.GONE

            suppress = true
            edit.setText("")
            edit.isEnabled = false
            suppress = false

            file = f
            original = ""
            last = ""
            dirty = false
            viewingImage = true
            tabTitle.text = "🖼 ${f.name}"
            paletteSection.visibility = View.GONE
            updateProps()
            refreshTree()
            logConsole("[INFO] Превью: ${f.name} (${img.width}×${img.height})")
        } catch (e: Exception) {
            logConsole("[ERROR] Не удалось открыть изображение: ${e.message}")
            clearImagePreview()
        }
    }
}