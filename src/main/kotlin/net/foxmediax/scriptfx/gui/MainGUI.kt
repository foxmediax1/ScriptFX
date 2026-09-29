package net.foxmediax.scriptfx.gui

import net.foxmediax.scriptfx.config.ScriptFXConfigScreen
import net.foxmediax.scriptfx.scriptengine.CommandRegistry
import net.foxmediax.scriptfx.scriptengine.ScriptContext
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.foxmediax.scriptfx.scriptengine.ScriptManager
import net.foxmediax.scriptfx.scriptengine.ScriptParser
import net.foxmediax.scriptfx.scriptengine.TriggerParser
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import java.io.File
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.CharacterEvent

enum class PanelSection(val displayName: String) {
    PROJECTS("Проекты"),
    CUTSCENES("Кат-сцены"),
    NPC_EDITOR("NPC-редактор"),
    LOGS("Логи"),
    SETTINGS("Настройки мода")
}

class FlatButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val label: String,
    private val centered: Boolean = true,
    private val onPress: () -> Unit
) : AbstractWidget(x, y, width, height, Component.literal(label)) {

    fun renderButton(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        extractWidgetRenderState(graphics, mouseX, mouseY, delta)
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        val hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
        val bg = if (hovered) 0xFF262626.toInt() else 0xFF101010.toInt()
        graphics.fill(x, y, x + width, y + height, bg)

        val font = Minecraft.getInstance().font
        val textColor = 0xFFEDEDED.toInt()
        val textY = y + (height - font.lineHeight) / 2
        if (centered) {
            val textWidth = font.width(label)
            graphics.text(font, label, x + (width - textWidth) / 2, textY, textColor, false)
        } else {
            graphics.text(font, label, x + 8, textY, textColor, false)
        }
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        onPress.invoke()
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, Component.literal(label))
    }
}

class ControlPanelScreen : Screen(Component.literal("ScriptFX")) {

    private data class Rect(val x1: Int, val y1: Int, val x2: Int, val y2: Int) {
        fun contains(px: Int, py: Int) = px in x1 until x2 && py in y1 until y2
    }

    private data class FileRow(val entry: FileEntry?, val rect: Rect, val isUp: Boolean = false)
    private data class ContextMenuItem(val label: String, val action: () -> Unit)
    private data class ContextMenuInfo(val x: Int, val y: Int, val items: List<ContextMenuItem>)

    private var selectedSection: PanelSection? = null

    private val margin = 20
    private val logoWidth = 140
    private val headerHeight = 50
    private val sidebarWidth = 140
    private val navButtonSize = 20

    private val panelLeft get() = margin
    private val panelTop get() = margin
    private val panelRight get() = width - margin
    private val panelBottom get() = height - margin
    private val headerLeft get() = panelLeft + logoWidth
    private val sidebarTop get() = panelTop + headerHeight
    private val contentLeft get() = panelLeft + sidebarWidth

    private val fileBrowser = FileBrowser()
    private var fileRows: List<FileRow> = emptyList()
    private var contextMenu: ContextMenuInfo? = null
    private var contextMenuRowRects: List<Rect> = emptyList()

    private var textInputBox: EditBox? = null
    private var textInputConfirmButton: FlatButton? = null

    private var showSaveSuccessMessage = false
    private var saveSuccessMessageUntil = 0L
    private var showRunMessage = false
    private var runMessageUntil = 0L

    private var scriptEditorFile: File? = null
    private var lastClickedFile: File? = null
    private var lastClickTime: Long = 0L
    private val undoStack = ArrayDeque<String>()
    private var lastEditorValue: String = ""
    private var scriptEditorBox: MultiLineEditBox? = null
    private var scriptEditorOriginalContent: String? = null
    private var scriptEditorSaveButton: FlatButton? = null
    private var scriptEditorRunButton: FlatButton? = null
    private var scriptEditorCloseButton: FlatButton? = null
    private var scriptEditorHintsButton: FlatButton? = null

    private var closeConfirmVisible = false
    private var closeConfirmSaveButton: FlatButton? = null
    private var closeConfirmDiscardButton: FlatButton? = null
    private var pendingNavigationAction: (() -> Unit)? = null

    private var deleteConfirmEntry: FileEntry? = null
    private var deleteConfirmYesButton: FlatButton? = null
    private var deleteConfirmNoButton: FlatButton? = null

    private var showDocumentation = false
    private var docsScrollOffset = 0
    private var docsCloseButtonRect: Rect? = null
    private var docsScrollbarTrackRect: Rect? = null
    private var docsScrollbarThumbRect: Rect? = null
    private var docsMaxScroll = 0
    private var draggingDocsScrollbar = false
    private var docsDragGrabOffsetY = 0

    private var showScriptHints = false
    private var scriptHintsScrollOffset = 0
    private var scriptHintsCloseButtonRect: Rect? = null
    private var scriptHintsRowRects: List<Rect> = emptyList()
    private var scriptHintsScrollbarTrackRect: Rect? = null
    private var scriptHintsScrollbarThumbRect: Rect? = null
    private var scriptHintsMaxScroll: Int = 0
    private var draggingScriptHintsScrollbar = false
    private var scriptHintsDragGrabOffsetY = 0
    private var logsScrollOffset = 0
    private var logsClearButtonRect: Rect? = null

    private fun openScriptHints() {
        showScriptHints = true
        scriptHintsScrollOffset = 0
        draggingScriptHintsScrollbar = false
        setFocused(null)
    }

    private fun closeScriptHints() {
        showScriptHints = false
        draggingScriptHintsScrollbar = false
        setFocused(null)
    }

    override fun init() {
        buildSidebarButtons()
        buildHeaderButtons()
    }

    private fun buildSidebarButtons() {
        val buttonHeight = 24
        val gap = 6
        var y = sidebarTop + gap

        for (section in PanelSection.entries) {
            addRenderableWidget(
                FlatButton(panelLeft + 8, y, sidebarWidth - 16, buttonHeight, section.displayName, centered = true) {
                    closeTextInput()
                    contextMenu = null
                    navigateAwayAndThen {
                        if (section == PanelSection.SETTINGS) {
                            Minecraft.getInstance().setScreen(ScriptFXConfigScreen.build(this))
                        } else {
                            selectedSection = section
                            if (section == PanelSection.LOGS) logsScrollOffset = Int.MAX_VALUE / 2
                        }
                    }
                }
            )
            y += buttonHeight + gap
        }
    }

