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

    private val list = UiScrollList(onSelect = { idx, _ -> selectEffect(idx) })
    private val idField = UiTextField(hint = "scriptfx:my_fx")

    private val btnRefresh = UiButton("Обновить") { reloadList() }
    private val btnPlay = UiButton("▶ Play") { playSelected() }
    private val btnStop = UiButton("■ Stop") { stopPreview() }
    private val btnScaleUp = UiButton("Scale+") {
        AaaPreviewHelper.setScale(AaaPreviewHelper.scale + 0.25f)
        status.text = "Scale: ${"%.2f".format(AaaPreviewHelper.scale)}"
        status.color = UiTheme.ACCENT
    }
    private val btnScaleDown = UiButton("Scale-") {
        AaaPreviewHelper.setScale(AaaPreviewHelper.scale - 0.25f)
        status.text = "Scale: ${"%.2f".format(AaaPreviewHelper.scale)}"
        status.color = UiTheme.ACCENT
    }
    private val btnResetCam = UiButton("Сброс") {
        AaaPreviewHelper.resetCamera()
        AaaPreviewHelper.setScale(1f)
        status.text = "Камера сброшена"
        status.color = UiTheme.TEXT_MUTED
    }

    private var vpX = 0
    private var vpY = 0
    private var vpW = 0
    private var vpH = 0
    private var dragging = false
    private var lastMx = 0
    private var lastMy = 0

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
        val leftW = (w * 0.32f).toInt().coerceIn(200, 340)

        val header = UiLabel("Эффекты проекта", UiTheme.ACCENT)
        header.setBounds(x + pad, y + pad, leftW, 16)
        root.add(header)

        list.setBounds(x + pad, y + 36, leftW - pad, h - 150)
        root.add(list)

        idField.setBounds(x + pad, y + h - 100, leftW - pad, 26)
        root.add(idField)

        btnPlay.setBounds(x + pad, y + h - 66, 70, 24)
        btnStop.setBounds(x + pad + 76, y + h - 66, 70, 24)
        btnRefresh.setBounds(x + pad + 152, y + h - 66, 80, 24)
        root.add(btnPlay)
        root.add(btnStop)
        root.add(btnRefresh)

        // правая зона «превью / статус AAA»
        vpX = x + leftW + pad
        vpY = y + 36
        vpW = w - leftW - pad * 2
        vpH = h - 90

        val prevTitle = UiLabel("AAA превью (в мире перед игроком)", UiTheme.ACCENT)
        prevTitle.setBounds(vpX, y + pad, vpW, 16)
        root.add(prevTitle)

        val frame = UiPanel(0xFF0A0A10.toInt())
        frame.setBounds(vpX, vpY, vpW, vpH)
        root.add(frame)

        info.setBounds(vpX + 12, vpY + 12, vpW - 24, 60)
        root.add(info)

        btnScaleDown.setBounds(vpX, y + h - 46, 70, 24)
        btnScaleUp.setBounds(vpX + 76, y + h - 46, 70, 24)
        btnResetCam.setBounds(vpX + 152, y + h - 46, 70, 24)
        root.add(btnScaleDown)
        root.add(btnScaleUp)
        root.add(btnResetCam)

        status.setBounds(vpX + 230, y + h - 42, (w - leftW - 250).coerceAtLeast(80), 16)
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

        status.text = "Проект: $project · эффектов: ${effects.size}"
        status.color = UiTheme.TEXT_MUTED
        refreshInfo()
        updateBounds()
    }

    private fun selectEffect(idx: Int) {
        if (idx !in effects.indices) return
        selected = effects[idx]
        idField.value = selected!!.identifier
        refreshInfo()
        status.text = "Выбран: ${selected!!.identifier}"
        status.color = UiTheme.SUCCESS
    }

    private fun playSelected() {
        val raw = idField.value.trim().ifBlank { selected?.identifier.orEmpty() }
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
            else append("ЛКМ по области — yaw/pitch (если поддерживается)")
        }
        info.color = if (!err.isNullOrBlank()) UiTheme.DANGER else UiTheme.TEXT_MUTED
    }

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        root.render(g, mouseX, mouseY, pt)
        // World-space AAA рисует сам мод частиц; здесь только UI + статус
        refreshInfoSoft(g)
    }

    private fun refreshInfoSoft(g: GuiGraphicsExtractor) {
        // лёгкое обновление текста без пересоздания layout
        val err = AaaPreviewHelper.loadError
        if (!err.isNullOrBlank()) {
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