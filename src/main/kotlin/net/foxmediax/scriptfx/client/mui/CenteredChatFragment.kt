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
import net.foxmediax.scriptfx.config.ScriptFXConfig

/**
 * Центрированный чат на Modern UI.
 * Minecraft (сеть, чат, экраны) — только через Minecraft.execute { }.
 */
class CenteredChatFragment : Fragment() {

    private companion object {
        const val PANEL_W_DP = 460
        const val PANEL_H_DP = 480
        const val PANEL_MAX_H_RATIO = 0.82f
        const val MAX_LINES = 100
        const val POLL_MS = 250L
        const val MAX_SUGGEST_VISIBLE = 7

        const val COL_SCRIM = 0x99080810.toInt()
        const val COL_PANEL = 0xF01A1A1E.toInt()
        const val COL_MSG_BG = 0xFF101014.toInt()
        const val COL_INPUT_BG = 0xFF0C0C12.toInt()
        const val COL_INPUT_BORDER = 0xFF2A2140.toInt()
        const val COL_TEXT = 0xFFEDEDED.toInt()
        const val COL_HINT = 0xFF888899.toInt()
        const val COL_SUGGEST_BG = 0xE6121220.toInt()
        const val COL_SUGGEST_SEL = 0x55FFFFFF.toInt()
        const val COL_BTN = 0xFF2A2140.toInt()

        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    @Volatile private var rootView: View? = null
    private var scroll: ScrollView? = null
    private var messagesContainer: LinearLayout? = null
    private var suggestionBox: LinearLayout? = null
    private var input: EditText? = null

    private var shownLines: List<String>? = null

    private var history: List<String> = emptyList()
    private var historyIndex = -1
    private var draft = ""

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

    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: DataSet?
    ): View {
        val ctx = requireContext()
        val root = FrameLayout(ctx).apply {
            background = ColorDrawable(COL_SCRIM)
        }

        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
            background = UiStyle.rounded(ctx, COL_PANEL, 10f, UiStyle.ACCENT, 1)
        }

