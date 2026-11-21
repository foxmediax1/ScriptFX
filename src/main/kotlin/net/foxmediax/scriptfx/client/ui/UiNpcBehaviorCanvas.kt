package net.foxmediax.scriptfx.client.ui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import kotlin.math.hypot

enum class NpcBehKind { START, CHECK, DIALOG, END, ACTION }

data class NpcBehNode(
    var id: String,
    var kind: NpcBehKind,
    var title: String,
    var param: String = "",
    var x: Float = 40f,
    var y: Float = 40f
)

data class NpcBehEdge(var from: String, var to: String, var label: String = "")

data class NpcBehGraph(
    val nodes: MutableList<NpcBehNode> = mutableListOf(),
    val edges: MutableList<NpcBehEdge> = mutableListOf()
) {
    companion object {
        fun defaultTemplate(): NpcBehGraph {
            val g = NpcBehGraph()
            g.nodes += NpcBehNode("start", NpcBehKind.START, "Старт", x = 40f, y = 80f)
            g.nodes += NpcBehNode("check", NpcBehKind.CHECK, "Проверка", "quest_flag", x = 200f, y = 80f)
            g.nodes += NpcBehNode("dialog", NpcBehKind.DIALOG, "Диалог", "hello", x = 380f, y = 40f)
            g.nodes += NpcBehNode("end", NpcBehKind.END, "Конец", x = 380f, y = 140f)
            g.edges += NpcBehEdge("start", "check")
            g.edges += NpcBehEdge("check", "dialog", "Да")
            g.edges += NpcBehEdge("check", "end", "Нет")
            return g
        }
    }
}

class UiNpcBehaviorCanvas : UiWidget() {

    var graph: NpcBehGraph = NpcBehGraph.defaultTemplate()
        set(value) {
            field = value
            selectedId = null
            onSelect?.invoke(null)
        }

    var onSelect: ((NpcBehNode?) -> Unit)? = null
    var onChanged: (() -> Unit)? = null

    private var selectedId: String? = null
    private var dragId: String? = null
    private var dragOffX = 0f
    private var dragOffY = 0f

    private val nodeW = 120f
    private val nodeH = 40f

    fun selected(): NpcBehNode? = graph.nodes.find { it.id == selectedId }

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        if (!visible) return
        g.fill(x, y, right, bottom, UiTheme.BG)
        val step = 20
        var gx = x
        while (gx < right) {
            g.fill(gx, y, gx + 1, bottom, 0xFF1A1424.toInt()); gx += step
        }
        var gy = y
        while (gy < bottom) {
            g.fill(x, gy, right, gy + 1, 0xFF1A1424.toInt()); gy += step
        }

        for (e in graph.edges) {
            val a = graph.nodes.find { it.id == e.from } ?: continue
            val b = graph.nodes.find { it.id == e.to } ?: continue
            val x0 = x + (a.x + nodeW).toInt()
            val y0 = y + (a.y + nodeH / 2).toInt()
            val x1 = x + b.x.toInt()
            val y1 = y + (b.y + nodeH / 2).toInt()
            drawLine(g, x0, y0, x1, y1, UiTheme.ACCENT_DIM)
            if (e.label.isNotBlank()) {
                val mx = (x0 + x1) / 2
                val my = (y0 + y1) / 2
                g.text(font, Component.literal(e.label), mx, my - 6, UiTheme.ACCENT_SOFT, false)
            }
        }

        for (n in graph.nodes) {
            val nx = x + n.x.toInt()
            val ny = y + n.y.toInt()
            val sel = n.id == selectedId
            val bg = when (n.kind) {
                NpcBehKind.START -> 0xFF1A3A28.toInt()
                NpcBehKind.END -> 0xFF3A1A1A.toInt()
                NpcBehKind.CHECK -> 0xFF2A2540.toInt()
                NpcBehKind.DIALOG -> 0xFF1A2A40.toInt()
                NpcBehKind.ACTION -> UiTheme.BG_CARD
            }
            g.fill(nx, ny, nx + nodeW.toInt(), ny + nodeH.toInt(), if (sel) UiTheme.BG_ACTIVE else bg)
            val border = if (sel) UiTheme.ACCENT else UiTheme.BORDER
            g.fill(nx, ny, nx + nodeW.toInt(), ny + 1, border)
            g.fill(nx, ny + nodeH.toInt() - 1, nx + nodeW.toInt(), ny + nodeH.toInt(), border)
            g.fill(nx, ny, nx + 1, ny + nodeH.toInt(), border)
            g.fill(nx + nodeW.toInt() - 1, ny, nx + nodeW.toInt(), ny + nodeH.toInt(), border)
            g.text(font, Component.literal(n.title), nx + 8, ny + 8, UiTheme.TEXT, false)
            if (n.param.isNotBlank()) {
                g.text(font, Component.literal(n.param.take(16)), nx + 8, ny + 22, UiTheme.TEXT_MUTED, false)
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
        val lx = (mx - x).toFloat()
        val ly = (my - y).toFloat()
        val hit = graph.nodes.asReversed().firstOrNull {
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
        val n = graph.nodes.find { it.id == id } ?: return false
        n.x = ((mx - x) - dragOffX).coerceAtLeast(0f)
        n.y = ((my - y) - dragOffY).coerceAtLeast(0f)
        onChanged?.invoke()
        return true
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int): Boolean {
        dragId = null
        return false
    }

    fun resetTemplate() {
        graph = NpcBehGraph.defaultTemplate()
        onChanged?.invoke()
    }
}

object NpcBehGraphCodec {
    fun toJson(g: NpcBehGraph): String {
        // минимально: id|kind|title|param|x|y на строку ноды; from>to:label на ребро
        val nodes = g.nodes.joinToString(";") {
            "${it.id},${it.kind},${it.title.replace(',',' ')},${it.param.replace(',',' ')},${it.x},${it.y}"
        }
        val edges = g.edges.joinToString(";") { "${it.from}>${it.to}:${it.label}" }
        return "N:$nodes|E:$edges"
    }

    fun fromJson(raw: String): NpcBehGraph {
        if (raw.isBlank() || !raw.startsWith("N:")) return NpcBehGraph.defaultTemplate()
        val g = NpcBehGraph()
        val parts = raw.split("|", limit = 2)
        val nPart = parts[0].removePrefix("N:")
        val ePart = parts.getOrNull(1)?.removePrefix("E:").orEmpty()
        nPart.split(";").filter { it.isNotBlank() }.forEach { row ->
            val p = row.split(",")
            if (p.size >= 6) {
                g.nodes += NpcBehNode(
                    p[0],
                    runCatching { NpcBehKind.valueOf(p[1]) }.getOrDefault(NpcBehKind.ACTION),
                    p[2], p[3],
                    p[4].toFloatOrNull() ?: 0f,
                    p[5].toFloatOrNull() ?: 0f
                )
            }
        }
        ePart.split(";").filter { it.isNotBlank() }.forEach { row ->
            val (ft, lab) = row.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            val (f, t) = ft.split(">", limit = 2)
            g.edges += NpcBehEdge(f, t, lab)
        }
        return if (g.nodes.isEmpty()) NpcBehGraph.defaultTemplate() else g
    }
}