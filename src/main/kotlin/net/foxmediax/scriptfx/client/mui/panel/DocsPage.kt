package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.text.Editable
import icyllis.modernui.text.TextWatcher
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.View
import icyllis.modernui.widget.EditText
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_ACCENT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_BTN
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_BTN_SELECTED
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_CONTENT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_DIVIDER
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_MUTED
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_SIDEBAR
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_TEXT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.MATCH
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.WRAP
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.dp
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.lp
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.px1
import net.foxmediax.scriptfx.gui.Documentation
import net.minecraft.client.Minecraft

/**
 * Документация в стиле студии:
 * поиск + дерево слева | статья справа
 */
class DocsPage(host: PanelHost) : PanelPage(host) {

    override val breadcrumb: String
        get() = selected?.let { "Документация -> ${it.title}" } ?: "Документация"

    private data class DocNode(
        val id: String,
        val title: String,
        val group: String,
        val section: String? = null,
        val subsection: String = "",
        val custom: (() -> List<Block>)? = null
    )

    private sealed class Block {
        data class Title(val text: String) : Block()
        data class Subtitle(val text: String) : Block()
        data class Body(val text: String) : Block()
        data class Code(val text: String) : Block()
        data class Step(val index: Int, val text: String) : Block()
        object Divider : Block()
    }

    private lateinit var ctx: Context
    private lateinit var searchEdit: EditText
    private lateinit var navBox: LinearLayout
    private lateinit var articleHost: LinearLayout
    private lateinit var articleTitle: TextView
    private lateinit var pageIndicator: TextView

    private var selected: DocNode? = null
    private var filter: String = ""

    private val tree: List<DocNode> = listOf(
        DocNode("quickstart", "Быстрый старт", "Введение", custom = { quickStart() }),
        DocNode("install", "Установка", "Введение", custom = { installPage() }),
        DocNode("first_script", "Первый сценарий", "Введение", custom = { firstScript() }),

        DocNode("api_vars", "Переменные", "API", "Скрипты", "Переменные"),
        DocNode("api_global", "Глобальные", "API", "Скрипты", "Глобальные переменные"),
        DocNode("api_story", "Сюжет", "API", "Скрипты", "Для сюжета"),
        DocNode("api_camera", "Камера", "API", "Скрипты", "Для камеры"),
        DocNode("api_cut", "Катсцены", "API", "Скрипты", "Катсцены"),
        DocNode("api_npc", "NPC", "API", "Скрипты", "NPC"),
        DocNode("api_custom", "Кастомные модели", "API", "Скрипты", "Кастомные модели NPC"),
        DocNode("api_particles", "Частицы", "API", "Скрипты", "Частицы"),
        DocNode("api_media", "Звук и видео", "API", "Скрипты", "Звук и видео"),
        DocNode("api_quests", "Квесты", "API", "Скрипты", "Квесты"),

        DocNode("triggers", "Триггеры", "API", "Триггеры", ""),
        DocNode("examples", "Примеры", "API", "Примеры скриптов", ""),
        DocNode("messages", "Чат и сообщения", "API", "Чат и сообщения", ""),
        DocNode("panel", "Панель управления", "API", "Панель управления", "")
    )

    override fun createView(ctx: Context): View {
        this.ctx = ctx
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ColorDrawable(COL_CONTENT)
        }

        root.addView(buildNav(ctx), lp(dp(ctx, 260), MATCH))
        root.addView(vLine(ctx), lp(px1(ctx), MATCH))
        root.addView(buildArticle(ctx), LinearLayout.LayoutParams(0, MATCH, 1f))

