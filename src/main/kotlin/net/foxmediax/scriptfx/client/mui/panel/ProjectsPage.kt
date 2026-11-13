package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.text.Editable
import icyllis.modernui.text.TextWatcher
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.MotionEvent
import icyllis.modernui.view.View
import icyllis.modernui.widget.EditText
import icyllis.modernui.widget.FrameLayout
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
import net.foxmediax.scriptfx.scriptengine.ScriptContext
import net.foxmediax.scriptfx.scriptengine.ScriptManager
import net.minecraft.client.Minecraft
import java.io.File
import icyllis.modernui.core.Core

/**
 * Экран «Сценарии»:
 * проводник | редактор + консоль | свойства
 */
class ProjectsPage(host: PanelHost, private val browser: FileBrowser) : PanelPage(host) {

    private lateinit var ctx: Context
    private lateinit var overlays: OverlayHost

    // left
    private lateinit var treeBox: LinearLayout
    private lateinit var pathView: TextView

    // center
    private lateinit var tabTitle: TextView
    private lateinit var edit: EditText
    private lateinit var consoleBox: LinearLayout

    // right
    private lateinit var propName: TextView
    private lateinit var propPath: TextView
    private lateinit var propStatus: TextView

    private var file: File? = null
    private var original = ""
    private var last = ""
    private var suppress = false
    private val undoStack = ArrayDeque<String>()

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

        refreshTree()
        showEmptyEditor()
        return root
    }

    override fun onDestroy() {
        if (this::overlays.isInitialized) overlays.clear()
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
    // LEFT: проводник
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

        val actions = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        actions.addView(
            PanelUi.flatButton(ctx, "Создать") { openBackgroundMenu() },
            lp(WRAP, WRAP)
        )
        actions.addView(
            PanelUi.flatButton(ctx, "Обновить") { refreshTree() },
            lp(WRAP, WRAP).apply { leftMargin = dp(ctx, 6) }
        )
        col.addView(actions, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) })

        treeBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
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
            val label = if (entry.isDirectory) "📁 ${entry.name}" else "📄 ${entry.name}"
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
                    } else {
                        false
                    }
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
    // CENTER: вкладка + редактор + консоль
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
        tabs.addView(
            PanelUi.flatButton(ctx, "▶") { runScript() },
            lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 4) }
        )
        tabs.addView(
            PanelUi.flatButton(ctx, "💾") { save() },
            lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 4) }
        )
        tabs.addView(
            PanelUi.flatButton(ctx, "✕") { closeFile() },
            lp(WRAP, WRAP)
        )
        col.addView(tabs, lp(MATCH, WRAP))
        col.addView(dividerH(ctx), lp(MATCH, px1(ctx)))

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
                    if (suppress) return
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

        val editorScroll = ScrollView(ctx)
        editorScroll.addView(edit, lp(MATCH, WRAP))
        col.addView(editorScroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

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

        logConsole("[INFO] Готов к работе")
        return col
    }

    private fun showEmptyEditor() {
        suppress = true
        edit.setText("")
        edit.isEnabled = false
        suppress = false
        file = null
        original = ""
        last = ""
        dirty = false
        tabTitle.text = "— нет файла —"
        updateProps()
    }

    private fun openEditor(f: File) {
        val text = try {
            f.readText()
        } catch (e: Exception) {
            logConsole("[ERROR] Не удалось прочитать ${f.name}")
            return
        }
        file = f
        original = text
        last = text
        dirty = false
        undoStack.clear()
        suppress = true
        edit.isEnabled = true
        edit.setText(text)
        suppress = false
        tabTitle.text = f.name
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

    private fun save() {
        val f = file ?: return
        try {
            f.writeText(edit.text?.toString() ?: "")
            original = edit.text?.toString() ?: ""
            last = original
            dirty = false
            tabTitle.text = f.name
            updateProps()
            logConsole("[INFO] Сохранено: ${f.name}")
        } catch (e: Exception) {
            logConsole("[ERROR] Сохранение не удалось: ${e.message}")
        }
    }

    private fun runScript() {
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
            while (consoleBox.childCount > 40) {
                consoleBox.removeViewAt(0)
            }
        }

        // ModernUI: View только с UI thread
        if (Core.isOnUiThread()) {
            apply.run()
        } else {
            Core.getUiHandler().post(apply)
        }
    }

    // ------------------------------------------------------------------
    // RIGHT: свойства
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
            lp(MATCH, WRAP)
        )
        return col
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
        openEditor(entry.file)
    }

    private fun openRowMenu(entry: FileEntry) {
        val items = mutableListOf<Pair<String, () -> Unit>>()
        items += "Открыть" to { openEntry(entry) }
        items += "Переименовать" to {
            overlays.input("Переименовать", entry.name) { newName ->
                if (!browser.rename(entry, newName)) {
                    logConsole("[WARN] Не удалось переименовать")
                }
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
                    openEditor(created)
                    logConsole("[INFO] Создан файл: $safe")
                } else {
                    logConsole("[WARN] Не удалось создать файл")
                }
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
                if (created.exists()) openEditor(created)
            }
        }

        overlays.actions("Создать", items)
    }

    private fun dividerV(ctx: Context) =
        View(ctx).apply { background = ColorDrawable(COL_DIVIDER) }

    private fun dividerH(ctx: Context) =
        View(ctx).apply { background = ColorDrawable(COL_DIVIDER) }
}