    private fun buildHeaderButtons() {
        val gap = 4
        val blockHeight = navButtonSize * 2 + gap
        val topY = panelTop + (headerHeight - blockHeight) / 2
        val bottomY = topY + navButtonSize + gap

        // Слева: домой (сверху) и назад (снизу)
        val navLeft = headerLeft + 8
        addRenderableWidget(
            FlatButton(navLeft, topY, navButtonSize, navButtonSize, "⌂", centered = true) {
                closeTextInput(); contextMenu = null
                navigateAwayAndThen { selectedSection = null }
            }
        )
        addRenderableWidget(
            FlatButton(navLeft, bottomY, navButtonSize, navButtonSize, "▲", centered = true) {
                closeTextInput(); contextMenu = null
                navigateAwayAndThen {
                    if (selectedSection == PanelSection.PROJECTS && fileBrowser.canGoUp()) {
                        fileBrowser.goUp()
                    } else {
                        selectedSection = null
                    }
                }
            }
        )

        // Справа: закрыть (сверху) и справка (снизу)
        val rightX = panelRight - navButtonSize - 8
        addRenderableWidget(
            FlatButton(rightX, topY, navButtonSize, navButtonSize, "X", centered = true) {
                onClose()
            }
        )
        addRenderableWidget(
            FlatButton(rightX, bottomY, navButtonSize, navButtonSize, "?", centered = true) {
                if (showDocumentation) closeDocumentation() else openDocumentation()
            }
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        trackEditorChanges()
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xE6161616.toInt())

        graphics.fill(panelLeft, panelTop, panelLeft + logoWidth, panelTop + headerHeight, 0xFF101010.toInt())
        drawLogo(graphics)

        graphics.fill(headerLeft, panelTop, panelRight, panelTop + headerHeight, 0xFF1E1E1E.toInt())
        val breadcrumb = "Панель управления -> " + (
                scriptEditorFile?.let { "Проекты -> ${it.name}" }
                    ?: selectedSection?.displayName
                    ?: "Главное меню"
                )
        graphics.text(
            font, breadcrumb,
            headerLeft + 8 + navButtonSize + 16, panelTop + (headerHeight - font.lineHeight) / 2,
            0xFFFFFFFF.toInt(), false
        )

        graphics.fill(panelLeft, sidebarTop, panelLeft + sidebarWidth, panelBottom, 0xFF141414.toInt())

        drawContent(graphics)

        drawVerticalDivider(graphics, headerLeft, panelTop, panelTop + headerHeight)
        drawVerticalDivider(graphics, headerLeft, panelTop, panelTop + headerHeight)
        drawVerticalDivider(graphics, headerLeft + 8 + navButtonSize + 8, panelTop, panelTop + headerHeight)
        drawHorizontalDivider(graphics, panelLeft, panelRight, panelTop + headerHeight)
        drawVerticalDivider(graphics, panelLeft + sidebarWidth, sidebarTop, panelBottom)
        drawHorizontalDivider(graphics, panelLeft, panelRight, panelTop + headerHeight)
        drawVerticalDivider(graphics, panelLeft + sidebarWidth, sidebarTop, panelBottom)

        drawVerticalDivider(graphics, headerLeft, panelTop, panelTop + headerHeight)
        drawVerticalDivider(graphics, headerLeft + 8 + navButtonSize + 8, panelTop, panelTop + headerHeight)
        drawVerticalDivider(graphics, panelRight - navButtonSize - 16, panelTop, panelTop + headerHeight)
        drawHorizontalDivider(graphics, panelLeft, panelRight, panelTop + headerHeight)
        drawVerticalDivider(graphics, panelLeft + sidebarWidth, sidebarTop, panelBottom)

        val overlayOpen = isBlockingOverlayOpen()
        super.extractRenderState(graphics, if (overlayOpen) -1 else mouseX, if (overlayOpen) -1 else mouseY, delta)

        drawContextMenu(graphics)
        if (closeConfirmVisible) drawCloseConfirmDialog(graphics, mouseX, mouseY, delta)
        if (deleteConfirmEntry != null) drawDeleteConfirmDialog(graphics, mouseX, mouseY, delta)
        if (showDocumentation) drawDocumentationOverlay(graphics)
        if (showScriptHints) drawScriptHintsOverlay(graphics)
    }

    private fun drawHorizontalDivider(graphics: GuiGraphicsExtractor, x1: Int, x2: Int, y: Int, color: Int = 0xFF3A3A3A.toInt()) {
        graphics.fill(x1, y, x2, y + 1, color)
    }

    private fun drawVerticalDivider(graphics: GuiGraphicsExtractor, x: Int, y1: Int, y2: Int, color: Int = 0xFF3A3A3A.toInt()) {
        graphics.fill(x, y1, x + 1, y2, color)
    }

    private fun drawLogo(graphics: GuiGraphicsExtractor) {
        val scriptText = "Script"
        val fxText = "FX"
        val startX = panelLeft + 10
        val startY = panelTop + 10

        graphics.text(font, scriptText, startX, startY, 0xFFFFFFFF.toInt(), false)
        graphics.text(font, fxText, startX + font.width(scriptText), startY, 0xFFB026FF.toInt(), false)
        graphics.fill(startX, startY + 12, panelLeft + logoWidth - 10, startY + 13, 0xFF555555.toInt())
        graphics.text(font, "Панель управления", startX, startY + 18, 0xFFAAAAAA.toInt(), false)
    }

    private fun contentRect(): Rect = Rect(contentLeft + 20, sidebarTop + 20, panelRight - 20, panelBottom - 20)

    private fun drawContent(graphics: GuiGraphicsExtractor) {
        val rect = contentRect()

        when (selectedSection) {
            PanelSection.PROJECTS -> {
                if (scriptEditorFile != null) {
                    fileRows = emptyList()
                    graphics.text(font, scriptEditorFile!!.name, rect.x1, rect.y1 - 10, 0xFFFFFFFF.toInt(), false)
                    drawScriptValidationStatus(graphics, rect)
                    drawSaveSuccessMessage(graphics, rect)
                    drawRunMessage(graphics)
                } else {
                    drawProjectsFileList(graphics, rect)
                }
            }
            PanelSection.LOGS -> drawLogsSection(graphics, rect)
            else -> {
                fileRows = emptyList()
                graphics.fill(rect.x1, rect.y1, rect.x2, rect.y2, 0xFF000000.toInt())
                val lines = selectedSection?.let { listOf("Раздел «${it.displayName}» — в разработке") }
                    ?: listOf(
                        "Это главное меню мода, для",
                        "работы с проектом откройте",
                        "соответствующий раздел,",
                        "который находится слева.",
                        "",
                        "←"
                    )
                var y = rect.y1 + (rect.y2 - rect.y1) / 2 - (lines.size * 6)
                for (line in lines) {
                    val lineWidth = font.width(line)
                    graphics.text(font, line, rect.x1 + (rect.x2 - rect.x1 - lineWidth) / 2, y, 0xFFAAAAAA.toInt(), false)
                    y += 12
                }
            }
        }
    }

