package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.text.Editable
import icyllis.modernui.text.TextWatcher
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.KeyEvent
import icyllis.modernui.view.MotionEvent
import icyllis.modernui.view.View
import icyllis.modernui.widget.EditText
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_MUTED
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_TEXT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.MATCH
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.WRAP
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.dp
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.lp
import net.foxmediax.scriptfx.gui.CommandDocs
import net.foxmediax.scriptfx.gui.FileBrowser
import net.foxmediax.scriptfx.gui.FileEntry
import net.foxmediax.scriptfx.gui.ScriptValidator
import net.foxmediax.scriptfx.scriptengine.CommandRegistry
import net.foxmediax.scriptfx.scriptengine.ScriptContext
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.foxmediax.scriptfx.scriptengine.ScriptManager
import net.foxmediax.scriptfx.scriptengine.ScriptParser
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.io.File

class ProjectsPage(host: PanelHost, private val browser: FileBrowser) : PanelPage(host) {

    private lateinit var ctx: Context
    private lateinit var overlays: OverlayHost
    private lateinit var listLayer: View
    private lateinit var editorLayer: View
    private lateinit var pathView: TextView
    private lateinit var rowsBox: LinearLayout

    // --- редактор ---
    private lateinit var nameView: TextView
    private lateinit var statusView: TextView
    private lateinit var msgView: TextView
    private lateinit var edit: EditText

    private var file: File? = null
    private var original = ""
    private var last = ""
    private var suppress = false
    private val undoStack = ArrayDeque<String>()
    private val clearMsg = Runnable { msgView.text = "" }

    @Volatile private var dirty = false

    override val breadcrumb: String? get() = file?.let { "Проекты -> ${it.name}" }

    /** Читается из isBackKey (другой поток), поэтому только поля-флаги. */
    override val interceptsEscape: Boolean get() = overlays.isOpen || dirty

    override fun onEscape() {
        if (overlays.isOpen) overlays.dismissTop() else host.closePanel()
    }

    // ------------------------------------------------------------------

