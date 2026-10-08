package net.foxmediax.scriptfx.client.mui

import icyllis.modernui.core.Context
import icyllis.modernui.fragment.Fragment
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.util.DataSet
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.LayoutInflater
import icyllis.modernui.view.View
import icyllis.modernui.view.ViewGroup
import icyllis.modernui.widget.Button
import icyllis.modernui.widget.EditText
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.TextView
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

class CenteredChatFragment : Fragment() {

    private var messagesContainer: LinearLayout? = null
    private var input: EditText? = null
    private var historyIndex = -1
    private val sentHistory = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: DataSet?
    ): View {
        val ctx = requireContext()

        // Корневой фрейм на весь экран
        val root = FrameLayout(ctx)

        // Центральная панель
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            background = ColorDrawable(0xCC1A1A1E.toInt()) // полупрозрачная тёмная
        }

        // Заголовок
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

        // Скролл с сообщениями
        val scroll = ScrollView(ctx)
        val messages = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
        }
        messagesContainer = messages
        scroll.addView(
            messages,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        panel.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // Низ: поле ввода + отправить
        val bottom = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 12, 0, 0)
        }

        val edit = EditText(ctx).apply {
            hint = "Сообщение..."
            textSize = 14f
            // Enter — отправка (если API поддерживает editor action)
            setSingleLine(true)
        }
        input = edit

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

        // Панель по центру, фиксированная ширина ~420dp-эквивалент
        val panelLp = FrameLayout.LayoutParams(
            dp(ctx, 420),
            dp(ctx, 320),
            Gravity.CENTER
        )
        root.addView(panel, panelLp)

        // Заполняем историю из ванильного чата
        loadVanillaHistory()

        return root
    }

    private fun loadVanillaHistory() {
        val mc = Minecraft.getInstance()
        val container = messagesContainer ?: return
        container.removeAllViews()

        // recentChat — строки, которые игрок отправлял; для входящих берём allMessages, если доступно
        try {
            val chat = mc.gui.chat
            // Пробуем вытащить последние сообщения (API может чуть отличаться — см. ниже)
            val recent = chat.recentChat
            // Показываем recent как fallback + подсказку
            if (recent.isEmpty()) {
                addLine("Нет сообщений в истории. Напиши что-нибудь.")
            } else {
                // recentChat обычно только исходящие команды/сообщения игрока
                for (line in recent.takeLast(30)) {
                    addLine(line)
                }
            }
        } catch (t: Throwable) {
            addLine("История чата недоступна: ${t.message}")
        }
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

    private fun sendCurrent() {
        val edit = input ?: return
        val raw = edit.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val connection = player.connection

        if (raw.startsWith("/")) {
            connection.sendCommand(raw.substring(1))
        } else {
            connection.sendChat(raw)
        }

        sentHistory.add(raw)
        historyIndex = sentHistory.size
        addLine("§7» §f$raw")
        edit.setText("")
    }

    private fun dp(ctx: Context, value: Int): Int {
        val density = ctx.resources.displayMetrics.density
        return (value * density + 0.5f).toInt()
    }
}