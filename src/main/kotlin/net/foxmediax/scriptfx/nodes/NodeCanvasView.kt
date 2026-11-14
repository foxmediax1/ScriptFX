package net.foxmediax.scriptfx.nodes

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.Canvas
import icyllis.modernui.graphics.Paint
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.graphics.text.FontPaint
import icyllis.modernui.text.Typeface
import icyllis.modernui.view.KeyEvent
import icyllis.modernui.view.MotionEvent
import icyllis.modernui.view.View
import org.lwjgl.glfw.GLFW
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.min

/**
 * Холст нод: pan (СКМ/ПКМ), zoom (колесо), drag, связи, Delete ребра/ноды.
 */
class NodeCanvasView(ctx: Context) : View(ctx) {

    var graph: NodeGraph = NodeGraph()
        set(value) {
            field = value
            selectedId = null
            selectedEdgeIndex = null
            onSelectionChanged?.invoke(null)
            invalidate()
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

    private val paint = Paint()
    private val fontPaint = FontPaint().apply {
        setFont(Typeface.SANS_SERIF)
        setFontSize(13f)
        setLocale(Locale.getDefault())
    }

    private companion object {
        const val STYLE_FILL = 0
        const val STYLE_STROKE = 1
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        background = ColorDrawable(0xFF121218.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(scale, scale)

        // сетка
        paint.color = 0xFF1C1C24.toInt()
        paint.style = STYLE_STROKE
        paint.strokeWidth = 1f / scale
        val step = 40f
        val left = (-panX / scale) - 50f
        val top = (-panY / scale) - 50f
        val right = left + width / scale + 100f
        val bottom = top + height / scale + 100f
        var gx = (left / step).toInt() * step
        while (gx < right) {
            canvas.drawLine(gx, top, gx, bottom, paint)
            gx += step
        }
        var gy = (top / step).toInt() * step
        while (gy < bottom) {
            canvas.drawLine(left, gy, right, gy, paint)
            gy += step
        }

        // рёбра
        paint.style = STYLE_STROKE
        for ((i, e) in graph.edges.withIndex()) {
            val a = graph.node(e.from) ?: continue
            val b = graph.node(e.to) ?: continue
            val (ax, ay) = outPort(a)
            val (bx, by) = inPort(b)
            val selected = selectedEdgeIndex == i
            paint.color = if (selected) 0xFFFFCC66.toInt() else 0xFF8B7EC8.toInt()
            paint.strokeWidth = (if (selected) 3.5f else 2.5f) / scale
            drawBezier(canvas, ax, ay, bx, by)
        }

        // временная связь
        linkingFrom?.let { id ->
            val a = graph.node(id) ?: return@let
            val (ax, ay) = outPort(a)
            paint.color = 0xFFC8B4FF.toInt()
            paint.style = STYLE_STROKE
            paint.strokeWidth = 2.5f / scale
            drawBezier(canvas, ax, ay, linkX, linkY)
        }

        for (n in graph.nodes) {
            drawNode(canvas, n, n.id == selectedId)
        }

        canvas.restore()
    }

    private fun drawBezier(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float) {
        val dx = min(120f, hypot(x1 - x0, y1 - y0) * 0.5f)
        var prevX = x0
        var prevY = y0
        val steps = 16
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            val u = 1f - t
            val cx1 = x0 + dx
            val cx2 = x1 - dx
            val x = u * u * u * x0 + 3 * u * u * t * cx1 + 3 * u * t * t * cx2 + t * t * t * x1
            val y = u * u * u * y0 + 3 * u * u * t * y0 + 3 * u * t * t * y1 + t * t * t * y1
            canvas.drawLine(prevX, prevY, x, y, paint)
            prevX = x
            prevY = y
        }
    }

    private fun drawNode(canvas: Canvas, n: GraphNode, selected: Boolean) {
        val x = n.x
        val y = n.y
        val rx = 8f

        paint.style = STYLE_FILL
        paint.color = when (n.kind) {
            NodeKind.START -> 0xFF1B3A2A.toInt()
            NodeKind.END -> 0xFF3A1B1B.toInt()
            NodeKind.WAIT, NodeKind.DELAY -> 0xFF2A2A18.toInt()
            else -> 0xFF1E1E28.toInt()
        }
        canvas.drawRoundRect(x, y, x + nodeW, y + nodeH, rx, paint)

        paint.style = STYLE_STROKE
        paint.strokeWidth = (if (selected) 2.5f else 1.2f) / scale
        paint.color = if (selected) 0xFFC8B4FF.toInt() else 0xFF3A3A4A.toInt()
        canvas.drawRoundRect(x, y, x + nodeW, y + nodeH, rx, paint)

        paint.style = STYLE_FILL
        paint.color = 0xFFC8B4FF.toInt()
        canvas.drawRect(x + 6f, y + 6f, x + nodeW - 6f, y + 10f, paint)

        val label = n.title.ifBlank { n.kind.name }
        paint.color = 0xFFE8E8F0.toInt()
        fontPaint.setFontSize(13f / scale.coerceAtLeast(0.5f))
        canvas.drawTextRun(
            label, 0, label.length, 0, label.length,
            x + 12f, y + 34f, false, paint, fontPaint
        )

        val cmd = n.params["cmd"] ?: n.params["args"]
        if (!cmd.isNullOrBlank() && n.kind != NodeKind.START && n.kind != NodeKind.END) {
            val sub = cmd.take(22)
            paint.color = 0xFF8888A0.toInt()
            fontPaint.setFontSize(11f / scale.coerceAtLeast(0.5f))
            canvas.drawTextRun(
                sub, 0, sub.length, 0, sub.length,
                x + 12f, y + 48f, false, paint, fontPaint
            )
        }

        val (ox, oy) = outPort(n)
        val (ix, iy) = inPort(n)
        paint.color = 0xFF88CC88.toInt()
        canvas.drawCircle(ix, iy, portR, paint)
        paint.color = 0xFF88AADD.toInt()
        canvas.drawCircle(ox, oy, portR, paint)
    }

    private fun inPort(n: GraphNode): Pair<Float, Float> =
        n.x to (n.y + nodeH / 2f)

    private fun outPort(n: GraphNode): Pair<Float, Float> =
        (n.x + nodeW) to (n.y + nodeH / 2f)

    private fun worldX(sx: Float) = (sx - panX) / scale
    private fun worldY(sy: Float) = (sy - panY) / scale

    private fun hitNode(wx: Float, wy: Float): GraphNode? =
        graph.nodes.asReversed().firstOrNull { n ->
            wx >= n.x && wx <= n.x + nodeW && wy >= n.y && wy <= n.y + nodeH
        }

    private fun hitOutPort(wx: Float, wy: Float): GraphNode? =
        graph.nodes.firstOrNull { n ->
            val (ox, oy) = outPort(n)
            hypot(wx - ox, wy - oy) <= portR * 2.2f
        }

    private fun hitEdge(wx: Float, wy: Float): Int? {
        val threshold = 12f / scale
        graph.edges.forEachIndexed { i, e ->
            val a = graph.node(e.from) ?: return@forEachIndexed
            val b = graph.node(e.to) ?: return@forEachIndexed
            val (ax, ay) = outPort(a)
            val (bx, by) = inPort(b)
            val mx = (ax + bx) * 0.5f
            val my = (ay + by) * 0.5f
            if (hypot(wx - mx, wy - my) <= threshold * 3f) return i
            for (s in 1..7) {
                val t = s / 8f
                val x = ax + (bx - ax) * t
                val y = ay + (by - ay) * t
                if (hypot(wx - x, wy - y) <= threshold) return i
            }
        }
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wx = worldX(event.x)
        val wy = worldY(event.y)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus()

                if (event.buttonState and MotionEvent.BUTTON_SECONDARY != 0 ||
                    event.buttonState and MotionEvent.BUTTON_TERTIARY != 0
                ) {
                    panning = true
                    panLastX = event.x
                    panLastY = event.y
                    return true
                }

                hitOutPort(wx, wy)?.let { n ->
                    linkingFrom = n.id
                    linkX = wx
                    linkY = wy
                    selectedId = n.id
                    selectedEdgeIndex = null
                    onSelectionChanged?.invoke(n)
                    invalidate()
                    return true
                }

                hitEdge(wx, wy)?.let { idx ->
                    selectedEdgeIndex = idx
                    selectedId = null
                    onSelectionChanged?.invoke(null)
                    invalidate()
                    return true
                }

                hitNode(wx, wy)?.let { n ->
                    selectedId = n.id
                    selectedEdgeIndex = null
                    dragNodeId = n.id
                    dragOffX = wx - n.x
                    dragOffY = wy - n.y
                    onSelectionChanged?.invoke(n)
                    invalidate()
                    return true
                }

                selectedId = null
                selectedEdgeIndex = null
                onSelectionChanged?.invoke(null)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (panning) {
                    panX += event.x - panLastX
                    panY += event.y - panLastY
                    panLastX = event.x
                    panLastY = event.y
                    invalidate()
                    return true
                }
                linkingFrom?.let {
                    linkX = wx
                    linkY = wy
                    invalidate()
                    return true
                }
                dragNodeId?.let { id ->
                    graph.node(id)?.let { n ->
                        n.x = wx - dragOffX
                        n.y = wy - dragOffY
                        onGraphChanged?.invoke()
                        invalidate()
                    }
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (panning) {
                    panning = false
                    return true
                }
                linkingFrom?.let { fromId ->
                    val target = graph.nodes.firstOrNull { n ->
                        n.id != fromId &&
                                hypot(wx - inPort(n).first, wy - inPort(n).second) <= portR * 2.5f
                    }
                    if (target != null) {
                        graph.edges.removeAll { it.from == fromId && it.fromPort == "out" }
                        graph.edges.add(GraphEdge(fromId, "out", target.id, "in"))
                        onGraphChanged?.invoke()
                    }
                    linkingFrom = null
                    invalidate()
                    return true
                }
                dragNodeId = null
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val v = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (v != 0f) {
                val old = scale
                scale = (scale * (if (v > 0) 1.1f else 0.9f)).coerceIn(0.35f, 2.5f)
                val cx = event.x
                val cy = event.y
                panX = cx - (cx - panX) * (scale / old)
                panY = cy - (cy - panY) * (scale / old)
                invalidate()
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            selectedEdgeIndex?.let { idx ->
                if (idx in graph.edges.indices) {
                    graph.edges.removeAt(idx)
                    selectedEdgeIndex = null
                    onGraphChanged?.invoke()
                    invalidate()
                    return true
                }
            }
            val id = selectedId ?: return true
            val n = graph.node(id) ?: return true
            if (n.kind == NodeKind.START || n.kind == NodeKind.END) return true
            graph.edges.removeAll { it.from == id || it.to == id }
            graph.nodes.removeAll { it.id == id }
            selectedId = null
            onSelectionChanged?.invoke(null)
            onGraphChanged?.invoke()
            invalidate()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    fun addNode(node: GraphNode) {
        var id = node.id
        var i = 1
        while (graph.node(id) != null) {
            id = "${node.id}_$i"
            i++
        }
        node.id = id
        if (node.x == 0f && node.y == 0f && width > 0 && height > 0) {
            node.x = (-panX + width * 0.5f) / scale - nodeW * 0.5f
            node.y = (-panY + height * 0.5f) / scale - nodeH * 0.5f
        }
        graph.nodes.add(node)
        selectedId = node.id
        selectedEdgeIndex = null
        onSelectionChanged?.invoke(node)
        onGraphChanged?.invoke()
        invalidate()
    }
}