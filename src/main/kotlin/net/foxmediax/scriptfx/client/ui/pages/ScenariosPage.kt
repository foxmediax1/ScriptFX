package net.foxmediax.scriptfx.client.ui.pages

import net.foxmediax.scriptfx.client.ui.StudioPage
import net.foxmediax.scriptfx.client.ui.StudioSession
import net.foxmediax.scriptfx.client.ui.UiButton
import net.foxmediax.scriptfx.client.ui.UiLabel
import net.foxmediax.scriptfx.client.ui.UiNodeCanvas
import net.foxmediax.scriptfx.client.ui.UiPanel
import net.foxmediax.scriptfx.client.ui.UiScrollList
import net.foxmediax.scriptfx.client.ui.UiTextArea
import net.foxmediax.scriptfx.client.ui.UiTextField
import net.foxmediax.scriptfx.client.ui.UiTheme
import net.foxmediax.scriptfx.gui.FileEntry
import net.foxmediax.scriptfx.nodes.GraphNode
import net.foxmediax.scriptfx.nodes.NodeKind
import net.foxmediax.scriptfx.nodes.NodePalette
import net.foxmediax.scriptfx.nodes.NodeTemplate
import net.foxmediax.scriptfx.nodes.ScriptNodeSync
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.io.File

class ScenariosPage : StudioPage {
    override val title = "Сценарии"

    private val root = UiPanel(0)
    private val browser get() = StudioSession.browser

    private var entries: List<FileEntry> = emptyList()
    private var openFile: File? = null
    private var original = ""
    private var dirty = false
    private var nodeMode = true

    private var selectedNode: GraphNode? = null
    private var paletteItems: List<NodeTemplate> = emptyList()

    private val pathLabel = UiLabel("", UiTheme.TEXT_MUTED)
    private val statusLabel = UiLabel("", UiTheme.TEXT_MUTED)

    private val fileList = UiScrollList(onSelect = { idx, _ -> onEntryClick(idx) })
    private val editor = UiTextArea { text ->
        dirty = text != original
        refreshStatus()
    }
    private val nodeCanvas = UiNodeCanvas()
    private val nameField = UiTextField(hint = "script.sfxs")

    private val btnUp = UiButton(".. вверх") { goUp() }
    private val btnSave = UiButton("Сохранить") { save() }
    private val btnNewScript = UiButton("Новый .sfxs") { newScript() }
    private val btnNewFolder = UiButton("Папка") { newFolder() }
    private val btnRefresh = UiButton("Обновить") { reloadTree() }
    private val btnMode = UiButton(">_") { toggleMode() }

    private val paletteList = UiScrollList(onSelect = { idx, _ -> spawnFromPalette(idx) })
    private val nodeTitle = UiLabel("Нода не выбрана", UiTheme.TEXT_MUTED)
    private val paramsPanel = UiPanel(UiTheme.BG_CARD)

    init {
        nodeCanvas.onGraphChanged = {
            dirty = true
            refreshStatus()
        }
        nodeCanvas.onSelectionChanged = { node ->
            selectedNode = node
            showNodeProps(node)
        }
    }

    override fun onShow() {
        val proj = StudioSession.selectedProject
        if (proj != null) browser.goTo(proj)
        reloadTree()
        refreshStatus()
    }

    override fun layout(x: Int, y: Int, w: Int, h: Int) {
        root.clearChildren()
        root.setBounds(x, y, w, h)

        val pad = UiTheme.PAD
        val leftW = (w * 0.22f).toInt().coerceIn(160, 260)
        val rightW = 200
        val centerX = x + leftW + pad
        val centerW = (w - leftW - rightW - pad * 3).coerceAtLeast(200)
        val rightX = x + w - rightW - pad

        pathLabel.setBounds(x + pad, y + 8, w - pad * 2, 14)
        root.add(pathLabel)

        btnUp.setBounds(x + pad, y + 28, 70, 22)
        btnRefresh.setBounds(x + pad + 76, y + 28, 70, 22)
        btnNewScript.setBounds(x + pad + 152, y + 28, 90, 22)
        btnNewFolder.setBounds(x + pad + 248, y + 28, 60, 22)
        btnSave.setBounds(x + pad + 314, y + 28, 80, 22)
        root.add(btnUp)
        root.add(btnRefresh)
        root.add(btnNewScript)
        root.add(btnNewFolder)
        root.add(btnSave)

        nameField.setBounds(x + pad + 400, y + 28, 140, 22)
        root.add(nameField)

        fileList.setBounds(x + pad, y + 56, leftW - pad, h - 80)
        root.add(fileList)

        editor.setBounds(centerX, y + 56, centerW, h - 80)
        root.add(editor)

        nodeCanvas.setBounds(centerX, y + 56, centerW, h - 80)
        root.add(nodeCanvas)

        btnMode.setBounds(centerX + centerW - 36, y + 56 + (h - 80) - 36, 28, 28)
        root.add(btnMode)

        // правая колонка
        val palTitle = UiLabel("Палитра нод", UiTheme.ACCENT)
        palTitle.setBounds(rightX, y + 56, rightW, 14)
        root.add(palTitle)

        paletteItems = NodePalette.all.filter {
            it.kind != NodeKind.START && it.kind != NodeKind.END
        }
        paletteList.items = paletteItems.map { it.title }
        val palH = (h * 0.35f).toInt().coerceAtLeast(100)
        paletteList.setBounds(rightX, y + 74, rightW, palH)
        root.add(paletteList)

        val propsTitle = UiLabel("Свойства", UiTheme.ACCENT)
        propsTitle.setBounds(rightX, paletteList.bottom + 10, rightW, 14)
        root.add(propsTitle)

        nodeTitle.setBounds(rightX, paletteList.bottom + 28, rightW, 14)
        root.add(nodeTitle)

        paramsPanel.setBounds(
            rightX,
            paletteList.bottom + 48,
            rightW,
            (y + h - 24) - (paletteList.bottom + 48)
        )
        root.add(paramsPanel)

        statusLabel.setBounds(x + pad, y + h - 20, w - pad * 2, 14)
        root.add(statusLabel)

        reloadTree()
        applyModeVisibility()
        showNodeProps(selectedNode)
        refreshStatus()
    }

