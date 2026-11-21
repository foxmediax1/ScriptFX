package net.foxmediax.scriptfx.client.ui.pages

import net.foxmediax.scriptfx.client.particles.AaaParticleBridge
import net.foxmediax.scriptfx.client.particles.AaaPreviewHelper
import net.foxmediax.scriptfx.client.ui.StudioPage
import net.foxmediax.scriptfx.client.ui.StudioSession
import net.foxmediax.scriptfx.client.ui.UiButton
import net.foxmediax.scriptfx.client.ui.UiLabel
import net.foxmediax.scriptfx.client.ui.UiPanel
import net.foxmediax.scriptfx.client.ui.UiScrollList
import net.foxmediax.scriptfx.client.ui.UiTextField
import net.foxmediax.scriptfx.client.ui.UiTheme
import net.foxmediax.scriptfx.particles.ParticleEffect
import net.foxmediax.scriptfx.particles.ParticleRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

class ParticlesPage : StudioPage {
    override val title = "Частицы"

    private val root = UiPanel(0)

    private var effects: List<ParticleEffect> = emptyList()
    private var selected: ParticleEffect? = null

    private val status = UiLabel("Выберите проект на Главной", UiTheme.TEXT_MUTED)
    private val info = UiLabel("", UiTheme.TEXT_MUTED)
    private val propsLabel = UiLabel("", UiTheme.TEXT_MUTED)

    private val list = UiScrollList(onSelect = { idx, _ -> selectEffect(idx) })
    private val idField = UiTextField(hint = "scriptfx:my_fx")

    private val btnRefresh = UiButton("Обновить") { reloadList() }
    private val btnPlay = UiButton("▶ Play") { playSelected() }
    private val btnStop = UiButton("■ Stop") { stopPreview() }
    private val btnScaleUp = UiButton("Scale+") {
        AaaPreviewHelper.setScale(AaaPreviewHelper.scale + 0.25f)
        status.text = "Scale: ${"%.2f".format(AaaPreviewHelper.scale)}"
        status.color = UiTheme.ACCENT
        refreshInfo()
    }
    private val btnScaleDown = UiButton("Scale-") {
        AaaPreviewHelper.setScale(AaaPreviewHelper.scale - 0.25f)
        status.text = "Scale: ${"%.2f".format(AaaPreviewHelper.scale)}"
        status.color = UiTheme.ACCENT
        refreshInfo()
    }
    private val btnResetCam = UiButton("Сброс") {
        AaaPreviewHelper.resetCamera()
        AaaPreviewHelper.setScale(1f)
        status.text = "Камера сброшена"
        status.color = UiTheme.TEXT_MUTED
        refreshInfo()
    }

    private var vpX = 0
    private var vpY = 0
    private var vpW = 0
    private var vpH = 0
    private var dragging = false
    private var lastMx = 0
    private var lastMy = 0

    init {
        btnPlay.style = UiButton.Style.PRIMARY
        btnStop.style = UiButton.Style.DANGER
    }

    override fun onShow() {
        reloadList()
    }

    override fun onHide() {
        AaaPreviewHelper.stop()
        AaaPreviewHelper.clearBounds()
    }