    override fun createView(ctx: Context): View {
        this.ctx = ctx
        val root = FrameLayout(ctx).apply {
            background = ColorDrawable(0xFF101014.toInt())
        }
        val overlayLayer = FrameLayout(ctx)
        overlays = OverlayHost(ctx, overlayLayer)

        listLayer = buildList(ctx)
        editorLayer = buildEditor(ctx).also { it.visibility = View.GONE }

        root.addView(listLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(editorLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(overlayLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        refreshList()
        return root
    }

    override fun onDestroy() {
        if (this::msgView.isInitialized) msgView.removeCallbacks(clearMsg)
        if (this::overlays.isInitialized) overlays.clear()
    }

    override fun goUp(): Boolean {
        if (!browser.canGoUp()) return false
        browser.goUp()
        refreshList()
        return true
    }

    /** Выход со страницы: при несохранённых правках спрашиваем. Редактор закрывается всегда. */
    override fun requestLeave(proceed: () -> Unit) {
        val f = file
        if (f != null && isDirty()) {
            overlays.confirm(
                "Были внесены правки в файл ${f.name}, желаете сохранить изменения?",
                "Сохранить и закрыть", "Выйти без сохранения",
                onYes = { save(); closeEditor(); proceed() },
                onNo = { closeEditor(); proceed() }
            )
        } else {
            closeEditor()
            proceed()
        }
    }

    // ------------------------------------------------------------------
    // Список файлов

    private fun buildList(ctx: Context): View {
        val page = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 4), dp(ctx, 2), dp(ctx, 4), dp(ctx, 2))
        }
        pathView = TextView(ctx).apply {
            textSize = 11f
            setTextColor(0xFF888888.toInt())
        }
        top.addView(pathView, LinearLayout.LayoutParams(0, WRAP, 1f))
        top.addView(PanelUi.flatButton(ctx, "Создать…") { openBackgroundMenu() }, lp(WRAP, WRAP))
        page.addView(top, lp(MATCH, WRAP))

        rowsBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 2), 0, dp(ctx, 2), dp(ctx, 2))
        }
        val scroll = ScrollView(ctx).apply {
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_DOWN && isSecondary(e)) {
                    openBackgroundMenu()
                    true
                } else false
            }
        }
        scroll.addView(rowsBox, lp(MATCH, WRAP))
        page.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return page
    }

    private fun isSecondary(e: MotionEvent) =
        (e.buttonState and MotionEvent.BUTTON_SECONDARY) != 0

    private fun refreshList() {
        pathView.text = "/scriptfx/${browser.breadcrumbPath()}"
        rowsBox.removeAllViews()

        if (browser.canGoUp()) {
            rowsBox.addView(row("../", COL_MUTED, { goUp() }, null), rowLp())
        }
        for (entry in browser.entries) {
            val label = if (entry.isDirectory) "${entry.name}/" else entry.name
            rowsBox.addView(
                row(label, COL_TEXT, { openEntry(entry) }, { openRowMenu(entry) }),
                rowLp()
            )
        }
        if (browser.entries.isEmpty()) {
            val hint = when {
                browser.isAtProjectsRoot() ->
                    "Пока нет проектов — ПКМ или «Создать…»"
                browser.isInsideProjectScripts() ->
                    "Нет скриптов — «Создать…»"
                else ->
                    "Пусто — «Создать…»"
            }
            rowsBox.addView(
                TextView(ctx).apply {
                    text = hint
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(0xFF666666.toInt())
                    setPadding(0, dp(ctx, 16), 0, 0)
                },
                lp(MATCH, WRAP)
            )
        }
    }

    private fun rowLp() = lp(MATCH, WRAP).apply { topMargin = dp(ctx, 1) }

    private fun row(
        label: String,
        color: Int,
        onClick: () -> Unit,
        onMenu: (() -> Unit)?
    ): View {
        val r = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ColorDrawable(
                if (onMenu == null) 0xFF141418.toInt() else 0xFF16161C.toInt()
            )
            setPadding(dp(ctx, 6), dp(ctx, 3), dp(ctx, 3), dp(ctx, 3))
            isClickable = true
            setOnClickListener { onClick() }
            if (onMenu != null) {
                setOnTouchListener { _, e ->
                    if (e.action == MotionEvent.ACTION_DOWN && isSecondary(e)) {
                        onMenu()
                        true
                    } else false
                }
            }
        }
        r.addView(
            TextView(ctx).apply {
                text = label
                textSize = 12f
                setTextColor(color)
                setSingleLine(true)
            },
            LinearLayout.LayoutParams(0, WRAP, 1f)
        )
        if (onMenu != null) {
            r.addView(
                TextView(ctx).apply {
                    text = "⋮"
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(COL_MUTED)
                    setPadding(dp(ctx, 6), 0, dp(ctx, 6), 0)
                    setOnClickListener { onMenu() }
                },
                lp(WRAP, WRAP)
            )
        }
        return r
    }

    private fun openEntry(entry: FileEntry) {
        if (entry.isDirectory) {
            browser.goInto(entry)
            refreshList()
        } else if (entry.file.extension == "sfxs") {
            openEditor(entry.file)
        }
    }

    // ------------------------------------------------------------------
    // Меню действий

    private fun openRowMenu(entry: FileEntry) {
        val items = mutableListOf<Pair<String, () -> Unit>>()
        items += (if (entry.isDirectory) "Открыть папку" else "Открыть файл") to {
            openEntry(entry)
        }
        items += "Переименовать" to {
            overlays.input("Переименовать", entry.name) { newName ->
                if (!browser.rename(entry, newName)) {
                    ScriptFXLog.warn("Не удалось переименовать '${entry.name}'")
                }
                refreshList()
            }
        }
        items += "Удалить" to { confirmDelete(entry) }
        overlays.actions(entry.name, items)
    }

    private fun confirmDelete(entry: FileEntry) {
        overlays.confirm(
            "Удалить \"${entry.name}\" безвозвратно?",
            "Удалить",
            "Отмена",
            onYes = {
                browser.delete(entry)
                refreshList()
            }
        )
    }

    private fun openBackgroundMenu() {
        fun ask(title: String, initial: String, create: (String) -> Unit) =
            overlays.input(title, initial) {
                create(it)
                refreshList()
            }

        val project = "Создать проект" to {
            ask("Создать проект", "мой_проект") { browser.createProject(it) }
        }
        val fileItem = "Создать файл" to {
            ask("Создать файл", "новый_файл.txt") { browser.createFile(it) }
        }
        val folder = "Создать папку" to {
            ask("Создать папку", "новая_папка") { browser.createFolder(it) }
        }
        val script = "Создать скриптовый файл" to {
            ask("Создать скриптовый файл", "новый_скрипт") { browser.createScriptFile(it) }
        }

        val items = mutableListOf<Pair<String, () -> Unit>>()
        when {
            browser.isAtProjectsRoot() -> items += project
            browser.isInsideProjectScripts() -> {
                items += script
                items += folder
            }
            browser.isInsideProjectRoot() -> {
                items += fileItem
                items += folder
                items += project
            }
            else -> {
                items += fileItem
                items += folder
            }
        }
        items += "Обновить" to {
            browser.refresh()
            refreshList()
        }
        overlays.actions("Действия", items)
    }

    // ------------------------------------------------------------------
    // Редактор скриптов

    private fun buildEditor(ctx: Context): View {
        val page = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 2), dp(ctx, 2), dp(ctx, 2), dp(ctx, 2))
        }
        nameView = TextView(ctx).apply {
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setSingleLine(true)
        }
        statusView = TextView(ctx).apply {
            textSize = 11f
            setPadding(dp(ctx, 6), 0, dp(ctx, 6), 0)
        }
        top.addView(nameView, LinearLayout.LayoutParams(0, WRAP, 1f))
        top.addView(statusView, lp(WRAP, WRAP))
        top.addView(
            PanelUi.flatButton(ctx, "X") { host.requestLeave { } },
            lp(WRAP, WRAP)
        )
        page.addView(top, lp(MATCH, WRAP))

        edit = EditText(ctx).apply {
            textSize = 12f
            gravity = Gravity.TOP or Gravity.START
            setSingleLine(false)
            minLines = 12
            setPadding(dp(ctx, 4), dp(ctx, 3), dp(ctx, 4), dp(ctx, 3))
        }
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (!suppress) onEdited(s.toString())
            }
        })
        edit.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                keyCode == GLFW.GLFW_KEY_Z &&
                event.isCtrlPressed
            ) {
                undo()
                true
            } else false
        }
        val scroll = ScrollView(ctx).apply {
            background = ColorDrawable(0xFF0C0C10.toInt())
        }
        scroll.addView(edit, lp(MATCH, WRAP))
        page.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val bottom = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(ctx, 4), 0, 0)
        }
        bottom.addView(
            PanelUi.flatButton(ctx, "Сохранить") {
                save()
                flash("Изменения сохранены!")
            },
            lp(WRAP, WRAP)
        )
        bottom.addView(
            PanelUi.flatButton(ctx, "▶ Запустить") {
                file?.let { run(it) }
            },
            lp(WRAP, WRAP).apply { leftMargin = dp(ctx, 6) }
        )
        msgView = TextView(ctx).apply {
            textSize = 11f
            setTextColor(0xFF55FF55.toInt())
            setPadding(dp(ctx, 8), 0, dp(ctx, 8), 0)
        }
        bottom.addView(msgView, LinearLayout.LayoutParams(0, WRAP, 1f))
        bottom.addView(
            PanelUi.flatButton(ctx, "Подсказки") { showCommandHints() },
            lp(WRAP, WRAP)
        )
        page.addView(bottom, lp(MATCH, WRAP))
        return page
    }

    private fun openEditor(f: File) {
        val text = try {
            f.readText()
        } catch (e: Exception) {
            ScriptFXLog.warn("Не удалось открыть '${f.name}': ${e.message}")
            return
        }
        file = f
        original = text
        last = text
        undoStack.clear()
        suppress = true
        edit.setText(text)
        edit.setSelection(0)
        suppress = false
        dirty = false
        nameView.text = f.name
        msgView.text = ""
        updateStatus(text)
        listLayer.visibility = View.GONE
        editorLayer.visibility = View.VISIBLE
        host.refreshBreadcrumb()
        edit.post { edit.requestFocus() }
    }

    private fun closeEditor() {
        if (file == null) return
        file = null
        dirty = false
        undoStack.clear()
        msgView.removeCallbacks(clearMsg)
        msgView.text = ""
        editorLayer.visibility = View.GONE
        listLayer.visibility = View.VISIBLE
        host.refreshBreadcrumb()
    }

    private fun isDirty(): Boolean =
        edit.text?.toString().orEmpty() != original

    private fun onEdited(current: String) {
        if (current.length < last.length) {
            undoStack.addLast(last)
            if (undoStack.size > 100) undoStack.removeFirst()
        }
        last = current
        dirty = current != original
        updateStatus(current)
    }

    private fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        suppress = true
        edit.setText(prev)
        edit.setSelection(prev.length)
        suppress = false
        last = prev
        dirty = prev != original
        updateStatus(prev)
    }

    private fun updateStatus(text: String) {
        val r = ScriptValidator.validate(text, CommandRegistry::isKnown)
        statusView.text = r.message
        statusView.setTextColor(
            if (r.ok) 0xFF55FF55.toInt() else 0xFFFFCC55.toInt()
        )
    }

    private fun save() {
        val f = file ?: return
        val text = edit.text?.toString().orEmpty()
        try {
            f.writeText(text)
            original = text
            dirty = false
        } catch (e: Exception) {
            ScriptFXLog.warn("Не удалось сохранить '${f.name}': ${e.message}")
        }
    }

    private fun flash(message: String) {
        msgView.removeCallbacks(clearMsg)
        msgView.text = message
        msgView.postDelayed(clearMsg, 3000L)
    }

    /** Запускает сохранённую версию файла (как в старой панели). */
    private fun run(f: File) {
        val mc = Minecraft.getInstance()
        mc.execute {
            if (!mc.hasSingleplayerServer()) {
                mc.player?.sendSystemMessage(
                    Component.literal("Запуск скриптов пока доступен только в одиночной игре")
                        .withStyle(ChatFormatting.RED)
                )
                return@execute
            }
            val server = mc.getSingleplayerServer() ?: return@execute
            val playerUuid = mc.player?.uuid ?: return@execute
            server.execute {
                val serverPlayer = server.playerList.getPlayer(playerUuid) ?: return@execute
                val commands = ScriptParser.parse(f.readText())
                ScriptManager.runAdHoc(
                    commands,
                    ScriptContext(server, serverPlayer),
                    f.nameWithoutExtension
                )
            }
            msgView.post { flash("Скрипт запущен!") }
        }
    }

    private fun showCommandHints() {
        val items = CommandDocs.ALL.map { doc ->
            "${doc.template} — ${doc.description}" to {
                val cur = edit.text?.toString().orEmpty()
                val insert = doc.template
                val next = if (cur.isEmpty() || cur.endsWith("\n")) cur + insert else "$cur\n$insert"
                suppress = true
                edit.setText(next)
                edit.setSelection(next.length)
                suppress = false
                onEdited(next)
            }
        }
        // все команды + переход в доки
        overlays.actions(
            "Команды скрипта (${CommandDocs.ALL.size})",
            CommandDocs.ALL.map { doc ->
                "${doc.template} — ${doc.description}" to {
                    val cur = edit.text?.toString().orEmpty()
                    val insert = doc.template
                    val next = if (cur.isEmpty() || cur.endsWith("\n")) cur + insert else "$cur\n$insert"
                    suppress = true
                    edit.setText(next)
                    edit.setSelection(next.length)
                    suppress = false
                    onEdited(next)
                }
            } + listOf("…полная документация" to { host.openDocumentation() })
        )
    }
}