package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.View
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.CutsceneClient
import net.foxmediax.scriptfx.gui.Documentation
import net.foxmediax.scriptfx.gui.PanelSection
import net.foxmediax.scriptfx.npc.NpcDefinition
import net.foxmediax.scriptfx.npc.NpcRegistry
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.Minecraft
import net.foxmediax.scriptfx.client.NpcPreviewHelper
import net.foxmediax.scriptfx.client.mui.UiStyle
import net.foxmediax.scriptfx.particles.ParticleRegistry
import net.foxmediax.scriptfx.client.particles.ParticleRuntime
import net.foxmediax.scriptfx.network.ParticlePayload
import net.foxmediax.scriptfx.particles.ParticleEffect
import icyllis.modernui.graphics.drawable.GradientDrawable
interface PanelHost {
    fun navigate(section: PanelSection?)
    fun requestLeave(proceed: () -> Unit)
    fun refreshBreadcrumb()
    fun closePanel()
    fun openSettings()
    fun openDocumentation()
    fun openScriptHints()
}

abstract class PanelPage(protected val host: PanelHost) {
    abstract fun createView(ctx: Context): View
    open val breadcrumb: String? = null
    open fun goUp(): Boolean = false
    open fun requestLeave(proceed: () -> Unit) = proceed()
    open fun onDestroy() {}
    open val interceptsEscape: Boolean get() = false
    open fun onEscape() {}
}

class HomePage(host: PanelHost) : PanelPage(host) {
    override fun createView(ctx: Context): View {
        val root = FrameLayout(ctx)
        root.background = ColorDrawable(0xFF000000.toInt())
        val tv = TextView(ctx).apply {
            text = "Это главное меню мода, для\nработы с проектом откройте\nсоответствующий раздел,\nкоторый находится слева.\n\n←"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(PanelUi.COL_MUTED)
        }
        root.addView(
            tv,
            FrameLayout.LayoutParams(PanelUi.WRAP, PanelUi.WRAP, Gravity.CENTER)
        )
        return root
    }
}

class PlaceholderPage(host: PanelHost, private val name: String) : PanelPage(host) {
    override val breadcrumb: String? get() = name

    override fun createView(ctx: Context): View {
        val root = FrameLayout(ctx)
        root.background = ColorDrawable(0xFF000000.toInt())
        val tv = TextView(ctx).apply {
            text = "Раздел «$name» — в разработке"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(PanelUi.COL_MUTED)
        }
        root.addView(
            tv,
            FrameLayout.LayoutParams(PanelUi.WRAP, PanelUi.WRAP, Gravity.CENTER)
        )
        return root
    }
}

class LogsPage(host: PanelHost) : PanelPage(host) {

    override val breadcrumb: String? get() = "Логи"

    private lateinit var box: LinearLayout
    private lateinit var scroll: ScrollView
    private var lastCount = -1

    override fun createView(ctx: Context): View {
        val root = PanelUi.pageRoot(ctx)

        root.addView(
            PanelUi.toolbar(
                ctx,
                "Логи ScriptFX",
                "Обновить" to { refresh() }
            ),
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
        )

        scroll = ScrollView(ctx)
        box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, PanelUi.dp(ctx, 8), 0, 0)
        }
        scroll.addView(box, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))
        root.addView(scroll, LinearLayout.LayoutParams(PanelUi.MATCH, 0, 1f))

        refresh()
        // лёгкий polling, пока страница жива
        box.post(object : Runnable {
            override fun run() {
                if (!box.isAttachedToWindow) return
                refresh()
                box.postDelayed(this, 1500L)
            }
        })
        return root
    }

    private fun refresh() {
        if (!this::box.isInitialized) return
        box.removeAllViews()
        val ctx = box.context
        val entries = ScriptFXLog.snapshot()

        if (entries.isEmpty()) {
            box.addView(
                PanelUi.mutedText(ctx, "Лог пуст"),
                PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
            )
            return
        }

        for (entry in entries.takeLast(200)) {
            val color = when (entry.level) {
                ScriptFXLog.Level.ERROR -> PanelUi.COL_ERROR
                ScriptFXLog.Level.WARN -> PanelUi.COL_WARNING
                ScriptFXLog.Level.INFO -> PanelUi.COL_TEXT
                ScriptFXLog.Level.DEBUG -> PanelUi.COL_MUTED
            }
            val tag = when (entry.level) {
                ScriptFXLog.Level.ERROR -> "ERROR"
                ScriptFXLog.Level.WARN -> "WARN"
                ScriptFXLog.Level.INFO -> "INFO"
                ScriptFXLog.Level.DEBUG -> "DEBUG"
            }
            // Entry → String
            val line = "[$tag] ${entry.message}"

            box.addView(
                TextView(ctx).apply {
                    text = line
                    textSize = 12f
                    setTextColor(color)
                    setPadding(0, PanelUi.dp(ctx, 1), 0, PanelUi.dp(ctx, 1))
                },
                PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
            )
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }
}

class CutscenesPage(host: PanelHost) : PanelPage(host) {

    override val breadcrumb: String? get() = "Кат-сцены"

    private lateinit var statusView: TextView
    private lateinit var cameraView: TextView
    private lateinit var hintView: TextView