    private fun reloadTree() {
        val proj = StudioSession.selectedProject
        if (proj == null) {
            pathLabel.text = "Сначала выберите проект на Главной"
            entries = emptyList()
            fileList.items = emptyList()
            editor.setText("")
            editor.enabled = false
            nodeCanvas.visible = false
            return
        }
        browser.refresh()
        pathLabel.text = "/${browser.breadcrumbPath()}"
        entries = browser.entries
        fileList.items = entries.map { e ->
            when {
                e.isDirectory -> "📁 ${e.name}"
                e.name.endsWith(".sfxs", true) -> "📜 ${e.name}"
                e.name.endsWith(".png", true) || e.name.endsWith(".jpg", true) -> "🖼 ${e.name}"
                else -> e.name
            }
        }
        editor.enabled = true
    }

    private fun onEntryClick(idx: Int) {
        if (idx !in entries.indices) return
        val e = entries[idx]
        if (e.isDirectory) {
            browser.goInto(e)
            reloadTree()
            return
        }
        openFile(e.file)
    }

    private fun openFile(f: File) {
        try {
            val text = f.readText()
            openFile = f
            original = text
            dirty = false
            editor.setText(text)

            if (f.name.endsWith(".sfxs", true)) {
                nodeMode = true
                nodeCanvas.graph = if (text.isBlank()) {
                    ScriptNodeSync.defaultTemplateGraph(f.nameWithoutExtension)
                } else {
                    ScriptNodeSync.textToGraph(text, f.nameWithoutExtension)
                }
            } else {
                nodeMode = false
            }
            selectedNode = null
            showNodeProps(null)
            applyModeVisibility()
            refreshStatus()
        } catch (ex: Exception) {
            statusLabel.text = "Ошибка чтения: ${ex.message}"
            statusLabel.color = UiTheme.DANGER
        }
    }

    fun save() {
        val f = openFile ?: return
        try {
            val text = if (nodeMode && f.name.endsWith(".sfxs", true)) {
                ScriptNodeSync.graphToText(nodeCanvas.graph, includeConnectMeta = true)
            } else {
                editor.value
            }
            f.writeText(text)
            original = text
            dirty = false
            if (!nodeMode) editor.setText(text)
            refreshStatus()
            statusLabel.text = "Сохранено: ${f.name}"
            statusLabel.color = UiTheme.SUCCESS
        } catch (ex: Exception) {
            statusLabel.text = "Ошибка записи: ${ex.message}"
            statusLabel.color = UiTheme.DANGER
        }
    }

    private fun toggleMode() {
        val f = openFile
        if (f == null || !f.name.endsWith(".sfxs", true)) {
            statusLabel.text = "Ноды только для .sfxs"
            statusLabel.color = UiTheme.WARNING
            return
        }
        if (nodeMode) {
            val text = ScriptNodeSync.graphToText(nodeCanvas.graph, includeConnectMeta = true)
            editor.setText(text)
            dirty = text != original
            nodeMode = false
        } else {
            nodeCanvas.graph = ScriptNodeSync.textToGraph(editor.value, f.nameWithoutExtension)
            nodeMode = true
        }
        selectedNode = null
        showNodeProps(null)
        applyModeVisibility()
        refreshStatus()
    }

    private fun applyModeVisibility() {
        val script = openFile?.name?.endsWith(".sfxs", true) == true
        nodeCanvas.visible = nodeMode && script
        editor.visible = !nodeMode || !script
        btnMode.visible = script
        paletteList.visible = script
        paramsPanel.visible = script
        nodeTitle.visible = script
    }