    override fun layout(x: Int, y: Int, w: Int, h: Int) {
        root.clearChildren()
        root.setBounds(x, y, w, h)

        val pad = UiTheme.PAD
        val leftW = (w * 0.26f).toInt().coerceIn(180, 280)
        val rightW = (w * 0.22f).toInt().coerceIn(160, 240)
        val midX = x + leftW + pad
        val midW = (w - leftW - rightW - pad * 3).coerceAtLeast(200)
        val rightX = x + w - rightW - pad
        val barH = 32

        root.add(UiLabel("Эффекты", UiTheme.ACCENT).also {
            it.setBounds(x + pad, y + pad, leftW - pad, 14)
        })
        list.setBounds(x + pad, y + 34, leftW - pad, h - 120)
        root.add(list)

        idField.setBounds(x + pad, y + h - 78, leftW - pad, 26)
        root.add(idField)

        btnRefresh.setBounds(x + pad, y + h - 44, leftW - pad, 28)
        root.add(btnRefresh)

        root.add(UiLabel("Предпросмотр", UiTheme.ACCENT).also {
            it.setBounds(midX, y + pad, midW, 14)
        })

        vpX = midX
        vpY = y + 34
        vpW = midW
        vpH = (h - 34 - barH - pad - 8).coerceAtLeast(120)

        root.add(UiPanel(0xFF0A0A10.toInt()).also {
            it.setBounds(vpX, vpY, vpW, vpH)
        })

        info.setBounds(vpX + 12, vpY + 12, vpW - 24, 56)
        root.add(info)

        val barY = vpY + vpH + 6
        btnPlay.setBounds(vpX, barY, 70, 26)
        btnStop.setBounds(vpX + 76, barY, 70, 26)
        btnScaleDown.setBounds(vpX + 156, barY, 60, 26)
        btnScaleUp.setBounds(vpX + 222, barY, 60, 26)
        btnResetCam.setBounds(vpX + 288, barY, 70, 26)
        root.add(btnPlay)
        root.add(btnStop)
        root.add(btnScaleDown)
        root.add(btnScaleUp)
        root.add(btnResetCam)

        root.add(UiLabel("Свойства эффекта", UiTheme.ACCENT).also {
            it.setBounds(rightX, y + pad, rightW, 14)
        })
        propsLabel.setBounds(rightX, y + 34, rightW, 140)
        root.add(propsLabel)

        status.setBounds(rightX, y + h - 28, rightW, 16)
        root.add(status)

        updateBounds()
        reloadList()
        refreshInfo()
    }

    private fun projectName(): String? = StudioSession.selectedProject?.name

    private fun reloadList() {
        val project = projectName()
        if (project == null) {
            effects = emptyList()
            list.items = emptyList()
            selected = null
            status.text = "Сначала выберите проект на Главной"
            status.color = UiTheme.WARNING
            refreshInfo()
            return
        }

        ParticleRegistry.reload()
        effects = ParticleRegistry.listEntries()
            .filter { it.project == project }
            .map { it.effect }
            .sortedBy { it.identifier.lowercase() }

        list.items = effects.map { it.identifier }
        list.selected = selected?.let { sel ->
            effects.indexOfFirst { it.identifier == sel.identifier }
        } ?: -1
        if (list.selected >= 0) selectEffect(list.selected)
        else {
            selected = null
            idField.value = ""
        }

        status.text = "Проект: $project · эффектов: ${effects.size}"
        status.color = UiTheme.TEXT_MUTED
        refreshInfo()
        updateBounds()
    }

    private fun selectEffect(idx: Int) {
        if (idx !in effects.indices) return
        selected = effects[idx]
        idField.value = selected!!.identifier
        status.text = "Выбран: ${selected!!.identifier}"
        status.color = UiTheme.SUCCESS
        refreshInfo()
    }