    private fun drawScriptValidationStatus(graphics: GuiGraphicsExtractor, rect: Rect) {
        val box = scriptEditorBox ?: return
        val (ok, message) = validateScript(box.getValue())
        val color = if (ok) 0xFF55FF55.toInt() else 0xFFFFCC55.toInt()
        val textWidth = font.width(message)
        graphics.text(font, message, rect.x2 - textWidth, rect.y1 - 10, color, false)
    }

    private fun drawSaveSuccessMessage(graphics: GuiGraphicsExtractor, rect: Rect) {
        if (!showSaveSuccessMessage) return

        if (System.currentTimeMillis() >= saveSuccessMessageUntil) {
            showSaveSuccessMessage = false
            return
        }

        val button = scriptEditorSaveButton ?: return
        graphics.text(
            font,
            "Изменения сохранены!",
            button.x,
            button.y + button.height + 3,
            0xFF55FF55.toInt(),
            false
        )
    }

    private fun drawRunMessage(graphics: GuiGraphicsExtractor) {
        if (!showRunMessage) return

        if (System.currentTimeMillis() >= runMessageUntil) {
            showRunMessage = false
            return
        }

        val button = scriptEditorRunButton ?: return
        graphics.text(
            font,
            "Скрипт запущен!",
            button.x,
            button.y + button.height + 3,
            0xFF55FF55.toInt(),
            false
        )
    }

    private fun pluralizeCommands(n: Int): String {
        val mod100 = n % 100
        val mod10 = n % 10
        val word = when {
            mod100 in 11..14 -> "команд"
            mod10 == 1 -> "команда"
            mod10 in 2..4 -> "команды"
            else -> "команд"
        }
        return "$n $word"
    }

    private fun validateScript(text: String): Pair<Boolean, String> {
        if (text.isBlank()) return true to "Пустой скрипт"

        val commands = ScriptParser.parse(text)
        if (commands.isEmpty()) return true to "Пустой скрипт"

        commands.forEachIndexed { index, command ->
            val isTriggerHeader = index == 0 && command.name in TriggerParser.TRIGGER_NAMES
            if (!isTriggerHeader && !CommandRegistry.isKnown(command.name)) {
                return false to "⚠ Неизвестная команда '${command.name}' (строка ${command.lineNumber})"
            }
        }
        return true to "✓ Скрипт корректен (${pluralizeCommands(commands.size)})"
    }

    private fun drawProjectsFileList(graphics: GuiGraphicsExtractor, rect: Rect) {
        graphics.fill(rect.x1, rect.y1, rect.x2, rect.y2, 0xFF000000.toInt())

        val path = fileBrowser.breadcrumbPath()
        graphics.text(font, "/scriptfx/$path", rect.x1 + 6, rect.y1 + 4, 0xFF888888.toInt(), false)

        val rows = mutableListOf<FileRow>()
        var y = rect.y1 + 18
        val rowHeight = 16

        if (fileBrowser.canGoUp()) {
            val r = Rect(rect.x1 + 4, y, rect.x2 - 4, y + rowHeight)
            graphics.fill(r.x1, r.y1, r.x2, r.y2, 0xFF141414.toInt())
            graphics.text(font, "../", r.x1 + 6, y + 4, 0xFFAAAAAA.toInt(), false)
            rows.add(FileRow(null, r, isUp = true))
            y += rowHeight + 2
        }

        for (entry in fileBrowser.entries) {
            if (y + rowHeight > rect.y2) break
            val r = Rect(rect.x1 + 4, y, rect.x2 - 4, y + rowHeight)
            graphics.fill(r.x1, r.y1, r.x2, r.y2, 0xFF161616.toInt())
            val label = if (entry.isDirectory) "${entry.name}/" else entry.name
            graphics.text(font, label, r.x1 + 6, y + 4, 0xFFEDEDED.toInt(), false)
            rows.add(FileRow(entry, r))
            y += rowHeight + 2
        }

        if (fileBrowser.entries.isEmpty()) {
            val hint = when {
                fileBrowser.isAtProjectsRoot() -> "Пока нет ни одного проекта — ПКМ здесь, чтобы создать первый"
                fileBrowser.isInsideProjectScripts() -> "Пока нет скриптов — ПКМ здесь, чтобы создать скрипт-файл"
                else -> "Пусто — ПКМ здесь, чтобы создать файл или папку"
            }
            val hintWidth = font.width(hint)
            graphics.text(font, hint, rect.x1 + (rect.x2 - rect.x1 - hintWidth) / 2, y + 10, 0xFF666666.toInt(), false)
        }

        fileRows = rows
    }

    private fun openScriptFileFromEntry(entry: FileEntry) {
        if (entry.isDirectory) return
        if (entry.file.extension != "sfxs") return
        openScriptEditor(entry.file)
    }

    private fun openScriptEditor(file: File) {
        closeScriptEditor()

        showSaveSuccessMessage = false
        saveSuccessMessageUntil = 0L

        val rect = contentRect()
        val headerRowHeight = 16
        val bottomRowHeight = 24
        val editorTop = rect.y1 + headerRowHeight
        val editorHeight = (rect.y2 - editorTop) - bottomRowHeight - 4

        val box = MultiLineEditBox.Builder()
            .setX(rect.x1)
            .setY(editorTop)
            .setShowBackground(true)
            .build(font, rect.x2 - rect.x1, editorHeight, Component.literal("Редактор скрипта"))
        val content = file.readText()
        box.setValue(content)

        val closeBtn = FlatButton(rect.x2 - 20, rect.y1, 20, 16, "X") {
            navigateAwayAndThen { }
        }

        val saveBtn = FlatButton(rect.x1, editorTop + editorHeight + 4, 100, 20, "Сохранить") {
            file.writeText(box.getValue())
            scriptEditorOriginalContent = box.getValue()
            showSaveSuccessMessage = true
            saveSuccessMessageUntil = System.currentTimeMillis() + 3000L
        }

        val runBtn = FlatButton(rect.x1 + 108, editorTop + editorHeight + 4, 150, 20, "▶ Запустить скрипт") {
            runScriptFile(file)
            if (Minecraft.getInstance().hasSingleplayerServer()) {
                showRunMessage = true
                runMessageUntil = System.currentTimeMillis() + 3000L
            }
        }

        val hintsBtn = FlatButton(rect.x2 - 90, editorTop + editorHeight + 4, 90, 20, "Подсказки") {
            openScriptHints()
        }

        addRenderableWidget(box)
        addRenderableWidget(closeBtn)
        addRenderableWidget(saveBtn)
        addRenderableWidget(runBtn)
        addRenderableWidget(hintsBtn)

        scriptEditorFile = file
        scriptEditorBox = box
        scriptEditorOriginalContent = content
        undoStack.clear()
        lastEditorValue = content
        scriptEditorCloseButton = closeBtn
        scriptEditorSaveButton = saveBtn
        scriptEditorRunButton = runBtn
        scriptEditorHintsButton = hintsBtn
    }

