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
import net.foxmediax.scriptfx.scriptengine.CommandRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
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
    private val suggestLabel = UiLabel("", UiTheme.TEXT_MUTED)
    private val consoleTitle = UiLabel("Консоль", UiTheme.ACCENT)

    private val consoleLines = ArrayDeque<String>()
    private var consoleX = 0
    private var consoleY = 0
    private var consoleW = 0
    private var consoleH = 0

    private val fileList = UiScrollList(onSelect = { idx, _ -> onEntryClick(idx) })
    private val editor = UiTextArea { text ->
        dirty = text != original
        refreshStatus()
        updateSuggestions(text)
    }
    private val nodeCanvas = UiNodeCanvas()
    private val nameField = UiTextField(hint = "script.sfxs")

    private val btnUp = UiButton(".. вверх") { goUp() }
    private val btnSave = UiButton("Сохранить") { save() }
    private val btnNewScript = UiButton("+ .sfxs") { newScript() }
    private val btnNewFolder = UiButton("Папка") { newFolder() }
    private val btnRefresh = UiButton("Обновить") { reloadTree() }
    private val btnMode = UiButton(">_") { toggleMode() }
    private val btnValidate = UiButton("✓") { validateScript() }
    private val btnClearLog = UiButton("clr") { clearConsole() }

    private val paletteList = UiScrollList(onSelect = { idx, _ -> spawnFromPalette(idx) })
    private val nodeTitle = UiLabel("Нода не выбрана", UiTheme.TEXT_MUTED)
    private val paramsPanel = UiPanel(UiTheme.BG_CARD)

    init {
        btnSave.style = UiButton.Style.PRIMARY
        btnValidate.style = UiButton.Style.PRIMARY
        btnNewScript.style = UiButton.Style.PRIMARY
        nodeCanvas.onGraphChanged = { dirty = true; refreshStatus() }
        nodeCanvas.onSelectionChanged = { node ->
            selectedNode = node
            showNodeProps(node)
        }
    }

    override fun onShow() {
        StudioSession.selectedProject?.let { browser.goTo(it) }
        reloadTree()
        refreshStatus()
    }

    override fun layout(x: Int, y: Int, w: Int, h: Int) {
        root.clearChildren()
        root.setBounds(x, y, w, h)
        val pad = UiTheme.PAD
        val leftW = (w * 0.20f).toInt().coerceIn(150, 220)
        val rightW = (w * 0.24f).toInt().coerceIn(180, 260)
        val centerX = x + leftW + pad
        val centerW = (w - leftW - rightW - pad * 3).coerceAtLeast(280)
        val rightX = x + w - rightW - pad
        val topBar = 52
        val editorH = (h - topBar - 72 - 8).coerceAtLeast(120)

        root.add(UiLabel("Проект", UiTheme.ACCENT).also {
            it.setBounds(x + pad, y + 10, leftW - pad, 14)
        })
        btnUp.setBounds(x + pad, y + 28, leftW - pad, 22)
        root.add(btnUp)
        fileList.setBounds(x + pad, y + 54, leftW - pad, h - 70)
        root.add(fileList)

        root.add(UiLabel("Сценарии", UiTheme.ACCENT).also {
            it.setBounds(centerX, y + 10, 100, 14)
        })
        statusLabel.setBounds(centerX + 100, y + 10, centerW - 100, 14)
        root.add(statusLabel)

        btnNewScript.setBounds(centerX, y + 28, 70, 22)
        btnNewFolder.setBounds(centerX + 74, y + 28, 56, 22)
        btnRefresh.setBounds(centerX + 134, y + 28, 70, 22)
        btnSave.setBounds(centerX + 208, y + 28, 80, 22)
        btnValidate.setBounds(centerX + 292, y + 28, 28, 22)
        root.add(btnNewScript); root.add(btnNewFolder); root.add(btnRefresh)
        root.add(btnSave); root.add(btnValidate)
        nameField.setBounds(centerX + 326, y + 28, (centerW - 326).coerceAtLeast(60), 22)
        root.add(nameField)

        editor.setBounds(centerX, y + topBar, centerW, editorH)
        root.add(editor)
        nodeCanvas.setBounds(centerX, y + topBar, centerW, editorH)
        root.add(nodeCanvas)
        btnMode.setBounds(centerX + centerW - 36, y + topBar + editorH - 36, 28, 28)
        root.add(btnMode)

        suggestLabel.setBounds(centerX, y + topBar + editorH + 2, centerW - 44, 12)
        root.add(suggestLabel)
        btnClearLog.setBounds(centerX + centerW - 40, y + topBar + editorH + 1, 36, 14)
        root.add(btnClearLog)
        consoleTitle.setBounds(centerX, y + topBar + editorH + 16, 80, 12)
        root.add(consoleTitle)
        consoleX = centerX
        consoleY = y + topBar + editorH + 30
        consoleW = centerW
        consoleH = (y + h - pad) - consoleY

        root.add(UiLabel("Свойства", UiTheme.ACCENT).also {
            it.setBounds(rightX, y + 10, rightW, 14)
        })
        pathLabel.setBounds(rightX, y + 28, rightW, 32)
        root.add(pathLabel)
        nodeTitle.setBounds(rightX, y + 64, rightW, 14)
        root.add(nodeTitle)
        root.add(UiLabel("Палитра нод", UiTheme.ACCENT).also {
            it.setBounds(rightX, y + 86, rightW, 14)
        })
        paletteItems = NodePalette.all.filter { it.kind != NodeKind.START && it.kind != NodeKind.END }
        paletteList.items = paletteItems.map { it.title }
        paletteList.setBounds(rightX, y + 104, rightW, (h * 0.32f).toInt().coerceAtLeast(90))
        root.add(paletteList)
        paramsPanel.setBounds(rightX, paletteList.bottom + 8, rightW, (y + h - pad) - (paletteList.bottom + 8))
        root.add(paramsPanel)

        applyModeVisibility()
        showNodeProps(selectedNode)
        refreshStatus()
    }

    private fun reloadTree() {
        browser.refresh()
        entries = browser.entries
        fileList.items = entries.map { e -> if (e.isDirectory) "📁 ${e.name}" else "📄 ${e.name}" }
        val p = browser.currentDir.path.replace('\\', '/')
        val name = StudioSession.selectedProject?.name
        pathLabel.text = if (name != null && p.contains(name)) "…/" + p.substringAfter(name).trimStart('/')
        else p.takeLast(40)
    }

    private fun onEntryClick(idx: Int) {
        val e = entries.getOrNull(idx) ?: return
        if (e.isDirectory) {
            browser.goInto(e)
            reloadTree()
        } else openFile(e.file)
    }

    private fun openFile(f: File) {
        try {
            val text = f.readText()
            openFile = f
            original = text
            dirty = false
            selectedNode = null
            if (f.name.endsWith(".sfxs", true)) {
                nodeMode = true
                nodeCanvas.graph = if (text.isBlank())
                    ScriptNodeSync.defaultTemplateGraph(f.nameWithoutExtension)
                else ScriptNodeSync.textToGraph(text, f.nameWithoutExtension)
                editor.setText(text)
            } else {
                nodeMode = false
                editor.setText(text)
            }
            applyModeVisibility()
            showNodeProps(null)
            refreshStatus()
            logConsole("[INFO] Открыт: ${f.name}")
            updateSuggestions(editor.value)
        } catch (ex: Exception) {
            logConsole("[ERROR] ${ex.message}")
        }
    }

    fun save() {
        val f = openFile ?: return
        try {
            val text = if (nodeMode && f.name.endsWith(".sfxs", true))
                ScriptNodeSync.graphToText(nodeCanvas.graph, includeConnectMeta = true)
            else editor.value
            f.writeText(text)
            original = text
            dirty = false
            if (!nodeMode) editor.setText(text)
            refreshStatus()
            logConsole("[INFO] Сохранено: ${f.name}")
        } catch (ex: Exception) {
            logConsole("[ERROR] ${ex.message}")
        }
    }

    private fun toggleMode() {
        val f = openFile
        if (f == null || !f.name.endsWith(".sfxs", true)) {
            logConsole("[WARN] Только .sfxs"); return
        }
        if (nodeMode) {
            editor.setText(ScriptNodeSync.graphToText(nodeCanvas.graph, includeConnectMeta = true))
            nodeMode = false
        } else {
            nodeCanvas.graph = ScriptNodeSync.textToGraph(editor.value, f.nameWithoutExtension)
            nodeMode = true
        }
        dirty = true
        applyModeVisibility()
        refreshStatus()
        updateSuggestions(editor.value)
    }

    private fun applyModeVisibility() {
        val script = openFile?.name?.endsWith(".sfxs", true) == true
        nodeCanvas.visible = nodeMode && script
        editor.visible = !nodeMode || !script
        paletteList.visible = script
        btnMode.visible = script
        btnValidate.visible = script
        if (nodeMode) suggestLabel.text = ""
    }

    private fun spawnFromPalette(idx: Int) {
        if (!nodeMode || openFile?.name?.endsWith(".sfxs", true) != true) {
            logConsole("[WARN] Нужен .sfxs в режиме нод"); return
        }
        if (idx !in paletteItems.indices) return
        nodeCanvas.addNode(NodePalette.spawn(paletteItems[idx], "n${System.currentTimeMillis() % 100000}", 0f, 0f))
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
            NodeKind.COMMAND -> listOf("cmd", "args").filter { node.params.containsKey(it) }.ifEmpty { node.params.keys.toList() }
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
            paramsPanel.add(UiLabel(key, UiTheme.TEXT_MUTED).also {
                it.setBounds(paramsPanel.x + 4, py, paramsPanel.w - 8, 12)
            })
            py += 14
            val k = key
            val field = UiTextField(value = node.params[k].orEmpty(), hint = k) { v ->
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
            if (proj != null && !browser.currentDir.canonicalPath.startsWith(proj.canonicalPath))
                browser.goTo(proj)
            reloadTree()
        }
    }

    private fun newScript() {
        val raw = nameField.value.trim().ifEmpty { "script.sfxs" }
        val name = if (raw.endsWith(".sfxs", true)) raw else "$raw.sfxs"
        browser.createScriptFile(name)
        val f = File(browser.currentDir, name)
        if (f.exists() && f.length() == 0L) {
            val g = ScriptNodeSync.defaultTemplateGraph(f.nameWithoutExtension)
            f.writeText(ScriptNodeSync.graphToText(g, includeConnectMeta = true))
        }
        nameField.value = ""
        reloadTree()
        if (f.exists()) openFile(f)
    }

    private fun newFolder() {
        browser.createFolder(nameField.value.trim().ifEmpty { "folder" })
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

    private fun logConsole(line: String) {
        consoleLines.addLast(line)
        while (consoleLines.size > 50) consoleLines.removeFirst()
    }

    private fun clearConsole() = consoleLines.clear()

    private fun currentScriptText(): String {
        val f = openFile ?: return ""
        if (!f.name.endsWith(".sfxs", true)) return editor.value
        return if (nodeMode) ScriptNodeSync.graphToText(nodeCanvas.graph, includeConnectMeta = false)
        else editor.value
    }

    private fun updateSuggestions(text: String) {
        if (nodeMode || openFile?.name?.endsWith(".sfxs", true) != true) {
            suggestLabel.text = ""; return
        }
        val line = text.substringAfterLast('\n')
        val token = line.trimStart().substringBefore(' ').lowercase()
        if (token.isEmpty() || line.trimStart().contains(' ')) {
            suggestLabel.text = ""; return
        }
        val names = try { CommandRegistry.suggestions(token, 8) } catch (_: Throwable) { emptyList() }
        suggestLabel.text = if (names.isEmpty()) "" else "Подсказки: " + names.joinToString("  ")
    }

    private data class CmdError(val line: Int, val cmd: String)

    private fun findCommandErrors(text: String): List<CmdError> {
        val out = ArrayList<CmdError>()
        text.lineSequence().forEachIndexed { index, raw ->
            val t = raw.trim()
            when {
                t.isEmpty() || t.startsWith("//") || t.startsWith("#") -> {}
                t.startsWith("}") || t.startsWith("{") -> {}
                t.lowercase().startsWith("node connect") -> {}
                else -> {
                    val cmd = t.substringBefore(' ').substringBefore('\t')
                    val known = try { CommandRegistry.isKnown(cmd.lowercase()) } catch (_: Throwable) { true }
                    if (cmd.isNotEmpty() && !known) out += CmdError(index + 1, cmd)
                }
            }
        }
        return out
    }

    private fun validateScript() {
        if (openFile?.name?.endsWith(".sfxs", true) != true) {
            logConsole("[WARN] Открой .sfxs"); return
        }
        val errors = findCommandErrors(currentScriptText())
        if (errors.isEmpty()) logConsole("[INFO] Проверка OK")
        else {
            logConsole("[WARN] Проблем: ${errors.size}")
            errors.take(20).forEach { logConsole("[WARN]  стр.${it.line}: «${it.cmd}»") }
        }
    }

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        root.render(g, mouseX, mouseY, pt)
        if (consoleW <= 0 || consoleH <= 0) return
        g.fill(consoleX, consoleY, consoleX + consoleW, consoleY + consoleH, UiTheme.BG_CARD)
        g.fill(consoleX, consoleY, consoleX + consoleW, consoleY + 1, UiTheme.BORDER)
        val font = Minecraft.getInstance().font
        val lineH = font.lineHeight + 1
        val visible = ((consoleH - 6) / lineH).coerceAtLeast(1)
        consoleLines.toList().takeLast(visible).forEachIndexed { i, line ->
            val col = when {
                line.contains("[ERROR]") -> UiTheme.DANGER
                line.contains("[WARN]") -> UiTheme.WARNING
                line.contains("[INFO]") -> UiTheme.SUCCESS
                else -> UiTheme.TEXT_MUTED
            }
            var shown = line
            while (shown.length > 4 && font.width(shown) > consoleW - 12) shown = shown.dropLast(1)
            g.text(font, Component.literal(shown), consoleX + 6, consoleY + 4 + i * lineH, col, false)
        }
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int) = root.mouseClicked(mx, my, button)
    override fun mouseReleased(mx: Int, my: Int, button: Int) = root.mouseReleased(mx, my, button)
    override fun mouseDragged(mx: Int, my: Int, button: Int, dx: Double, dy: Double) =
        root.mouseDragged(mx, my, button, dx, dy)
    override fun mouseScrolled(mx: Double, my: Double, sx: Double, sy: Double) =
        root.mouseScrolled(mx, my, sx, sy)
    override fun keyPressed(key: Int, scan: Int, modifiers: Int) = root.keyPressed(key, scan, modifiers)
    override fun charTyped(codePoint: Int, modifiers: Int) = root.charTyped(codePoint, modifiers)
}