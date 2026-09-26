package net.foxmediax.scriptfx.gui

import net.foxmediax.scriptfx.config.ScriptFXConfigScreen
import net.foxmediax.scriptfx.scriptengine.ScriptContext
import net.foxmediax.scriptfx.scriptengine.ScriptManager
import net.foxmediax.scriptfx.scriptengine.ScriptParser
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

enum class PanelSection(val displayName: String) {
    PROJECTS("Проекты"),
    CUTSCENES("Кат-сцены"),
    NPC_EDITOR("NPC-редактор"),
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

    // Публичная обёртка: extractWidgetRenderState у родителя protected,
    // а нам нужно вызывать отрисовку кнопки вручную снаружи (для модального диалога).
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

    // ---- файловый менеджер ----
    private val fileBrowser = FileBrowser()
    private var fileRows: List<FileRow> = emptyList()
    private var contextMenu: ContextMenuInfo? = null
    private var contextMenuRowRects: List<Rect> = emptyList()

    // ---- универсальное поле ввода (переименование / создание файла-папки-проекта) ----
    private var textInputBox: EditBox? = null
    private var textInputConfirmButton: FlatButton? = null

    // ---- редактор скрипта (открывается прямо внутри "Проекты") ----
    private var scriptEditorFile: File? = null
    private var scriptEditorBox: MultiLineEditBox? = null
    private var scriptEditorOriginalContent: String? = null
    private var scriptEditorSaveButton: FlatButton? = null
    private var scriptEditorRunButton: FlatButton? = null
    private var scriptEditorCloseButton: FlatButton? = null

    // ---- диалог "есть несохранённые изменения" ----
    // ВАЖНО: эти две кнопки НЕ регистрируются через addRenderableWidget — рисуем и обрабатываем клики сами,
    // иначе стандартный Screen рисует их ДО модального затемнения (они оказываются под ним)
    // и пропускает клики фоновым виджетам сквозь диалог.
    private var closeConfirmVisible = false
    private var closeConfirmSaveButton: FlatButton? = null
    private var closeConfirmDiscardButton: FlatButton? = null
    private var pendingNavigationAction: (() -> Unit)? = null

