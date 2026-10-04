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
import java.net.URI
import net.fabricmc.loader.api.FabricLoader

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

    private data class DocumentationSubsection(
        val name: String
    )

    private data class DocumentationSection(
        val name: String,
        val children: List<DocumentationSubsection> = emptyList()
    )

    private data class DocumentationSectionHit(
        val rect: Rect,
        val section: String
    )

    private data class DocumentationExpandHit(
        val rect: Rect,
        val section: String
    )

    private data class DocumentationSubsectionHit(
        val rect: Rect,
        val section: String,
        val subsection: String
    )

    private data class FileRow(val entry: FileEntry?, val rect: Rect, val isUp: Boolean = false)
    private data class ContextMenuItem(val label: String, val enabled: Boolean = true, val action: () -> Unit)
    private data class ContextMenuInfo(val x: Int, val y: Int, val items: List<ContextMenuItem>)
    private data class SelPos(val line: Int, val col: Int)

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

    private var documentationSection = "Главное меню"
    private var documentationSubsection = "Credits"

    private var documentationScrollOffset = 0
    private var documentationMaxScroll = 0

    private val expandedDocumentationSections =
        mutableSetOf<String>()

    private val documentationHistory =
        mutableListOf<Pair<String, String>>()

    private var documentationHistoryIndex = -1

    private var documentationContent =
        emptyList<String>()

    private var docsCloseButtonRect: Rect? = null
    private var docMouseX = -1
    private var docMouseY = -1

    private var documentationHomeButtonRect: Rect? = null
    private var documentationBackButtonRect: Rect? = null

    private var documentationTelegramRect: Rect? = null
    private var documentationYouTubeRect: Rect? = null

    private var documentationSectionHits =
        emptyList<DocumentationSectionHit>()

    private var documentationExpandHits =
        emptyList<DocumentationExpandHit>()

    private var documentationSubsectionHits =
        emptyList<DocumentationSubsectionHit>()

    private var documentationTabRects =
        emptyList<Rect>()

    private var documentationScrollbarTrackRect: Rect? = null
    private var documentationScrollbarThumbRect: Rect? = null

    private var draggingDocumentationScrollbar = false
    private var documentationScrollbarDragGrabOffsetY = 0
    private var docsScrollbarTrackRect: Rect? = null
    private var docsScrollbarThumbRect: Rect? = null
    private var docsMaxScroll = 0
    private var draggingDocsScrollbar = false
    private var docsDragGrabOffsetY = 0
    private var docContentArea: Rect? = null

    private var docWrappedLines: List<String> = emptyList()
    private var docLineContinues: List<Boolean> = emptyList()
    private var docTextArea: Rect? = null
    private var docTextLeftX = 0
    private var docFirstLineY = 0
    private var docLineStep = 1
    private var docSelAnchor: SelPos? = null
    private var docSelCaret: SelPos? = null
    private var draggingDocSelection = false

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

    private val documentationSections = listOf(
        DocumentationSection(
            name = "Главное меню",
            children = listOf(
                DocumentationSubsection("Credits")
            )
        ),

        DocumentationSection(
            name = "Скрипты",
            children = listOf(
                DocumentationSubsection("Переменные"),
                DocumentationSubsection("Глобальные переменные"),
                DocumentationSubsection("Для сюжета"),
                DocumentationSubsection("Для камеры"),
                DocumentationSubsection("Катсцены")
            )
        ),

        DocumentationSection(
            name = "Триггеры"
        )
    )

    private fun openScriptHints() {
        showScriptHints = true
        scriptHintsScrollOffset = 0
        draggingScriptHintsScrollbar = false
        setFocused(null)
    }

    private fun closeScriptHints() {
        showScriptHints = false
        draggingScriptHintsScrollbar = false

        scriptEditorBox?.let {
            if (!children().contains(it)) {
                addRenderableWidget(it)
            }
        }

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
                            if (FabricLoader.getInstance().isModLoaded("cloth-config")) {
                                Minecraft.getInstance().setScreen(ScriptFXConfigScreen.build(this))
                            } else {
                                ScriptFXLog.warn("Настройки недоступны: не установлен Cloth Config. Значения можно править в config/scriptfx.json")
                            }
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

    /**
     * Ранний обработчик мыши документации. Вызывается в самом начале mouseClicked,
     * поэтому порядок остальных проверок в функции на него не влияет.
     * Возвращает true, если клик полностью обработан.
     */
    private fun handleDocumentationMouse(mx: Int, my: Int, button: Int): Boolean {
        if (showScriptHints || isDialogOpen()) return false

        // Новое нажатие означает, что любая прошлая протяжка выделения закончилась
        draggingDocSelection = false

        // 1) Меню открыто: клик по пункту выполняет его, клик мимо закрывает меню
        val menu = contextMenu
        if (menu != null) {
            val rowIndex = contextMenuRowRects.indexOfFirst { it.contains(mx, my) }
            contextMenu = null
            if (rowIndex >= 0) {
                val item = menu.items[rowIndex]
                if (item.enabled) item.action.invoke()
                return true
            }
        }

        // 2) ПКМ только открывает меню: не выделяет и не нажимает кнопки
        if (button == 1) {
            val area = docContentArea
            if (area != null && area.contains(mx, my)) {
                openDocumentationContextMenu(mx, my)
                ScriptFXLog.info("Меню документации открыто: x=$mx y=$my") // временно
            }
            return true
        }

        // 3) Любые кнопки, кроме левой, документации не нужны
        return button != 0
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

        drawContextMenu(graphics, mouseX, mouseY)
        if (closeConfirmVisible) drawCloseConfirmDialog(graphics, mouseX, mouseY, delta)
        if (deleteConfirmEntry != null) drawDeleteConfirmDialog(graphics, mouseX, mouseY, delta)
        if (showScriptHints) drawScriptHintsOverlay(graphics)

        if (showDocumentation) drawDocumentationOverlay(graphics, mouseX, mouseY)
        if (showDocumentation) drawContextMenu(graphics, mouseX, mouseY)   // ← должна быть эта строка
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

    /** Обрезает текст с "..." так, чтобы он помещался в maxWidth пикселей. */
    private fun fitText(text: String, maxWidth: Int): String {
        if (font.width(text) <= maxWidth) return text

        val ellipsis = "..."
        var end = text.length
        while (end > 0 && font.width(text.substring(0, end) + ellipsis) > maxWidth) {
            end--
        }
        return text.substring(0, end).trimEnd() + ellipsis
    }

    private fun drawLogsSection(graphics: GuiGraphicsExtractor, rect: Rect) {
        graphics.fill(rect.x1, rect.y1, rect.x2, rect.y2, 0xFF000000.toInt())

        val clearRect = Rect(rect.x2 - 70, rect.y1 + 2, rect.x2 - 2, rect.y1 + 16)
        graphics.fill(clearRect.x1, clearRect.y1, clearRect.x2, clearRect.y2, 0xFF262626.toInt())
        graphics.text(font, "Очистить", clearRect.x1 + 4, clearRect.y1 + 3, 0xFFAAAAAA.toInt(), false)
        logsClearButtonRect = clearRect

        // Разделительная черта между кнопкой и зоной логов.
        drawHorizontalDivider(graphics, rect.x1, rect.x2, rect.y1 + 19)

        // Верхняя граница зоны логов (под разделителем).
        val logsTop = rect.y1 + 24

        val entries = ScriptFXLog.snapshot()
        if (entries.isEmpty()) {
            graphics.text(
                font, "Пока пусто — здесь появятся сообщения во время выполнения скриптов",
                rect.x1 + 6, logsTop, 0xFF666666.toInt(), false
            )
            return
        }

        val timeFormat = java.text.SimpleDateFormat("HH:mm:ss")
        val lineHeight = font.lineHeight + 2
        val visibleTop = logsTop
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

        // Обрезаем вывод по зоне логов, чтобы прокручиваемые строки не залезали на разделитель и кнопку.
        graphics.enableScissor(rect.x1, visibleTop, rect.x2, rect.y2)

        var y = visibleTop - logsScrollOffset
        for ((lines, color) in wrapped) {
            for (line in lines) {
                if (y in (visibleTop - lineHeight)..visibleBottom) {
                    graphics.text(font, line, rect.x1 + 6, y, color, false)
                }
                y += lineHeight
            }
        }

        graphics.disableScissor()
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
                items.add(ContextMenuItem("Создать скриптовый файл") {
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

    private fun drawContextMenu(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val menu = contextMenu ?: run { contextMenuRowRects = emptyList(); return }

        val itemHeight = 16
        val menuWidth = 160
        val bottom = menu.y + menu.items.size * itemHeight

        graphics.fill(menu.x - 1, menu.y - 1, menu.x + menuWidth + 1, bottom + 1, 0xFF3A3A3A.toInt())
        graphics.fill(menu.x, menu.y, menu.x + menuWidth, bottom, 0xFF1E1E1E.toInt())

        val rows = mutableListOf<Rect>()
        var y = menu.y
        for (item in menu.items) {
            val r = Rect(menu.x, y, menu.x + menuWidth, y + itemHeight)
            rows.add(r)

            if (item.enabled && r.contains(mouseX, mouseY)) {
                graphics.fill(r.x1, r.y1, r.x2, r.y2, 0xFF353535.toInt())
            }

            graphics.text(
                font,
                item.label,
                menu.x + 6,
                y + (itemHeight - font.lineHeight) / 2 + 1,
                if (item.enabled) 0xFFEDEDED.toInt() else 0xFF6A6A6A.toInt(),
                false
            )
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
        documentationSection = "Главное меню"
        documentationSubsection = "Credits"

        documentationScrollOffset = 0
        documentationMaxScroll = 0

        documentationHistory.clear()
        documentationHistoryIndex = -1

        expandedDocumentationSections.clear()

        documentationContent = emptyList()

        draggingDocumentationScrollbar = false

        showDocumentation = true

        setFocused(null)

        resetDocSelection()
    }

    private fun closeDocumentation() {
        showDocumentation = false

        draggingDocumentationScrollbar = false

        documentationScrollbarTrackRect = null
        documentationScrollbarThumbRect = null

        documentationSectionHits = emptyList()
        documentationExpandHits = emptyList()
        documentationSubsectionHits = emptyList()
        documentationTabRects = emptyList()

        setFocused(null)
        resetDocSelection()
    }

    private fun openDocumentationPage(
        section: String,
        subsection: String,
        addToHistory: Boolean = true
    ) {
        if (
            addToHistory &&
            (documentationSection != section || documentationSubsection != subsection)
        ) {
            while (documentationHistory.size > documentationHistoryIndex + 1) {
                documentationHistory.removeAt(documentationHistory.lastIndex)
            }

            documentationHistory.add(documentationSection to documentationSubsection)
            documentationHistoryIndex = documentationHistory.lastIndex
        }

        documentationSection = section
        documentationSubsection = subsection
        documentationScrollOffset = 0

        documentationContent = Documentation.page(section, subsection)

        resetDocSelection()
    }

    private fun goDocumentationHome() {
        openDocumentationPage(
            "Главное меню",
            "Credits"
        )
    }

    private fun goDocumentationBack() {
        if (documentationHistoryIndex < 0) {
            return
        }

        val previous =
            documentationHistory[
                documentationHistoryIndex
            ]

        documentationHistoryIndex--

        openDocumentationPage(
            previous.first,
            previous.second,
            addToHistory = false
        )
    }

    private fun loadDocumentationFile(
        fileName: String
    ): List<String> {
        val path =
            "/scriptfx/documentation/$fileName"

        return try {
            ControlPanelScreen::class.java
                .getResourceAsStream(path)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { reader ->
                    reader.readLines()
                }
                ?: listOf(
                    "Не удалось загрузить документацию.",
                    "",
                    "Файл не найден:",
                    fileName
                )
        } catch (e: Exception) {
            ScriptFXLog.error(
                "Не удалось загрузить файл документации $fileName: ${e.message}"
            )

            listOf(
                "Ошибка загрузки документации.",
                "",
                fileName
            )
        }
    }

    private fun drawDocumentationOverlay(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int
    ) {
        docMouseX = mouseX
        docMouseY = mouseY

        // Полностью непрозрачная подложка: нижний слой больше не просвечивает
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xFF101010.toInt())

        val left = panelLeft + 20
        val right = panelRight - 20
        val top = panelTop + 16
        val bottom = panelBottom - 16

        drawDocumentationHeader(graphics, left, top, right)

        val contentTop = top + 24 + 10
        val contentBottom = bottom - 24 - 8

        val sidebarWidth = ((right - left) * 0.28f).toInt()
        val separatorX = left + sidebarWidth

        drawDocumentationSidebar(graphics, left, contentTop, separatorX, contentBottom)

        graphics.fill(separatorX, contentTop, separatorX + 1, contentBottom, 0xFF444444.toInt())

        drawDocumentationContent(graphics, separatorX + 1, contentTop, right, contentBottom)

        drawDocumentationBottomButtons(graphics, left, top, right, bottom)
    }

    private fun drawDocumentationHeader(
        graphics: GuiGraphicsExtractor,
        left: Int,
        top: Int,
        right: Int
    ) {
        val headerHeight = 24
        val bottom = top + headerHeight

        graphics.fill(left, top, right, bottom, 0xFF161616.toInt())
        graphics.fill(left, bottom, right, bottom + 1, 0xFF3A3A3A.toInt())

        val title = "Документация мода ScriptFX"
        graphics.text(
            font,
            title,
            left + (right - left - font.width(title)) / 2,
            top + (headerHeight - font.lineHeight) / 2 + 1,
            0xFFFFFFFF.toInt(),
            false
        )

        val closeRect = Rect(right - headerHeight, top, right, bottom)
        val hovered = closeRect.contains(docMouseX, docMouseY)

        graphics.fill(
            closeRect.x1, closeRect.y1, closeRect.x2, closeRect.y2,
            if (hovered) 0xFF8A2A2A.toInt() else 0xFF262626.toInt()
        )
        drawCloseIcon(
            graphics,
            (closeRect.x1 + closeRect.x2) / 2,
            (closeRect.y1 + closeRect.y2) / 2,
            0xFFFFFFFF.toInt()
        )

        docsCloseButtonRect = closeRect
    }

    private fun drawDocumentationSidebar(
        graphics: GuiGraphicsExtractor,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        graphics.fill(
            left,
            top,
            right,
            bottom,
            0xFF111111.toInt()
        )

        val sectionHits =
            mutableListOf<DocumentationSectionHit>()

        val expandHits =
            mutableListOf<DocumentationExpandHit>()

        val subsectionHits =
            mutableListOf<DocumentationSubsectionHit>()

        var y = top + 6

        for (section in documentationSections) {

            if (y + 22 > bottom) {
                break
            }

            val row =
                Rect(
                    left + 6,
                    y,
                    right - 6,
                    y + 22
                )

            val selected =
                documentationSection == section.name

            graphics.fill(
                row.x1,
                row.y1,
                row.x2,
                row.y2,
                if (selected)
                    0xFF303030.toInt()
                else
                    0xFF181818.toInt()
            )

            graphics.text(
                font,
                section.name,
                row.x1 + 7,
                row.y1 + 6,
                0xFFEDEDED.toInt(),
                false
            )

            sectionHits.add(
                DocumentationSectionHit(
                    row,
                    section.name
                )
            )

            if (section.children.isNotEmpty()) {

                val expanded =
                    expandedDocumentationSections
                        .contains(section.name)

                val expandRect =
                    Rect(
                        row.x2 - 24,
                        row.y1,
                        row.x2,
                        row.y2
                    )

                drawPixelArrow(
                    graphics,
                    (expandRect.x1 + expandRect.x2) / 2,
                    (expandRect.y1 + expandRect.y2) / 2,
                    expanded,
                    0xFFFFFFFF.toInt()
                )

                expandHits.add(
                    DocumentationExpandHit(
                        expandRect,
                        section.name
                    )
                )

                y += 24

                if (expanded) {

                    for (child in section.children) {

                        if (y + 20 > bottom) {
                            break
                        }

                        val childRect =
                            Rect(
                                left + 18,
                                y,
                                right - 6,
                                y + 20
                            )

                        val childSelected =
                            documentationSection ==
                                    section.name &&
                                    documentationSubsection ==
                                    child.name

                        graphics.fill(
                            childRect.x1,
                            childRect.y1,
                            childRect.x2,
                            childRect.y2,
                            if (childSelected)
                                0xFF353535.toInt()
                            else
                                0xFF151515.toInt()
                        )

                        graphics.text(
                            font,
                            child.name,
                            childRect.x1 + 6,
                            childRect.y1 + 5,
                            0xFFCCCCCC.toInt(),
                            false
                        )

                        subsectionHits.add(
                            DocumentationSubsectionHit(
                                childRect,
                                section.name,
                                child.name
                            )
                        )

                        y += 22
                    }
                }
            } else {
                y += 24
            }
        }

        documentationSectionHits = sectionHits
        documentationExpandHits = expandHits
        documentationSubsectionHits = subsectionHits
    }

    private fun drawDocumentationContent(
        graphics: GuiGraphicsExtractor,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        graphics.fill(left, top, right, bottom, 0xFF0D0D0D.toInt())

        // Хит-боксы живут ровно один кадр: страница сама заполнит те, что ей нужны.
        // Иначе невидимые кнопки с прошлой страницы продолжают ловить клики.
        documentationTabRects = emptyList()
        documentationTelegramRect = null
        documentationYouTubeRect = null
        documentationScrollbarTrackRect = null
        documentationScrollbarThumbRect = null
        docTextArea = null
        docContentArea = Rect(left, top, right, bottom)

        if (documentationSection == "Главное меню" && documentationSubsection == "Credits") {
            drawDocumentationCredits(graphics, left, top, right, bottom)
            return
        }

        if (documentationSection == "Скрипты" && documentationSubsection == "Скрипты") {
            drawScriptsStartPage(graphics, left, top, right, bottom)
            return
        }

        drawDocumentationText(graphics, left, top, right, bottom)
    }

    private fun drawDocumentationCredits(
        graphics: GuiGraphicsExtractor,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        val lineStep = font.lineHeight + 4
        val buttonWidth = 90
        val buttonHeight = 20
        val gap = 8

        val title = "Добро пожаловать в документацию!"
        val body = listOf(
            "Тут находится вся информация о том,",
            "как работать с данным модом."
        )
        val hint = "Чтобы начать, выберите любой раздел слева!"
        val credits = listOf(
            "Разработчик мода - FoxMediaX",
            "Автор идеи - FoxGameYT"
        )

        // Весь блок центрируется по вертикали как единая группа
        val totalHeight =
            font.lineHeight + 14 +
                    body.size * lineStep + 6 +
                    font.lineHeight + 16 +
                    1 + 12 +
                    credits.size * lineStep + 6 +
                    buttonHeight

        var y = top + ((bottom - top - totalHeight) / 2).coerceAtLeast(8)

        drawCenteredDocumentationText(graphics, title, left, right, y, 0xFFFFFFFF.toInt())
        y += font.lineHeight + 14

        for (line in body) {
            drawCenteredDocumentationText(graphics, line, left, right, y, 0xFFCCCCCC.toInt())
            y += lineStep
        }
        y += 6

        drawCenteredDocumentationText(graphics, hint, left, right, y, 0xFFE6E6E6.toInt())
        y += font.lineHeight + 16

        val centerX = (left + right) / 2
        graphics.fill(centerX - 30, y, centerX + 30, y + 1, 0xFF333333.toInt())
        y += 1 + 12

        for (line in credits) {
            drawCenteredDocumentationText(graphics, line, left, right, y, 0xFF8A8A8A.toInt())
            y += lineStep
        }
        y += 6

        val totalWidth = buttonWidth * 2 + gap
        val startX = left + (right - left - totalWidth) / 2

        val telegram = Rect(startX, y, startX + buttonWidth, y + buttonHeight)
        val youtube = Rect(
            startX + buttonWidth + gap, y,
            startX + buttonWidth * 2 + gap, y + buttonHeight
        )

        drawDocumentationButton(graphics, telegram, "Telegram")
        drawDocumentationButton(graphics, youtube, "YouTube")

        documentationTelegramRect = telegram
        documentationYouTubeRect = youtube
    }

    private fun drawCenteredDocumentationText(
        graphics: GuiGraphicsExtractor,
        text: String,
        left: Int,
        right: Int,
        y: Int,
        color: Int
    ) {
        val textWidth =
            font.width(text)

        graphics.text(
            font,
            text,
            left + (right - left - textWidth) / 2,
            y,
            color,
            false
        )
    }

    private fun drawDocumentationButton(
        graphics: GuiGraphicsExtractor,
        rect: Rect,
        text: String
    ) {
        val hovered = rect.contains(docMouseX, docMouseY)

        graphics.fill(
            rect.x1, rect.y1, rect.x2, rect.y2,
            if (hovered) 0xFF3A3A3A.toInt() else 0xFF262626.toInt()
        )

        val label = fitText(text, rect.x2 - rect.x1 - 8)

        graphics.text(
            font,
            label,
            rect.x1 + (rect.x2 - rect.x1 - font.width(label)) / 2,
            rect.y1 + (rect.y2 - rect.y1 - font.lineHeight) / 2 + 1,
            0xFFFFFFFF.toInt(),
            false
        )
    }

    /**
     * Пиксельная стрелка-треугольник.
     * up = false — смотрит вниз (раздел свёрнут), up = true — смотрит вверх (раздел развёрнут).
     */
    private fun drawPixelArrow(
        graphics: GuiGraphicsExtractor,
        centerX: Int,
        centerY: Int,
        up: Boolean,
        color: Int
    ) {
        val px = 2                                  // размер одного «пикселя»
        val widthsInCells = if (up) listOf(1, 3, 5, 7) else listOf(7, 5, 3, 1)
        val top = centerY - widthsInCells.size * px / 2

        widthsInCells.forEachIndexed { row, cells ->
            val width = cells * px
            val x1 = centerX - width / 2
            val y1 = top + row * px
            graphics.fill(x1, y1, x1 + width, y1 + px, color)
        }
    }

    private fun drawDocumentationBottomButtons(
        graphics: GuiGraphicsExtractor,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        val size = 24
        val gap = 6

        val back = Rect(right - size, bottom - size, right, bottom)
        val home = Rect(back.x1 - gap - size, bottom - size, back.x1 - gap, bottom)

        val atHome = documentationSection == "Главное меню" &&
                documentationSubsection == "Credits"
        val canGoBack = documentationHistoryIndex >= 0

        val active = 0xFFFFFFFF.toInt()
        val dimmed = 0xFF666666.toInt()

        drawIconButtonBackground(graphics, home, enabled = !atHome)
        drawHomeIcon(
            graphics,
            (home.x1 + home.x2) / 2,
            (home.y1 + home.y2) / 2,
            if (atHome) dimmed else active,
            0xFF262626.toInt()
        )

        drawIconButtonBackground(graphics, back, enabled = canGoBack)
        drawBackIcon(
            graphics,
            (back.x1 + back.x2) / 2,
            (back.y1 + back.y2) / 2,
            if (canGoBack) active else dimmed
        )

        documentationHomeButtonRect = home
        documentationBackButtonRect = back
    }

    private fun drawScriptsStartPage(
        graphics: GuiGraphicsExtractor,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        // ВАЖНО: порядок должен совпадать с обработчиком кликов (индексы 0..3)
        val names = listOf(
            "Переменные",
            "Глобальные переменные",
            "Для сюжета",
            "Для камеры",
            "Катсцены"
        )

        val title = "Вы находитесь в разделе Скрипты"
        val hint = "Выберите вкладку:"

        val columns = 2
        val rows = (names.size + columns - 1) / columns
        val gap = 8
        val buttonHeight = 24
        val sidePadding = 24

        val available = right - left - sidePadding * 2
        val widest = names.maxOf { font.width(it) } + 24
        val buttonWidth = minOf(widest, (available - gap * (columns - 1)) / columns)
            .coerceAtLeast(60)

        val gridWidth = columns * buttonWidth + gap * (columns - 1)
        val gridHeight = rows * buttonHeight + gap * (rows - 1)

        // Весь блок центрируется по вертикали как единая группа
        val totalHeight =
            font.lineHeight + 10 +
                    font.lineHeight + 16 +
                    gridHeight

        var y = top + ((bottom - top - totalHeight) / 2).coerceAtLeast(8)

        drawCenteredDocumentationText(graphics, title, left, right, y, 0xFFFFFFFF.toInt())
        y += font.lineHeight + 10

        drawCenteredDocumentationText(graphics, hint, left, right, y, 0xFFCCCCCC.toInt())
        y += font.lineHeight + 16

        val startX = left + (right - left - gridWidth) / 2
        val hits = mutableListOf<Rect>()

        for ((index, name) in names.withIndex()) {
            val col = index % columns
            val row = index / columns

            val x1 = startX + col * (buttonWidth + gap)
            val y1 = y + row * (buttonHeight + gap)

            val rect = Rect(x1, y1, x1 + buttonWidth, y1 + buttonHeight)

            drawDocumentationButton(graphics, rect, name)
            hits.add(rect)
        }

        documentationTabRects = hits
    }

    private fun drawDocumentationText(
        graphics: GuiGraphicsExtractor,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        val padding = 14
        val textLeft = left + padding
        val maxWidth = right - left - padding * 2 - 10
        val lineHeight = font.lineHeight + 3

        val wrappedLines = mutableListOf<String>()
        val continues = mutableListOf<Boolean>()

        for (line in documentationContent) {
            when {
                line.isBlank() -> {
                    wrappedLines.add("")
                    continues.add(false)
                }
                line == Documentation.DIVIDER -> {
                    wrappedLines.add(Documentation.DIVIDER)
                    continues.add(false)
                }
                else -> wrapText(line.trim(), maxWidth).forEachIndexed { index, part ->
                    wrappedLines.add(part)
                    continues.add(index > 0) // true = продолжение предыдущей строки
                }
            }
        }

        // Раскладка изменилась (другая страница / размер окна) - старое выделение недействительно
        if (wrappedLines != docWrappedLines) {
            docSelAnchor = null
            docSelCaret = null
            draggingDocSelection = false
        }
        docWrappedLines = wrappedLines
        docLineContinues = continues

        val totalHeight = wrappedLines.size * lineHeight
        val viewportHeight = bottom - top - padding * 2

        documentationMaxScroll = (totalHeight - viewportHeight).coerceAtLeast(0)
        documentationScrollOffset = documentationScrollOffset.coerceIn(0, documentationMaxScroll)

        val firstLineY = top + padding - documentationScrollOffset

        // Данные для мыши (выделение / контекстное меню); справа исключаем полосу прокрутки
        docTextArea = Rect(left, top, right - 12, bottom)
        docTextLeftX = textLeft
        docFirstLineY = firstLineY
        docLineStep = lineHeight

        val selection = docSelectionRange()
        val selectionColor = 0xFF264F78.toInt()

        var y = firstLineY

        for ((index, line) in wrappedLines.withIndex()) {
            // рисуем только строки, целиком попавшие в область контента
            if (y >= top && y + font.lineHeight <= bottom) {
                if (line == Documentation.DIVIDER) {
                    val lineY = y + font.lineHeight / 2
                    graphics.fill(textLeft, lineY, right - padding - 10, lineY + 1, 0xFF333333.toInt())
                } else {
                    if (selection != null && index in selection.first.line..selection.second.line) {
                        val from = if (index == selection.first.line)
                            selection.first.col.coerceIn(0, line.length) else 0
                        val to = if (index == selection.second.line)
                            selection.second.col.coerceIn(0, line.length) else line.length

                        val x1 = textLeft + font.width(line.substring(0, from))
                        var x2 = textLeft + font.width(line.substring(0, to))
                        // небольшой "хвост" показывает, что выделение продолжается на следующую строку
                        if (index != selection.second.line) x2 += 4

                        if (x2 > x1) {
                            graphics.fill(x1, y - 1, x2, y - 1 + lineHeight, selectionColor)
                        }
                    }

                    if (line.isNotEmpty()) {
                        graphics.text(font, line, textLeft, y, 0xFFCCCCCC.toInt(), false)
                    }
                }
            }
            y += lineHeight
        }

        drawDocumentationScrollbar(graphics, right - 8, top + 5, bottom - 5)
    }

    /** Текст строки для выделения/копирования (разделитель - пустая строка). */
    private fun docLineText(index: Int): String {
        val text = docWrappedLines.getOrNull(index) ?: return ""
        return if (text == Documentation.DIVIDER) "" else text
    }

    /** Нормализованный диапазон выделения (начало <= конец) или null, если выделения нет. */
    private fun docSelectionRange(): Pair<SelPos, SelPos>? {
        val a = docSelAnchor ?: return null
        val c = docSelCaret ?: return null
        if (a == c) return null
        return if (a.line < c.line || (a.line == c.line && a.col <= c.col)) a to c else c to a
    }

    /** Позиция (строка, символ) под курсором мыши. */
    private fun docPosAt(mx: Int, my: Int): SelPos {
        if (docWrappedLines.isEmpty()) return SelPos(0, 0)

        val line = (my - docFirstLineY).floorDiv(docLineStep).coerceIn(0, docWrappedLines.lastIndex)
        val text = docLineText(line)
        val dx = mx - docTextLeftX

        // Ближайшая граница символа
        for (col in text.indices) {
            val left = font.width(text.substring(0, col))
            val right = font.width(text.substring(0, col + 1))
            if (dx < (left + right) / 2) return SelPos(line, col)
        }
        return SelPos(line, text.length)
    }

    private fun startDocSelection(mx: Int, my: Int, doubleClick: Boolean): Boolean {
        val area = docTextArea ?: return false
        if (!area.contains(mx, my)) return false

        val pos = docPosAt(mx, my)
        if (doubleClick) {
            draggingDocSelection = false
            selectDocWordAt(pos)
        } else {
            docSelAnchor = pos
            docSelCaret = pos
            draggingDocSelection = true
        }
        return true
    }

    private fun selectDocWordAt(pos: SelPos) {
        val text = docLineText(pos.line)
        if (text.isEmpty()) return

        fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_'

        var anchor = pos.col.coerceIn(0, text.length)
        if (anchor >= text.length || !isWordChar(text[anchor])) anchor-- // берём символ слева
        if (anchor < 0 || !isWordChar(text[anchor])) return                // клик по пробелу/знаку

        var start = anchor
        var end = anchor + 1
        while (start > 0 && isWordChar(text[start - 1])) start--
        while (end < text.length && isWordChar(text[end])) end++

        docSelAnchor = SelPos(pos.line, start)
        docSelCaret = SelPos(pos.line, end)
    }

    private fun selectAllDocText() {
        if (docWrappedLines.isEmpty()) return
        val last = docWrappedLines.lastIndex
        docSelAnchor = SelPos(0, 0)
        docSelCaret = SelPos(last, docLineText(last).length)
    }

    private fun docSelectedText(): String {
        val (start, end) = docSelectionRange() ?: return ""
        val sb = StringBuilder()

        for (i in start.line..end.line) {
            val text = docLineText(i)
            val from = if (i == start.line) start.col.coerceIn(0, text.length) else 0
            val to = if (i == end.line) end.col.coerceIn(0, text.length) else text.length
            if (to > from) sb.append(text.substring(from, to))

            if (i < end.line) {
                // Мягкий перенос (строка разбита только из-за ширины окна) склеиваем пробелом
                sb.append(if (docLineContinues.getOrNull(i + 1) == true) ' ' else '\n')
            }
        }
        return sb.toString().trimEnd()
    }

    private fun copyDocSelection() {
        val text = docSelectedText()
        if (text.isEmpty()) return
        Minecraft.getInstance().keyboardHandler.setClipboard(text)
    }

    private fun resetDocSelection() {
        docSelAnchor = null
        docSelCaret = null
        draggingDocSelection = false
        docTextArea = null
        docContentArea = null
        docWrappedLines = emptyList()
        docLineContinues = emptyList()
        contextMenu = null
    }

    private fun openDocumentationContextMenu(x: Int, y: Int) {
        val hasSelection = docSelectionRange() != null
        val hasText = docTextArea != null && docWrappedLines.isNotEmpty()

        val items = listOf(
            ContextMenuItem("Копировать выделение", enabled = hasSelection) { copyDocSelection() },
            ContextMenuItem("Выделить всё", enabled = hasText) { selectAllDocText() }
        )

        val menuWidth = 160
        val menuHeight = items.size * 16
        val menuX = x.coerceAtMost(width - menuWidth - 4).coerceAtLeast(0)
        val menuY = y.coerceAtMost(height - menuHeight - 4).coerceAtLeast(0)

        contextMenu = ContextMenuInfo(menuX, menuY, items)
    }

    private fun drawDocumentationScrollbar(
        graphics: GuiGraphicsExtractor,
        x: Int,
        top: Int,
        bottom: Int
    ) {
        if (documentationMaxScroll <= 0) {
            documentationScrollbarTrackRect = null
            documentationScrollbarThumbRect = null
            return
        }

        val track =
            Rect(
                x,
                top,
                x + 5,
                bottom
            )

        graphics.fill(
            track.x1,
            track.y1,
            track.x2,
            track.y2,
            0xFF242424.toInt()
        )

        val trackHeight =
            bottom - top

        val thumbHeight =
            (
                    trackHeight *
                            (
                                    trackHeight.toFloat() /
                                            (
                                                    trackHeight +
                                                            documentationMaxScroll
                                                    )
                                    )
                    ).toInt()
                .coerceIn(
                    20,
                    trackHeight
                )

        val travel =
            (
                    trackHeight -
                            thumbHeight
                    ).coerceAtLeast(0)

        val ratio =
            if (documentationMaxScroll > 0) {
                documentationScrollOffset.toFloat() /
                        documentationMaxScroll
            } else {
                0f
            }

        val thumbTop =
            top +
                    (travel * ratio).toInt()

        val thumb =
            Rect(
                x,
                thumbTop,
                x + 5,
                thumbTop + thumbHeight
            )

        graphics.fill(
            thumb.x1,
            thumb.y1,
            thumb.x2,
            thumb.y2,
            0xFF666666.toInt()
        )

        documentationScrollbarTrackRect = track
        documentationScrollbarThumbRect = thumb
    }

    private fun drawIconButtonBackground(
        graphics: GuiGraphicsExtractor,
        rect: Rect,
        enabled: Boolean
    ) {
        val hovered = enabled && rect.contains(docMouseX, docMouseY)
        graphics.fill(
            rect.x1, rect.y1, rect.x2, rect.y2,
            if (hovered) 0xFF3A3A3A.toInt() else 0xFF262626.toInt()
        )
    }

    /** Маленький треугольник: up = true — вверх, иначе вниз. */
    private fun drawChevron(
        graphics: GuiGraphicsExtractor,
        cx: Int,
        cy: Int,
        up: Boolean,
        color: Int
    ) {
        for (i in 0..3) {
            val half = if (up) i else 3 - i
            graphics.fill(cx - half, cy - 2 + i, cx + half + 1, cy - 1 + i, color)
        }
    }

    private fun drawCloseIcon(graphics: GuiGraphicsExtractor, cx: Int, cy: Int, color: Int) {
        for (i in 0..6) {
            graphics.fill(cx - 3 + i, cy - 3 + i, cx - 2 + i, cy - 2 + i, color)
            graphics.fill(cx + 3 - i, cy - 3 + i, cx + 4 - i, cy - 2 + i, color)
        }
    }

    private fun drawHomeIcon(
        graphics: GuiGraphicsExtractor,
        cx: Int,
        cy: Int,
        color: Int,
        background: Int
    ) {
        // крыша
        for (i in 0..4) {
            graphics.fill(cx - i, cy - 6 + i, cx + i + 1, cy - 5 + i, color)
        }
        // стены
        graphics.fill(cx - 3, cy - 1, cx + 4, cy + 5, color)
        // дверь
        graphics.fill(cx - 1, cy + 2, cx + 1, cy + 5, background)
    }

    private fun drawBackIcon(graphics: GuiGraphicsExtractor, cx: Int, cy: Int, color: Int) {
        // наконечник стрелки
        for (i in 0..3) {
            graphics.fill(cx - 5 + i, cy - i, cx - 4 + i, cy + i + 1, color)
        }
        // древко
        graphics.fill(cx - 1, cy - 1, cx + 5, cy + 2, color)
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

    override fun mouseClicked(
        event: MouseButtonEvent,
        doubleClick: Boolean
    ): Boolean {

        val mx = event.x().toInt()
        val my = event.y().toInt()
        val button = event.buttonInfo().button()

        if (showDocumentation && handleDocumentationMouse(mx, my, button)) return true

        /*
         * =========================================================
         * ДОКУМЕНТАЦИЯ
         * =========================================================
         */

        if (showDocumentation) {

            // X
            docsCloseButtonRect?.let {
                if (it.contains(mx, my)) {
                    closeDocumentation()
                    return true
                }
            }

            // Home
            documentationHomeButtonRect?.let {
                if (it.contains(mx, my)) {
                    goDocumentationHome()
                    return true
                }
            }

            // Back
            documentationBackButtonRect?.let {
                if (it.contains(mx, my)) {
                    goDocumentationBack()
                    return true
                }
            }

            // Telegram
            documentationTelegramRect?.let {
                if (it.contains(mx, my)) {
                    openLink("https://t.me/foxgameyt_prod", "Telegram")
                    return true
                }
            }

            // YouTube
            documentationYouTubeRect?.let {
                if (it.contains(mx, my)) {
                    openLink("https://www.youtube.com/@FoxMediaX_prod", "YouTube")
                    return true
                }
            }

            /*
             * Сначала проверяем кнопку-стрелку.
             *
             * Это важно:
             * нажатие на стрелку НЕ должно одновременно
             * открывать сам раздел.
             */
            for (hit in documentationExpandHits) {
                if (hit.rect.contains(mx, my)) {
                    if (expandedDocumentationSections.contains(hit.section)) {
                        expandedDocumentationSections.remove(hit.section)
                    } else {
                        expandedDocumentationSections.add(hit.section)
                    }
                    return true
                }
            }

            // Подразделы
            for (hit in documentationSubsectionHits) {
                if (hit.rect.contains(mx, my)) {
                    openDocumentationPage(hit.section, hit.subsection)
                    return true
                }
            }

            // Основные разделы
            for (hit in documentationSectionHits) {
                if (hit.rect.contains(mx, my)) {
                    val subsection = when (hit.section) {
                        "Главное меню" -> "Credits"
                        "Скрипты" -> "Скрипты"
                        else -> hit.section
                    }
                    openDocumentationPage(hit.section, subsection)
                    return true
                }
            }

            // Кнопки вкладок Скриптов
            for (index in documentationTabRects.indices) {
                if (documentationTabRects[index].contains(mx, my)) {
                    val subsection = when (index) {
                        0 -> "Переменные"
                        1 -> "Глобальные переменные"
                        2 -> "Для сюжета"
                        3 -> "Для камеры"
                        4 -> "Катсцены"
                        else -> return true
                    }
                    openDocumentationPage("Скрипты", subsection)
                    return true
                }
            }

            // Scrollbar thumb
            documentationScrollbarThumbRect?.let {
                if (it.contains(mx, my)) {
                    draggingDocumentationScrollbar = true
                    documentationScrollbarDragGrabOffsetY = my - it.y1
                    return true
                }
            }

            // Начало выделения текста (только на страницах с текстом)
            if (button == 0 && startDocSelection(mx, my, doubleClick)) return true

            /*
             * Ключевой момент:
             * НИКАКИЕ события мыши из документации не должны проходить дальше.
             */
            return true
        }

        /*
         * =========================================================
         * ПОДСКАЗКИ СКРИПТОВ
         * =========================================================
         */
        if (showScriptHints) {

            setFocused(null)

            // X
            scriptHintsCloseButtonRect?.let {

                if (it.contains(mx, my)) {

                    closeScriptHints()
                    draggingScriptHintsScrollbar = false

                    /*
                     * Возвращаем редактор обратно.
                     */
                    scriptEditorBox?.let {
                        if (!children().contains(it)) {
                            addRenderableWidget(it)
                        }
                    }

                    setFocused(null)

                    return true
                }
            }

            // Scrollbar thumb
            scriptHintsScrollbarThumbRect?.let {

                if (it.contains(mx, my)) {

                    draggingScriptHintsScrollbar = true

                    scriptHintsDragGrabOffsetY =
                        my - it.y1

                    return true
                }
            }

            // Scrollbar track
            scriptHintsScrollbarTrackRect?.let { track ->

                if (
                    track.contains(mx, my) &&
                    scriptHintsMaxScroll > 0
                ) {

                    val thumbHeight =
                        scriptHintsScrollbarThumbRect
                            ?.let { it.y2 - it.y1 }
                            ?: 12

                    val travel =
                        (
                                track.y2 -
                                        track.y1 -
                                        thumbHeight
                                ).coerceAtLeast(1)

                    val newThumbTop =
                        (
                                my -
                                        thumbHeight / 2
                                ).coerceIn(
                                track.y1,
                                track.y2 - thumbHeight
                            )

                    val ratio =
                        (
                                newThumbTop -
                                        track.y1
                                ).toFloat() /
                                travel

                    scriptHintsScrollOffset =
                        (
                                ratio *
                                        scriptHintsMaxScroll
                                ).toInt().coerceIn(
                                0,
                                scriptHintsMaxScroll
                            )

                    draggingScriptHintsScrollbar = true
                    scriptHintsDragGrabOffsetY =
                        thumbHeight / 2

                    return true
                }
            }

            /*
             * Нажатие на команду подсказки.
             */
            if (button == 0) {

                val rowIndex =
                    scriptHintsRowRects
                        .indexOfFirst {
                            it.contains(mx, my)
                        }

                if (rowIndex >= 0) {

                    val doc =
                        CommandDocs.ALL[rowIndex]

                    val current =
                        scriptEditorBox?.getValue()
                            ?: ""

                    val separator =
                        if (
                            current.isEmpty() ||
                            current.endsWith("\n")
                        ) {
                            ""
                        } else {
                            "\n"
                        }

                    scriptEditorBox?.setValue(
                        current +
                                separator +
                                doc.template +
                                "\n"
                    )

                    closeScriptHints()

                    /*
                     * Возвращаем редактор.
                     */
                    scriptEditorBox?.let {
                        if (!children().contains(it)) {
                            addRenderableWidget(it)
                        }
                    }

                    setFocused(null)

                    return true
                }
            }

            /*
             * НИКАКОЙ клик из подсказок не должен
             * попасть в редактор.
             */
            return true
        }

        /*
         * =========================================================
         * ДИАЛОГ СОХРАНЕНИЯ
         * =========================================================
         */

        if (closeConfirmVisible) {

            val save =
                closeConfirmSaveButton

            val discard =
                closeConfirmDiscardButton

            when {

                save != null &&
                        mx >= save.x &&
                        mx < save.x + save.width &&
                        my >= save.y &&
                        my < save.y + save.height -> {

                    save.onClick(
                        event,
                        doubleClick
                    )
                }

                discard != null &&
                        mx >= discard.x &&
                        mx < discard.x + discard.width &&
                        my >= discard.y &&
                        my < discard.y + discard.height -> {

                    discard.onClick(
                        event,
                        doubleClick
                    )
                }
            }

            return true
        }

        /*
         * =========================================================
         * ДИАЛОГ УДАЛЕНИЯ
         * =========================================================
         */

        deleteConfirmEntry?.let {

            val yes =
                deleteConfirmYesButton

            val no =
                deleteConfirmNoButton

            when {

                yes != null &&
                        mx >= yes.x &&
                        mx < yes.x + yes.width &&
                        my >= yes.y &&
                        my < yes.y + yes.height -> {

                    yes.onClick(
                        event,
                        doubleClick
                    )
                }

                no != null &&
                        mx >= no.x &&
                        mx < no.x + no.width &&
                        my >= no.y &&
                        my < no.y + no.height -> {

                    no.onClick(
                        event,
                        doubleClick
                    )
                }
            }

            return true
        }

        /*
         * =========================================================
         * КОНТЕКСТНОЕ МЕНЮ
         * =========================================================
         */

        contextMenu?.let {

            val rowIndex =
                contextMenuRowRects
                    .indexOfFirst {
                        it.contains(mx, my)
                    }

            if (rowIndex >= 0) {

                it.items[rowIndex]
                    .action
                    .invoke()

                contextMenu = null

                return true

            } else {

                contextMenu = null
            }
        }

        /*
         * =========================================================
         * ЛОГИ
         * =========================================================
         */

        if (
            selectedSection ==
            PanelSection.LOGS
        ) {

            logsClearButtonRect?.let {

                if (it.contains(mx, my)) {

                    ScriptFXLog.clear()

                    return true
                }
            }
        }

        /*
         * =========================================================
         * ПРОЕКТЫ
         * =========================================================
         */

        if (
            selectedSection ==
            PanelSection.PROJECTS &&
            scriptEditorFile == null
        ) {

            val row =
                fileRows.firstOrNull {
                    it.rect.contains(mx, my)
                }

            if (button == 1) {

                if (
                    row != null &&
                    !row.isUp &&
                    row.entry != null
                ) {

                    openContextMenu(
                        mx,
                        my,
                        row.entry
                    )

                } else if (
                    contentRect()
                        .contains(mx, my)
                ) {

                    openBackgroundContextMenu(
                        mx,
                        my
                    )
                }

                return true
            }

            if (
                button == 0 &&
                row != null
            ) {

                when {

                    row.isUp ->
                        fileBrowser.goUp()

                    row.entry!!.isDirectory ->
                        fileBrowser.goInto(
                            row.entry
                        )

                    else ->
                        openScriptFileFromEntry(
                            row.entry
                        )
                }

                return true
            }
        }

        return super.mouseClicked(
            event,
            doubleClick
        )
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double
    ): Boolean {

        if (showDocumentation) {

            documentationScrollOffset =
                (
                        documentationScrollOffset -
                                (scrollY * 12).toInt()
                        ).coerceIn(
                        0,
                        documentationMaxScroll
                    )

            return true
        }

        if (showScriptHints) {

            scriptHintsScrollOffset =
                (
                        scriptHintsScrollOffset -
                                (scrollY * 12).toInt()
                        ).coerceIn(
                        0,
                        scriptHintsMaxScroll
                    )

            return true
        }

        if (
            selectedSection ==
            PanelSection.LOGS
        ) {

            logsScrollOffset -=
                (scrollY * 12).toInt()

            return true
        }

        return super.mouseScrolled(
            mouseX,
            mouseY,
            scrollX,
            scrollY
        )
    }

    override fun mouseDragged(
        event: MouseButtonEvent,
        dragX: Double,
        dragY: Double
    ): Boolean {

        if (draggingDocSelection && event.buttonInfo().button() == 0) {
            val area = docTextArea
            if (area != null) {
                val my = event.y().toInt()

                // автопрокрутка, когда курсор уходит за область текста
                if (my < area.y1) {
                    documentationScrollOffset = (documentationScrollOffset - 6).coerceIn(0, documentationMaxScroll)
                } else if (my > area.y2) {
                    documentationScrollOffset = (documentationScrollOffset + 6).coerceIn(0, documentationMaxScroll)
                }

                docSelCaret = docPosAt(event.x().toInt(), my.coerceIn(area.y1, area.y2 - 1))
            }
            return true
        }

        if (draggingDocumentationScrollbar) {

            val track =
                documentationScrollbarTrackRect

            val thumb =
                documentationScrollbarThumbRect

            if (
                track != null &&
                thumb != null &&
                documentationMaxScroll > 0
            ) {

                val thumbHeight =
                    thumb.y2 - thumb.y1

                val travel =
                    (
                            track.y2 -
                                    track.y1 -
                                    thumbHeight
                            ).coerceAtLeast(1)

                val mouseY =
                    event.y().toInt()

                val newThumbTop =
                    (
                            mouseY -
                                    documentationScrollbarDragGrabOffsetY
                            ).coerceIn(
                            track.y1,
                            track.y2 - thumbHeight
                        )

                val ratio =
                    (
                            newThumbTop -
                                    track.y1
                            ).toFloat() /
                            travel

                documentationScrollOffset =
                    (
                            ratio *
                                    documentationMaxScroll
                            ).toInt().coerceIn(
                            0,
                            documentationMaxScroll
                        )
            }

            return true
        }

        if (draggingScriptHintsScrollbar) {

            val track =
                scriptHintsScrollbarTrackRect

            val thumb =
                scriptHintsScrollbarThumbRect

            if (
                track != null &&
                thumb != null &&
                scriptHintsMaxScroll > 0
            ) {

                val thumbHeight =
                    thumb.y2 - thumb.y1

                val travel =
                    (
                            track.y2 -
                                    track.y1 -
                                    thumbHeight
                            ).coerceAtLeast(1)

                val my =
                    event.y().toInt()

                val newThumbTop =
                    (
                            my -
                                    scriptHintsDragGrabOffsetY
                            ).coerceIn(
                            track.y1,
                            track.y2 - thumbHeight
                        )

                val ratio =
                    (
                            newThumbTop -
                                    track.y1
                            ).toFloat() /
                            travel

                scriptHintsScrollOffset =
                    (
                            ratio *
                                    scriptHintsMaxScroll
                            ).toInt().coerceIn(
                            0,
                            scriptHintsMaxScroll
                        )
            }

            return true
        }

        if (isBlockingOverlayOpen()) {
            return true
        }

        return super.mouseDragged(
            event,
            dragX,
            dragY
        )
    }

    /** Открывает ссылку в браузере средствами самого Minecraft (java.awt.Desktop в игре недоступен). */
    private fun openLink(url: String, label: String) {
        try {
            net.minecraft.util.Util.getPlatform().openUri(URI.create(url))
        } catch (e: Exception) {
            ScriptFXLog.error("Не удалось открыть $label: ${e.message}")
        }
    }

    override fun mouseReleased(
        event: MouseButtonEvent
    ): Boolean {

        if (draggingDocSelection) {
            draggingDocSelection = false
            return true
        }

        if (draggingDocumentationScrollbar) {
            draggingDocumentationScrollbar = false
            return true
        }

        if (draggingScriptHintsScrollbar) {
            draggingScriptHintsScrollbar = false
            return true
        }

        if (isBlockingOverlayOpen()) {
            return true
        }

        return super.mouseReleased(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        // Esc: сначала закрываем контекстное меню документации, потом уже сами оверлеи
        if (event.key() == 256 && showDocumentation && contextMenu != null) {
            contextMenu = null
            return true
        }

        // Документация: Ctrl+C - копировать выделение, Ctrl+A - выделить всё
        if (showDocumentation && !showScriptHints && !isDialogOpen() && event.hasControlDown()) {
            when (event.key()) {
                67 -> { copyDocSelection(); return true }   // C
                65 -> { selectAllDocText(); return true }   // A
            }
        }

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