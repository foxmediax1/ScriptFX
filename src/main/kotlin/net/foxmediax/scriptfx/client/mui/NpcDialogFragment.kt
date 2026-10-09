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
import icyllis.modernui.widget.Button
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
        val perChar = ScriptFXConfig.dialogTypewriterMs.coerceAtLeast(1)
        return ((System.currentTimeMillis() - shownAt) / perChar).toInt().coerceIn(0, bodyText.length)
    }
}

class NpcDialogFragment(private val model: NpcDialogModel?) : Fragment() {

    constructor() : this(null)

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val FRAME_MS = 16L
        const val MAX_ALPHA = 0.9f

        const val COL_HEADER = 0xFF1E2A44.toInt()
        const val COL_BODY = 0xFF101018.toInt()
        const val COL_NAME = 0xFFFFD866.toInt()
        const val COL_BTN = 0xFF181828.toInt()
    }

    @Volatile private var rootView: View? = null
    private var scroll: ScrollView? = null
    private var bodyView: TextView? = null
    private var shownChars = -1
    private var userScrolled = false

    private val frame = object : Runnable {
        override fun run() {
            val root = rootView ?: return          // экран закрыт: цикл останавливается
            val m = model ?: return

            root.alpha = m.fade() * MAX_ALPHA

            val n = m.revealedChars()
            if (n != shownChars) {
                shownChars = n
                bodyView?.text = m.bodyText.substring(0, n)
                if (!userScrolled) scrollToEnd()
            }

            val done = m.fade() >= 1f && n >= m.bodyText.length
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
        if (m == null) {                           // восстановление без модели: закрываемся
            val mc = Minecraft.getInstance()
            mc.execute { mc.setScreen(null) }
            return root
        }

        root.alpha = 0f
        root.isFocusableInTouchMode = true

        val keyListener = View.OnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@OnKeyListener false
            val n = keyCode - GLFW.GLFW_KEY_1
            if (n in m.buttons.indices) { pick(m, n + 1); true } else false
        }
        root.setOnKeyListener(keyListener)

        // ---------- слева: имя + текст ----------
        val left = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        left.addView(
            TextView(ctx).apply {
                text = m.npcName
                textSize = 14f
                setTextColor(COL_NAME)
                setSingleLine(true)
                setPadding(dp(ctx, 10), dp(ctx, 5), dp(ctx, 10), dp(ctx, 5))
                background = ColorDrawable(COL_HEADER)
            },
            LinearLayout.LayoutParams(MATCH, WRAP)
        )

        val body = TextView(ctx).apply {
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
        }
        bodyView = body

        val sv = ScrollView(ctx).apply {
            background = ColorDrawable(COL_BODY)
            // колесо или перетаскивание полосы — дальше не автопрокручиваем
            setOnGenericMotionListener { _, _ -> userScrolled = true; false }
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_DOWN) userScrolled = true
                false
            }
        }
        sv.addView(body, ViewGroup.LayoutParams(MATCH, WRAP))
        scroll = sv
        left.addView(sv, LinearLayout.LayoutParams(MATCH, dp(ctx, 130)))

        root.addView(
            left,
            FrameLayout.LayoutParams(dp(ctx, 240), WRAP, Gravity.CENTER_VERTICAL or Gravity.START)
                .apply { leftMargin = dp(ctx, 24) }
        )

        // ---------- справа: кнопки ответов ----------
        val right = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        m.buttons.forEachIndexed { i, label ->
            right.addView(
                Button(ctx).apply {
                    text = "${i + 1}. $label"
                    textSize = 13f
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    background = ColorDrawable(COL_BTN)
                    setOnClickListener { pick(m, i + 1) }
                    setOnKeyListener(keyListener)
                },
                LinearLayout.LayoutParams(MATCH, WRAP).apply { if (i > 0) topMargin = dp(ctx, 6) }
            )
        }
        root.addView(
            right,
            FrameLayout.LayoutParams(dp(ctx, 170), WRAP, Gravity.CENTER_VERTICAL or Gravity.END)
                .apply { rightMargin = dp(ctx, 24) }
        )

        rootView = root
        root.requestFocus()
        root.post(frame)
        return root
    }

    override fun onDestroyView() {
        rootView = null
        scroll = null
        bodyView = null
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
        if (m.fade() <= 0.8f) return               // как раньше: ответ только после появления окна
        if (m.picked) return
        m.picked = true
        m.onChoose(button)                         // дальше — в основной поток (см. NpcDialogClient)
    }

    private fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density + 0.5f).toInt()
}