    private fun playSelected() {
        val raw = idField.value.trim().ifEmpty { selected?.identifier.orEmpty() }
        if (raw.isEmpty()) {
            status.text = "Нет id эффекта"
            status.color = UiTheme.WARNING
            return
        }
        if (!AaaParticleBridge.available) {
            status.text = "AAA Particles не загружен"
            status.color = UiTheme.DANGER
            return
        }
        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.player == null) {
            status.text = "Нужен singleplayer-мир"
            status.color = UiTheme.WARNING
            return
        }
        val id = Identifier.tryParse(raw)
        if (id == null) {
            status.text = "Некорректный Identifier: $raw"
            status.color = UiTheme.DANGER
            return
        }
        updateBounds()
        AaaPreviewHelper.play(id)
        status.text = "Play: $id · scale=${"%.2f".format(AaaPreviewHelper.scale)}"
        status.color = UiTheme.SUCCESS
        refreshInfo()
    }

    private fun stopPreview() {
        AaaPreviewHelper.stop()
        status.text = "Остановлено"
        status.color = UiTheme.TEXT_MUTED
        refreshInfo()
    }

    private fun updateBounds() {
        if (vpW > 16 && vpH > 16) {
            AaaPreviewHelper.setBounds(vpX + 8, vpY + 8, vpX + vpW - 8, vpY + vpH - 8)
        } else {
            AaaPreviewHelper.clearBounds()
        }
    }

    private fun refreshInfo() {
        val aaa = if (AaaParticleBridge.available) "AAA: OK" else "AAA: нет"
        val play = if (AaaPreviewHelper.playing) "playing" else "idle"
        val eff = AaaPreviewHelper.effectId?.toString() ?: selected?.identifier ?: "—"
        val err = AaaPreviewHelper.loadError
        info.text = buildString {
            appendLine("$aaa · $play")
            appendLine("effect: $eff")
            appendLine("scale=${"%.2f".format(AaaPreviewHelper.scale)}  dist=${"%.1f".format(AaaPreviewHelper.distance)}")
            if (!err.isNullOrBlank()) append("err: $err")
            else append("ЛКМ — yaw/pitch · колёсико — zoom")
        }
        info.color = if (!err.isNullOrBlank()) UiTheme.DANGER else UiTheme.TEXT_MUTED

        val s = selected
        propsLabel.text = if (s == null) {
            "Эффект не выбран"
        } else {
            buildString {
                appendLine("identifier:")
                appendLine(s.identifier)
                appendLine("material: ${s.material}")
                appendLine("spawnRate: ${s.spawnRate}")
                appendLine("lifetime: ${s.particleLifetime}")
                appendLine("scale: ${"%.2f".format(AaaPreviewHelper.scale)}")
            }
        }
        propsLabel.color = UiTheme.TEXT_MUTED
    }

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        root.render(g, mouseX, mouseY, pt)
        val err = AaaPreviewHelper.loadError
        if (!err.isNullOrBlank() && vpW > 0) {
            g.text(
                Minecraft.getInstance().font,
                Component.literal(err),
                vpX + 12,
                vpY + vpH - 24,
                UiTheme.DANGER,
                false
            )
        }
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (button == 0 && mx in vpX until (vpX + vpW) && my in vpY until (vpY + vpH)) {
            dragging = true
            lastMx = mx
            lastMy = my
            return true
        }
        return root.mouseClicked(mx, my, button)
    }

    override fun mouseDragged(mx: Int, my: Int, button: Int, dx: Double, dy: Double): Boolean {
        if (dragging && button == 0) {
            AaaPreviewHelper.drag((mx - lastMx).toFloat(), (my - lastMy).toFloat())
            lastMx = mx
            lastMy = my
            return true
        }
        return root.mouseDragged(mx, my, button, dx, dy)
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int): Boolean {
        if (dragging) {
            dragging = false
            return true
        }
        return root.mouseReleased(mx, my, button)
    }

    override fun mouseScrolled(mx: Double, my: Double, sx: Double, sy: Double): Boolean {
        if (mx.toInt() in vpX until (vpX + vpW) && my.toInt() in vpY until (vpY + vpH)) {
            AaaPreviewHelper.zoom(sy.toFloat())
            status.text = "dist=${"%.1f".format(AaaPreviewHelper.distance)}"
            status.color = UiTheme.ACCENT
            return true
        }
        return root.mouseScrolled(mx, my, sx, sy)
    }

    override fun keyPressed(key: Int, scan: Int, modifiers: Int): Boolean =
        root.keyPressed(key, scan, modifiers)

    override fun charTyped(codePoint: Int, modifiers: Int): Boolean =
        root.charTyped(codePoint, modifiers)
}