    override fun createView(ctx: Context): View {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(0xFF000000.toInt())
            setPadding(
                PanelUi.dp(ctx, 10),
                PanelUi.dp(ctx, 8),
                PanelUi.dp(ctx, 10),
                PanelUi.dp(ctx, 8)
            )
        }

        // ----- Заголовок + кнопки -----
        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(
            TextView(ctx).apply {
                text = "Кат-сцены"
                textSize = 15f
                setTextColor(PanelUi.COL_TEXT)
            },
            LinearLayout.LayoutParams(0, PanelUi.WRAP, 1f)
        )
        top.addView(
            PanelUi.flatButton(ctx, "Обновить") { refresh() },
            PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP)
        )
        top.addView(
            PanelUi.flatButton(ctx, "Прервать (Ctrl+Alt+End)") {
                interruptCutscene()
            }.apply {
                setTextColor(0xFFFF8888.toInt())
            },
            PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP).apply {
                leftMargin = PanelUi.dp(ctx, 6)
            }
        )
        root.addView(top, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))

        val scroll = ScrollView(ctx)
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, PanelUi.dp(ctx, 8), 0, PanelUi.dp(ctx, 8))
        }

        // ----- Статус -----
        content.addView(sectionTitle(ctx, "Статус"))
        statusView = TextView(ctx).apply {
            textSize = 12f
            setTextColor(PanelUi.COL_MUTED)
            setPadding(0, PanelUi.dp(ctx, 4), 0, PanelUi.dp(ctx, 8))
        }
        content.addView(statusView, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))

        // ----- Камера -----
        content.addView(sectionTitle(ctx, "Камера (клиент)"))
        cameraView = TextView(ctx).apply {
            textSize = 12f
            setTextColor(PanelUi.COL_MUTED)
            setPadding(0, PanelUi.dp(ctx, 4), 0, PanelUi.dp(ctx, 8))
        }
        content.addView(cameraView, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))

        // ----- Быстрые шаблоны -----
        content.addView(sectionTitle(ctx, "Шаблоны скриптов"))
        content.addView(
            TextView(ctx).apply {
                text = "Вставь в .fxscript проекта (раздел «Проекты»)."
                textSize = 11f
                setTextColor(0xFF666666.toInt())
                setPadding(0, PanelUi.dp(ctx, 2), 0, PanelUi.dp(ctx, 6))
            },
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
        )

        for ((title, body) in TEMPLATES) {
            content.addView(
                templateCard(ctx, title, body),
                PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP).apply {
                    bottomMargin = PanelUi.dp(ctx, 6)
                }
            )
        }

        // ----- Справка по командам -----
        content.addView(sectionTitle(ctx, "Команды"))
        content.addView(
            TextView(ctx).apply {
                text = COMMAND_HELP
                textSize = 11f
                setTextColor(PanelUi.COL_MUTED)
                setPadding(0, PanelUi.dp(ctx, 4), 0, PanelUi.dp(ctx, 8))
            },
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
        )

        // ----- Подсказка -----
        hintView = TextView(ctx).apply {
            text = "Прерывание во время катсцены: Ctrl + Alt + End\n" +
                    "Редактор таймлайна (визуальный) — отдельная итерация; " +
                    "сейчас катсцены собираются командами в скриптах."
            textSize = 11f
            setTextColor(0xFF555555.toInt())
            setPadding(0, PanelUi.dp(ctx, 8), 0, 0)
        }
        content.addView(hintView, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))

        scroll.addView(content, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))
        root.addView(
            scroll,
            LinearLayout.LayoutParams(PanelUi.MATCH, 0, 1f)
        )

        refresh()
        return root
    }

    private fun sectionTitle(ctx: Context, text: String): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = 13f
            setTextColor(PanelUi.COL_ACCENT)
            setPadding(0, PanelUi.dp(ctx, 10), 0, PanelUi.dp(ctx, 2))
        }

    private fun templateCard(ctx: Context, title: String, body: String): View {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(0xFF101014.toInt())
            setPadding(
                PanelUi.dp(ctx, 8),
                PanelUi.dp(ctx, 6),
                PanelUi.dp(ctx, 8),
                PanelUi.dp(ctx, 6)
            )
        }
        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(
            TextView(ctx).apply {
                text = title
                textSize = 12f
                setTextColor(PanelUi.COL_TEXT)
            },
            LinearLayout.LayoutParams(0, PanelUi.WRAP, 1f)
        )
        head.addView(
            PanelUi.flatButton(ctx, "Копировать") {
                copyToClipboard(body)
            },
            PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP)
        )
        box.addView(head, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))
        box.addView(
            TextView(ctx).apply {
                text = body
                textSize = 11f
                setTextColor(0xFF888888.toInt())
                setPadding(0, PanelUi.dp(ctx, 4), 0, 0)
            },
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
        )
        return box
    }

    private fun refresh() {
        if (!this::statusView.isInitialized) return

        val mc = Minecraft.getInstance()
        mc.execute {
            val active = CutsceneClient.active
            val locked = CutsceneClient.locked
            val hud = CutsceneClient.hudHidden
            val lb = CutsceneClient.letterbox
            val lbH = CutsceneClient.letterboxHeight

            val statusText = buildString {
                append(if (active) "● Катсцена активна" else "○ Катсцена не идёт")
                append('\n')
                append(if (locked) "Управление: заблокировано" else "Управление: свободно")
                append('\n')
                append(if (hud) "HUD: скрыт" else "HUD: виден")
                append('\n')
                append(
                    if (lb) "Letterbox: вкл (${(lbH * 100).toInt()}%)"
                    else "Letterbox: выкл"
                )
            }

            val camText = if (!active) {
                "Камера не в режиме катсцены."
            } else {
                val fov = CutsceneClient.fovOverride
                buildString {
                    append("pos  %.2f  %.2f  %.2f\n".format(
                        CutsceneClient.camX,
                        CutsceneClient.camY,
                        CutsceneClient.camZ
                    ))
                    append("yaw  %.1f   pitch  %.1f\n".format(
                        CutsceneClient.camYaw,
                        CutsceneClient.camPitch
                    ))
                    append(
                        if (fov != null) "fov   %.1f".format(fov)
                        else "fov   (ванильный)"
                    )
                }
            }

            statusView.post {
                statusView.text = statusText
                cameraView.text = camText
            }
        }
    }

    private fun interruptCutscene() {
        val mc = Minecraft.getInstance()
        mc.execute {
            if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
                    .canSend(net.foxmediax.scriptfx.network.CutsceneInterruptPayload.TYPE)
            ) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    net.foxmediax.scriptfx.network.CutsceneInterruptPayload()
                )
            }
            CutsceneClient.clear()
            refresh()
        }
    }

    private fun copyToClipboard(text: String) {
        val mc = Minecraft.getInstance()
        mc.execute {
            mc.keyboardHandler.clipboard = text
        }
    }

    companion object {
        private val TEMPLATES = listOf(
            "Простой влёт камеры" to """
                cutscene_start player
                player_lock on
                hud_hide on
                letterbox on 0.12
                camera_set 0 80 0 0 20
                camera_move 10 78 10 45 10 "2.sec"
                ticktime "1.sec"
                cutscene_end
            """.trimIndent(),
            "Путь из нескольких точек" to """
                cutscene_start player
                player_lock on
                camera_path_start
                camera_path_point 0 70 0 0 0 "1.5.sec"
                camera_path_point 20 72 15 90 5 "2.sec"
                camera_path_point 40 70 0 180 0 "1.5.sec"
                camera_path_end wait
                cutscene_end
            """.trimIndent(),
            "LookAt + FOV" to """
                cutscene_start player
                player_lock on
                camera_set 100 75 100 0 0
                camera_fov 40 "0.8.sec"
                camera_lookat 100 65 120 "1.sec"
                ticktime "2.sec"
                camera_fov 70 "0.5.sec"
                cutscene_end
            """.trimIndent()
        )

        private val COMMAND_HELP = """
            cutscene_start [player|all] — старт
            cutscene_end — конец, возврат позиции
            player_lock on|off — блок управления
            hud_hide on|off — скрыть HUD
            letterbox on|off [0.0–0.4] — чёрные полосы
            camera_set x y z yaw pitch [world] — телепорт камеры
            camera_move x y z yaw pitch duration — плавный перелёт
            camera_path_start / camera_path_point ... / camera_path_end [wait]
            camera_lookat x y z [duration]
            camera_lookat_entity "Ник"|@s [duration]
            camera_fov 10–170 [duration]
            Прерывание игроком: Ctrl+Alt+End
            Аварийный сброс: /scriptfx camera_reset
        """.trimIndent()
    }
}