        // --- заголовок ---
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        header.addView(
            TextView(ctx).apply {
                text = "Чат"
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(UiStyle.ACCENT)
            },
            lp(MATCH, WRAP)
        )
        header.addView(
            View(ctx).apply {
                background = ColorDrawable(UiStyle.ACCENT_DARK)
            },
            LinearLayout.LayoutParams(MATCH, dp(ctx, 2)).apply {
                topMargin = dp(ctx, 8)
            }
        )
        panel.addView(
            header,
            LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(ctx, 8)
            }
        )

        // --- сообщения ---
        val scrollView = ScrollView(ctx).apply {
            background = UiStyle.rounded(ctx, COL_MSG_BG, 6f)
            setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
        }
        scroll = scrollView
        val messages = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 2), dp(ctx, 2), dp(ctx, 2), dp(ctx, 2))
        }
        messagesContainer = messages
        scrollView.addView(messages, ViewGroup.LayoutParams(MATCH, WRAP))
        panel.addView(
            scrollView,
            LinearLayout.LayoutParams(MATCH, 0, 1f).apply {
                bottomMargin = dp(ctx, 6)
            }
        )

        // --- подсказки команд ---
        val sBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(ctx, 6), dp(ctx, 4), dp(ctx, 6), dp(ctx, 4))
            background = UiStyle.rounded(ctx, COL_SUGGEST_BG, 6f, UiStyle.ACCENT_DARK, 1)
        }
        suggestionBox = sBox
        panel.addView(
            sBox,
            LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(ctx, 6)
            }
        )

        // --- ввод ---
        val bottom = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val edit = EditText(ctx).apply {
            hint = "Напишите сообщение…"
            textSize = 14f
            setSingleLine(true)
            setTextColor(COL_TEXT)
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
            background = UiStyle.rounded(ctx, COL_INPUT_BG, 6f, COL_INPUT_BORDER, 1)
            minimumHeight = dp(ctx, 36)
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
            textSize = 14f
            setTextColor(COL_TEXT)
            setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8))
            background = UiStyle.buttonBg(
                ctx,
                COL_BTN,
                UiStyle.mix(COL_BTN, 0xFFFFFFFF.toInt(), 0.12f),
                UiStyle.mix(COL_BTN, 0xFF000000.toInt(), 0.25f),
                radiusDp = 6f
            )
            setOnClickListener { sendCurrent() }
        }

        bottom.addView(edit, LinearLayout.LayoutParams(0, WRAP, 1f))
        bottom.addView(
            sendBtn,
            LinearLayout.LayoutParams(WRAP, WRAP).apply {
                leftMargin = dp(ctx, 8)
            }
        )
        panel.addView(bottom, lp(MATCH, WRAP))

        panel.addView(
            TextView(ctx).apply {
                text = "Enter — отправить · Esc — закрыть · ↑↓ — история · Tab — подсказка"
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(COL_HINT)
                setPadding(0, dp(ctx, 8), 0, 0)
            },
            lp(MATCH, WRAP)
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
        rootView?.removeCallbacks(poll)
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
    // История сообщений чата

    private var shownKeys: List<String>? = null

    private fun requestRefresh() {
        val mc = Minecraft.getInstance()
        mc.execute {
            val msgs = chronological()
            val keys = msgs.map { it.content().string }
            rootView?.post { applyHistory(msgs, keys) }
        }
    }

    private fun chronological(): List<GuiMessage> {
        val chat = Minecraft.getInstance().gui.chat
        val all = (chat as ChatComponentAccessor).`scriptfx$getAllMessages`()
        if (all.isEmpty()) return emptyList()

        val copy = ArrayList(all)
        val newestFirst = copy.size < 2 || copy.first().addedTime() >= copy.last().addedTime()
        if (newestFirst) copy.reverse()
        return copy.takeLast(MAX_LINES)
    }

    private fun addSystemLine(text: String) {
        val ctx = context ?: return
        val container = messagesContainer ?: return
        container.addView(
            TextView(ctx).apply {
                this.text = text
                textSize = 12f
                setTextColor(COL_HINT)
                gravity = Gravity.CENTER
                setPadding(dp(ctx, 8), dp(ctx, 12), dp(ctx, 8), dp(ctx, 12))
            },
            LinearLayout.LayoutParams(MATCH, WRAP)
        )
    }

    private fun addChatBubble(component: net.minecraft.network.chat.Component) {
        val ctx = context ?: return
        val container = messagesContainer ?: return
        val full = component.string.replace('\n', ' ').trim()
        if (full.isEmpty()) return

        val (prefix, body, prefixColor) = splitSpeaker(full)

        val bubble = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6))
            // фон + обводка из настроек мода (как на скрине)
            background = UiStyle.rounded(
                ctx,
                ScriptFXConfig.messageBg,
                6f,
                ScriptFXConfig.messageBorder,
                2
            )
        }

        // «голова» / маркер слева
        if (prefix.isNotEmpty()) {
            bubble.addView(
                TextView(ctx).apply {
                    text = "👤"
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setPadding(0, 0, dp(ctx, 8), 0)
                },
                LinearLayout.LayoutParams(WRAP, WRAP)
            )
        }

        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        if (prefix.isNotEmpty()) {
            textCol.addView(
                TextView(ctx).apply {
                    text = prefix
                    textSize = 13f
                    setTextColor(prefixColor)
                },
                LinearLayout.LayoutParams(WRAP, WRAP)
            )
        }

        textCol.addView(
            TextView(ctx).apply {
                text = body
                textSize = 13f
                setTextColor(COL_TEXT)
            },
            LinearLayout.LayoutParams(0, WRAP, 1f)
        )

        bubble.addView(textCol, LinearLayout.LayoutParams(0, WRAP, 1f))

        container.addView(
            bubble,
            LinearLayout.LayoutParams(MATCH, WRAP).apply {
                topMargin = dp(ctx, 4)
                bottomMargin = dp(ctx, 4)
            }
        )
    }

    /** prefix включает ":", body — остаток. */
    private fun splitSpeaker(full: String): Triple<String, String, Int> {
        // printNPC: "[Имя]: текст" или "[Имя] [ремарка]: текст"
        val bracket = Regex("""^(\[[^\]]+](?:\s*\[[^\]]+])?:\s*)(.*)""").find(full)
        if (bracket != null) {
            return Triple(bracket.groupValues[1], bracket.groupValues[2], UiStyle.ACCENT)
        }
        // игрок: "Ник: текст"
        val player = Regex("""^([A-Za-z0-9_]{1,16}:\s*)(.*)""").find(full)
        if (player != null) {
            return Triple(player.groupValues[1], player.groupValues[2], 0xFFFFFF55.toInt()) // жёлтый как в оверлее
        }
        return Triple("", full, COL_TEXT)
    }

    private fun applyHistory(msgs: List<GuiMessage>, keys: List<String>) {
        if (keys == shownKeys) return
        shownKeys = keys
        val container = messagesContainer ?: return
        container.removeAllViews()
        if (msgs.isEmpty()) {
            addSystemLine("Чат пуст")
        } else {
            msgs.forEach { addChatBubble(it.content()) }
        }
        scroll?.post { scroll?.fullScroll(View.FOCUS_DOWN) }
    }

    // ------------------------------------------------------------------
    // История ввода

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
            draft = edit.text.toString()
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
    // Подсказки команд

    private fun requestSuggestions() {
        val text = input?.text?.toString().orEmpty()
        if (!text.startsWith("/")) {
            hideSuggestions()
            return
        }
        val caret = input?.selectionStart?.coerceAtLeast(0) ?: text.length
        val seq = ++suggestSeq

        val mc = Minecraft.getInstance()
        mc.execute {
            try {
                val connection = mc.connection
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
                setPadding(dp(ctx, 6), dp(ctx, 4), dp(ctx, 6), dp(ctx, 4))
                if (selected) {
                    background = UiStyle.rounded(ctx, COL_SUGGEST_SEL, 4f)
                }
                setOnClickListener {
                    suggestionIndex = i
                    applySuggestion()
                }
            }
            box.addView(tv, LinearLayout.LayoutParams(MATCH, WRAP))
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
        val raw = input!!.text.toString().trim()
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

    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)

    private fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density + 0.5f).toInt()

}