package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.view.View
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_ACCENT
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
import net.foxmediax.scriptfx.gui.FileBrowser
import net.foxmediax.scriptfx.gui.PanelSection
import java.io.File

/**
 * Раздел «Катсцены»: справка по командам + создание шаблонного .sfxs.
 * Исполнение — через script engine (CutsceneCommands / CameraCommands).
 */
class CutscenesPage(host: PanelHost) : PanelPage(host) {

    override val breadcrumb: String get() = "Катсцены"

    private lateinit var ctx: Context
    private lateinit var overlays: OverlayHost
    private lateinit var listBox: LinearLayout
    private lateinit var detail: LinearLayout

    private val browser: FileBrowser
        get() = ControlPanelFragment.fileBrowser

    private data class CmdRow(val name: String, val args: String, val desc: String)

    private val commands = listOf(
        CmdRow("cutscene_start", "[player|all]", "Старт режима катсцены (камера/ввод)"),
        CmdRow("cutscene_end", "", "Завершить катсцену"),
        CmdRow("camera_set", "x y z yaw pitch [world]", "Мгновенно поставить камеру"),
        CmdRow("camera_move", "x y z yaw pitch duration", "Плавно переместить камеру"),
        CmdRow("camera_path_start", "", "Начать путь камеры"),
        CmdRow("camera_path_point", "x y z yaw pitch duration", "Точка пути"),
        CmdRow("camera_path_end", "[wait]", "Запустить путь; wait — ждать конца"),
        CmdRow("camera_fov", "fov [duration]", "Поле зрения"),
        CmdRow("camera_lookat", "x y z [duration]", "Смотреть в точку"),
        CmdRow("ticktime", "1.sec", "Пауза в скрипте"),
        CmdRow("print", "\"текст\"", "Реплика / титр"),
        CmdRow("stopscript", "", "Конец скрипта")
    )