    private fun closeScriptEditor() {
        scriptEditorBox?.let { removeWidget(it) }
        scriptEditorCloseButton?.let { removeWidget(it) }
        scriptEditorSaveButton?.let { removeWidget(it) }
        scriptEditorRunButton?.let { removeWidget(it) }
        scriptEditorHintsButton?.let { removeWidget(it) }
        scriptEditorBox = null
        scriptEditorCloseButton = null
        scriptEditorSaveButton = null
        scriptEditorRunButton = null
        scriptEditorHintsButton = null
        scriptEditorFile = null
        scriptEditorOriginalContent = null
        undoStack.clear()
        lastEditorValue = ""
        showRunMessage = false
        showScriptHints = false
        draggingScriptHintsScrollbar = false
    }

    private fun navigateAwayAndThen(action: () -> Unit) {
        if (scriptEditorFile != null && scriptEditorBox?.getValue() != scriptEditorOriginalContent) {
            pendingNavigationAction = action
            openCloseConfirmDialog()
        } else {
            closeScriptEditor()
            action()
        }
    }

    private fun runScriptFile(file: File) {
        val client = Minecraft.getInstance()
        if (!client.hasSingleplayerServer()) {
            client.player?.sendSystemMessage(
                Component.literal("Запуск скриптов пока доступен только в одиночной игре").withStyle(ChatFormatting.RED)
            )
            return
        }
        val server = client.getSingleplayerServer() ?: return
        val playerUuid = client.player?.uuid ?: return

        server.execute {
            val serverPlayer = server.playerList.getPlayer(playerUuid) ?: return@execute
            val commands = ScriptParser.parse(file.readText())
            ScriptManager.runAdHoc(commands, ScriptContext(server, serverPlayer), file.nameWithoutExtension)
        }
    }

    private fun closeConfirmBounds(): Rect {
        val rect = contentRect()
        val cx = rect.x1 + (rect.x2 - rect.x1) / 2
        val cy = rect.y1 + (rect.y2 - rect.y1) / 2
        val boxW = 280
        val boxH = 80
        return Rect(cx - boxW / 2, cy - boxH / 2, cx + boxW / 2, cy + boxH / 2)
    }

    private fun openCloseConfirmDialog() {
        setFocused(null)
        closeCloseConfirmDialog()
        val box = closeConfirmBounds()

        closeConfirmSaveButton = FlatButton(box.x1 + 10, box.y2 - 24, 125, 20, "Сохранить и закрыть") {
            scriptEditorFile?.writeText(scriptEditorBox?.getValue() ?: "")
            finishPendingNavigation()
        }
        closeConfirmDiscardButton = FlatButton(box.x2 - 135, box.y2 - 24, 125, 20, "Выйти без сохранения") {
            finishPendingNavigation()
        }
        closeConfirmVisible = true
    }

    private fun finishPendingNavigation() {
        closeCloseConfirmDialog()
        closeScriptEditor()
        val next = pendingNavigationAction
        pendingNavigationAction = null
        next?.invoke()
    }

    private fun closeCloseConfirmDialog() {
        closeConfirmSaveButton = null
        closeConfirmDiscardButton = null
        closeConfirmVisible = false
    }

    private fun drawCloseConfirmDialog(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val rect = contentRect()
        graphics.fill(rect.x1, rect.y1, rect.x2, rect.y2, 0x99000000.toInt())

        val box = closeConfirmBounds()
        graphics.fill(box.x1, box.y1, box.x2, box.y2, 0xFF1E1E1E.toInt())
        graphics.text(font, "Внимание!", box.x1 + 10, box.y1 + 8, 0xFFFF5555.toInt(), false)

        val fileName = scriptEditorFile?.name ?: ""
        val message = "Были внесены правки в файл $fileName, желаете сохранить изменения?"
        for ((i, line) in wrapText(message, box.x2 - box.x1 - 20).withIndex()) {
            graphics.text(font, line, box.x1 + 10, box.y1 + 22 + i * 10, 0xFFCCCCCC.toInt(), false)
        }

        closeConfirmSaveButton?.renderButton(graphics, mouseX, mouseY, delta)
        closeConfirmDiscardButton?.renderButton(graphics, mouseX, mouseY, delta)
    }

    private fun openDeleteConfirm(entry: FileEntry) {
        setFocused(null)
        closeDeleteConfirm()
        val box = closeConfirmBounds()
        deleteConfirmEntry = entry
        deleteConfirmYesButton = FlatButton(box.x1 + 10, box.y2 - 24, 125, 20, "Удалить") {
            fileBrowser.delete(entry)
            closeDeleteConfirm()
        }
        deleteConfirmNoButton = FlatButton(box.x2 - 135, box.y2 - 24, 125, 20, "Отмена") {
            closeDeleteConfirm()
        }
    }

    private fun closeDeleteConfirm() {
        deleteConfirmEntry = null
        deleteConfirmYesButton = null
        deleteConfirmNoButton = null
    }

    private fun drawDeleteConfirmDialog(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val entry = deleteConfirmEntry ?: return
        val rect = contentRect()
        graphics.fill(rect.x1, rect.y1, rect.x2, rect.y2, 0x99000000.toInt())

        val box = closeConfirmBounds()
        graphics.fill(box.x1, box.y1, box.x2, box.y2, 0xFF1E1E1E.toInt())
        graphics.text(font, "Внимание!", box.x1 + 10, box.y1 + 8, 0xFFFF5555.toInt(), false)

        val message = "Удалить \"${entry.name}\" безвозвратно?"
        for ((i, line) in wrapText(message, box.x2 - box.x1 - 20).withIndex()) {
            graphics.text(font, line, box.x1 + 10, box.y1 + 22 + i * 10, 0xFFCCCCCC.toInt(), false)
        }

        deleteConfirmYesButton?.renderButton(graphics, mouseX, mouseY, delta)
        deleteConfirmNoButton?.renderButton(graphics, mouseX, mouseY, delta)
    }