    // ---- документация ----
    private var showDocumentation = false
    private var docsScrollOffset = 0
    private var docsCloseButtonRect: Rect? = null

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
                        }
                    }
                }
            )
            y += buttonHeight + gap
        }
    }

    private fun buildHeaderButtons() {
        val navLeft = headerLeft + 8
        addRenderableWidget(
            FlatButton(navLeft, panelTop + 5, navButtonSize, navButtonSize, "⌂", centered = true) {
                closeTextInput(); contextMenu = null
                navigateAwayAndThen { selectedSection = null }
            }
        )
        addRenderableWidget(
            FlatButton(navLeft, panelTop + 5 + navButtonSize + 4, navButtonSize, navButtonSize, "▲", centered = true) {
                closeTextInput(); contextMenu = null
                navigateAwayAndThen { selectedSection = null }
            }
        )
        addRenderableWidget(
            FlatButton(panelRight - navButtonSize, panelTop, navButtonSize, navButtonSize, "X", centered = true) {
                onClose()
            }
        )
        addRenderableWidget(
            FlatButton(panelRight - navButtonSize, panelTop + navButtonSize + 4, navButtonSize, navButtonSize, "?", centered = true) {
                if (showDocumentation) closeDocumentation() else openDocumentation()
            }
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
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
            headerLeft + 8 + navButtonSize + 12, panelTop + (headerHeight - font.lineHeight) / 2,
            0xFFFFFFFF.toInt(), false
        )

        graphics.fill(panelLeft, sidebarTop, panelLeft + sidebarWidth, panelBottom, 0xFF141414.toInt())

        drawContent(graphics)

        drawVerticalDivider(graphics, headerLeft, panelTop, panelTop + headerHeight)
        drawHorizontalDivider(graphics, panelLeft, panelRight, panelTop + headerHeight)
        drawVerticalDivider(graphics, panelLeft + sidebarWidth, sidebarTop, panelBottom)

        super.extractRenderState(graphics, mouseX, mouseY, delta)

        drawContextMenu(graphics)
        // closeConfirm рисуется ПОСЛЕДНИМ и рисует свои кнопки вручную поверх всего остального
        if (closeConfirmVisible) drawCloseConfirmDialog(graphics, mouseX, mouseY, delta)
        if (showDocumentation) drawDocumentationOverlay(graphics)
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
                } else {
                    drawProjectsFileList(graphics, rect)
                }
            }
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

    // ---------------- ПРОЕКТЫ: файловый менеджер ----------------

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

    // ---------------- редактор скрипта ----------------

    private fun openScriptEditor(file: File) {
        closeScriptEditor()

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
        }

        val runBtn = FlatButton(rect.x1 + 108, editorTop + editorHeight + 4, 150, 20, "▶ Запустить скрипт") {
            runScriptFile(file)
        }

        addRenderableWidget(box)
        addRenderableWidget(closeBtn)
        addRenderableWidget(saveBtn)
        addRenderableWidget(runBtn)

        scriptEditorFile = file
        scriptEditorBox = box
        scriptEditorOriginalContent = content
        scriptEditorCloseButton = closeBtn
        scriptEditorSaveButton = saveBtn
        scriptEditorRunButton = runBtn
    }

    private fun closeScriptEditor() {
        scriptEditorBox?.let { removeWidget(it) }
        scriptEditorCloseButton?.let { removeWidget(it) }
        scriptEditorSaveButton?.let { removeWidget(it) }
        scriptEditorRunButton?.let { removeWidget(it) }
        scriptEditorBox = null
        scriptEditorCloseButton = null
        scriptEditorSaveButton = null
        scriptEditorRunButton = null
        scriptEditorFile = null
        scriptEditorOriginalContent = null
    }

    /** Если в открытом скрипте есть несохранённые правки — сперва спрашивает подтверждение,
     *  и выполняет action только после ответа. Если правок нет — закрывает редактор и сразу выполняет action. */
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
            // TODO: на выделенном сервере тут нужен сетевой пакет клиент -> сервер, пока работает только в одиночной игре/LAN
            return
        }
        val server = client.getSingleplayerServer() ?: return
        val playerUuid = client.player?.uuid ?: return

        server.execute {
            val serverPlayer = server.playerList.getPlayer(playerUuid) ?: return@execute
            val commands = ScriptParser.parse(file.readText())
            ScriptManager.runAdHoc(commands, ScriptContext(server, serverPlayer))
        }
    }

    // ---------------- диалог "несохранённые изменения" ----------------

    private fun closeConfirmBounds(): Rect {
        val rect = contentRect()
        val cx = rect.x1 + (rect.x2 - rect.x1) / 2
        val cy = rect.y1 + (rect.y2 - rect.y1) / 2
        val boxW = 280
        val boxH = 80
        return Rect(cx - boxW / 2, cy - boxH / 2, cx + boxW / 2, cy + boxH / 2)
    }

    private fun openCloseConfirmDialog() {
        closeCloseConfirmDialog()
        val box = closeConfirmBounds()

        // ВАЖНО: НЕ addRenderableWidget — рисуем и обрабатываем клики вручную,
        // чтобы кнопки не оказались "под" затемнением и не пропускали клики фону.
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

        // рисуем кнопки вручную через публичную обёртку renderButton(), уже поверх подложки диалога
        closeConfirmSaveButton?.renderButton(graphics, mouseX, mouseY, delta)
        closeConfirmDiscardButton?.renderButton(graphics, mouseX, mouseY, delta)
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

    // ---------------- контекстные меню ----------------

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
        items.add(ContextMenuItem("Удалить") { fileBrowser.delete(entry) })
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

    // ---------------- универсальное текстовое поле ----------------

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

    // ---------------- документация ----------------

    private fun openDocumentation() {
        docsScrollOffset = 0
        showDocumentation = true
    }

    private fun closeDocumentation() {
        showDocumentation = false
    }

    private fun drawDocumentationOverlay(graphics: GuiGraphicsExtractor) {
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xF5101010.toInt())

        val left = panelLeft + 20
        val top = panelTop + 20
        val right = panelRight - 20
        val bottom = panelBottom - 20

        graphics.text(font, "Документация ScriptFX", left, top, 0xFFFFFFFF.toInt(), false)

        val closeRect = Rect(right - 20, top - 4, right, top + 12)
        graphics.fill(closeRect.x1, closeRect.y1, closeRect.x2, closeRect.y2, 0xFF262626.toInt())
        graphics.text(font, "X", closeRect.x1 + 6, closeRect.y1 + 2, 0xFFFFFFFF.toInt(), false)
        docsCloseButtonRect = closeRect

        val maxWidth = right - left
        var y = top + 18 - docsScrollOffset
        for (paragraph in Documentation.TEXT.split("\n")) {
            if (paragraph.isBlank()) {
                y += font.lineHeight + 2
                continue
            }
            for (line in wrapText(paragraph, maxWidth)) {
                if (y in (top + 16)..(bottom - font.lineHeight)) {
                    graphics.text(font, line, left, y, 0xFFCCCCCC.toInt(), false)
                }
                y += font.lineHeight + 2
            }
        }
    }

    // ---------------- ввод мыши ----------------

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()
        val button = event.buttonInfo().button() // 0 = левая, 1 = правая

        if (showDocumentation) {
            docsCloseButtonRect?.let { if (it.contains(mx, my)) closeDocumentation() }
            return true
        }

        if (closeConfirmVisible) {
            // Диалог модальный: клик обрабатывается ТОЛЬКО нами и никуда дальше не идёт,
            // независимо от того, попал он в одну из двух кнопок или в пустое место фона.
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

        contextMenu?.let {
            val rowIndex = contextMenuRowRects.indexOfFirst { r -> r.contains(mx, my) }
            if (rowIndex >= 0) {
                it.items[rowIndex].action.invoke()
                contextMenu = null
                return true
            } else {
                contextMenu = null
            }
        }

        if (selectedSection == PanelSection.PROJECTS && scriptEditorFile == null) {
            val row = fileRows.firstOrNull { it.rect.contains(mx, my) }

            if (button == 1) {
                if (row != null && !row.isUp && row.entry != null) {
                    openContextMenu(mx, my, row.entry)
                } else if (contentRect().contains(mx, my)) {
                    openBackgroundContextMenu(mx, my)
                }
                return true
            }

            if (button == 0 && row != null) {
                when {
                    row.isUp -> fileBrowser.goUp()
                    row.entry!!.isDirectory -> fileBrowser.goInto(row.entry)
                    else -> openScriptFileFromEntry(row.entry)
                }
                return true
            }
        }

        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (showDocumentation) {
            docsScrollOffset = (docsScrollOffset - (scrollY * 12).toInt()).coerceAtLeast(0)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }
}