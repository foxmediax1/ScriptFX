package net.foxmediax.scriptfx.client.mui

import icyllis.modernui.core.Context
import icyllis.modernui.fragment.Fragment
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.util.DataSet
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.KeyEvent
import icyllis.modernui.view.LayoutInflater
import icyllis.modernui.view.MotionEvent
import icyllis.modernui.view.View
import icyllis.modernui.view.ViewGroup
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.config.ScriptFXConfig
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import kotlin.math.max

/** Состояние диалога живёт вне фрагмента: если экран пересоздан, печать текста не начинается заново. */
class NpcDialogModel(
    val npcName: String,
    fullText: String,
    val buttons: List<String>,
    val onChoose: (Int) -> Unit
) {
    val shownAt = System.currentTimeMillis()
    @Volatile var picked = false
    @Volatile var textDone = false

    // "Страж: кто идёт?" -> "кто идёт?" (имя уже в шапке)
    val bodyText: String = fullText.trim().let { t ->
        val prefix = "$npcName:"
        if (npcName.isNotBlank() && t.startsWith(prefix, ignoreCase = true))
            t.substring(prefix.length).trim() else t
    }

    fun fade(): Float {
        val ms = ScriptFXConfig.dialogFadeMs.coerceAtLeast(1).toFloat()
        val t = ((System.currentTimeMillis() - shownAt) / ms).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun revealedChars(): Int {
        if (textDone) return bodyText.length
        val perChar = ScriptFXConfig.dialogTypewriterMs.coerceAtLeast(1)
        return ((System.currentTimeMillis() - shownAt) / perChar).toInt().coerceIn(0, bodyText.length)
    }

    fun skipTypewriter() {
        textDone = true
    }

    fun isTextComplete(): Boolean = revealedChars() >= bodyText.length
}

class NpcDialogFragment(private val model: NpcDialogModel?) : Fragment() {

    constructor() : this(null)

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val FRAME_MS = 16L
        const val MAX_ALPHA = 0.97f

        const val COL_CARD = 0xF2141420.toInt()
        const val COL_CARD_STROKE = 0x66C8B4FF
        const val COL_BODY = 0xFFF2F2F7.toInt()
        const val COL_BTN = 0xFF1B1B2C.toInt()
        const val COL_BTN_HOVER = 0xFF2A2446.toInt()
        const val COL_BTN_PRESSED = 0xFF3C3162.toInt()
        const val COL_CHIP_TEXT = 0xFF14101F.toInt()
        const val COL_HINT = 0xFF8888A0.toInt()
    }

    @Volatile private var rootView: View? = null
    private var scroll: ScrollView? = null
    private var bodyView: TextView? = null
    private var buttonsPanel: LinearLayout? = null
    private var hintView: TextView? = null
    private var shownChars = -1
    private var userScrolled = false
    private var buttonsVisible = false

    private val frame = object : Runnable {
        override fun run() {
            val root = rootView ?: return
            val m = model ?: return

            root.alpha = m.fade() * MAX_ALPHA

            val n = m.revealedChars()
            if (n != shownChars) {
                shownChars = n
                bodyView?.text = m.bodyText.substring(0, n)
                if (!userScrolled) scrollToEnd()
            }

            // Кнопки появляются после полного текста (или skip)
            val complete = m.isTextComplete()
            if (complete && !buttonsVisible) {
                buttonsVisible = true
                buttonsPanel?.alpha = 1f
                buttonsPanel?.visibility = View.VISIBLE
                hintView?.text = "1–${m.buttons.size.coerceAtLeast(1)} · выбор"
            } else if (!complete) {
                buttonsPanel?.alpha = 0.35f
            }

            val done = m.fade() >= 1f && complete
            if (!done) root.postDelayed(this, FRAME_MS)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: DataSet?
    ): View {
        val ctx = requireContext()
        val root = FrameLayout(ctx)
        val m = model
        if (m == null) {
            val mc = Minecraft.getInstance()
            mc.execute { mc.setScreen(null) }
            return root
        }

        root.alpha = 0f
        root.isFocusableInTouchMode = true
        root.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            // Space / Enter — пропуск печати
            if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
                if (!m.isTextComplete()) {
                    m.skipTypewriter()
                    return@setOnKeyListener true
                }
            }
            val n = keyCode - GLFW.GLFW_KEY_1
            if (n in m.buttons.indices) {
                pick(m, n + 1)
                true
            } else false
        }

        // Клик по карточке — skip typewriter
        root.setOnClickListener {
            if (!m.isTextComplete()) m.skipTypewriter()
        }

        // ---------- слева: карточка с именем и текстом ----------
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyle.rounded(ctx, COL_CARD, 10f, COL_CARD_STROKE, 1)
        }

        val plate = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 12), dp(ctx, 9), dp(ctx, 12), dp(ctx, 9))
        }
        plate.addView(
            View(ctx).apply { background = UiStyle.rounded(ctx, UiStyle.ACCENT, 2f) },
            LinearLayout.LayoutParams(dp(ctx, 3), dp(ctx, 16)).apply { rightMargin = dp(ctx, 8) }
        )
        plate.addView(
            TextView(ctx).apply {
                text = m.npcName
                textSize = 14f
                setTextColor(UiStyle.ACCENT)
                setSingleLine(true)
            },
            LinearLayout.LayoutParams(0, WRAP, 1f)
        )
        card.addView(plate, LinearLayout.LayoutParams(MATCH, WRAP))
        card.addView(
            View(ctx).apply { background = ColorDrawable(0x33FFFFFF) },
            LinearLayout.LayoutParams(MATCH, maxOf(1, dp(ctx, 1)))
        )

        val body = TextView(ctx).apply {
            textSize = 14f
            setTextColor(COL_BODY)
            setPadding(dp(ctx, 12), dp(ctx, 9), dp(ctx, 12), dp(ctx, 9))
        }
        bodyView = body

        val sv = ScrollView(ctx).apply {
            setOnGenericMotionListener { _, _ -> userScrolled = true; false }
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_DOWN) userScrolled = true
                false
            }
        }
        sv.addView(body, ViewGroup.LayoutParams(MATCH, WRAP))
        scroll = sv
        card.addView(sv, LinearLayout.LayoutParams(MATCH, dp(ctx, 130)))

        hintView = TextView(ctx).apply {
            text = "Пробел — пропуск текста"
            textSize = 10f
            setTextColor(COL_HINT)
            setPadding(dp(ctx, 12), 0, dp(ctx, 12), dp(ctx, 8))
        }
        card.addView(hintView, LinearLayout.LayoutParams(MATCH, WRAP))

        root.addView(
            card,
            FrameLayout.LayoutParams(dp(ctx, 270), WRAP, Gravity.CENTER_VERTICAL or Gravity.START)
                .apply { leftMargin = dp(ctx, 24) }
        )

        // ---------- справа: варианты ответа ----------
        val right = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            alpha = 0.35f
        }
        buttonsPanel = right
        m.buttons.forEachIndexed { i, label ->
            right.addView(
                answerRow(ctx, m, i, label),
                LinearLayout.LayoutParams(MATCH, WRAP).apply { if (i > 0) topMargin = dp(ctx, 6) }
            )
        }
        root.addView(
            right,
            FrameLayout.LayoutParams(dp(ctx, 190), WRAP, Gravity.CENTER_VERTICAL or Gravity.END)
                .apply { rightMargin = dp(ctx, 24) }
        )

        rootView = root
        root.requestFocus()
        root.post(frame)
        return root
    }

    private fun answerRow(ctx: Context, m: NpcDialogModel, index: Int, label: String): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiStyle.buttonBg(ctx, COL_BTN, COL_BTN_HOVER, COL_BTN_PRESSED, 8f)
            setPadding(dp(ctx, 8), dp(ctx, 7), dp(ctx, 10), dp(ctx, 7))
            isClickable = true
            setOnClickListener { pick(m, index + 1) }
        }
        row.addView(
            TextView(ctx).apply {
                text = "${index + 1}"
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(COL_CHIP_TEXT)
                background = UiStyle.rounded(ctx, UiStyle.ACCENT, 5f)
            },
            LinearLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18)).apply { rightMargin = dp(ctx, 8) }
        )
        row.addView(
            TextView(ctx).apply {
                text = label
                textSize = 13f
                setTextColor(0xFFFFFFFF.toInt())
            },
            LinearLayout.LayoutParams(0, WRAP, 1f)
        )
        return row
    }

    override fun onDestroyView() {
        rootView = null
        scroll = null
        bodyView = null
        buttonsPanel = null
        hintView = null
        super.onDestroyView()
    }

    private fun scrollToEnd() {
        val sv = scroll ?: return
        sv.post {
            val child = sv.getChildAt(0) ?: return@post
            sv.scrollTo(0, max(0, child.height - sv.height))
        }
    }

    private fun pick(m: NpcDialogModel, button: Int) {
        if (m.fade() <= 0.8f) return
        // Пока текст не допечатан — сначала skip, второй клик по кнопке выберет
        if (!m.isTextComplete()) {
            m.skipTypewriter()
            return
        }
        if (m.picked) return
        m.picked = true
        m.onChoose(button)
    }

    private fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density + 0.5f).toInt()
}