    private fun wrapText(text: String, maxWidth: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (font.width(candidate) > maxWidth && current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    private fun drawLogsSection(graphics: GuiGraphicsExtractor, rect: Rect) {
        graphics.fill(rect.x1, rect.y1, rect.x2, rect.y2, 0xFF000000.toInt())

        val clearRect = Rect(rect.x2 - 70, rect.y1 + 2, rect.x2 - 2, rect.y1 + 16)
        graphics.fill(clearRect.x1, clearRect.y1, clearRect.x2, clearRect.y2, 0xFF262626.toInt())
        graphics.text(font, "Очистить", clearRect.x1 + 4, clearRect.y1 + 3, 0xFFAAAAAA.toInt(), false)
        logsClearButtonRect = clearRect

        val entries = ScriptFXLog.snapshot()
        if (entries.isEmpty()) {
            graphics.text(
                font, "Пока пусто — здесь появятся сообщения во время выполнения скриптов",
                rect.x1 + 6, rect.y1 + 22, 0xFF666666.toInt(), false
            )
            return
        }

        val timeFormat = java.text.SimpleDateFormat("HH:mm:ss")
        val lineHeight = font.lineHeight + 2
        val visibleTop = rect.y1 + 22
        val visibleBottom = rect.y2 - 4

        var totalLines = 0
        val wrapped = entries.map { entry ->
            val color = when (entry.level) {
                ScriptFXLog.Level.INFO -> 0xFFAAAAAA.toInt()
                ScriptFXLog.Level.WARN -> 0xFFFFCC55.toInt()
                ScriptFXLog.Level.ERROR -> 0xFFFF5555.toInt()
            }
            val prefix = "[${timeFormat.format(java.util.Date(entry.time))}] "
            val lines = wrapText(prefix + entry.message, rect.x2 - rect.x1 - 12)
            totalLines += lines.size
            lines to color
        }

        val maxScroll = ((totalLines * lineHeight) - (visibleBottom - visibleTop)).coerceAtLeast(0)
        logsScrollOffset = logsScrollOffset.coerceIn(0, maxScroll)

        var y = visibleTop - logsScrollOffset
        for ((lines, color) in wrapped) {
            for (line in lines) {
                if (y in (visibleTop - lineHeight)..visibleBottom) {
                    graphics.text(font, line, rect.x1 + 6, y, color, false)
                }
                y += lineHeight
            }
        }
    }

    /** Отслеживает удаления текста в редакторе и сохраняет прошлые состояния для кнопки "Вернуть". */
    private fun trackEditorChanges() {
        val box = scriptEditorBox ?: return
        val current = box.getValue()
        if (current != lastEditorValue) {
            if (current.length < lastEditorValue.length) {
                undoStack.addLast(lastEditorValue)
                if (undoStack.size > 100) undoStack.removeFirst()
            }
            lastEditorValue = current
        }
    }

    private fun undoEditor() {
        val box = scriptEditorBox ?: return
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeLast()
        lastEditorValue = previous
        box.setValue(previous)
    }

    /** Эмулирует Ctrl+<клавиша> (Cmd на macOS) в редакторе. */
    private fun sendEditorShortcut(key: Int) {
        val box = scriptEditorBox ?: return
        setFocused(box)
        val isMac = System.getProperty("os.name").orEmpty().lowercase().contains("mac")
        val modifiers = if (isMac) 8 else 2 // 8 = Super (Cmd), 2 = Ctrl
        box.keyPressed(KeyEvent(key, 0, modifiers))
    }

    /** Удаляет выделенный текст, не трогая буфер обмена. Без выделения ничего не делает. */
    private fun deleteEditorSelection() {
        val clipboard = Minecraft.getInstance().keyboardHandler
        val saved = clipboard.getClipboard()
        sendEditorShortcut(88) // X - вырезать
        clipboard.setClipboard(saved)
    }

    private fun openEditorContextMenu(x: Int, y: Int) {
        val items = mutableListOf<ContextMenuItem>()
        items.add(ContextMenuItem("Вырезать") { sendEditorShortcut(88) })
        items.add(ContextMenuItem("Копировать") { sendEditorShortcut(67) })
        items.add(ContextMenuItem("Вставить") { sendEditorShortcut(86) })
        items.add(ContextMenuItem("Удалить") { deleteEditorSelection() })
        items.add(ContextMenuItem("Очистить всё") { scriptEditorBox?.setValue("") })
        if (undoStack.isNotEmpty()) {
            items.add(ContextMenuItem("Вернуть") { undoEditor() })
        }

        // Чтобы меню не вылезало за экран
        val menuHeight = items.size * 16
        val menuX = x.coerceAtMost(width - 160 - 4).coerceAtLeast(0)
        val menuY = y.coerceAtMost(height - menuHeight - 4).coerceAtLeast(0)
        contextMenu = ContextMenuInfo(menuX, menuY, items)
    }

    private fun openContextMenu(x: Int, y: Int, entry: FileEntry) {
        val items = mutableListOf<ContextMenuItem>()
        if (entry.isDirectory) {
            items.add(ContextMenuItem("Открыть папку") { fileBrowser.goInto(entry) })
        } else {
            items.add(ContextMenuItem("Открыть файл") { openScriptFileFromEntry(entry) })
        }
        items.add(ContextMenuItem("Переименовать") {
            openTextInput(x, y, entry.name) { newName -> fileBrowser.rename(entry, newName) }
        })
        items.add(ContextMenuItem("Удалить") { openDeleteConfirm(entry) })
        contextMenu = ContextMenuInfo(x, y, items)
    }

    private fun openBackgroundContextMenu(x: Int, y: Int) {
        val items = mutableListOf<ContextMenuItem>()

        when {
            fileBrowser.isAtProjectsRoot() -> {
                items.add(ContextMenuItem("Создать проект") {
                    openTextInput(x, y, "мой_проект") { name -> fileBrowser.createProject(name) }
                })
            }
            fileBrowser.isInsideProjectScripts() -> {
                items.add(ContextMenuItem("Создать скрипт-файл.sfxs") {
                    openTextInput(x, y, "новый_скрипт") { name -> fileBrowser.createScriptFile(name) }
                })
                items.add(ContextMenuItem("Создать папку") {
                    openTextInput(x, y, "новая_папка") { name -> fileBrowser.createFolder(name) }
                })
            }
            fileBrowser.isInsideProjectRoot() -> {
                items.add(ContextMenuItem("Создать файл") {
                    openTextInput(x, y, "новый_файл.txt") { name -> fileBrowser.createFile(name) }
                })
                items.add(ContextMenuItem("Создать папку") {
                    openTextInput(x, y, "новая_папка") { name -> fileBrowser.createFolder(name) }
                })
                items.add(ContextMenuItem("Создать проект") {
                    openTextInput(x, y, "мой_проект") { name -> fileBrowser.createProject(name) }
                })
            }
            else -> {
                items.add(ContextMenuItem("Создать файл") {
                    openTextInput(x, y, "новый_файл.txt") { name -> fileBrowser.createFile(name) }
                })
                items.add(ContextMenuItem("Создать папку") {
                    openTextInput(x, y, "новая_папка") { name -> fileBrowser.createFolder(name) }
                })
            }
        }

        items.add(ContextMenuItem("Обновить") { fileBrowser.refresh() })
        contextMenu = ContextMenuInfo(x, y, items)
    }

    private fun drawContextMenu(graphics: GuiGraphicsExtractor) {
        val menu = contextMenu ?: run { contextMenuRowRects = emptyList(); return }

        val itemHeight = 16
        val menuWidth = 160
        graphics.fill(menu.x, menu.y, menu.x + menuWidth, menu.y + menu.items.size * itemHeight, 0xFF1E1E1E.toInt())

        val rows = mutableListOf<Rect>()
        var y = menu.y
        for (item in menu.items) {
            val r = Rect(menu.x, y, menu.x + menuWidth, y + itemHeight)
            rows.add(r)
            graphics.text(font, item.label, menu.x + 6, y + 4, 0xFFEDEDED.toInt(), false)
            y += itemHeight
        }
        contextMenuRowRects = rows
    }

    private fun openTextInput(x: Int, y: Int, initialValue: String, onConfirm: (String) -> Unit) {
        closeTextInput()

        val box = EditBox(font, x, y, 160, 16, Component.literal(""))
        box.setValue(initialValue)
        addRenderableWidget(box)

        val confirm = FlatButton(x + 164, y - 2, 20, 20, "OK") {
            val value = box.getValue()
            closeTextInput()
            onConfirm(value)
        }
        addRenderableWidget(confirm)

        textInputBox = box
        textInputConfirmButton = confirm
    }

    private fun closeTextInput() {
        textInputBox?.let { removeWidget(it) }
        textInputConfirmButton?.let { removeWidget(it) }
        textInputBox = null
        textInputConfirmButton = null
    }

    private fun openDocumentation() {
        docsScrollOffset = 0
        draggingDocsScrollbar = false
        showDocumentation = true
        setFocused(null)
    }

    private fun closeDocumentation() {
        showDocumentation = false
        draggingDocsScrollbar = false
    }

    private fun drawDocumentationOverlay(graphics: GuiGraphicsExtractor) {
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xF5101010.toInt())

        val left = panelLeft + 20
        val top = panelTop + 20
        val right = panelRight - 20
        val bottom = panelBottom - 20

        graphics.text(font, "Документация ScriptFX", left, top, 0xFFFFFFFF.toInt(), false)

        val closeRect = Rect(right - 20, top - 5, right, top + 15)
        graphics.fill(closeRect.x1, closeRect.y1, closeRect.x2, closeRect.y2, 0xFF262626.toInt())
        graphics.text(
            font, "X",
            closeRect.x1 + (closeRect.x2 - closeRect.x1 - font.width("X")) / 2,
            closeRect.y1 + (closeRect.y2 - closeRect.y1 - font.lineHeight) / 2 + 1,
            0xFFFFFFFF.toInt(), false
        )
        docsCloseButtonRect = closeRect

        val scrollbarWidth = 6
        val scrollbarGap = 6
        val textRight = right - scrollbarWidth - scrollbarGap

        val visibleTop = top + 16
        val visibleBottom = bottom
        val lineStep = font.lineHeight + 2

        // Разбиваем документацию на строки
        val lines = mutableListOf<String>()
        for (paragraph in Documentation.TEXT.split("\n")) {
            if (paragraph.isBlank()) {
                lines.add("")
            } else {
                lines.addAll(wrapText(paragraph, textRight - left))
            }
        }

        val contentHeight = lines.size * lineStep + 2
        val viewportHeight = (visibleBottom - visibleTop).coerceAtLeast(1)
        val maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0)
        docsMaxScroll = maxScroll
        docsScrollOffset = docsScrollOffset.coerceIn(0, maxScroll)

        var y = visibleTop + 2 - docsScrollOffset
        for (line in lines) {
            if (line.isNotEmpty() && y >= visibleTop && y <= visibleBottom - font.lineHeight) {
                graphics.text(font, line, left, y, 0xFFCCCCCC.toInt(), false)
            }
            y += lineStep
        }

        // Полоса прокрутки
        if (maxScroll > 0) {
            val trackLeft = textRight + scrollbarGap
            val trackTop = visibleTop
            val trackBottom = visibleBottom
            val trackHeight = trackBottom - trackTop

            graphics.fill(trackLeft, trackTop, right, trackBottom, 0xFF1A1A1A.toInt())

            val thumbHeight = ((trackHeight.toFloat() * viewportHeight) / contentHeight)
                .toInt()
                .coerceIn(12, trackHeight)
            val maxThumbTravel = (trackHeight - thumbHeight).coerceAtLeast(0)
            val scrollRatio = docsScrollOffset.toFloat() / maxScroll
            val thumbTop = trackTop + (maxThumbTravel * scrollRatio).toInt().coerceIn(0, maxThumbTravel)

            graphics.fill(trackLeft, thumbTop, right, thumbTop + thumbHeight, 0xFF7A3FC0.toInt())

            docsScrollbarTrackRect = Rect(trackLeft, trackTop, right, trackBottom)
            docsScrollbarThumbRect = Rect(trackLeft, thumbTop, right, thumbTop + thumbHeight)
        } else {
            docsScrollbarTrackRect = null
            docsScrollbarThumbRect = null
        }
    }

