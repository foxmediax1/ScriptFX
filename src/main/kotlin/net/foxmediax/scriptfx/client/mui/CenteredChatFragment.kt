package net.foxmediax.scriptfx.client.mui

import icyllis.modernui.core.Context
import icyllis.modernui.fragment.Fragment
import icyllis.modernui.graphics.drawable.ColorDrawable
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
import kotlin.math.min

/**
 * ВАЖНО: Modern UI работает в своём UI-потоке. Всё, что трогает Minecraft
 * (экраны, курсор, сеть, чат), выполняем через Minecraft.execute { } (основной поток).
 */
class CenteredChatFragment : Fragment() {

    private companion object {
        const val PANEL_W_DP = 420
        const val PANEL_H_DP = 440
        const val PANEL_MAX_H_RATIO = 0.8f
        const val MAX_LINES = 100
        const val POLL_MS = 250L
    }

    @Volatile private var rootView: View? = null
    private var scroll: ScrollView? = null
    private var messagesContainer: LinearLayout? = null
    private var input: EditText? = null

    private var shownLines: List<String>? = null

    private val poll = object : Runnable {
        override fun run() {
            val root = rootView ?: return   // экран закрыт: цикл останавливается
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

        edit.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
            ) {
                sendCurrent()
                true
            } else false
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
        root.postDelayed(poll, POLL_MS)
        edit.post { edit.requestFocus() }

        return root
    }

    override fun onDestroyView() {
        rootView = null
        scroll = null
        messagesContainer = null
        input = null
        shownLines = null
        super.onDestroyView()
    }

    // ------------------------------------------------------------------
    // История. Снимок берём в основном потоке, в UI-поток передаём готовые строки.

    private fun requestRefresh() {
        val mc = Minecraft.getInstance()
        mc.execute {
            val lines = chronological().map { it.content().string }
            rootView?.post { applyHistory(lines) }
        }
    }

    /** Основной поток. Сообщения ванильного чата от старых к новым. */
    private fun chronological(): List<GuiMessage> {
        val chat = Minecraft.getInstance().gui.chat
        val all = ((chat as Any) as ChatComponentAccessor).`scriptfx$getAllMessages`()
        if (all.isEmpty()) return emptyList()

        val copy = ArrayList(all)
        val newestFirst = copy.size < 2 || copy.first().addedTime() >= copy.last().addedTime()
        if (newestFirst) copy.reverse()
        return copy.takeLast(MAX_LINES)
    }

    /** UI-поток. */
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

    private fun sendCurrent() {
        val raw = input?.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return

        val mc = Minecraft.getInstance()
        // Основной поток: отправка пакета, история ввода и закрытие экрана (захват курсора).
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