class DocsPage(host: PanelHost) : PanelPage(host) {

    override val breadcrumb: String?
        get() = current?.let { "Документация -> ${it.section} / ${it.subsection}" }
            ?: "Документация"

    private data class Node(val section: String, val subsection: String)

    private var current: Node? = null
    private lateinit var docsBody: LinearLayout
    private lateinit var titleView: TextView

    /**
     * Дерево = реальные ключи Documentation.page().
     * subsection = "" означает «весь раздел» (Триггеры, Примеры).
     */
    private val TREE: List<Pair<String, List<String>>> = listOf(
        "Скрипты" to listOf(
            "Переменные",
            "Глобальные переменные",
            "Для сюжета",
            "Для камеры",
            "Катсцены",
            "NPC",
            "Частицы",
            "Звук и видео",
            "Квесты"
        ),
        "Триггеры" to listOf(""),
        "Примеры скриптов" to listOf(""),
        "Чат и сообщения" to listOf(""),
        "Панель управления" to listOf("")
    )

    override fun createView(ctx: Context): View {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ColorDrawable(PanelUi.COL_CONTENT)
        }

        val nav = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PanelUi.dp(ctx, 6), PanelUi.dp(ctx, 6), PanelUi.dp(ctx, 6), PanelUi.dp(ctx, 6))
            background = ColorDrawable(0xFF141418.toInt())
        }

        for ((section, subs) in TREE) {
            nav.addView(
                PanelUi.sectionTitle(ctx, section),
                PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
            )
            for (sub in subs) {
                val label = if (sub.isEmpty()) "  · Обзор" else "  · $sub"
                nav.addView(
                    PanelUi.flatButton(ctx, label) {
                        current = Node(section, sub)
                        host.refreshBreadcrumb()
                        renderDocsBody()
                    },
                    PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP).apply {
                        topMargin = PanelUi.dp(ctx, 2)
                    }
                )
            }
        }

        val navScroll = ScrollView(ctx)
        navScroll.addView(nav, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))
        root.addView(navScroll, PanelUi.lp(PanelUi.dp(ctx, 168), PanelUi.MATCH))

        val right = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PanelUi.dp(ctx, 10), PanelUi.dp(ctx, 8), PanelUi.dp(ctx, 10), PanelUi.dp(ctx, 8))
        }
        titleView = TextView(ctx).apply {
            textSize = 14f
            setTextColor(PanelUi.COL_TEXT)
            setPadding(0, 0, 0, PanelUi.dp(ctx, 6))
        }
        right.addView(titleView, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))

        val scroll = ScrollView(ctx)
        docsBody = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(docsBody, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))
        right.addView(scroll, LinearLayout.LayoutParams(PanelUi.MATCH, 0, 1f))

        root.addView(right, LinearLayout.LayoutParams(0, PanelUi.MATCH, 1f))

        current = Node("Скрипты", "Переменные")
        renderDocsBody()
        return root
    }

    private fun renderDocsBody() {
        if (!this::docsBody.isInitialized) return
        val ctx = docsBody.context
        docsBody.removeAllViews()
        val node = current ?: return

        titleView.text = if (node.subsection.isEmpty()) {
            node.section
        } else {
            "${node.section} / ${node.subsection}"
        }

        val lines = Documentation.page(node.section, node.subsection)
        if (lines.isEmpty()) {
            docsBody.addView(
                PanelUi.mutedText(ctx, "Нет текста для «${node.section}» / «${node.subsection}»"),
                PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
            )
            return
        }

        for (line in lines) {
            if (line.trim() == Documentation.DIVIDER) {
                docsBody.addView(
                    PanelUi.divider(ctx),
                    PanelUi.lp(PanelUi.MATCH, PanelUi.px1(ctx)).apply {
                        topMargin = PanelUi.dp(ctx, 8)
                        bottomMargin = PanelUi.dp(ctx, 8)
                    }
                )
            } else if (line.startsWith("Раздел:")) {
                docsBody.addView(
                    TextView(ctx).apply {
                        text = line
                        textSize = 13f
                        setTextColor(PanelUi.COL_ACCENT)
                        setPadding(0, PanelUi.dp(ctx, 4), 0, PanelUi.dp(ctx, 4))
                    },
                    PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
                )
            } else {
                docsBody.addView(
                    TextView(ctx).apply {
                        text = line
                        textSize = 12f
                        setTextColor(PanelUi.COL_TEXT)
                        setPadding(0, PanelUi.dp(ctx, 2), 0, PanelUi.dp(ctx, 2))
                        // тап — копировать строку (удобно для команд)
                        setOnClickListener {
                            val mc = Minecraft.getInstance()
                            mc.execute { mc.keyboardHandler.clipboard = line }
                        }
                    },
                    PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
                )
            }
        }
    }
}

