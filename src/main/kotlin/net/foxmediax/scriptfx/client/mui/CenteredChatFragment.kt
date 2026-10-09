package net.foxmediax.scriptfx.client.mui

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.suggestion.Suggestion
import icyllis.modernui.core.Context
import icyllis.modernui.fragment.Fragment
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.text.Editable
import icyllis.modernui.text.TextWatcher
import icyllis.modernui.util.DataSet
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.KeyEvent
import icyllis.modernui.view.LayoutInflater
import icyllis.modernui.view.View
import icyllis.modernui.view.ViewGroup
import icyllis.modernui.widget.Button
import icyllis.modernui.widget.EditText
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.mixin.client.ChatComponentAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.chat.GuiMessage
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

/**
 * ВАЖНО: Modern UI работает в своём UI-потоке. Всё, что трогает Minecraft
 * (экраны, курсор, сеть, чат, команды), выполняем через Minecraft.execute { } (основной поток).
 */
class CenteredChatFragment : Fragment() {

    private companion object {
        const val PANEL_W_DP = 420
        const val PANEL_H_DP = 440
        const val PANEL_MAX_H_RATIO = 0.8f
        const val MAX_LINES = 100
        const val POLL_MS = 250L
        const val MAX_SUGGEST_VISIBLE = 7
    }

    @Volatile private var rootView: View? = null
    private var scroll: ScrollView? = null
    private var messagesContainer: LinearLayout? = null
    private var suggestionBox: LinearLayout? = null
    private var input: EditText? = null

    private var shownLines: List<String>? = null

    // --- история введённого (стрелки вверх/вниз) ---
    private var history: List<String> = emptyList()
    private var historyIndex = -1
    private var draft = ""

    // --- подсказки команд ---
    private var suggestions: List<Suggestion> = emptyList()
    private var suggestionIndex = 0
    private var suggestSeq = 0
    private var suppressWatcher = false

    private val poll = object : Runnable {
        override fun run() {
            val root = rootView ?: return
            requestRefresh()
            root.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: DataSet?
    ): View {
        val ctx = requireContext()
        val root = FrameLayout(ctx)

        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            background = ColorDrawable(0xCC1A1A1E.toInt())
        }

        val header = TextView(ctx).apply {
            text = "Чат"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }
        panel.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val scrollView = ScrollView(ctx)
        scroll = scrollView
        val messages = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
        }
        messagesContainer = messages
        scrollView.addView(
            messages,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        panel.addView(
            scrollView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val sBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(8, 4, 8, 4)
            background = ColorDrawable(0xE6101018.toInt())
        }
        suggestionBox = sBox
        panel.addView(
            sBox,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val bottom = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 12, 0, 0)
        }

