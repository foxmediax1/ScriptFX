package net.foxmediax.scriptfx.client.ui.pages

import net.foxmediax.scriptfx.client.ui.StudioPage
import net.foxmediax.scriptfx.client.ui.StudioSession
import net.foxmediax.scriptfx.client.ui.UiButton
import net.foxmediax.scriptfx.client.ui.UiLabel
import net.foxmediax.scriptfx.client.ui.UiPanel
import net.foxmediax.scriptfx.client.ui.UiScrollList
import net.foxmediax.scriptfx.client.ui.UiTextField
import net.foxmediax.scriptfx.client.ui.UiTheme
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.io.File

class CutscenesPage : StudioPage {
    override val title = "Катсцены"

    private val root = UiPanel(0)
    private val list = UiScrollList(onSelect = { idx, _ -> selectFile(idx) })
    private val status = UiLabel("", UiTheme.TEXT_MUTED)
    private val nameField = UiTextField(hint = "имя шаблона, напр. intro")
    private val preview = UiLabel("", UiTheme.TEXT_MUTED)

    private var files: List<File> = emptyList()
    private var selected: File? = null

    private val projectDir: File?
        get() = StudioSession.selectedProject

    override fun onShow() = reload()

    override fun layout(x: Int, y: Int, w: Int, h: Int) {
        root.clearChildren()
        root.setBounds(x, y, w, h)
        val pad = UiTheme.PAD
        val leftW = (w * 0.34f).toInt().coerceIn(200, 320)

        root.add(UiLabel("Катсцены", UiTheme.ACCENT).also {
            it.setBounds(x + pad, y + pad, leftW, 16)
        })

        list.setBounds(x + pad, y + 36, leftW - pad, h - 110)
        root.add(list)

        nameField.setBounds(x + pad, y + h - 68, leftW - pad, 26)
        root.add(nameField)

        val btn = UiButton("+ Шаблон .sfxs") { createTemplate() }
        btn.style = UiButton.Style.PRIMARY
        btn.setBounds(x + pad, y + h - 36, leftW - pad, 28)
        root.add(btn)

        val rx = x + leftW + pad
        val rw = w - leftW - pad * 2

        root.add(UiLabel("Справка по командам", UiTheme.ACCENT).also {
            it.setBounds(rx, y + pad, rw, 16)
        })

        val help = listOf(
            "cutscene_start [player|all]",
            "camera_set x y z yaw pitch",
            "camera_move x y z yaw pitch duration",
            "camera_path_start",
            "camera_path_point x y z yaw pitch duration",
            "camera_path_end [wait]",
            "camera_fov value [duration]",
            "camera_lookat x y z [duration]",
            "cutscene_end",
            "stopscript"
        )
        help.forEachIndexed { i, line ->
            root.add(UiLabel(line, UiTheme.TEXT_MUTED).also {
                it.setBounds(rx, y + 36 + i * 16, rw, 14)
            })
        }

        val helpBottom = y + 36 + help.size * 16 + 12
        root.add(UiLabel("Выбранный файл", UiTheme.ACCENT).also {
            it.setBounds(rx, helpBottom, rw, 14)
        })
        preview.setBounds(rx, helpBottom + 20, rw, 48)
        root.add(preview)

        val btnRefresh = UiButton("Обновить список") { reload() }
        btnRefresh.setBounds(rx, y + h - 36, 140, 28)
        root.add(btnRefresh)

        status.setBounds(rx + 150, y + h - 30, rw - 150, 14)
        root.add(status)
    }

    private fun cutDir(): File? {
        val proj = projectDir ?: return null
        return File(proj, "cutscenes").also { if (!it.exists()) it.mkdirs() }
    }

    private fun reload() {
        val dir = cutDir()
        files = dir?.listFiles()
            ?.filter { it.isFile && it.extension.equals("sfxs", true) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
        list.items = files.map { it.name }
        if (selected != null) {
            selected = files.find { it.name == selected!!.name }
        }
        status.text = if (projectDir == null) "Выбери проект на главной"
        else "${files.size} файл(ов) в cutscenes/"
        status.color = UiTheme.TEXT_MUTED
        updatePreview()
    }

    private fun selectFile(idx: Int) {
        selected = files.getOrNull(idx)
        updatePreview()
    }

    private fun updatePreview() {
        val f = selected
        if (f == null || !f.isFile) {
            preview.text = "Файл не выбран"
            preview.color = UiTheme.TEXT_MUTED
            return
        }
        val head = runCatching {
            f.readLines().take(6).joinToString(" | ")
        }.getOrElse { it.message ?: "error" }
        preview.text = "${f.name}\n$head"
        preview.color = UiTheme.TEXT
    }

    private fun createTemplate() {
        val dir = cutDir()
        if (dir == null) {
            status.text = "Нет проекта"
            status.color = UiTheme.WARNING
            return
        }
        val name = nameField.value.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .ifEmpty { "intro" }
        val file = File(dir, "$name.sfxs")
        if (file.exists()) {
            status.text = "Уже есть: ${file.name}"
            status.color = UiTheme.WARNING
            return
        }
        file.writeText(
            """
            // Шаблон катсцены ScriptFX
            cutscene_start player
            camera_set 0 70 0 0 20
            ticktime 0.5.sec
            print "Начало сцены..."
            ticktime 1.5.sec
            camera_move 10 72 10 -45 15 2.sec
            ticktime 2.sec
            cutscene_end
            stopscript
            """.trimIndent() + "\n"
        )
        nameField.value = ""
        selected = file
        reload()
        status.text = "Создано: cutscenes/${file.name}"
        status.color = UiTheme.SUCCESS
    }

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) =
        root.render(g, mouseX, mouseY, pt)

    override fun mouseClicked(mx: Int, my: Int, button: Int) = root.mouseClicked(mx, my, button)
    override fun mouseReleased(mx: Int, my: Int, button: Int) = root.mouseReleased(mx, my, button)
    override fun mouseScrolled(mx: Double, my: Double, sx: Double, sy: Double) =
        root.mouseScrolled(mx, my, sx, sy)
    override fun keyPressed(key: Int, scan: Int, modifiers: Int) = root.keyPressed(key, scan, modifiers)
    override fun charTyped(codePoint: Int, modifiers: Int) = root.charTyped(codePoint, modifiers)
}