class NpcEditorPage(host: PanelHost) : PanelPage(host) {

    override val breadcrumb: String?
        get() = when {
            selectedDef != null -> "NPC-редактор -> ${selectedDef!!.id}"
            selectedProject != null -> "NPC-редактор -> $selectedProject"
            else -> "NPC-редактор"
        }

    override fun onDestroy() {
        NpcPreviewHelper.clear()  // definition = null, bounds = null, entity = null
    }

    private var selectedProject: String? = null
    private var selectedDef: NpcDefinition? = null

    private lateinit var ctx: Context
    private lateinit var overlays: OverlayHost
    private lateinit var pathView: TextView
    private lateinit var leftForm: LinearLayout
    private lateinit var detailBox: LinearLayout
    private lateinit var previewView: NpcPreviewView

    override fun createView(ctx: Context): View {
        this.ctx = ctx
        NpcRegistry.reload()

        val root = FrameLayout(ctx).apply {
            background = ColorDrawable(PanelUi.COL_CONTENT)
        }
        val overlayLayer = FrameLayout(ctx)
        overlays = OverlayHost(ctx, overlayLayer)

        val page = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PanelUi.dp(ctx, 10), PanelUi.dp(ctx, 8), PanelUi.dp(ctx, 10), PanelUi.dp(ctx, 8))
        }

        // ----- верхняя полоса: путь + кнопки -----
        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, PanelUi.dp(ctx, 8))
        }
        pathView = TextView(ctx).apply {
            textSize = 12f
            setTextColor(PanelUi.COL_MUTED)
        }
        top.addView(pathView, LinearLayout.LayoutParams(0, PanelUi.WRAP, 1f))
        top.addView(PanelUi.flatButton(ctx, "Обновить") {
            NpcRegistry.reload()
            refresh()
        }, PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP))
        top.addView(
            PanelUi.flatButton(ctx, "Создать…") { openCreate() },
            PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP).apply {
                leftMargin = PanelUi.dp(ctx, 6)
            }
        )
        page.addView(top, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))

        // ----- три колонки -----
        val columns = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        // === ЛЕВАЯ: форма ===
        leftForm = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PanelUi.dp(ctx, 8), PanelUi.dp(ctx, 4), PanelUi.dp(ctx, 8), PanelUi.dp(ctx, 4))
            background = UiStyle.rounded(ctx, 0xFF101014.toInt(), 8f)
        }
        buildLeftForm(ctx, leftForm)
        val leftScroll = ScrollView(ctx)
        leftScroll.addView(leftForm, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))
        columns.addView(
            leftScroll,
            LinearLayout.LayoutParams(0, PanelUi.MATCH, 0.95f).apply {
                rightMargin = PanelUi.dp(ctx, 8)
            }
        )

        // === ЦЕНТР: превью ===
        val previewWrap = FrameLayout(ctx).apply {
            // серый, чтобы чёрные части скина не сливались
            background = UiStyle.rounded(ctx, 0xFF3A3A42.toInt(), 8f, 0xFF55555E.toInt(), 1)
            setPadding(PanelUi.dp(ctx, 4), PanelUi.dp(ctx, 4), PanelUi.dp(ctx, 4), PanelUi.dp(ctx, 4))
        }
        previewView = NpcPreviewView(ctx)
        previewWrap.addView(
            previewView,
            FrameLayout.LayoutParams(PanelUi.MATCH, PanelUi.MATCH)
        )
        columns.addView(
            previewWrap,
            LinearLayout.LayoutParams(0, PanelUi.MATCH, 1.1f).apply {
                rightMargin = PanelUi.dp(ctx, 8)
            }
        )

        // === ПРАВАЯ: свойства ===
        detailBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PanelUi.dp(ctx, 10), PanelUi.dp(ctx, 8), PanelUi.dp(ctx, 10), PanelUi.dp(ctx, 8))
            background = UiStyle.rounded(ctx, 0xFF101014.toInt(), 8f)
        }
        val rightScroll = ScrollView(ctx)
        rightScroll.addView(detailBox, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))
        columns.addView(
            rightScroll,
            LinearLayout.LayoutParams(0, PanelUi.MATCH, 1.0f)
        )

        page.addView(columns, LinearLayout.LayoutParams(PanelUi.MATCH, 0, 1f))

        root.addView(page, FrameLayout.LayoutParams(PanelUi.MATCH, PanelUi.MATCH))
        root.addView(overlayLayer, FrameLayout.LayoutParams(PanelUi.MATCH, PanelUi.MATCH))

        refresh()
        return root
    }

    // ------------------------------------------------------------------
    // Левая форма (как на макете)
    // ------------------------------------------------------------------

    private fun buildLeftForm(ctx: Context, box: LinearLayout) {
        box.addView(
            TextView(ctx).apply {
                text = "Проекты и список NPC"
                textSize = 15f
                setTextColor(PanelUi.COL_TEXT)
                setPadding(0, 0, 0, PanelUi.dp(ctx, 10))
            },
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
        )
        // listBox добавляется в ensureListBox() / refresh()
    }

    // ------------------------------------------------------------------
    // refresh / детали
    // ------------------------------------------------------------------

    private fun refresh() {
        if (!this::pathView.isInitialized) return
        val project = selectedProject
        val def = selectedDef

        pathView.text = when {
            def != null && project != null ->
                "/scriptfx/projects/$project/npc/${def.id}.fxnpc"
            project != null ->
                "/scriptfx/projects/$project/npc"
            else ->
                "/scriptfx/projects/*/npc"
        }

        ensureListBox()
        listBox.removeAllViews()

        when {
            project == null -> {
                val projects = NpcRegistry.listProjects()
                if (projects.isEmpty()) {
                    listBox.addView(
                        hint("Нет проектов"),
                        PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
                    )
                } else {
                    for (projectName in projects) {
                        listBox.addView(
                            listChip(projectName) {
                                selectedProject = projectName
                                selectedDef = null
                                refresh()
                            },
                            LinearLayout.LayoutParams(PanelUi.MATCH, PanelUi.WRAP).apply {
                                topMargin = PanelUi.dp(ctx, 4)
                            }
                        )
                    }
                }
            }

            def == null -> {
                listBox.addView(
                    listChip("← Проекты") {
                        selectedProject = null
                        selectedDef = null
                        refresh()
                    },
                    PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
                )
                val defs = NpcRegistry.listNpcs(project)
                if (defs.isEmpty()) {
                    listBox.addView(
                        hint("Нет .fxnpc — «Создать…»"),
                        PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
                    )
                } else {
                    for (npcDef in defs) {
                        listBox.addView(
                            listChip(npcDef.id) {
                                selectedDef = npcDef
                                refresh()
                            },
                            LinearLayout.LayoutParams(PanelUi.MATCH, PanelUi.WRAP).apply {
                                topMargin = PanelUi.dp(ctx, 4)
                            }
                        )
                    }
                }
            }

            else -> {
                listBox.addView(
                    listChip("← Список NPC") {
                        selectedDef = null
                        refresh()
                    },
                    PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
                )
            }
        }

        detailBox.removeAllViews()
        if (def != null && project != null) {
            buildDetailPanel(project, def)
        } else {
            detailBox.addView(
                hint("Выберите NPC слева или создайте нового"),
                PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
            )
        }

        if (this::previewView.isInitialized) {
            previewView.definition = def
        }
        host.refreshBreadcrumb()
    }

    private lateinit var listBox: LinearLayout

    private fun ensureListBox() {
        if (this::listBox.isInitialized) return
        listBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        leftForm.addView(listBox, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))
    }

    private fun buildDetailPanel(project: String, def: NpcDefinition) {
        detailBox.removeAllViews()

        // Заголовок карточки
        detailBox.addView(
            TextView(ctx).apply {
                text = "Редактирование"
                textSize = 11f
                setTextColor(PanelUi.COL_MUTED)
                setPadding(0, 0, 0, PanelUi.dp(ctx, 2))
            },
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
        )
        detailBox.addView(
            TextView(ctx).apply {
                text = def.id
                textSize = 16f
                setTextColor(PanelUi.COL_TEXT)
                setPadding(0, 0, 0, PanelUi.dp(ctx, 12))
            },
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
        )

        // --- Основное ---
        beginSection("Основное")
        propReadonly("ID NPC", def.id)
        propEdit("Отображаемое имя", def.displayName) { v ->
            save(project, def.copy(displayName = v.ifBlank { def.id }))
        }
        endSection()

        // --- Модель ---
        beginSection("Модель / анимация")
        propEdit("Модель", def.model) { v ->
            save(project, def.copy(model = v.trim()))
        }
        propEdit("Текстура", def.texture) { v ->
            save(project, def.copy(texture = v.trim()))
        }
        propEdit("Анимация", def.animation) { v ->
            save(project, def.copy(animation = v.trim()))
        }
        propEdit("Стандартная анимация", def.defaultAnim) { v ->
            save(project, def.copy(defaultAnim = v.ifBlank { "idle" }))
        }
        endSection()

        // --- Поведение ---
        beginSection("Поведение")
        propEdit("Стандартный режим", def.defaultMode) { v ->
            save(project, def.copy(defaultMode = v.ifBlank { "interact" }))
        }

        val modes = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, PanelUi.dp(ctx, 2), 0, PanelUi.dp(ctx, 8))
        }
        val modeMap = listOf(
            "dialog" to "Диалог",
            "interact" to "Интеракт",
            "trade" to "Торговля"
        )
        for ((id, label) in modeMap) {
            val selected = def.defaultMode.equals(id, ignoreCase = true) ||
                    def.defaultMode.equals(label, ignoreCase = true)
            modes.addView(
                modeChip(label, selected) {
                    save(project, def.copy(defaultMode = id))
                },
                PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP).apply {
                    rightMargin = PanelUi.dp(ctx, 6)
                }
            )
        }
        detailBox.addView(modes, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))

        propEdit("Скрипт после интеракции", def.onInteractScript) { v ->
            save(project, def.copy(onInteractScript = v.trim()))
        }
        propEdit("Клавиша интеракции", def.interactKey) { v ->
            save(project, def.copy(interactKey = v.ifBlank { "X" }))
        }
        endSection()

        // --- Характеристики ---
        beginSection("Характеристики")
        propEdit("Здоровье", def.maxHealth.toString()) { v ->
            val n = v.toFloatOrNull() ?: def.maxHealth
            save(project, def.copy(maxHealth = n))
        }
        propEdit("Скорость", def.speed.toString()) { v ->
            val s = v.toFloatOrNull() ?: def.speed
            save(project, def.copy(speed = s.coerceIn(0.01f, 2f)))
        }
        endSection()

        beginSection("Флаги")
        propToggle("Смотреть на игрока", def.lookAtPlayer) { on ->
            save(project, def.copy(lookAtPlayer = on))
        }
        propToggle("Гравитация", def.gravity) { on ->
            save(project, def.copy(gravity = on))
        }
        propToggle("Неуязвимость", def.invulnerable) { on ->
            save(project, def.copy(invulnerable = on))
        }
        propToggle("Коллизия", def.collide) { on ->
            save(project, def.copy(collide = on))
        }
        propToggle("Имя над головой", def.nametag) { on ->
            save(project, def.copy(nametag = on))
        }
        propToggle("Без звука", def.silent) { on ->
            save(project, def.copy(silent = on))
        }
        endSection()

        // Удалить — внизу, отдельно
        detailBox.addView(
            PanelUi.flatButton(ctx, "Удалить NPC") {
                overlays.confirm(
                    message = "Удалить ${def.id}?",
                    yes = "Удалить",
                    no = "Отмена",
                    onYes = {
                        NpcRegistry.delete(project, def.id)
                        selectedDef = null
                        NpcRegistry.reload()
                        refresh()
                    }
                )
            },
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP).apply {
                topMargin = PanelUi.dp(ctx, 16)
            }
        )
    }

    private fun beginSection(title: String) {
        detailBox.addView(
            TextView(ctx).apply {
                text = title
                textSize = 12f
                setTextColor(PanelUi.COL_ACCENT)
                setPadding(0, PanelUi.dp(ctx, 10), 0, PanelUi.dp(ctx, 6))
            },
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
        )
    }

    private fun endSection() {
        detailBox.addView(
            View(ctx).apply {
                background = ColorDrawable(PanelUi.COL_DIVIDER)
            },
            PanelUi.lp(PanelUi.MATCH, PanelUi.px1(ctx)).apply {
                topMargin = PanelUi.dp(ctx, 6)
                bottomMargin = PanelUi.dp(ctx, 2)
            }
        )
    }
    private fun sectionTitle(title: String) {
        detailBox.addView(
            TextView(ctx).apply {
                text = title
                textSize = 12f
                setTextColor(PanelUi.COL_ACCENT)
                setPadding(0, PanelUi.dp(ctx, 12), 0, PanelUi.dp(ctx, 4))
            },
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
        )
    }

    private fun propToggle(key: String, value: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                PanelUi.dp(ctx, 10),
                PanelUi.dp(ctx, 6),
                PanelUi.dp(ctx, 8),
                PanelUi.dp(ctx, 6)
            )
            background = PanelUi.btnBg(ctx, 0xFF1A1A22.toInt(), 8f)
        }

        row.addView(
            TextView(ctx).apply {
                text = key
                textSize = 12f
                setTextColor(PanelUi.COL_TEXT)
            },
            LinearLayout.LayoutParams(0, PanelUi.WRAP, 1f)
        )

        val label = if (value) "Вкл" else "Выкл"
        row.addView(
            PanelUi.flatButton(ctx, label) {
                onChange(!value)
            }.apply {
                if (value) {
                    background = PanelUi.btnBg(ctx, PanelUi.COL_BTN_SELECTED, 6f)
                }
            },
            PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP)
        )

        detailBox.addView(
            row,
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP).apply {
                bottomMargin = PanelUi.dp(ctx, 6)
            }
        )
    }

    /** Строка свойства️ */
    private fun propEdit(key: String, value: String, onSave: (String) -> Unit) {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                PanelUi.dp(ctx, 10),
                PanelUi.dp(ctx, 8),
                PanelUi.dp(ctx, 8),
                PanelUi.dp(ctx, 8)
            )
            background = PanelUi.btnBg(ctx, 0xFF1A1A22.toInt(), 8f)
        }

        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        texts.addView(
            TextView(ctx).apply {
                text = key
                textSize = 11f
                setTextColor(PanelUi.COL_MUTED)
            },
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
        )
        texts.addView(
            TextView(ctx).apply {
                text = value.ifBlank { "—" }
                textSize = 13f
                setTextColor(PanelUi.COL_TEXT)
                setPadding(0, PanelUi.dp(ctx, 2), 0, 0)
            },
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
        )

        row.addView(texts, LinearLayout.LayoutParams(0, PanelUi.WRAP, 1f))
        row.addView(
            PanelUi.flatButton(ctx, "✎") {
                overlays.input(key, value) { onSave(it) }
            },
            PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP)
        )

        detailBox.addView(
            row,
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP).apply {
                bottomMargin = PanelUi.dp(ctx, 6)
            }
        )
    }

    private fun propReadonly(key: String, value: String) {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                PanelUi.dp(ctx, 10),
                PanelUi.dp(ctx, 8),
                PanelUi.dp(ctx, 10),
                PanelUi.dp(ctx, 8)
            )
            background = PanelUi.btnBg(ctx, 0xFF16161C.toInt(), 8f)
        }

        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        texts.addView(
            TextView(ctx).apply {
                text = key
                textSize = 11f
                setTextColor(PanelUi.COL_MUTED)
            },
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
        )
        texts.addView(
            TextView(ctx).apply {
                text = value
                textSize = 13f
                setTextColor(PanelUi.COL_TEXT)
                setPadding(0, PanelUi.dp(ctx, 2), 0, 0)
            },
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
        )

        row.addView(texts, LinearLayout.LayoutParams(0, PanelUi.WRAP, 1f))
        detailBox.addView(
            row,
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP).apply {
                bottomMargin = PanelUi.dp(ctx, 6)
            }
        )
    }

    private fun modeChip(label: String, selected: Boolean, onClick: () -> Unit): View {
        return PanelUi.flatButton(ctx, label, onClick).apply {
            alpha = if (selected) 1f else 0.75f
            if (selected) {
                background = PanelUi.btnBg(ctx, PanelUi.COL_BTN_SELECTED, 8f)
            }
        }
    }

    private fun listChip(label: String, onClick: () -> Unit): View =
        PanelUi.flatButton(ctx, label, onClick).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }

    private fun hint(text: String) = TextView(ctx).apply {
        this.text = text
        textSize = 12f
        setTextColor(PanelUi.COL_MUTED)
        setPadding(0, PanelUi.dp(ctx, 8), 0, 0)
    }

    private fun openCreate() {
        // твой существующий openCreate() — оставь как был
        val project = selectedProject
        if (project == null) {
            overlays.actions("Выберите проект", listOf("ОК" to {}))
            return
        }
        overlays.input("ID нового NPC", "npc_") { id ->
            val clean = id.trim()
            if (clean.isEmpty()) return@input
            val def = NpcDefinition(id = clean, displayName = clean)
            save(project, def)
            selectedDef = def
            // fillFormFrom(def) — убрать
            refresh()
        }
    }

    private fun save(project: String, def: NpcDefinition) {
        // вызови свой NpcRegistry.save / writeFxnpc — как в текущем коде
        NpcRegistry.save(project, def)
        selectedDef = def
        NpcRegistry.reload()
        selectedDef = NpcRegistry.get(project, def.id) ?: def
        refresh()
    }
}

