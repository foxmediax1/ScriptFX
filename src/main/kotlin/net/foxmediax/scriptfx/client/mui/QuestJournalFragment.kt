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
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.ClientQuest
import net.foxmediax.scriptfx.client.QuestClient
import net.foxmediax.scriptfx.config.ScriptFXConfig
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

/** Журнал квестов (клавиша Y): книжный разворот на Modern UI. */
class QuestJournalFragment : Fragment() {

    private enum class Tab(val title: String, val status: String) {
        ACTIVE("Активные", "active"),
        COMPLETED("Выполненные", "completed"),
        FAILED("Проваленные", "failed")
    }

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val POLL_MS = 250L
    }

    @Volatile private var rootView: View? = null
    private var tabsBox: LinearLayout? = null
    private var listBox: LinearLayout? = null
    private var detailBox: LinearLayout? = null

    private var tab = Tab.ACTIVE
    private var selectedId: String? = null
    private var shownVersion = -1

    private val poll = object : Runnable {
        override fun run() {
            val root = rootView ?: return
            if (QuestClient.version != shownVersion) refresh()
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

        root.isFocusableInTouchMode = true
        root.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == GLFW.GLFW_KEY_Y || keyCode == GLFW.GLFW_KEY_ESCAPE)
            ) {
                closeJournal()
                true
            } else false
        }

        val dm = ctx.resources.displayMetrics
        val bookW = min(dp(ctx, 700), (dm.widthPixels * 0.94f).toInt())
        val bookH = min(dp(ctx, 430), (dm.heightPixels * 0.9f).toInt())

        val book = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = UiStyle.rounded(ctx, ScriptFXConfig.questCover, 14f, UiStyle.ACCENT_DARK, 2)
            setPadding(dp(ctx, 10), dp(ctx, 10), dp(ctx, 10), dp(ctx, 10))
        }

        val left = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyle.rounded(ctx, ScriptFXConfig.questPaperL, 8f)
            setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 14), dp(ctx, 12))
        }
        left.addView(
            TextView(ctx).apply {
                text = "Журнал квестов"
                textSize = 19f
                setTextColor(ScriptFXConfig.questInk)
            },
            LinearLayout.LayoutParams(MATCH, WRAP)
        )
        val tabs = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(ctx, 10), 0, dp(ctx, 8))
        }
        tabsBox = tabs
        left.addView(tabs, LinearLayout.LayoutParams(MATCH, WRAP))
        left.addView(
            View(ctx).apply { background = ColorDrawable(ScriptFXConfig.questLine) },
            LinearLayout.LayoutParams(MATCH, max(1, dp(ctx, 1)))
        )

        val list = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(ctx, 8), 0, 0)
        }
        listBox = list
        val listScroll = ScrollView(ctx)
        listScroll.addView(list, ViewGroup.LayoutParams(MATCH, WRAP))
        left.addView(listScroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        book.addView(left, LinearLayout.LayoutParams(0, MATCH, 1f))

        book.addView(
            View(ctx).apply { background = ColorDrawable(ScriptFXConfig.questSpine) },
            LinearLayout.LayoutParams(dp(ctx, 6), MATCH)
        )

        val right = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = UiStyle.rounded(ctx, ScriptFXConfig.questPaperR, 8f)
            setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 12))
        }
        val detail = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        detailBox = detail
        val detailScroll = ScrollView(ctx)
        detailScroll.addView(detail, ViewGroup.LayoutParams(MATCH, WRAP))
        right.addView(detailScroll, LinearLayout.LayoutParams(MATCH, MATCH))
        book.addView(right, LinearLayout.LayoutParams(0, MATCH, 1.15f))

        root.addView(book, FrameLayout.LayoutParams(bookW, bookH, Gravity.CENTER))

        rootView = root
        refresh()
        root.requestFocus()
        root.postDelayed(poll, POLL_MS)
        return root
    }

    override fun onDestroyView() {
        rootView = null
        tabsBox = null
        listBox = null
        detailBox = null
        super.onDestroyView()
    }

    private fun refresh() {
        val ctx = context ?: return
        shownVersion = QuestClient.version

        val items = QuestClient.quests
            .filter { it.status == tab.status }
            .sortedBy { it.title.lowercase() }
        if (selectedId == null || items.none { it.id == selectedId }) {
            selectedId = items.firstOrNull()?.id
        }

        buildTabs(ctx)
        renderList(ctx, items)
        renderDetail(ctx, items.firstOrNull { it.id == selectedId })
    }

    private fun buildTabs(ctx: Context) {
        val box = tabsBox ?: return
        box.removeAllViews()
        for (t in Tab.entries) {
            val count = QuestClient.quests.count { it.status == t.status }
            val selected = t == tab
            val tv = TextView(ctx).apply {
                text = "${t.title} ($count)"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(if (selected) ScriptFXConfig.questPaperL else ScriptFXConfig.questInk)
                background = if (selected) {
                    UiStyle.rounded(ctx, ScriptFXConfig.questInk, 6f)
                } else {
                    UiStyle.buttonBg(
                        ctx,
                        ScriptFXConfig.questTabIdle,
                        ScriptFXConfig.questRowHover,
                        ScriptFXConfig.questRowSel,
                        6f,
                        0
                    )
                }
                setPadding(dp(ctx, 6), dp(ctx, 5), dp(ctx, 6), dp(ctx, 5))
                isClickable = true
                setOnClickListener {
                    tab = t
                    selectedId = null
                    refresh()
                }
            }
            box.addView(
                tv,
                LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                    if (t != Tab.ACTIVE) leftMargin = dp(ctx, 4)
                }
            )
        }
    }

    private fun renderList(ctx: Context, items: List<ClientQuest>) {
        val box = listBox ?: return
        box.removeAllViews()

        if (items.isEmpty()) {
            box.addView(
                TextView(ctx).apply {
                    text = "Здесь пока пусто"
                    textSize = 13f
                    setTextColor(ScriptFXConfig.questInkMuted)
                    setPadding(0, dp(ctx, 12), 0, 0)
                },
                LinearLayout.LayoutParams(MATCH, WRAP)
            )
            return
        }

        for (q in items) {
            val sel = q.id == selectedId
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = if (sel) {
                    UiStyle.rounded(ctx, ScriptFXConfig.questRowSel, 6f)
                } else {
                    UiStyle.buttonBg(
                        ctx,
                        0x00000000,
                        ScriptFXConfig.questRowHover,
                        ScriptFXConfig.questRowSel,
                        6f,
                        0
                    )
                }
                setPadding(dp(ctx, 8), dp(ctx, 7), dp(ctx, 8), dp(ctx, 7))
                isClickable = true
                setOnClickListener {
                    selectedId = q.id
                    refresh()
                }
            }
            row.addView(
                TextView(ctx).apply {
                    text = "●"
                    textSize = 12f
                    setTextColor(statusColor(q.status))
                },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(ctx, 8) }
            )
            row.addView(
                TextView(ctx).apply {
                    text = q.title
                    textSize = 14f
                    setTextColor(ScriptFXConfig.questInk)
                    setSingleLine(true)
                },
                LinearLayout.LayoutParams(0, WRAP, 1f)
            )
            if (q.objectives.isNotEmpty()) {
                row.addView(
                    TextView(ctx).apply {
                        text = "${q.objectives.count { it.done }}/${q.objectives.size}"
                        textSize = 12f
                        setTextColor(ScriptFXConfig.questInkMuted)
                    },
                    LinearLayout.LayoutParams(WRAP, WRAP)
                )
            }
            box.addView(
                row,
                LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(ctx, 2) }
            )
        }
    }

    private fun renderDetail(ctx: Context, q: ClientQuest?) {
        val box = detailBox ?: return
        box.removeAllViews()

        if (q == null) {
            box.addView(
                TextView(ctx).apply {
                    text = "Выберите квест на левой странице"
                    textSize = 13f
                    setTextColor(ScriptFXConfig.questInkMuted)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(ctx, 40), 0, 0)
                },
                LinearLayout.LayoutParams(MATCH, WRAP)
            )
            return
        }

        box.addView(
            TextView(ctx).apply {
                text = q.title
                textSize = 21f
                setTextColor(ScriptFXConfig.questInk)
            },
            LinearLayout.LayoutParams(MATCH, WRAP)
        )
        box.addView(
            TextView(ctx).apply {
                text = statusLabel(q.status)
                textSize = 11f
                setTextColor(0xFFFFFFFF.toInt())
                background = UiStyle.rounded(ctx, statusColor(q.status), 9f)
                setPadding(dp(ctx, 9), dp(ctx, 3), dp(ctx, 9), dp(ctx, 3))
            },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(ctx, 6) }
        )
        box.addView(
            View(ctx).apply { background = ColorDrawable(ScriptFXConfig.questLine) },
            LinearLayout.LayoutParams(MATCH, max(1, dp(ctx, 1))).apply {
                topMargin = dp(ctx, 10)
                bottomMargin = dp(ctx, 8)
            }
        )

        if (q.description.isNotBlank()) {
            box.addView(
                TextView(ctx).apply {
                    text = q.description
                    textSize = 13f
                    setTextColor(ScriptFXConfig.questInk)
                    setPadding(0, 0, 0, dp(ctx, 10))
                },
                LinearLayout.LayoutParams(MATCH, WRAP)
            )
        }

        if (q.objectives.isNotEmpty()) {
            box.addView(
                TextView(ctx).apply {
                    text = "Цели"
                    textSize = 14f
                    setTextColor(ScriptFXConfig.questInkMuted)
                    setPadding(0, dp(ctx, 4), 0, dp(ctx, 4))
                },
                LinearLayout.LayoutParams(MATCH, WRAP)
            )
        }

        for (ob in q.objectives) {
            val line = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            line.addView(
                TextView(ctx).apply {
                    text = if (ob.done) "✔" else "○"
                    textSize = 13f
                    setTextColor(
                        if (ob.done) ScriptFXConfig.questDone else ScriptFXConfig.questInkMuted
                    )
                },
                LinearLayout.LayoutParams(dp(ctx, 20), WRAP)
            )
            line.addView(
                TextView(ctx).apply {
                    text = ob.text
                    textSize = 13f
                    setTextColor(
                        if (ob.done) ScriptFXConfig.questInkMuted else ScriptFXConfig.questInk
                    )
                },
                LinearLayout.LayoutParams(0, WRAP, 1f)
            )
            if (ob.target > 1) {
                line.addView(
                    TextView(ctx).apply {
                        text = "${ob.progress}/${ob.target}"
                        textSize = 12f
                        setTextColor(ScriptFXConfig.questInkMuted)
                    },
                    LinearLayout.LayoutParams(WRAP, WRAP)
                )
            }
            box.addView(
                line,
                LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(ctx, 5) }
            )

            if (ob.target > 1) {
                val p = ob.progress.coerceIn(0, ob.target)
                val bar = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    background = UiStyle.rounded(ctx, ScriptFXConfig.questBarBg, 3f)
                }
                if (p > 0) {
                    bar.addView(
                        View(ctx).apply {
                            background = UiStyle.rounded(
                                ctx,
                                if (ob.done) ScriptFXConfig.questDone else ScriptFXConfig.questActive,
                                3f
                            )
                        },
                        LinearLayout.LayoutParams(0, MATCH, p.toFloat())
                    )
                }
                if (ob.target - p > 0) {
                    bar.addView(
                        View(ctx),
                        LinearLayout.LayoutParams(0, MATCH, (ob.target - p).toFloat())
                    )
                }
                box.addView(
                    bar,
                    LinearLayout.LayoutParams(MATCH, dp(ctx, 5)).apply {
                        topMargin = dp(ctx, 3)
                        leftMargin = dp(ctx, 20)
                    }
                )
            }
        }
    }

    private fun statusColor(status: String): Int = when (status) {
        "completed" -> ScriptFXConfig.questDone
        "failed" -> ScriptFXConfig.questFailed
        else -> ScriptFXConfig.questActive
    }

    private fun statusLabel(status: String): String = when (status) {
        "completed" -> "Выполнен"
        "failed" -> "Провален"
        else -> "Выполняется"
    }

    private fun closeJournal() {
        val mc = Minecraft.getInstance()
        mc.execute { mc.setScreen(null) }
    }

    private fun dp(ctx: Context, v: Int): Int = UiStyle.dp(ctx, v)
}