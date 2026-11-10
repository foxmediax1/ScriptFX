package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.view.Gravity
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
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_SUCCESS
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_TEXT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_WARNING
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.MATCH
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.WRAP
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.dp
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.lp
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.px1

/**
 * UI квестов как на макете:
 * список квестов | граф узлов | свойства выбранного узла
 */
class QuestsPage(host: PanelHost) : PanelPage(host) {

    override val breadcrumb: String get() = "Квесты"

    private lateinit var ctx: Context
    private lateinit var questListBox: LinearLayout
    private lateinit var graphHost: FrameLayout
    private lateinit var typeValue: TextView
    private lateinit var textValue: TextView
    private lateinit var answersBox: LinearLayout
    private lateinit var transitionValue: TextView
    private lateinit var selectedQuestTitle: TextView

    private var selectedQuestId: String = "hero_path"
    private var selectedNodeId: String = "dialog_artifact"

    private data class QuestItem(val id: String, val title: String, val icon: String)
    private data class QuestNode(
        val id: String,
        val title: String,
        val subtitle: String,
        val kind: NodeKind,
        val x: Float, // 0..1 относительно графа
        val y: Float
    )

    private enum class NodeKind { START, DIALOG, ACTION, SUCCESS, REWARD }

    private val quests = mutableListOf(
        QuestItem("hero_path", "Путь героя", "🟣"),
        QuestItem("treasures", "Сокровища", "🟡"),
        QuestItem("lost_artifact", "Потерянный артефакт", "🟠")
    )

    private val nodesByQuest = mapOf(
        "hero_path" to listOf(
            QuestNode("start", "Начало", "", NodeKind.START, 0.50f, 0.10f),
            QuestNode("talk", "Поговорить\nс хранителем", "диалог", NodeKind.DIALOG, 0.28f, 0.38f),
            QuestNode("find", "Найти артефакт", "задача", NodeKind.ACTION, 0.72f, 0.38f),
            QuestNode("return", "Вернуться к герою", "успех", NodeKind.SUCCESS, 0.50f, 0.62f),
            QuestNode("reward", "Награда", "награда", NodeKind.REWARD, 0.50f, 0.84f)
        ),
        "treasures" to listOf(
            QuestNode("start", "Начало", "", NodeKind.START, 0.50f, 0.18f),
            QuestNode("map", "Найти карту", "задача", NodeKind.ACTION, 0.35f, 0.48f),
            QuestNode("chest", "Открыть сундук", "задача", NodeKind.ACTION, 0.65f, 0.48f),
            QuestNode("reward", "Награда", "награда", NodeKind.REWARD, 0.50f, 0.78f)
        ),
        "lost_artifact" to listOf(
            QuestNode("start", "Начало", "", NodeKind.START, 0.50f, 0.20f),
            QuestNode("dialog_artifact", "Ты нашёл артефакт?", "диалог", NodeKind.DIALOG, 0.50f, 0.50f),
            QuestNode("reward", "К награде", "награда", NodeKind.REWARD, 0.50f, 0.80f)
        )
    )

    private val edgesByQuest = mapOf(
        "hero_path" to listOf(
            "start" to "talk",
            "start" to "find",
            "talk" to "return",
            "find" to "return",
            "return" to "reward"
        ),
        "treasures" to listOf(
            "start" to "map",
            "start" to "chest",
            "map" to "reward",
            "chest" to "reward"
        ),
        "lost_artifact" to listOf(
            "start" to "dialog_artifact",
            "dialog_artifact" to "reward"
        )
    )

    override fun createView(ctx: Context): View {
        this.ctx = ctx
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ColorDrawable(COL_CONTENT)
        }

        root.addView(buildQuestList(ctx), lp(dp(ctx, 240), MATCH))
        root.addView(vLine(ctx), lp(px1(ctx), MATCH))
        root.addView(buildGraph(ctx), LinearLayout.LayoutParams(0, MATCH, 1f))
        root.addView(vLine(ctx), lp(px1(ctx), MATCH))
        root.addView(buildProperties(ctx), lp(dp(ctx, 280), MATCH))