    private fun spawnFromPalette(idx: Int) {
        if (!nodeMode) {
            statusLabel.text = "Включи режим нод (>_)"
            statusLabel.color = UiTheme.WARNING
            return
        }
        if (idx !in paletteItems.indices) return
        val t = paletteItems[idx]
        val id = "n${System.currentTimeMillis() % 100000}"
        nodeCanvas.addNode(NodePalette.spawn(t, id, 0f, 0f))
        dirty = true
        refreshStatus()
    }

    private fun showNodeProps(node: GraphNode?) {
        paramsPanel.clearChildren()
        if (node == null) {
            nodeTitle.text = "Нода не выбрана"
            nodeTitle.color = UiTheme.TEXT_MUTED
            return
        }
        nodeTitle.text = node.title
        nodeTitle.color = UiTheme.TEXT

        val editable = node.kind != NodeKind.START && node.kind != NodeKind.END
        val keys = when (node.kind) {
            NodeKind.COMMAND -> listOf("cmd", "args").filter { node.params.containsKey(it) }
                .ifEmpty { node.params.keys.toList() }
            NodeKind.WAIT -> listOf("duration")
            NodeKind.SCRIPT -> listOf("script")
            NodeKind.QUEST_START, NodeKind.QUEST_COMPLETE, NodeKind.QUEST_FAIL -> listOf("quest")
            NodeKind.BRANCH -> listOf("var", "op", "value")
            NodeKind.CUTSCENE_SET -> listOf("x", "y", "z", "yaw", "pitch")
            NodeKind.CUTSCENE_MOVE -> listOf("x", "y", "z", "yaw", "pitch", "duration")
            NodeKind.CUTSCENE_START -> listOf("mode")
            else -> node.params.keys.toList()
        }.ifEmpty { node.params.keys.toList() }

        var py = paramsPanel.y + 4
        for (key in keys) {
            val lab = UiLabel(key, UiTheme.TEXT_MUTED)
            lab.setBounds(paramsPanel.x + 4, py, paramsPanel.w - 8, 12)
            paramsPanel.add(lab)
            py += 14

            val k = key
            val field = UiTextField(
                value = node.params[k].orEmpty(),
                hint = k
            ) { v ->
                if (!editable) return@UiTextField
                node.params[k] = v
                dirty = true
                refreshStatus()
            }
            field.enabled = editable
            field.setBounds(paramsPanel.x + 4, py, paramsPanel.w - 8, 24)
            paramsPanel.add(field)
            py += 28
            if (py > paramsPanel.bottom - 28) break
        }
    }

    private fun goUp() {
        if (browser.canGoUp()) {
            browser.goUp()
            val proj = StudioSession.selectedProject
            if (proj != null && !browser.currentDir.canonicalPath.startsWith(proj.canonicalPath)) {
                browser.goTo(proj)
            }
            reloadTree()
        }
    }

    private fun newScript() {
        val raw = nameField.value.trim().ifEmpty { "script.sfxs" }
        val name = if (raw.endsWith(".sfxs", true)) raw else "$raw.sfxs"
        browser.createScriptFile(name)
        val f = File(browser.currentDir, name)
        if (f.exists() && f.length() == 0L) {
            val graph = ScriptNodeSync.defaultTemplateGraph(f.nameWithoutExtension)
            f.writeText(ScriptNodeSync.graphToText(graph, includeConnectMeta = true))
        }
        nameField.value = ""
        reloadTree()
        if (f.exists()) openFile(f)
    }

    private fun newFolder() {
        val name = nameField.value.trim().ifEmpty { "folder" }
        browser.createFolder(name)
        nameField.value = ""
        reloadTree()
    }

    private fun refreshStatus() {
        val f = openFile
        statusLabel.text = when {
            f == null -> "Файл не открыт"
            dirty -> "${f.name} • изменён" + if (nodeMode) " • ноды" else " • текст"
            else -> "${f.name} • сохранён" + if (nodeMode) " • ноды" else " • текст"
        }
        statusLabel.color = when {
            dirty -> UiTheme.WARNING
            f != null -> UiTheme.SUCCESS
            else -> UiTheme.TEXT_MUTED
        }
        btnSave.enabled = f != null && dirty
    }

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) =
        root.render(g, mouseX, mouseY, pt)

    override fun mouseClicked(mx: Int, my: Int, button: Int) = root.mouseClicked(mx, my, button)
    override fun mouseReleased(mx: Int, my: Int, button: Int) = root.mouseReleased(mx, my, button)
    override fun mouseDragged(mx: Int, my: Int, button: Int, dx: Double, dy: Double) =
        root.mouseDragged(mx, my, button, dx, dy)
    override fun mouseScrolled(mx: Double, my: Double, sx: Double, sy: Double) =
        root.mouseScrolled(mx, my, sx, sy)
    override fun keyPressed(key: Int, scan: Int, modifiers: Int) = root.keyPressed(key, scan, modifiers)
    override fun charTyped(codePoint: Int, modifiers: Int) = root.charTyped(codePoint, modifiers)
}