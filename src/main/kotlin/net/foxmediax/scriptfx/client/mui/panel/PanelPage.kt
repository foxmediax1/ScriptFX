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
import net.foxmediax.scriptfx.client.NpcCustomsLoader
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
import net.foxmediax.scriptfx.npc.NpcModelPreset
import java.awt.Canvas

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
            val entries = ScriptFXLog.getRecentLogs()

            if (entries.isEmpty()) {
                box.addView(
                    PanelUi.mutedText(ctx, "Лог пуст"),
                    PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
                )
                return
            }

            for (line in entries.takeLast(200)) {
                val color = when {
                    line.startsWith("[ERROR]") -> PanelUi.COL_ERROR
                    line.startsWith("[WARN]") -> PanelUi.COL_WARNING
                    line.startsWith("[DEBUG]") -> PanelUi.COL_MUTED
                    else -> PanelUi.COL_TEXT
                }

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