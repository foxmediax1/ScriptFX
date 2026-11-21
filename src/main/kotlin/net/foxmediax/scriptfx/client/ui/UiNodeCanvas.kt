package net.foxmediax.scriptfx.client.ui

import net.foxmediax.scriptfx.nodes.GraphEdge
import net.foxmediax.scriptfx.nodes.GraphNode
import net.foxmediax.scriptfx.nodes.NodeGraph
import net.foxmediax.scriptfx.nodes.NodeKind
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.hypot

class UiNodeCanvas : UiWidget() {

    var graph: NodeGraph = NodeGraph()
        set(value) {
            field = value
            selectedId = null
            selectedEdgeIndex = null
            onSelectionChanged?.invoke(null)
        }

    var onGraphChanged: (() -> Unit)? = null
    var onSelectionChanged: ((GraphNode?) -> Unit)? = null

    private var panX = 0f
    private var panY = 0f
    private var scale = 1f

    private var selectedId: String? = null
    private var selectedEdgeIndex: Int? = null
    private var dragNodeId: String? = null
    private var dragOffX = 0f
    private var dragOffY = 0f

    private var panning = false
    private var panLastX = 0f
    private var panLastY = 0f

    private var linkingFrom: String? = null
    private var linkX = 0f
    private var linkY = 0f

    private val nodeW = 160f
    private val nodeH = 56f
    private val portR = 7f

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        if (!visible) return
        g.fill(x, y, right, bottom, 0xFF121218.toInt())

        val step = (40f * scale).toInt().coerceAtLeast(8)
        var gx = x + ((panX * scale).toInt() % step + step) % step
        while (gx < right) {
            g.fill(gx, y, gx + 1, bottom, 0xFF1C1C24.toInt())
            gx += step
        }
        var gy = y + ((panY * scale).toInt() % step + step) % step
        while (gy < bottom) {
            g.fill(x, gy, right, gy + 1, 0xFF1C1C24.toInt())
            gy += step
        }

        fun sx(wx: Float) = x + (wx * scale + panX).toInt()
        fun sy(wy: Float) = y + (wy * scale + panY).toInt()

        for ((i, e) in graph.edges.withIndex()) {
            val a = graph.node(e.from) ?: continue
            val b = graph.node(e.to) ?: continue
            val x0 = sx(a.x + nodeW)
            val y0 = sy(a.y + nodeH / 2f)
            val x1 = sx(b.x)
            val y1 = sy(b.y + nodeH / 2f)
            val col = if (selectedEdgeIndex == i) 0xFFFFCC66.toInt() else 0xFF8B7EC8.toInt()
            drawLine(g, x0, y0, x1, y1, col)
        }

        linkingFrom?.let { id ->
            val a = graph.node(id) ?: return@let
            drawLine(
                g,
                sx(a.x + nodeW), sy(a.y + nodeH / 2f),
                sx(linkX), sy(linkY),
                0xFFC8B4FF.toInt()
            )
        }

