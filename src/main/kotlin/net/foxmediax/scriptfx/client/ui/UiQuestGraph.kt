package net.foxmediax.scriptfx.client.ui

import net.foxmediax.scriptfx.quest.QuestDefinition
import net.foxmediax.scriptfx.quest.QuestEdge
import net.foxmediax.scriptfx.quest.QuestObjective
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import kotlin.math.hypot

/**
 * Визуальный граф целей квеста (макет «Квесты»).
 * Drag нод, клик = выбор. Позже: Arc3D для скруглений/кривых.
 */
class UiQuestGraph : UiWidget() {

    var definition: QuestDefinition? = null
        set(value) {
            field = value
            selectedId = null
        }

    var onSelect: ((QuestObjective?) -> Unit)? = null
    var onChanged: (() -> Unit)? = null

    private var selectedId: String? = null
    private var dragId: String? = null
    private var dragOffX = 0f
    private var dragOffY = 0f

    private val nodeW = 150f
    private val nodeH = 48f

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        if (!visible) return
        g.fill(x, y, right, bottom, UiTheme.BG)

        val step = 24
        var gx = x
        while (gx < right) {
            g.fill(gx, y, gx + 1, bottom, 0xFF1A1424.toInt())
            gx += step
        }
        var gy = y
        while (gy < bottom) {
            g.fill(x, gy, right, gy + 1, 0xFF1A1424.toInt())
            gy += step
        }
        g.fill(x, y, right, y + 1, UiTheme.BORDER)
        g.fill(x, bottom - 1, right, bottom, UiTheme.BORDER)
        g.fill(x, y, x + 1, bottom, UiTheme.BORDER)
        g.fill(right - 1, y, right, bottom, UiTheme.BORDER)

        val def = definition
        if (def == null) {
            g.text(font, Component.literal("Выбери квест слева"), x + 12, y + 12, UiTheme.TEXT_MUTED, false)
            return
        }
        if (def.objectives.isEmpty()) {
            g.text(font, Component.literal("Нет целей — нажми «+ Цель»"), x + 12, y + 12, UiTheme.TEXT_MUTED, false)
            return
        }

        for (e in def.edges) {
            val a = def.objectives.find { it.id == e.from } ?: continue
            val b = def.objectives.find { it.id == e.to } ?: continue
            val x0 = x + (a.x + nodeW).toInt()
            val y0 = y + (a.y + nodeH / 2f).toInt()
            val x1 = x + b.x.toInt()
            val y1 = y + (b.y + nodeH / 2f).toInt()
            drawLine(g, x0, y0, x1, y1, UiTheme.ACCENT_DIM)
        }

        for (o in def.objectives) {
            val nx = x + o.x.toInt()
            val ny = y + o.y.toInt()
            val sel = o.id == selectedId
            g.fill(nx, ny, nx + nodeW.toInt(), ny + nodeH.toInt(), if (sel) UiTheme.BG_ACTIVE else UiTheme.BG_CARD)
            val border = if (sel) UiTheme.ACCENT else UiTheme.BORDER
            g.fill(nx, ny, nx + nodeW.toInt(), ny + 1, border)
            g.fill(nx, ny + nodeH.toInt() - 1, nx + nodeW.toInt(), ny + nodeH.toInt(), border)
            g.fill(nx, ny, nx + 1, ny + nodeH.toInt(), border)
            g.fill(nx + nodeW.toInt() - 1, ny, nx + nodeW.toInt(), ny + nodeH.toInt(), border)
            if (sel) g.fill(nx, ny, nx + 3, ny + nodeH.toInt(), UiTheme.ACCENT)

            g.text(font, Component.literal(o.id.ifBlank { "?" }), nx + 10, ny + 8, UiTheme.TEXT, false)
            val sub = o.text.take(22)
            if (sub.isNotBlank()) {
                g.text(font, Component.literal(sub), nx + 10, ny + 26, UiTheme.TEXT_MUTED, false)
            }
        }
    }

    private fun drawLine(g: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        val dx = x1 - x0
        val dy = y1 - y0
        val steps = hypot(dx.toDouble(), dy.toDouble()).toInt().coerceAtLeast(1)
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val px = (x0 + dx * t).toInt()
            val py = (y0 + dy * t).toInt()
            g.fill(px, py, px + 2, py + 2, color)
        }
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (!visible || !contains(mx, my) || button != 0) return false
        val def = definition ?: return true
        val lx = (mx - x).toFloat()
        val ly = (my - y).toFloat()
        val hit = def.objectives.asReversed().firstOrNull {
            lx >= it.x && lx <= it.x + nodeW && ly >= it.y && ly <= it.y + nodeH
        }
        selectedId = hit?.id
        onSelect?.invoke(hit)
        if (hit != null) {
            dragId = hit.id
            dragOffX = lx - hit.x
            dragOffY = ly - hit.y
        } else dragId = null
        return true
    }

    override fun mouseDragged(mx: Int, my: Int, button: Int, dx: Double, dy: Double): Boolean {
        val id = dragId ?: return false
        val def = definition ?: return false
        val o = def.objectives.find { it.id == id } ?: return false
        o.x = ((mx - x) - dragOffX).coerceAtLeast(0f)
        o.y = ((my - y) - dragOffY).coerceAtLeast(0f)
        onChanged?.invoke()
        return true
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int): Boolean {
        dragId = null
        return false
    }

    fun layoutChain() {
        val def = definition ?: return
        def.objectives.forEachIndexed { i, o ->
            o.x = 24f + i * (nodeW + 36f)
            o.y = 40f
        }
        def.edges.clear()
        for (i in 0 until def.objectives.lastIndex) {
            def.edges.add(QuestEdge(def.objectives[i].id, def.objectives[i + 1].id))
        }
        onChanged?.invoke()
    }
}