    private fun drawScriptHintsOverlay(graphics: GuiGraphicsExtractor) {
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xFF101010.toInt())

        val left = panelLeft + 20
        val top = panelTop + 20
        val right = panelRight - 20
        val bottom = panelBottom - 20

        graphics.text(font, "Подсказки — клик добавляет команду в конец скрипта", left, top, 0xFFFFFFFF.toInt(), false)

        val closeRect = Rect(right - 20, top - 4, right, top + 12)
        graphics.fill(closeRect.x1, closeRect.y1, closeRect.x2, closeRect.y2, 0xFF262626.toInt())
        graphics.text(font, "X", closeRect.x1 + 6, closeRect.y1 + 2, 0xFFFFFFFF.toInt(), false)
        scriptHintsCloseButtonRect = closeRect

        val scrollbarWidth = 6
        val scrollbarGap = 6
        val listRight = right - scrollbarWidth - scrollbarGap

        val rowHeight = 30
        val visibleTop = top + 20
        val visibleBottom = bottom

        val totalHeight = CommandDocs.ALL.size * rowHeight
        val viewportHeight = (visibleBottom - visibleTop).coerceAtLeast(1)
        val maxScroll = (totalHeight - viewportHeight).coerceAtLeast(0)
        scriptHintsScrollOffset = scriptHintsScrollOffset.coerceIn(0, maxScroll)