        selected = tree.first()
        refreshNav()
        renderArticle()
        return root
    }

    // ------------------------------------------------------------------
    // LEFT NAV
    // ------------------------------------------------------------------

    private fun buildNav(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_SIDEBAR)
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
        }

        searchEdit = EditText(ctx).apply {
            hint = "Поиск в документации…"
            textSize = 12f
            setTextColor(COL_TEXT)
            setHintTextColor(COL_MUTED)
            background = ColorDrawable(COL_BTN)
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
            setSingleLine(true)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    filter = s?.toString()?.trim().orEmpty()
                    refreshNav()
                }
            })
        }
        col.addView(searchEdit, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 12) })

        navBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx)
        scroll.addView(navBox, lp(MATCH, WRAP))
        col.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return col
    }

    private fun refreshNav() {
        navBox.removeAllViews()
        val q = filter.lowercase()
        val filtered = if (q.isEmpty()) {
            tree
        } else {
            tree.filter {
                it.title.lowercase().contains(q) || it.group.lowercase().contains(q)
            }
        }

        if (filtered.isEmpty()) {
            navBox.addView(PanelUi.mutedText(ctx, "Ничего не найдено"), lp(MATCH, WRAP))
            return
        }

        var lastGroup = ""
        for (node in filtered) {
            if (node.group != lastGroup) {
                lastGroup = node.group
                navBox.addView(TextView(ctx).apply {
                    text = node.group.uppercase()
                    textSize = 11f
                    setTextColor(COL_ACCENT)
                    setPadding(0, dp(ctx, 10), 0, dp(ctx, 6))
                }, lp(MATCH, WRAP))
            }

            val active = selected?.id == node.id
            val row = TextView(ctx).apply {
                text = node.title
                textSize = 13f
                setTextColor(COL_TEXT)
                background = ColorDrawable(if (active) COL_BTN_SELECTED else COL_BTN)
                setPadding(dp(ctx, 10), dp(ctx, 10), dp(ctx, 10), dp(ctx, 10))
                isClickable = true
                setOnClickListener {
                    selected = node
                    refreshNav()
                    renderArticle()
                    host.refreshBreadcrumb()
                }
            }
            navBox.addView(row, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 4) })
        }
    }

    // ------------------------------------------------------------------
    // RIGHT ARTICLE
    // ------------------------------------------------------------------

    private fun buildArticle(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_CONTENT)
            setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 16))
        }

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(ctx).apply {
            text = "▶"
            textSize = 18f
            setTextColor(COL_ACCENT)
        }, lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 8) })

        articleTitle = TextView(ctx).apply {
            text = "Документация"
            textSize = 20f
            setTextColor(COL_TEXT)
        }
        header.addView(articleTitle, LinearLayout.LayoutParams(0, WRAP, 1f))

        pageIndicator = TextView(ctx).apply {
            text = ""
            textSize = 12f
            setTextColor(COL_MUTED)
        }
        header.addView(pageIndicator, lp(WRAP, WRAP))
        col.addView(header, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 14) })

        val scroll = ScrollView(ctx)
        articleHost = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(articleHost, lp(MATCH, WRAP))
        col.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return col
    }

    private fun renderArticle() {
        articleHost.removeAllViews()
        val node = selected ?: return
        articleTitle.text = node.title

        val index = tree.indexOfFirst { it.id == node.id }.let { if (it < 0) 0 else it }
        pageIndicator.text = "${index + 1} / ${tree.size}"

        val blocks = node.custom?.invoke() ?: linesToBlocks(
            Documentation.page(node.section ?: "", node.subsection)
        )

        if (blocks.isEmpty()) {
            articleHost.addView(
                PanelUi.mutedText(ctx, "Раздел пока пуст."),
                lp(MATCH, WRAP)
            )
            return
        }

        for (block in blocks) {
            when (block) {
                is Block.Title -> articleHost.addView(TextView(ctx).apply {
                    text = block.text
                    textSize = 16f
                    setTextColor(COL_ACCENT)
                    setPadding(0, dp(ctx, 8), 0, dp(ctx, 6))
                }, lp(MATCH, WRAP))

                is Block.Subtitle -> articleHost.addView(TextView(ctx).apply {
                    text = block.text
                    textSize = 14f
                    setTextColor(COL_TEXT)
                    setPadding(0, dp(ctx, 10), 0, dp(ctx, 4))
                }, lp(MATCH, WRAP))

                is Block.Body -> articleHost.addView(TextView(ctx).apply {
                    text = block.text
                    textSize = 13f
                    setTextColor(COL_TEXT)
                    setPadding(0, dp(ctx, 2), 0, dp(ctx, 2))
                    setOnClickListener {
                        val mc = Minecraft.getInstance()
                        mc.execute { mc.keyboardHandler.clipboard = block.text }
                    }
                }, lp(MATCH, WRAP))

                is Block.Code -> {
                    val box = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        background = ColorDrawable(COL_BTN)
                        setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
                    }
                    box.addView(TextView(ctx).apply {
                        text = block.text
                        textSize = 12f
                        setTextColor(COL_MUTED)
                        setOnClickListener {
                            val mc = Minecraft.getInstance()
                            mc.execute { mc.keyboardHandler.clipboard = block.text }
                        }
                    }, lp(MATCH, WRAP))
                    articleHost.addView(box, lp(MATCH, WRAP).apply {
                        topMargin = dp(ctx, 6)
                        bottomMargin = dp(ctx, 8)
                    })
                }

                is Block.Step -> {
                    val row = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.TOP
                        setPadding(0, dp(ctx, 6), 0, dp(ctx, 6))
                    }
                    row.addView(TextView(ctx).apply {
                        text = "${block.index}."
                        textSize = 14f
                        setTextColor(COL_ACCENT)
                        setPadding(0, 0, dp(ctx, 10), 0)
                    }, lp(WRAP, WRAP))
                    row.addView(TextView(ctx).apply {
                        text = block.text
                        textSize = 13f
                        setTextColor(COL_TEXT)
                    }, LinearLayout.LayoutParams(0, WRAP, 1f))
                    articleHost.addView(row, lp(MATCH, WRAP))
                }

                is Block.Divider -> articleHost.addView(
                    View(ctx).apply { background = ColorDrawable(COL_DIVIDER) },
                    lp(MATCH, px1(ctx)).apply {
                        topMargin = dp(ctx, 10)
                        bottomMargin = dp(ctx, 10)
                    }
                )
            }
        }

        articleHost.addView(PanelUi.spacer(ctx, 24))
        articleHost.addView(
            PanelUi.mutedText(ctx, "Клик по строке копирует её в буфер."),
            lp(MATCH, WRAP)
        )
    }

    private fun linesToBlocks(lines: List<String>): List<Block> {
        if (lines.isEmpty()) return emptyList()
        val out = mutableListOf<Block>()
        val codeBuf = StringBuilder()

        fun flushCode() {
            if (codeBuf.isNotEmpty()) {
                out += Block.Code(codeBuf.toString().trimEnd())
                codeBuf.clear()
            }
        }

        for (raw in lines) {
            val line = raw.trimEnd()
            val t = line.trim()
            when {
                t == Documentation.DIVIDER || t == "---" -> {
                    flushCode()
                    out += Block.Divider
                }
                t.startsWith("Раздел:") -> {
                    flushCode()
                    out += Block.Title(t.removePrefix("Раздел:").trim().trim('"'))
                }
                t.matches(Regex("""^\d+\.\s+.*""")) -> {
                    flushCode()
                    val num = t.substringBefore('.').toIntOrNull() ?: 1
                    out += Block.Step(num, t.substringAfter('.').trim())
                }
                looksLikeCommand(t) -> {
                    if (codeBuf.isNotEmpty()) codeBuf.append('\n')
                    codeBuf.append(t)
                }
                t.isEmpty() -> flushCode()
                else -> {
                    flushCode()
                    out += Block.Body(t)
                }
            }
        }
        flushCode()
        return out
    }

    private fun looksLikeCommand(line: String): Boolean {
        if (line.isEmpty()) return false
        val first = line.substringBefore(' ')
        return first.matches(Regex("""[a-z_][a-z0-9_]*""")) &&
                (line.contains('"') ||
                        line.contains("ticktime") ||
                        line.contains("print") ||
                        line.contains("camera") ||
                        line.contains("cutscene") ||
                        line.contains("state_") ||
                        line.contains("checkpoint") ||
                        line.contains("npc_") ||
                        line.contains("particle"))
    }

    // ------------------------------------------------------------------
    // Custom pages
    // ------------------------------------------------------------------

    private fun quickStart(): List<Block> = listOf(
        Block.Title("Добро пожаловать в ScriptFX"),
        Block.Body("Этот раздел поможет быстро создать первый сценарий и запустить его в игре."),
        Block.Divider,
        Block.Step(1, "Создайте проект — в разделе «Сценарии» нажмите «Создать» и сделайте папку проекта."),
        Block.Step(2, "Создайте скрипт .sfxs внутри проекта (лучше в папке scripts)."),
        Block.Step(3, "Напишите простой сценарий, например print и ticktime."),
        Block.Step(4, "Сохраните файл и нажмите ▶ Запустить (одиночная игра)."),
        Block.Divider,
        Block.Subtitle("Минимальный пример"),
        Block.Code(
            """
            print "Привет, путник!"
            ticktime "1.sec"
            print "Добро пожаловать в ScriptFX"
            """.trimIndent()
        ),
        Block.Body("Дальше смотри разделы API: Триггеры, NPC, Катсцены, Частицы.")
    )

    private fun installPage(): List<Block> = listOf(
        Block.Title("Установка"),
        Block.Step(1, "Нужны Minecraft (версия мода), Fabric Loader и Fabric API."),
        Block.Step(2, "Положи ScriptFX .jar в папку mods."),
        Block.Step(3, "Запусти клиент, зайди в мир."),
        Block.Step(4, "Открой панель ScriptFX клавишей из настроек управления."),
        Block.Divider,
        Block.Body("Файлы проектов: config/scriptfx/projects/"),
        Block.Body("Логи мода: раздел «Логи» в панели.")
    )

    private fun firstScript(): List<Block> = listOf(
        Block.Title("Первый сценарий"),
        Block.Body("Скрипты пишутся простыми командами построчно (язык .sfxs)."),
        Block.Divider,
        Block.Subtitle("Пример с задержкой"),
        Block.Code(
            """
            print "Глава 1"
            ticktime "2.sec"
            print "Ты проснулся в деревне..."
            """.trimIndent()
        ),
        Block.Subtitle("Старт при входе в мир"),
        Block.Code(
            """
            worldstartscript once
            print "Добро пожаловать!"
            """.trimIndent()
        ),
        Block.Body("Подробности — в API → Переменные и Триггеры.")
    )

    private fun vLine(ctx: Context) =
        View(ctx).apply { background = ColorDrawable(COL_DIVIDER) }
}