package net.foxmediax.scriptfx.client.ui

import net.foxmediax.scriptfx.hud.HudDefinition
import net.foxmediax.scriptfx.hud.HudElement
import net.foxmediax.scriptfx.hud.HudElementType
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/**
 * Редактор элементов HUD внутри frame width×height.
 * Drag выбранного элемента, клик = select.
 */
class UiHudCanvas : UiWidget() {

    var definition: HudDefinition? = null
        set(value) {
            field = value
            selectedId = null
            onSelect?.invoke(null)
        }

    var onSelect: ((HudElement?) -> Unit)? = null
    var onChanged: (() -> Unit)? = null

    private var selectedId: String? = null
    private var dragId: String? = null
    private var dragOffX = 0f
    private var dragOffY = 0f

    /** масштаб: screen-px / hud-unit */
    private fun scale(): Float {
        val def = definition ?: return 1f
        val sx = if (def.width > 1f) w / def.width else 1f
        val sy = if (def.height > 1f) h / def.height else 1f
        return minOf(sx, sy)
    }

    private fun originX(): Float {
        val def = definition ?: return x.toFloat()
        return x + (w - def.width * scale()) / 2f
    }

    private fun originY(): Float {
        val def = definition ?: return y.toFloat()
        return y + (h - def.height * scale()) / 2f
    }

    fun selected(): HudElement? =
        definition?.elements?.find { it.id == selectedId }

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        if (!visible) return
        g.fill(x, y, right, bottom, UiTheme.BG)
        g.fill(x, y, right, y + 1, UiTheme.BORDER)
        g.fill(x, bottom - 1, right, bottom, UiTheme.BORDER)

        val def = definition
        if (def == null) {
            g.text(font, Component.literal("Выбери HUD слева"), x + 12, y + 12, UiTheme.TEXT_MUTED, false)
            return
        }

        val s = scale()
        val ox = originX().toInt()
        val oy = originY().toInt()
        val fw = (def.width * s).toInt()
        val fh = (def.height * s).toInt()

        // frame
        g.fill(ox, oy, ox + fw, oy + fh, def.background)
        g.fill(ox, oy, ox + fw, oy + 1, UiTheme.ACCENT_DIM)
        g.fill(ox, oy + fh - 1, ox + fw, oy + fh, UiTheme.ACCENT_DIM)
        g.fill(ox, oy, ox + 1, oy + fh, UiTheme.ACCENT_DIM)
        g.fill(ox + fw - 1, oy, ox + fw, oy + fh, UiTheme.ACCENT_DIM)

        for (el in def.elements) {
            if (!el.visible) continue
            val ex = ox + (el.x * s).toInt()
            val ey = oy + (el.y * s).toInt()
            val ew = (el.w * s).toInt().coerceAtLeast(4)
            val eh = (el.h * s).toInt().coerceAtLeast(4)
            val sel = el.id == selectedId

            when (el.type) {
                HudElementType.RECT, HudElementType.IMAGE -> {
                    g.fill(ex, ey, ex + ew, ey + eh, el.color)
                }
                HudElementType.TEXT -> {
                    g.fill(ex, ey, ex + ew, ey + eh, if (sel) 0x332A1540 else 0x22000000)
                    val t = el.text.ifBlank { el.id }
                    g.text(font, Component.literal(t.take(40)), ex + 2, ey + 2, el.color, false)
                }
            }

            if (sel) {
                g.fill(ex, ey, ex + ew, ey + 1, UiTheme.ACCENT)
                g.fill(ex, ey + eh - 1, ex + ew, ey + eh, UiTheme.ACCENT)
                g.fill(ex, ey, ex + 1, ey + eh, UiTheme.ACCENT)
                g.fill(ex + ew - 1, ey, ex + ew, ey + eh, UiTheme.ACCENT)
            }
        }

        g.text(
            font,
            Component.literal("${def.width.toInt()}×${def.height.toInt()}  ·  ${def.elements.size} el"),
            x + 8,
            bottom - 14,
            UiTheme.TEXT_DIM,
            false
        )
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (!visible || !contains(mx, my) || button != 0) return false
        val def = definition ?: return true
        val s = scale()
        val ox = originX()
        val oy = originY()
        val lx = (mx - ox) / s
        val ly = (my - oy) / s

        val hit = def.elements.asReversed().firstOrNull { el ->
            el.visible &&
                    lx >= el.x && lx <= el.x + el.w &&
                    ly >= el.y && ly <= el.y + el.h
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
        val el = def.elements.find { it.id == id } ?: return false
        val s = scale()
        val ox = originX()
        val oy = originY()
        val lx = (mx - ox) / s
        val ly = (my - oy) / s
        el.x = (lx - dragOffX).coerceIn(0f, (def.width - el.w).coerceAtLeast(0f))
        el.y = (ly - dragOffY).coerceIn(0f, (def.height - el.h).coerceAtLeast(0f))
        onChanged?.invoke()
        return true
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int): Boolean {
        dragId = null
        return false
    }

    fun addElement(type: HudElementType) {
        val def = definition ?: return
        val n = def.elements.size + 1
        val el = when (type) {
            HudElementType.RECT -> HudElement(
                id = "rect_$n", type = type,
                x = 20f, y = 20f, w = 80f, h = 40f,
                color = 0xAA3B2460.toInt()
            )
            HudElementType.TEXT -> HudElement(
                id = "text_$n", type = type,
                x = 20f, y = 20f, w = 120f, h = 24f,
                text = "Текст $n", color = 0xFFE0D4FF.toInt()
            )
            HudElementType.IMAGE -> HudElement(
                id = "img_$n", type = type,
                x = 20f, y = 20f, w = 48f, h = 48f,
                color = 0x88FFFFFF.toInt()
            )
        }
        def.elements.add(el)
        selectedId = el.id
        onSelect?.invoke(el)
        onChanged?.invoke()
    }

    fun removeSelected() {
        val def = definition ?: return
        val id = selectedId ?: return
        def.elements.removeAll { it.id == id }
        selectedId = null
        onSelect?.invoke(null)
        onChanged?.invoke()
    }
}