    override fun createView(ctx: Context): View {
        this.ctx = ctx
        val root = FrameLayout(ctx).apply {
            background = ColorDrawable(COL_CONTENT)
        }
        val overlayLayer = FrameLayout(ctx)
        overlays = OverlayHost(ctx, overlayLayer)

        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        body.addView(buildLeft(ctx), lp(dp(ctx, 280), MATCH))
        body.addView(View(ctx).apply { background = ColorDrawable(COL_DIVIDER) }, lp(px1(ctx), MATCH))
        body.addView(buildRight(ctx), LinearLayout.LayoutParams(0, MATCH, 1f))

        root.addView(body, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(overlayLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        refreshScripts()
        return root
    }

    override fun onDestroy() {
        if (this::overlays.isInitialized) overlays.clear()
    }

    private fun buildLeft(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_SIDEBAR)
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
        }

        col.addView(TextView(ctx).apply {
            text = "Катсцены"
            textSize = 14f
            setTextColor(COL_ACCENT)
        }, lp(MATCH, WRAP))

        col.addView(TextView(ctx).apply {
            text = "Скрипты в текущей папке проекта с префиксом cutscene_ или в подпапке cutscenes/"
            textSize = 11f
            setTextColor(COL_MUTED)
            setPadding(0, dp(ctx, 6), 0, dp(ctx, 10))
        }, lp(MATCH, WRAP))

        col.addView(
            PanelUi.accentButton(ctx, "+ Новый шаблон катсцены") {
                createTemplateDialog()
            },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) }
        )

        col.addView(
            PanelUi.flatButton(ctx, "Открыть в Сценариях") {
                host.navigate(PanelSection.PROJECTS)
            },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 12) }
        )

        listBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx)
        scroll.addView(listBox, lp(MATCH, WRAP))
        col.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return col
    }

    private fun buildRight(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 16))
        }
        col.addView(TextView(ctx).apply {
            text = "Команды камеры и катсцен"
            textSize = 14f
            setTextColor(COL_TEXT)
        }, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 12) })

        detail = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx)
        scroll.addView(detail, lp(MATCH, WRAP))
        col.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        for (c in commands) {
            detail.addView(TextView(ctx).apply {
                text = c.name
                textSize = 13f
                setTextColor(COL_ACCENT)
            }, lp(MATCH, WRAP))
            detail.addView(TextView(ctx).apply {
                text = if (c.args.isBlank()) c.desc else "${c.args}\n${c.desc}"
                textSize = 12f
                setTextColor(COL_MUTED)
                setPadding(0, dp(ctx, 2), 0, dp(ctx, 12))
            }, lp(MATCH, WRAP))
        }
        return col
    }

    private fun scriptsDir(): File {
        val base = browser.currentDir
        val sub = File(base, "cutscenes")
        if (sub.isDirectory) return sub
        return base
    }

    private fun listCutsceneFiles(): List<File> {
        val dir = scriptsDir()
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter {
                it.isFile && it.name.endsWith(".sfxs", true) &&
                        (it.name.startsWith("cutscene_", true) ||
                                dir.name.equals("cutscenes", true))
            }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    private fun refreshScripts() {
        listBox.removeAllViews()
        val files = listCutsceneFiles()
        if (files.isEmpty()) {
            listBox.addView(
                PanelUi.mutedText(ctx, "Нет файлов cutscene_*.sfxs\nСоздай шаблон кнопкой выше"),
                lp(MATCH, WRAP)
            )
            return
        }
        for (f in files) {
            listBox.addView(
                PanelUi.flatButton(ctx, f.nameWithoutExtension) {
                    overlays.actions(
                        f.name,
                        listOf(
                            "Открыть в Сценариях" to {
                                // переход: пользователь откроет файл в Projects вручную
                                host.navigate(PanelSection.PROJECTS)
                                Unit
                            },
                            "Показать путь" to {
                                overlays.actions(f.absolutePath, listOf("ОК" to {}))
                                Unit
                            }
                        )
                    )
                },
                lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 4) }
            )
        }
    }

    private fun createTemplateDialog() {
        overlays.input("Имя катсцены", "intro") { raw ->
            val name = raw.trim()
                .lowercase()
                .replace(Regex("[^a-z0-9_]+"), "_")
                .trim('_')
                .ifEmpty { "intro" }
            createTemplate(name)
        }
    }

    private fun createTemplate(name: String) {
        val dir = scriptsDir().also {
            if (!it.exists()) it.mkdirs()
        }
        // если не cutscenes — всё равно кладём cutscene_ префикс
        val fileName = if (dir.name.equals("cutscenes", true)) {
            "$name.sfxs"
        } else {
            "cutscene_$name.sfxs"
        }
        val file = File(dir, fileName)
        if (file.exists()) {
            overlays.actions("Файл уже есть: ${file.name}", listOf("ОК" to {}))
            return
        }
        file.writeText(TEMPLATE.trimIndent() + "\n")
        refreshScripts()
        overlays.actions(
            "Создано: ${file.name}\nОткрой в разделе Сценарии",
            listOf(
                "В Сценарии" to { host.navigate(PanelSection.PROJECTS); Unit },
                "ОК" to {}
            )
        )
        // рядом с .sfxs
        val cutsfx = File(dir, "$name.cutsfx")
        if (!cutsfx.exists()) {
            cutsfx.writeText(
                """
        # ScriptFX cutscene meta
        script=$fileName
        version=1
        """.trimIndent() + "\n"
            )
        }
    }

    companion object {
        private val TEMPLATE = """
            // Шаблон катсцены ScriptFX
            // Подставь свои координаты (F3 в игре)

            cutscene_start player

            camera_set 0 70 0 0 20
            ticktime 0.5.sec

            print "Начало сцены..."
            ticktime 1.5.sec

            camera_move 10 72 10 -45 15 2.sec
            ticktime 2.sec

            camera_path_start
            camera_path_point 10 72 10 -45 15 1.sec
            camera_path_point 20 74 0 90 10 2.sec
            camera_path_point 0 70 0 0 20 1.5.sec
            camera_path_end wait

            print "Конец сцены"
            ticktime 1.sec

            cutscene_end
            stopscript
        """.trimIndent()
    }
}