        val edit = EditText(ctx).apply {
            hint = "Сообщение..."
            textSize = 14f
            setSingleLine(true)
        }
        input = edit

        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (suppressWatcher) return
                historyIndex = -1
                requestSuggestions()
            }
        })

        // Modern UI EditText: нет setOnEditorActionListener — только setOnKeyListener
        edit.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                GLFW.GLFW_KEY_ESCAPE -> {
                    closeChat()
                    true
                }
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    sendCurrent()
                    true
                }
                GLFW.GLFW_KEY_TAB -> {
                    if (suggestions.isNotEmpty()) applySuggestion()
                    true
                }
                GLFW.GLFW_KEY_UP -> {
                    if (suggestions.isNotEmpty()) moveSuggestion(-1) else navigateHistory(-1)
                    true
                }
                GLFW.GLFW_KEY_DOWN -> {
                    if (suggestions.isNotEmpty()) moveSuggestion(+1) else navigateHistory(+1)
                    true
                }
                else -> false
            }
        }

        val sendBtn = Button(ctx).apply {
            text = "➤"
            setOnClickListener { sendCurrent() }
        }

        bottom.addView(
            edit,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        bottom.addView(
            sendBtn,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = 8 }
        )
        panel.addView(
            bottom,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val maxH = (ctx.resources.displayMetrics.heightPixels * PANEL_MAX_H_RATIO).toInt()
        val panelH = min(dp(ctx, PANEL_H_DP), maxH)
        root.addView(
            panel,
            FrameLayout.LayoutParams(dp(ctx, PANEL_W_DP), panelH, Gravity.CENTER)
        )

        rootView = root
        requestRefresh()
        loadSentHistory()
        root.postDelayed(poll, POLL_MS)
        edit.post { edit.requestFocus() }

        return root
    }

    override fun onDestroyView() {
        rootView = null
        scroll = null
        messagesContainer = null
        suggestionBox = null
        input = null
        shownLines = null
        suggestions = emptyList()
        history = emptyList()
        historyIndex = -1
        super.onDestroyView()
    }

    // ------------------------------------------------------------------
    // История сообщений чата (основной поток → UI-поток)

    private fun requestRefresh() {
        val mc = Minecraft.getInstance()
        mc.execute {
            val lines = chronological().map { msg ->
                // GuiMessage в 26.1: content() — Component
                msg.content().string
            }
            rootView?.post { applyHistory(lines) }
        }
    }

    /** Только основной поток Minecraft. */
    private fun chronological(): List<GuiMessage> {
        val chat = Minecraft.getInstance().gui.chat
        val all = (chat as ChatComponentAccessor).`scriptfx$getAllMessages`()
        if (all.isEmpty()) return emptyList()

        val copy = ArrayList(all)
        // allMessages обычно newest-first
        val newestFirst = copy.size < 2 || copy.first().addedTime() >= copy.last().addedTime()
        if (newestFirst) copy.reverse()
        return copy.takeLast(MAX_LINES)
    }

    /** UI-поток Modern UI. */
    private fun applyHistory(lines: List<String>) {
        if (lines == shownLines) return
        shownLines = lines
        val container = messagesContainer ?: return
        container.removeAllViews()
        if (lines.isEmpty()) addLine("Чат пуст") else lines.forEach { addLine(it) }
        scroll?.post { scroll?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addLine(text: String) {
        val ctx = context ?: return
        val container = messagesContainer ?: return
        val tv = TextView(ctx).apply {
            this.text = text
            textSize = 13f
            setPadding(4, 4, 4, 4)
        }
        container.addView(
            tv,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    // ------------------------------------------------------------------
    // История ввода (↑ / ↓)

    private fun loadSentHistory() {
        val mc = Minecraft.getInstance()
        mc.execute {
            val snapshot = ArrayList(mc.gui.chat.recentChat)
            rootView?.post { history = snapshot }
        }
    }

    private fun navigateHistory(delta: Int) {
        val edit = input ?: return
        if (history.isEmpty()) return

        if (historyIndex == -1) {
            if (delta > 0) return
            draft = edit.text?.toString().orEmpty()
            historyIndex = history.size
        }

        val next = historyIndex + delta
        if (next < 0) return
        if (next >= history.size) {
            historyIndex = -1
            setInput(draft)
            return
        }
        historyIndex = next
        setInput(history[next])
    }

    private fun setInput(text: String) {
        val edit = input ?: return
        suppressWatcher = true
        edit.setText(text)
        edit.setSelection(text.length)
        suppressWatcher = false
    }

    // ------------------------------------------------------------------
    // Подсказки команд (Brigadier)

    private fun requestSuggestions() {
        val edit = input ?: return
        val text = edit.text?.toString().orEmpty()
        if (!text.startsWith("/")) {
            hideSuggestions()
            return
        }
        val caret = edit.selectionStart.coerceIn(0, text.length)
        val seq = ++suggestSeq

        val mc = Minecraft.getInstance()
        mc.execute {
            try {
                val connection = mc.player?.connection
                if (connection == null) {
                    rootView?.post { hideSuggestions() }
                    return@execute
                }
                val dispatcher = connection.commands
                val reader = StringReader(text)
                if (reader.canRead() && reader.peek() == '/') reader.skip()
                val parse = dispatcher.parse(reader, connection.suggestionsProvider)

                dispatcher.getCompletionSuggestions(parse, caret).thenAccept { result ->
                    rootView?.post {
                        if (seq == suggestSeq) showSuggestions(text, result.list)
                    }
                }
            } catch (_: Exception) {
                rootView?.post { hideSuggestions() }
            }
        }
    }

    private fun showSuggestions(forText: String, list: List<Suggestion>) {
        val current = input?.text?.toString().orEmpty()
        if (current != forText) return
        suggestions = list
        suggestionIndex = 0
        renderSuggestions()
    }

    private fun hideSuggestions() {
        suggestSeq++
        suggestions = emptyList()
        suggestionIndex = 0
        suggestionBox?.removeAllViews()
        suggestionBox?.visibility = View.GONE
    }

    private fun renderSuggestions() {
        val box = suggestionBox ?: return
        val ctx = context ?: return
        box.removeAllViews()
        if (suggestions.isEmpty()) {
            box.visibility = View.GONE
            return
        }

        val start = (suggestionIndex - MAX_SUGGEST_VISIBLE / 2)
            .coerceIn(0, max(0, suggestions.size - MAX_SUGGEST_VISIBLE))
        val end = min(suggestions.size, start + MAX_SUGGEST_VISIBLE)

        for (i in start until end) {
            val selected = i == suggestionIndex
            val tv = TextView(ctx).apply {
                text = suggestions[i].text
                textSize = 13f
                setTextColor(if (selected) 0xFFFFFF55.toInt() else 0xFFAAAAAA.toInt())
                setPadding(6, 3, 6, 3)
                if (selected) background = ColorDrawable(0x55FFFFFF)
                setOnClickListener {
                    suggestionIndex = i
                    applySuggestion()
                }
            }
            box.addView(
                tv,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        box.visibility = View.VISIBLE
    }

    private fun moveSuggestion(delta: Int) {
        if (suggestions.isEmpty()) return
        suggestionIndex = (suggestionIndex + delta + suggestions.size) % suggestions.size
        renderSuggestions()
    }

    private fun applySuggestion() {
        val edit = input ?: return
        val picked = suggestions.getOrNull(suggestionIndex) ?: return
        val newText = picked.apply(edit.text?.toString().orEmpty())

        suppressWatcher = true
        edit.setText(newText)
        edit.setSelection(newText.length)
        suppressWatcher = false

        requestSuggestions()
    }

    // ------------------------------------------------------------------

    private fun closeChat() {
        val mc = Minecraft.getInstance()
        mc.execute { mc.setScreen(null) }
    }

    private fun sendCurrent() {
        val raw = input?.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return

        val mc = Minecraft.getInstance()
        mc.execute {
            val connection = mc.player?.connection ?: return@execute
            mc.gui.chat.addRecentChat(raw)
            if (raw.startsWith("/")) {
                connection.sendCommand(raw.substring(1))
            } else {
                connection.sendChat(raw)
            }
            mc.setScreen(null)
        }
    }

    private fun dp(ctx: Context, value: Int): Int {
        val density = ctx.resources.displayMetrics.density
        return (value * density + 0.5f).toInt()
    }
}