        val rows = mutableListOf<Rect>()
        var y = visibleTop - scriptHintsScrollOffset
        for (doc in CommandDocs.ALL) {
            val rowBottom = y + rowHeight - 2
            if (y >= visibleTop && rowBottom <= visibleBottom) {
                graphics.fill(left, y, listRight, rowBottom, 0xFF161616.toInt())
                graphics.text(font, doc.template, left + 6, y + 3, 0xFFB026FF.toInt(), false)
                graphics.text(font, doc.description, left + 6, y + 3 + font.lineHeight + 1, 0xFFAAAAAA.toInt(), false)
                rows.add(Rect(left, y, listRight, rowBottom))
            } else {
                rows.add(Rect(0, 0, 0, 0))
            }
            y += rowHeight
        }
        scriptHintsRowRects = rows
        scriptHintsMaxScroll = maxScroll

        if (maxScroll > 0) {
            val trackLeft = listRight + scrollbarGap
            val trackTop = visibleTop
            val trackBottom = visibleBottom
            val trackHeight = trackBottom - trackTop

            graphics.fill(trackLeft, trackTop, right, trackBottom, 0xFF1A1A1A.toInt())

            val thumbHeight = ((trackHeight.toFloat() * viewportHeight) / totalHeight)
                .toInt()
                .coerceIn(12, trackHeight)
            val maxThumbTravel = (trackHeight - thumbHeight).coerceAtLeast(0)
            val scrollRatio = scriptHintsScrollOffset.toFloat() / maxScroll
            val thumbTop = trackTop + (maxThumbTravel * scrollRatio).toInt().coerceIn(0, maxThumbTravel)

            graphics.fill(trackLeft, thumbTop, right, thumbTop + thumbHeight, 0xFF7A3FC0.toInt())

            scriptHintsScrollbarTrackRect = Rect(trackLeft, trackTop, right, trackBottom)
            scriptHintsScrollbarThumbRect = Rect(trackLeft, thumbTop, right, thumbTop + thumbHeight)
        } else {
            scriptHintsScrollbarTrackRect = null
            scriptHintsScrollbarThumbRect = null
        }
    }

    private fun scrollFromThumb(track: Rect, thumb: Rect, mouseY: Int, grabOffset: Int, maxScroll: Int): Int {
        val thumbHeight = thumb.y2 - thumb.y1
        val travel = (track.y2 - track.y1 - thumbHeight).coerceAtLeast(1)
        val newThumbTop = (mouseY - grabOffset).coerceIn(track.y1, track.y2 - thumbHeight)
        val ratio = (newThumbTop - track.y1).toFloat() / travel
        return (ratio * maxScroll).toInt().coerceIn(0, maxScroll)
    }

    private fun isDialogOpen(): Boolean = closeConfirmVisible || deleteConfirmEntry != null

    private fun isBlockingOverlayOpen(): Boolean = showScriptHints || showDocumentation || isDialogOpen()

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()

        // Обработка документации (модальное окно)
        if (showDocumentation) {
            docsCloseButtonRect?.let {
                if (it.contains(mx, my)) {
                    closeDocumentation()
                    return true
                }
            }

            docsScrollbarThumbRect?.let { thumb ->
                if (thumb.contains(mx, my)) {
                    draggingDocsScrollbar = true
                    docsDragGrabOffsetY = my - thumb.y1
                    return true
                }
            }

            docsScrollbarTrackRect?.let { track ->
                if (track.contains(mx, my) && docsMaxScroll > 0) {
                    val thumbHeight = docsScrollbarThumbRect?.let { it.y2 - it.y1 } ?: 12
                    docsScrollOffset = scrollFromThumb(track, docsScrollbarThumbRect ?: track, my, thumbHeight / 2, docsMaxScroll)
                    draggingDocsScrollbar = true
                    docsDragGrabOffsetY = thumbHeight / 2
                    return true
                }
            }
            return true
        }

        // Обработка подсказок (модальное окно)
        if (showScriptHints) {
            setFocused(null)

            scriptHintsCloseButtonRect?.let {
                if (it.contains(mx, my)) {
                    closeScriptHints()
                    return true
                }
            }

            scriptHintsScrollbarThumbRect?.let { thumb ->
                if (thumb.contains(mx, my)) {
                    draggingScriptHintsScrollbar = true
                    scriptHintsDragGrabOffsetY = my - thumb.y1
                    return true
                }
            }

            scriptHintsScrollbarTrackRect?.let { track ->
                if (track.contains(mx, my) && scriptHintsMaxScroll > 0) {
                    val thumbHeight = scriptHintsScrollbarThumbRect?.let { it.y2 - it.y1 } ?: 12
                    val travel = (track.y2 - track.y1 - thumbHeight).coerceAtLeast(1)
                    val newThumbTop = (my - thumbHeight / 2).coerceIn(track.y1, track.y2 - thumbHeight)
                    val ratio = (newThumbTop - track.y1).toFloat() / travel
                    scriptHintsScrollOffset = (ratio * scriptHintsMaxScroll).toInt().coerceIn(0, scriptHintsMaxScroll)
                    draggingScriptHintsScrollbar = true
                    scriptHintsDragGrabOffsetY = thumbHeight / 2
                    return true
                }
            }

            val rowIndex = scriptHintsRowRects.indexOfFirst { it.contains(mx, my) }
            if (rowIndex >= 0) {
                val doc = CommandDocs.ALL[rowIndex]
                val current = scriptEditorBox?.getValue() ?: ""
                val separator = if (current.isEmpty() || current.endsWith("\n")) "" else "\n"
                scriptEditorBox?.setValue(current + separator + doc.template + "\n")
                closeScriptHints()
            }
            return true
        }

        // Обработка диалога подтверждения закрытия
        if (closeConfirmVisible) {
            val save = closeConfirmSaveButton
            val discard = closeConfirmDiscardButton
            when {
                save != null && mx >= save.x && mx < save.x + save.width && my >= save.y && my < save.y + save.height ->
                    save.onClick(event, doubleClick)
                discard != null && mx >= discard.x && mx < discard.x + discard.width && my >= discard.y && my < discard.y + discard.height ->
                    discard.onClick(event, doubleClick)
            }
            return true
        }

        // Обработка диалога подтверждения удаления
        deleteConfirmEntry?.let {
            val yes = deleteConfirmYesButton
            val no = deleteConfirmNoButton
            when {
                yes != null && mx >= yes.x && mx < yes.x + yes.width && my >= yes.y && my < yes.y + yes.height ->
                    yes.onClick(event, doubleClick)
                no != null && mx >= no.x && mx < no.x + no.width && my >= no.y && my < no.y + no.height ->
                    no.onClick(event, doubleClick)
            }
            return true
        }

        // Обработка раздела логов
        if (selectedSection == PanelSection.LOGS) {
            logsClearButtonRect?.let { if (it.contains(mx, my)) { ScriptFXLog.clear(); return true } }
        }

        // Контекстное меню (обрабатываем первым, чтобы клики по пунктам не попадали в строки файлов)
        contextMenu?.let { menu ->
            val rowIndex = contextMenuRowRects.indexOfFirst { r -> r.contains(mx, my) }
            contextMenu = null
            if (rowIndex >= 0) {
                menu.items[rowIndex].action.invoke()
            }
            return true
        }

        // Пока открыто поле ввода имени - клики отдаём обычным виджетам (поле, кнопка OK, боковые кнопки)
        if (textInputBox != null) {
            return super.mouseClicked(event, doubleClick)
        }

        // ПКМ в редакторе скриптов - контекстное меню редактора
        if (selectedSection == PanelSection.PROJECTS && scriptEditorFile != null && event.button() == 1) {
            val box = scriptEditorBox
            if (box != null && mx >= box.x && mx < box.x + box.width && my >= box.y && my < box.y + box.height) {
                openEditorContextMenu(mx, my)
                return true
            }
        }

        // Обработка раздела проектов
        if (selectedSection == PanelSection.PROJECTS && scriptEditorFile == null) {
            val button = event.button()
            val row = fileRows.firstOrNull { it.rect.contains(mx, my) }

            if (row != null) {
                val entry = row.entry
                when {
                    // ЛКМ по "../" - родительская папка
                    button == 0 && row.isUp -> {
                        fileBrowser.goUp()
                        return true
                    }
                    // ЛКМ по папке - открыть
                    button == 0 && entry != null && entry.isDirectory -> {
                        fileBrowser.goInto(entry)
                        return true
                    }
                    // Двойной ЛКМ по .sfxs - открыть в редакторе
                    button == 0 && entry != null && entry.file.extension == "sfxs" -> {
                        val now = System.currentTimeMillis()
                        val isDouble = doubleClick || (lastClickedFile == entry.file && now - lastClickTime < 500)
                        if (isDouble) {
                            lastClickedFile = null
                            openScriptEditor(entry.file)
                        } else {
                            lastClickedFile = entry.file
                            lastClickTime = now
                        }
                        return true
                    }
                    // ПКМ по файлу или папке - контекстное меню
                    button == 1 && entry != null -> {
                        openContextMenu(mx, my, entry)
                        return true
                    }
                }
            }

            // ПКМ по пустой области - меню создания
            if (button == 1 && contentRect().contains(mx, my)) {
                openBackgroundContextMenu(mx, my)
                return true
            }
        }

        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (isDialogOpen()) return true
        if (showDocumentation) {
            docsScrollOffset = (docsScrollOffset - (scrollY * 12).toInt()).coerceIn(0, docsMaxScroll)
            return true
        }
        if (showScriptHints) {
            scriptHintsScrollOffset = (scriptHintsScrollOffset - (scrollY * 12).toInt()).coerceAtLeast(0)
            return true
        }
        if (selectedSection == PanelSection.LOGS) {
            logsScrollOffset -= (scrollY * 12).toInt()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (draggingDocsScrollbar) {
            val track = docsScrollbarTrackRect
            val thumb = docsScrollbarThumbRect
            if (track != null && thumb != null && docsMaxScroll > 0) {
                docsScrollOffset = scrollFromThumb(track, thumb, event.y().toInt(), docsDragGrabOffsetY, docsMaxScroll)
            }
            return true
        }
        if (draggingScriptHintsScrollbar) {
            val track = scriptHintsScrollbarTrackRect
            val thumb = scriptHintsScrollbarThumbRect
            if (track != null && thumb != null && scriptHintsMaxScroll > 0) {
                val thumbHeight = thumb.y2 - thumb.y1
                val travel = (track.y2 - track.y1 - thumbHeight).coerceAtLeast(1)
                val my = event.y().toInt()
                val newThumbTop = (my - scriptHintsDragGrabOffsetY).coerceIn(track.y1, track.y2 - thumbHeight)
                val ratio = (newThumbTop - track.y1).toFloat() / travel
                scriptHintsScrollOffset = (ratio * scriptHintsMaxScroll).toInt().coerceIn(0, scriptHintsMaxScroll)
            }
            return true
        }
        if (isBlockingOverlayOpen()) return true
        return super.mouseDragged(event, dragX, dragY)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (draggingDocsScrollbar) {
            draggingDocsScrollbar = false
            return true
        }
        if (draggingScriptHintsScrollbar) {
            draggingScriptHintsScrollbar = false
            return true
        }
        if (isBlockingOverlayOpen()) return true
        return super.mouseReleased(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (isBlockingOverlayOpen()) {
            if (event.key() == 256) dismissTopOverlay() // 256 = Esc
            return true
        }
        if (event.key() == 90 && event.hasControlDown() && scriptEditorBox?.isFocused == true) { // Ctrl+Z
            undoEditor()
            return true
        }
        return super.keyPressed(event)
    }

    private fun dismissTopOverlay() {
        when {
            deleteConfirmEntry != null -> closeDeleteConfirm()
            closeConfirmVisible -> {
                closeCloseConfirmDialog()
                pendingNavigationAction = null
            }
            showScriptHints -> closeScriptHints()
            showDocumentation -> closeDocumentation()
        }
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (isBlockingOverlayOpen()) return true
        return super.charTyped(event)
    }
}