package net.foxmediax.scriptfx.client

import com.google.gson.JsonParser
import net.foxmediax.scriptfx.network.QuestToastPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.max
import kotlin.math.min

data class ClientObjective(val id: String, val text: String, val target: Int, val progress: Int) {
    val done: Boolean get() = progress >= target
}

data class ClientQuest(
    val id: String,
    val title: String,
    val description: String,
    val status: String,
    val objectives: List<ClientObjective>
)

/** Клиентские данные журнала квестов и всплывашки слева сверху. */
object QuestClient {

    // ------------------------------------------------------------ журнал

    @Volatile var quests: List<ClientQuest> = emptyList()
        private set

    /** Растёт при каждом обновлении списка: журнал по нему понимает, что пора перерисоваться. */
    @Volatile var version = 0
        private set

    fun receiveSync(json: String) {
        quests = runCatching {
            JsonParser.parseString(json).asJsonArray.map { e ->
                val o = e.asJsonObject
                ClientQuest(
                    o["id"].asString,
                    o["title"].asString,
                    o["description"].asString,
                    o["status"].asString,
                    o["objectives"].asJsonArray.map { x ->
                        val ob = x.asJsonObject
                        ClientObjective(
                            ob["id"].asString,
                            ob["text"].asString,
                            max(1, ob["target"].asInt),
                            ob["progress"].asInt
                        )
                    }
                )
            }
        }.getOrDefault(emptyList())
        version++
    }

    fun clear() {
        queue.clear()
        current = null
        quests = emptyList()
        version++
    }

    // ------------------------------------------------------------ всплывашка

    private class Toast(val kind: String, val title: String, val text: String)

    private enum class Phase { IN, HOLD, OUT }

    private const val IN_MS = 350f
    private const val OUT_MS = 350f
    private const val GAP_MS = 120L
    private const val MAX_QUEUE = 20

    private val queue = ArrayDeque<Toast>()
    private var current: Toast? = null
    private var phase = Phase.IN
    private var phaseStart = 0L
    private var holdMs = 4000L
    private var nextAt = 0L

    fun receiveToast(p: QuestToastPayload) {
        if (queue.size >= MAX_QUEUE) queue.removeFirst()
        queue.addLast(Toast(p.kind, p.title, p.text))
    }

    private fun accentOf(kind: String): Int = when (kind) {
        "completed" -> 0xFF55FF88.toInt()
        "failed" -> 0xFFFF5555.toInt()
        "updated" -> 0xFF6CB6FF.toInt()
        else -> 0xFFC8B4FF.toInt()
    }

    private fun labelOf(kind: String): String = when (kind) {
        "completed" -> "Квест выполнен"
        "failed" -> "Квест провален"
        "updated" -> "Квест обновлён"
        else -> "Новый квест"
    }

    private fun easeOut(x: Float): Float {
        val i = 1f - x.coerceIn(0f, 1f)
        return 1f - i * i * i
    }

    private fun easeIn(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * c
    }

    private fun argb(alpha: Int, rgb: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

    /** Появляется слева сверху: выезжает из-за края, висит, уезжает обратно, затем следующая. */
    fun renderToast(g: GuiGraphicsExtractor) {
        val now = System.currentTimeMillis()

        var t = current
        if (t == null) {
            if (now < nextAt) return
            t = queue.removeFirstOrNull() ?: return
            current = t
            phase = Phase.IN
            phaseStart = now
            holdMs = (4200L / (1f + 0.3f * queue.size)).toLong().coerceAtLeast(1500L)
        }

        val elapsed = (now - phaseStart).toFloat()
        var slide = 1f       // 0 = за краем экрана, 1 = на месте
        when (phase) {
            Phase.IN -> {
                slide = easeOut(elapsed / IN_MS)
                if (elapsed >= IN_MS) { phase = Phase.HOLD; phaseStart = now }
            }
            Phase.HOLD -> {
                if (elapsed >= holdMs) { phase = Phase.OUT; phaseStart = now }
            }
            Phase.OUT -> {
                if (elapsed >= OUT_MS) {
                    current = null
                    nextAt = now + GAP_MS
                    return
                }
                slide = 1f - easeIn(elapsed / OUT_MS)
            }
        }

        val alpha = (slide * 255).toInt()
        if (alpha < 8) return   // при очень малой альфе текст рисуется непрозрачным

        val font = Minecraft.getInstance().font
        val maxTextW = 170
        val lh = font.lineHeight + 2
        val accent = accentOf(t.kind)
        val label = labelOf(t.kind)
        val title = fit(font, t.title, maxTextW)
        val lines = if (t.text.isBlank()) emptyList() else wrap(font, t.text, maxTextW).take(3)

        val contentW = max(
            font.width(label),
            max(font.width(title), lines.maxOfOrNull { font.width(it) } ?: 0)
        )
        val padX = 8
        val padY = 6
        val stripe = 3
        val w = contentW + padX * 2 + stripe
        val h = padY * 2 + lh * 2 + lines.size * lh

        // слайд слева: x = -(w+10) за краем -> 8 на месте
        val x = (-(w + 10) + (w + 10 + 8) * slide).toInt()
        val y = 10

        g.fill(x, y, x + w, y + h, argb((alpha * 0.92f).toInt(), 0x141420))
        g.fill(x, y, x + stripe, y + h, argb(alpha, accent))
        g.fill(x + stripe, y, x + w, y + 1, argb(alpha / 3, accent))

        val tx = x + stripe + padX
        var ty = y + padY
        g.text(font, label, tx, ty, argb(alpha, accent), true)
        ty += lh
        g.text(font, title, tx, ty, argb(alpha, 0xFFFFFF), true)
        ty += lh
        for (line in lines) {
            g.text(font, line, tx, ty, argb(alpha, 0xCCCCCC), true)
            ty += lh
        }
    }

    private fun fit(font: Font, s: String, maxW: Int): String {
        if (font.width(s) <= maxW) return s
        var cut = s
        while (cut.isNotEmpty() && font.width("$cut…") > maxW) cut = cut.dropLast(1)
        return "$cut…"
    }

    private fun wrap(font: Font, text: String, maxW: Int): List<String> {
        val lines = mutableListOf<String>()
        var cur = StringBuilder()
        for (word in text.split(" ")) {
            val cand = if (cur.isEmpty()) word else "$cur $word"
            if (font.width(cand) > maxW && cur.isNotEmpty()) {
                lines.add(cur.toString())
                cur = StringBuilder(word)
            } else cur = StringBuilder(cand)
        }
        if (cur.isNotEmpty()) lines.add(cur.toString())
        return lines
    }
}