package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.Canvas
import icyllis.modernui.graphics.Paint
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.graphics.text.FontPaint
import icyllis.modernui.text.Typeface
import icyllis.modernui.view.MotionEvent
import icyllis.modernui.view.View
import net.foxmediax.scriptfx.quest.QuestDefinition
import net.foxmediax.scriptfx.quest.QuestEdge
import net.foxmediax.scriptfx.quest.QuestObjective
import java.util.Locale
import kotlin.math.hypot

/**
 * Простой граф целей квеста: drag нод, клик = выбор.
 * Рёбра берутся из QuestDefinition.edges.
 */
class QuestGraphView(ctx: Context) : View(ctx) {

    var definition: QuestDefinition? = null
        set(value) {
            field = value
            selectedId = null
            invalidate()
        }

    var onSelect: ((QuestObjective?) -> Unit)? = null
    var onChanged: (() -> Unit)? = null

    private var selectedId: String? = null
    private var dragId: String? = null
    private var dragOffX = 0f
    private var dragOffY = 0f

    private val nodeW = 140f
    private val nodeH = 48f

    private val paint = Paint()
    private val font = FontPaint().apply {
        setFont(Typeface.SANS_SERIF)
        setFontSize(12f)
        setLocale(Locale.getDefault())
    }

    init {
        background = ColorDrawable(0xFF121218.toInt())
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val def = definition ?: return

        // edges
        paint.style = 1 // STROKE
        paint.color = 0xFF6A8CFF.toInt()
        paint.strokeWidth = 2f
        for (e in def.edges) {
            val a = def.objectives.find { it.id == e.from } ?: continue
            val b = def.objectives.find { it.id == e.to } ?: continue
            val ax = a.x + nodeW
            val ay = a.y + nodeH * 0.5f
            val bx = b.x
            val by = b.y + nodeH * 0.5f
            canvas.drawLine(ax, ay, bx, by, paint)
        }

        // nodes
        for (o in def.objectives) {
            val sel = o.id == selectedId
            val rx = 8f
            paint.style = 0 // FILL
            paint.color = if (sel) 0xFF2A2540.toInt() else 0xFF1E1E2A.toInt()
            canvas.drawRoundRect(o.x, o.y, o.x + nodeW, o.y + nodeH, rx, paint)

            paint.style = 1 // STROKE
            paint.color = if (sel) 0xFFC8B4FF.toInt() else 0xFF3A3A4A.toInt()
            paint.strokeWidth = if (sel) 2.5f else 1.2f
            canvas.drawRoundRect(o.x, o.y, o.x + nodeW, o.y + nodeH, rx, paint)

            paint.style = 0
            paint.color = 0xFFE8E8F0.toInt()
            font.setFontSize(12f)
            val title = o.id.ifBlank { "?" }
            canvas.drawTextRun(
                title, 0, title.length, 0, title.length,
                o.x + 10f, o.y + 20f, false, paint, font
            )
            val sub = o.text.take(18)
            if (sub.isNotBlank()) {
                paint.color = 0xFF8888A0.toInt()
                font.setFontSize(10f)
                canvas.drawTextRun(
                    sub, 0, sub.length, 0, sub.length,
                    o.x + 10f, o.y + 38f, false, paint, font
                )
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val def = definition ?: return super.onTouchEvent(event)
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = def.objectives.asReversed().firstOrNull {
                    x in it.x..(it.x + nodeW) && y in it.y..(it.y + nodeH)
                }
                selectedId = hit?.id
                onSelect?.invoke(hit)
                if (hit != null) {
                    dragId = hit.id
                    dragOffX = x - hit.x
                    dragOffY = y - hit.y
                } else {
                    dragId = null
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val id = dragId ?: return true
                val o = def.objectives.find { it.id == id } ?: return true
                o.x = (x - dragOffX).coerceAtLeast(0f)
                o.y = (y - dragOffY).coerceAtLeast(0f)
                onChanged?.invoke()
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragId = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun layoutChain() {
        val def = definition ?: return
        def.objectives.forEachIndexed { i, o ->
            o.x = 24f + i * (nodeW + 40f)
            o.y = 40f
        }
        if (def.edges.isEmpty() && def.objectives.size >= 2) {
            def.edges.clear()
            for (i in 0 until def.objectives.lastIndex) {
                def.edges.add(
                    QuestEdge(def.objectives[i].id, def.objectives[i + 1].id)
                )
            }
        }
        invalidate()
        onChanged?.invoke()
    }
}