class ParticlesPage(host: PanelHost) : PanelPage(host) {

    override val breadcrumb: String get() = "Частицы"

    private lateinit var ctx: Context
    private lateinit var listBox: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var overlays: OverlayHost

    override fun createView(ctx: Context): View {
        this.ctx = ctx
        ParticleRegistry.reload()

        val root = FrameLayout(ctx).apply {
            background = ColorDrawable(PanelUi.COL_CONTENT)
        }
        val overlayLayer = FrameLayout(ctx)
        overlays = OverlayHost(ctx, overlayLayer)

        val page = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PanelUi.dp(ctx, 12), PanelUi.dp(ctx, 10), PanelUi.dp(ctx, 12), PanelUi.dp(ctx, 10))
        }

        // --- шапка ---
        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(
            TextView(ctx).apply {
                text = "Эффекты Snowstorm"
                textSize = 16f
                setTextColor(PanelUi.COL_TEXT)
            },
            LinearLayout.LayoutParams(0, PanelUi.WRAP, 1f)
        )
        top.addView(
            PanelUi.flatButton(ctx, "Обновить") {
                ParticleRegistry.reload()
                refreshList()
            },
            PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP)
        )
        top.addView(
            PanelUi.flatButton(ctx, "Стоп все") {
                ParticleRuntime.handlePayload(
                    ParticlePayload(ParticlePayload.STOP_ALL, "", "", 0.0, 0.0, 0.0)
                )
                statusView.text = "Все эмиттеры остановлены"
            },
            PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP).apply {
                leftMargin = PanelUi.dp(ctx, 6)
            }
        )
        page.addView(top, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))

        page.addView(
            TextView(ctx).apply {
                text = "config/scriptfx/projects/*/particles/*.particle.json  ·  Play = у игрока (~ ~1 ~)"
                textSize = 11f
                setTextColor(PanelUi.COL_MUTED)
                setPadding(0, PanelUi.dp(ctx, 6), 0, PanelUi.dp(ctx, 4))
            },
            PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
        )

        statusView = TextView(ctx).apply {
            text = ""
            textSize = 12f
            setTextColor(PanelUi.COL_MUTED)
            setPadding(0, 0, 0, PanelUi.dp(ctx, 8))
        }
        page.addView(statusView, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))

        val scroll = ScrollView(ctx)
        listBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(listBox, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))
        page.addView(scroll, LinearLayout.LayoutParams(PanelUi.MATCH, 0, 1f))

        root.addView(page, FrameLayout.LayoutParams(PanelUi.MATCH, PanelUi.MATCH))
        root.addView(overlayLayer, FrameLayout.LayoutParams(PanelUi.MATCH, PanelUi.MATCH))

        refreshList()
        return root
    }

    private fun refreshList() {
        listBox.removeAllViews()
        val effects = ParticleRegistry.list()
        statusView.text = "Загружено: ${effects.size}"

        if (effects.isEmpty()) {
            listBox.addView(
                TextView(ctx).apply {
                    text = "Нет эффектов.\nПоложи файл из Snowstorm: projects/<имя>/particles/*.particle.json"
                    textSize = 13f
                    setTextColor(PanelUi.COL_MUTED)
                },
                PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
            )
            return
        }

        for (fx in effects) {
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(PanelUi.dp(ctx, 10), PanelUi.dp(ctx, 8), PanelUi.dp(ctx, 10), PanelUi.dp(ctx, 8))
                background = UiStyle.rounded(ctx, 0xFF141418.toInt(), 8f, 0xFF2A2A34.toInt(), 1)
            }

            card.addView(
                TextView(ctx).apply {
                    text = fx.identifier
                    textSize = 14f
                    setTextColor(PanelUi.COL_TEXT)
                },
                PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
            )

            val meta = buildString {
                append(fx.material)
                append(" · rate ")
                append(if (fx.rateMode == ParticleEffect.RateMode.INSTANT) "instant ${fx.spawnRate.toInt()}" else "${fx.spawnRate}/s")
                append(" · life ")
                append(fx.particleLifetime)
                append("s")
                if (fx.flipbook != null) append(" · flipbook ${fx.flipbook.maxFrame}f")
            }
            card.addView(
                TextView(ctx).apply {
                    text = meta
                    textSize = 11f
                    setTextColor(PanelUi.COL_MUTED)
                    setPadding(0, PanelUi.dp(ctx, 3), 0, PanelUi.dp(ctx, 6))
                },
                PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
            )

            val actions = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            actions.addView(
                PanelUi.flatButton(ctx, "Play") {
                    playAtPlayer(fx.identifier)
                    statusView.text = "Play: ${fx.identifier}"
                },
                PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP)
            )
            actions.addView(
                PanelUi.flatButton(ctx, "Stop") {
                    ParticleRuntime.handlePayload(
                        ParticlePayload(ParticlePayload.STOP, fx.identifier, "", 0.0, 0.0, 0.0)
                    )
                    statusView.text = "Stop: ${fx.identifier}"
                },
                PanelUi.lp(PanelUi.WRAP, PanelUi.WRAP).apply {
                    leftMargin = PanelUi.dp(ctx, 6)
                }
            )
            card.addView(actions, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))

            listBox.addView(
                card,
                LinearLayout.LayoutParams(PanelUi.MATCH, PanelUi.WRAP).apply {
                    bottomMargin = PanelUi.dp(ctx, 8)
                }
            )
        }
    }

    private fun playAtPlayer(effectId: String) {
        val p = Minecraft.getInstance().player ?: return
        ParticleRuntime.handlePayload(
            ParticlePayload(
                ParticlePayload.PLAY,
                effectId,
                "",
                p.x,
                p.y + 1.0,
                p.z
            )
        )
    }
}