        for (n in graph.nodes) {
            drawNode(g, n, n.id == selectedId, ::sx, ::sy)
        }
    }

    private fun drawLine(g: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        val steps = hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toInt().coerceIn(1, 80)
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val px = (x0 + (x1 - x0) * t).toInt()
            val py = (y0 + (y1 - y0) * t).toInt()
            g.fill(px, py, px + 2, py + 2, color)
        }
    }

    private fun drawNode(
        g: GuiGraphicsExtractor,
        n: GraphNode,
        selected: Boolean,
        sx: (Float) -> Int,
        sy: (Float) -> Int
    ) {
        val x0 = sx(n.x)
        val y0 = sy(n.y)
        val x1 = sx(n.x + nodeW)
        val y1 = sy(n.y + nodeH)

        val fill = when (n.kind) {
            NodeKind.START -> 0xFF1B3A2A.toInt()
            NodeKind.END -> 0xFF3A1B1B.toInt()
            NodeKind.WAIT -> 0xFF2A2A18.toInt()
            else -> 0xFF1E1E28.toInt()
        }
        g.fill(x0, y0, x1, y1, fill)

        val border = if (selected) UiTheme.ACCENT else UiTheme.BORDER
        g.fill(x0, y0, x1, y0 + 1, border)
        g.fill(x0, y1 - 1, x1, y1, border)
        g.fill(x0, y0, x0 + 1, y1, border)
        g.fill(x1 - 1, y0, x1, y1, border)
        g.fill(x0 + 4, y0 + 4, x1 - 4, y0 + 7, UiTheme.ACCENT)

        val label = n.title.ifBlank { n.kind.name }
        g.text(font, Component.literal(label), x0 + 8, y0 + 16, UiTheme.TEXT, false)

        val cmd = n.params["cmd"] ?: n.params["args"]
        if (!cmd.isNullOrBlank() && n.kind != NodeKind.START && n.kind != NodeKind.END) {
            g.text(font, Component.literal(cmd.take(22)), x0 + 8, y0 + 32, UiTheme.TEXT_MUTED, false)
        }

        val iy = sy(n.y + nodeH / 2f)
        val pr = (portR * scale).toInt().coerceAtLeast(3)
        val ix = sx(n.x)
        val ox = sx(n.x + nodeW)
        g.fill(ix - pr, iy - pr, ix + pr, iy + pr, 0xFF88CC88.toInt())
        g.fill(ox - pr, iy - pr, ox + pr, iy + pr, 0xFF88AADD.toInt())
    }

    private fun worldX(mx: Int) = (mx - x - panX) / scale
    private fun worldY(my: Int) = (my - y - panY) / scale

    private fun hitNode(wx: Float, wy: Float): GraphNode? =
        graph.nodes.asReversed().firstOrNull { n ->
            wx >= n.x && wx <= n.x + nodeW && wy >= n.y && wy <= n.y + nodeH
        }

    private fun hitOutPort(wx: Float, wy: Float): GraphNode? =
        graph.nodes.firstOrNull { n ->
            hypot(wx - (n.x + nodeW), wy - (n.y + nodeH / 2f)) <= portR * 2.2f
        }

    private fun hitEdge(wx: Float, wy: Float): Int? {
        val thr = 14f / scale
        graph.edges.forEachIndexed { i, e ->
            val a = graph.node(e.from) ?: return@forEachIndexed
            val b = graph.node(e.to) ?: return@forEachIndexed
            val ax = a.x + nodeW
            val ay = a.y + nodeH / 2f
            val bx = b.x
            val by = b.y + nodeH / 2f
            for (s in 0..8) {
                val t = s / 8f
                val px = ax + (bx - ax) * t
                val py = ay + (by - ay) * t
                if (hypot(wx - px, wy - py) <= thr) return i
            }
        }
        return null
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (!visible || !contains(mx, my)) return false
        val wx = worldX(mx)
        val wy = worldY(my)

        if (button == 1 || button == 2) {
            panning = true
            panLastX = mx.toFloat()
            panLastY = my.toFloat()
            return true
        }
        if (button != 0) return true

        hitOutPort(wx, wy)?.let { n ->
            linkingFrom = n.id
            linkX = wx
            linkY = wy
            selectedId = n.id
            selectedEdgeIndex = null
            onSelectionChanged?.invoke(n)
            return true
        }

        hitEdge(wx, wy)?.let { idx ->
            selectedEdgeIndex = idx
            selectedId = null
            onSelectionChanged?.invoke(null)
            return true
        }

        hitNode(wx, wy)?.let { n ->
            selectedId = n.id
            selectedEdgeIndex = null
            dragNodeId = n.id
            dragOffX = wx - n.x
            dragOffY = wy - n.y
            onSelectionChanged?.invoke(n)
            return true
        }

        selectedId = null
        selectedEdgeIndex = null
        onSelectionChanged?.invoke(null)
        return true
    }

    override fun mouseDragged(mx: Int, my: Int, button: Int, dx: Double, dy: Double): Boolean {
        if (panning) {
            panX += mx - panLastX
            panY += my - panLastY
            panLastX = mx.toFloat()
            panLastY = my.toFloat()
            return true
        }
        val wx = worldX(mx)
        val wy = worldY(my)
        linkingFrom?.let {
            linkX = wx
            linkY = wy
            return true
        }
        dragNodeId?.let { id ->
            graph.node(id)?.let { n ->
                n.x = wx - dragOffX
                n.y = wy - dragOffY
                onGraphChanged?.invoke()
            }
            return true
        }
        return false
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int): Boolean {
        if (panning) {
            panning = false
            return true
        }
        val wx = worldX(mx)
        val wy = worldY(my)
        linkingFrom?.let { fromId ->
            val target = graph.nodes.firstOrNull { n ->
                n.id != fromId &&
                        hypot(wx - n.x, wy - (n.y + nodeH / 2f)) <= portR * 2.5f
            }
            if (target != null) {
                graph.edges.removeAll { it.from == fromId && it.fromPort == "out" }
                graph.edges.add(GraphEdge(fromId, "out", target.id, "in"))
                onGraphChanged?.invoke()
            }
            linkingFrom = null
            return true
        }
        dragNodeId = null
        return false
    }

    override fun mouseScrolled(mx: Double, my: Double, sx: Double, sy: Double): Boolean {
        if (!visible || !contains(mx.toInt(), my.toInt())) return false
        val old = scale
        scale = (scale * (if (sy > 0) 1.1f else 0.9f)).coerceIn(0.35f, 2.5f)
        val cx = mx.toFloat() - x
        val cy = my.toFloat() - y
        panX = cx - (cx - panX) * (scale / old)
        panY = cy - (cy - panY) * (scale / old)
        return true
    }

    override fun keyPressed(key: Int, scan: Int, modifiers: Int): Boolean {
        if (key != GLFW.GLFW_KEY_DELETE && key != GLFW.GLFW_KEY_BACKSPACE) return false
        selectedEdgeIndex?.let { idx ->
            if (idx in graph.edges.indices) {
                graph.edges.removeAt(idx)
                selectedEdgeIndex = null
                onGraphChanged?.invoke()
                return true
            }
        }
        val id = selectedId ?: return false
        val n = graph.node(id) ?: return false
        if (n.kind == NodeKind.START || n.kind == NodeKind.END) return true
        graph.edges.removeAll { it.from == id || it.to == id }
        graph.nodes.removeAll { it.id == id }
        selectedId = null
        onSelectionChanged?.invoke(null)
        onGraphChanged?.invoke()
        return true
    }

    fun addNode(node: GraphNode) {
        var id = node.id
        var i = 1
        while (graph.node(id) != null) {
            id = "${node.id}_$i"
            i++
        }
        node.id = id
        if (node.x == 0f && node.y == 0f && w > 0 && h > 0) {
            node.x = (w * 0.5f - panX) / scale - nodeW * 0.5f
            node.y = (h * 0.5f - panY) / scale - nodeH * 0.5f
        }
        graph.nodes.add(node)
        selectedId = node.id
        onSelectionChanged?.invoke(node)
        onGraphChanged?.invoke()
    }
}