        refreshQuestList()
        rebuildGraph()
        bindNodeProps(selectedNodeId)
        return root
    }

    // ------------------------------------------------------------------
    // LEFT
    // ------------------------------------------------------------------

    private fun buildQuestList(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_SIDEBAR)
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
        }

        col.addView(TextView(ctx).apply {
            text = "Квесты"
            textSize = 13f
            setTextColor(COL_ACCENT)
        }, lp(MATCH, WRAP))

        col.addView(
            PanelUi.accentButton(ctx, "+ Создать квест") {
                val id = "quest_${quests.size + 1}"
                quests += QuestItem(id, "Новый квест ${quests.size + 1}", "🟣")
                selectedQuestId = id
                selectedNodeId = "start"
                refreshQuestList()
                rebuildGraph()
                bindNodeProps(selectedNodeId)
            },
            lp(MATCH, WRAP).apply {
                topMargin = dp(ctx, 10)
                bottomMargin = dp(ctx, 10)
            }
        )

        questListBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx)
        scroll.addView(questListBox, lp(MATCH, WRAP))
        col.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return col
    }

    private fun refreshQuestList() {
        questListBox.removeAllViews()
        for (q in quests) {
            val selected = q.id == selectedQuestId
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ColorDrawable(if (selected) COL_BTN_SELECTED else COL_BTN)
                setPadding(dp(ctx, 10), dp(ctx, 10), dp(ctx, 10), dp(ctx, 10))
                isClickable = true
                setOnClickListener {
                    selectedQuestId = q.id
                    selectedNodeId = nodesByQuest[q.id]?.firstOrNull()?.id ?: "start"
                    refreshQuestList()
                    rebuildGraph()
                    bindNodeProps(selectedNodeId)
                }
            }
            row.addView(TextView(ctx).apply {
                text = "${q.icon}  ${q.title}"
                textSize = 13f
                setTextColor(COL_TEXT)
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            questListBox.addView(row, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 6) })
        }
    }

    // ------------------------------------------------------------------
    // CENTER — граф
    // ------------------------------------------------------------------

    private fun buildGraph(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_CONTENT)
        }

        selectedQuestTitle = TextView(ctx).apply {
            text = "Граф квеста"
            textSize = 12f
            setTextColor(COL_MUTED)
            setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8))
            background = ColorDrawable(COL_SIDEBAR)
        }
        col.addView(selectedQuestTitle, lp(MATCH, WRAP))
        col.addView(vLine(ctx), lp(MATCH, px1(ctx)))

        graphHost = FrameLayout(ctx).apply {
            background = ColorDrawable(0xFF0B0714.toInt())
        }
        col.addView(graphHost, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return col
    }

    private fun rebuildGraph() {
        graphHost.removeAllViews()
        val nodes = nodesByQuest[selectedQuestId] ?: defaultNodes()
        val edges = edgesByQuest[selectedQuestId] ?: emptyList()
        val questTitle = quests.firstOrNull { it.id == selectedQuestId }?.title ?: selectedQuestId
        selectedQuestTitle.text = "Граф · $questTitle"

        // Сетка (имитация)
        val grid = View(ctx).apply {
            background = ColorDrawable(0xFF120B1F.toInt())
        }
        graphHost.addView(grid, FrameLayout.LayoutParams(MATCH, MATCH))

        // Рёбра — простые линии-заглушки через тонкие View между центрами
        // (Modern UI без Canvas path — делаем упрощённо: вертикальные/горизонтальные коннекторы)
        for ((fromId, toId) in edges) {
            val from = nodes.find { it.id == fromId } ?: continue
            val to = nodes.find { it.id == toId } ?: continue
            graphHost.post {
                val w = graphHost.width
                val h = graphHost.height
                if (w <= 0 || h <= 0) return@post
                val x1 = (from.x * w).toInt()
                val y1 = (from.y * h).toInt()
                val x2 = (to.x * w).toInt()
                val y2 = (to.y * h).toInt()
                val line = View(ctx).apply { background = ColorDrawable(0xFF5B3B8C.toInt()) }
                val left = minOf(x1, x2)
                val top = minOf(y1, y2)
                val width = maxOf(px1(ctx) * 2, kotlin.math.abs(x2 - x1))
                val height = maxOf(px1(ctx) * 2, kotlin.math.abs(y2 - y1))
                // Упрощённый коннектор: вертикаль + горизонталь через один rect-приближение
                val connector = View(ctx).apply { background = ColorDrawable(0x665B3B8C) }
                val lpLine = FrameLayout.LayoutParams(
                    if (kotlin.math.abs(x2 - x1) < 8) dp(ctx, 2) else width,
                    if (kotlin.math.abs(y2 - y1) < 8) dp(ctx, 2) else height
                ).apply {
                    leftMargin = left
                    topMargin = top
                }
                graphHost.addView(connector, lpLine)
            }
        }

        // Узлы
        graphHost.post {
            val w = graphHost.width
            val h = graphHost.height
            if (w <= 0 || h <= 0) return@post
            for (node in nodes) {
                val card = nodeCard(ctx, node, node.id == selectedNodeId)
                val cardW = dp(ctx, 160)
                val cardH = dp(ctx, 64)
                val lpNode = FrameLayout.LayoutParams(cardW, cardH).apply {
                    leftMargin = (node.x * w).toInt() - cardW / 2
                    topMargin = (node.y * h).toInt() - cardH / 2
                }
                graphHost.addView(card, lpNode)
            }
        }
    }

    private fun defaultNodes() = listOf(
        QuestNode("start", "Начало", "", NodeKind.START, 0.5f, 0.2f)
    )

    private fun nodeCard(ctx: Context, node: QuestNode, selected: Boolean): View {
        val bg = when (node.kind) {
            NodeKind.START -> 0xFF2A1B4D.toInt()
            NodeKind.DIALOG -> 0xFF1B2A4D.toInt()
            NodeKind.ACTION -> 0xFF1B3A4D.toInt()
            NodeKind.SUCCESS -> 0xFF1B3D2E.toInt()
            NodeKind.REWARD -> 0xFF3D2E1B.toInt()
        }
        val accent = when (node.kind) {
            NodeKind.START -> COL_ACCENT
            NodeKind.DIALOG -> 0xFF60A5FA.toInt()
            NodeKind.ACTION -> 0xFF22D3EE.toInt()
            NodeKind.SUCCESS -> COL_SUCCESS
            NodeKind.REWARD -> COL_WARNING
        }
        val icon = when (node.kind) {
            NodeKind.START -> "👤"
            NodeKind.DIALOG -> "💬"
            NodeKind.ACTION -> "📦"
            NodeKind.SUCCESS -> "✅"
            NodeKind.REWARD -> "⭐"
        }

        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = ColorDrawable(if (selected) COL_BTN_SELECTED else bg)
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
            isClickable = true
            setOnClickListener {
                selectedNodeId = node.id
                rebuildGraph()
                bindNodeProps(node.id)
            }
        }
        box.addView(TextView(ctx).apply {
            text = "$icon  ${node.title}"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(COL_TEXT)
        }, lp(MATCH, WRAP))
        if (node.subtitle.isNotBlank()) {
            box.addView(TextView(ctx).apply {
                text = node.subtitle
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(accent)
            }, lp(MATCH, WRAP))
        }
        // обводка выбранного
        if (selected) {
            box.background = ColorDrawable(COL_BTN_SELECTED)
        }
        return box
    }

    // ------------------------------------------------------------------
    // RIGHT — свойства узла
    // ------------------------------------------------------------------

    private fun buildProperties(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_SIDEBAR)
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
        }

        col.addView(TextView(ctx).apply {
            text = "Свойства узла"
            textSize = 13f
            setTextColor(COL_ACCENT)
        }, lp(MATCH, WRAP))

        col.addView(PanelUi.spacer(ctx, 14))
        col.addView(label(ctx, "Тип"), lp(MATCH, WRAP))
        typeValue = value(ctx, "Диалог")
        col.addView(typeValue, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 12) })

        col.addView(label(ctx, "Текст"), lp(MATCH, WRAP))
        textValue = value(ctx, "—")
        col.addView(textValue, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 12) })

        col.addView(label(ctx, "Варианты ответов"), lp(MATCH, WRAP))
        answersBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(ctx, 6), 0, dp(ctx, 10))
        }
        col.addView(answersBox, lp(MATCH, WRAP))

        col.addView(label(ctx, "Переход"), lp(MATCH, WRAP))
        transitionValue = value(ctx, "—")
        col.addView(transitionValue, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 16) })

        col.addView(
            PanelUi.accentButton(ctx, "Сохранить узел") {
                // UI-заглушка: позже запись в файл квеста
            },
            lp(MATCH, WRAP)
        )
        return col
    }

    private fun bindNodeProps(nodeId: String) {
        val node = (nodesByQuest[selectedQuestId] ?: defaultNodes()).find { it.id == nodeId }
            ?: return
        typeValue.text = when (node.kind) {
            NodeKind.START -> "Старт"
            NodeKind.DIALOG -> "Диалог"
            NodeKind.ACTION -> "Действие"
            NodeKind.SUCCESS -> "Успех"
            NodeKind.REWARD -> "Награда"
        }
        textValue.text = when (node.id) {
            "dialog_artifact" -> "Ты нашёл артефакт?"
            "talk" -> "Поговорить с хранителем"
            "find" -> "Найти артефакт"
            "return" -> "Вернуться к герою"
            "reward" -> "Выдать награду"
            else -> node.title.replace('\n', ' ')
        }

        answersBox.removeAllViews()
        if (node.kind == NodeKind.DIALOG) {
            answersBox.addView(answerChip(ctx, "Да"), lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 6) })
            answersBox.addView(answerChip(ctx, "Нет"), lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 6) })
        } else {
            answersBox.addView(PanelUi.mutedText(ctx, "—"), lp(MATCH, WRAP))
        }

        transitionValue.text = when (node.id) {
            "dialog_artifact" -> "К награде"
            "talk", "find" -> "Вернуться к герою"
            "return" -> "Награда"
            "start" -> "Следующие узлы"
            else -> "—"
        }
    }

    private fun answerChip(ctx: Context, text: String): View =
        TextView(ctx).apply {
            this.text = text
            textSize = 12f
            setTextColor(COL_TEXT)
            background = ColorDrawable(COL_BTN)
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
        }

    private fun label(ctx: Context, t: String) = PanelUi.mutedText(ctx, t, 11f)

    private fun value(ctx: Context, t: String) = TextView(ctx).apply {
        text = t
        textSize = 13f
        setTextColor(COL_TEXT)
        setPadding(0, dp(ctx, 4), 0, 0)
    }

    private fun vLine(ctx: Context) =
        View(ctx).apply { background = ColorDrawable(COL_DIVIDER) }
}