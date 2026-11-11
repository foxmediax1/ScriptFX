package net.foxmediax.scriptfx.hud

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.foxmediax.scriptfx.client.hud.HudRuntime
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * Отрисовка активных кастомных HUD поверх игры.
 * Подключается через HudElementRegistry в ScriptFXClient.
 */
@Environment(EnvType.CLIENT)
object HudOverlay {

    fun render(graphics: GuiGraphicsExtractor) {
        val mc = Minecraft.getInstance()
        if (mc.options.hideGui) return

        val sw = mc.window.guiScaledWidth
        val sh = mc.window.guiScaledHeight
        val font = mc.font

        for (a in HudRuntime.visible()) {
            val def = a.def
            val alpha = a.alpha.coerceIn(0f, 1f)
            if (alpha <= 0.01f) continue

            val (bx, by) = anchorPos(
                def.anchor,
                def.offsetX,
                def.offsetY,
                def.width,
                def.height,
                sw,
                sh
            )

            // лёгкий сдвиг при появлении
            val slide = if (!a.closing && alpha < 1f) (1f - alpha) * 8f else 0f
            val ox = bx
            val oy = by + slide

            // фон панели
            graphics.fill(
                ox.toInt(),
                oy.toInt(),
                (ox + def.width).toInt(),
                (oy + def.height).toInt(),
                applyAlpha(def.background, alpha)
            )

            for (el in def.elements) {
                if (!el.visible) continue

                val ex = (ox + el.x).toInt()
                val ey = (oy + el.y).toInt()

                when (el.type) {
                    HudElementType.RECT -> {
                        graphics.fill(
                            ex,
                            ey,
                            (ex + el.w).toInt(),
                            (ey + el.h).toInt(),
                            applyAlpha(el.color, alpha)
                        )
                    }

                    HudElementType.TEXT -> {
                        val color = applyAlpha(el.color, alpha)
                        var ly = ey
                        for (line in el.text.split('\n')) {
                            graphics.text(font, line, ex, ly, color, false)
                            ly += font.lineHeight + 2
                        }
                    }

                    HudElementType.IMAGE -> {
                        // заглушка до подключения текстур
                        graphics.fill(
                            ex,
                            ey,
                            (ex + el.w).toInt(),
                            (ey + el.h).toInt(),
                            applyAlpha(0x44FFFFFF, alpha)
                        )
                    }
                }
            }
        }
    }

    private fun anchorPos(
        anchor: HudAnchor,
        offX: Float,
        offY: Float,
        w: Float,
        h: Float,
        sw: Int,
        sh: Int
    ): Pair<Float, Float> {
        val x = when (anchor) {
            HudAnchor.TOP_LEFT,
            HudAnchor.CENTER_LEFT,
            HudAnchor.BOTTOM_LEFT -> offX

            HudAnchor.TOP_CENTER,
            HudAnchor.CENTER,
            HudAnchor.BOTTOM_CENTER -> (sw - w) / 2f + offX

            HudAnchor.TOP_RIGHT,
            HudAnchor.CENTER_RIGHT,
            HudAnchor.BOTTOM_RIGHT -> sw - w - offX
        }

        val y = when (anchor) {
            HudAnchor.TOP_LEFT,
            HudAnchor.TOP_CENTER,
            HudAnchor.TOP_RIGHT -> offY

            HudAnchor.CENTER_LEFT,
            HudAnchor.CENTER,
            HudAnchor.CENTER_RIGHT -> (sh - h) / 2f + offY

            HudAnchor.BOTTOM_LEFT,
            HudAnchor.BOTTOM_CENTER,
            HudAnchor.BOTTOM_RIGHT -> sh - h - offY
        }

        return x to y
    }

    private fun applyAlpha(argb: Int, alpha: Float): Int {
        val a = (argb ushr 24) and 0xFF
        val na = (a * alpha).toInt().coerceIn(0, 255)
        return (na shl 24) or (argb and 0x00FFFFFF)
    }
}