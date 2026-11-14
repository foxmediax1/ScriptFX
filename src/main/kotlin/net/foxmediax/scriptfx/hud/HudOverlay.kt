package net.foxmediax.scriptfx.hud

import com.mojang.blaze3d.platform.NativeImage
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.foxmediax.scriptfx.ScriptFX
import net.foxmediax.scriptfx.client.hud.HudRuntime
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.io.File
import java.security.MessageDigest

@Environment(EnvType.CLIENT)
object HudOverlay {

    private data class Tex(val id: Identifier, val w: Int, val h: Int)

    private val texCache = HashMap<String, Tex>()

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
                def.anchor, def.offsetX, def.offsetY, def.width, def.height, sw, sh
            )
            val slide = if (!a.closing && alpha < 1f) (1f - alpha) * 8f else 0f
            val ox = bx
            val oy = by + slide

            graphics.fill(
                ox.toInt(), oy.toInt(),
                (ox + def.width).toInt(), (oy + def.height).toInt(),
                applyAlpha(def.background, alpha)
            )

            for (el in def.elements) {
                if (!el.visible) continue
                val ex = (ox + el.x).toInt()
                val ey = (oy + el.y).toInt()

                when (el.type) {
                    HudElementType.RECT -> {
                        graphics.fill(
                            ex, ey,
                            (ex + el.w).toInt(), (ey + el.h).toInt(),
                            applyAlpha(el.color, alpha)
                        )
                    }

                    HudElementType.TEXT -> {
                        val display = HudRuntime.textFor(def.id, el.id, el.text)
                        val color = applyAlpha(el.color, alpha)
                        var ly = ey
                        for (line in display.split('\n')) {
                            graphics.text(font, line, ex, ly, color, false)
                            ly += font.lineHeight + 2
                        }
                    }

                    HudElementType.IMAGE -> {
                        val tex = resolveImage(def.project, el.imagePath)
                        if (tex != null) {
                            val color = applyAlpha(0xFFFFFFFF.toInt(), alpha)
                            graphics.blit(
                                RenderPipelines.GUI_TEXTURED,
                                tex.id,
                                ex, ey,
                                0f, 0f,
                                el.w.toInt(), el.h.toInt(),
                                tex.w, tex.h,
                                tex.w, tex.h,
                                color
                            )
                        } else {
                            graphics.fill(
                                ex, ey,
                                (ex + el.w).toInt(), (ey + el.h).toInt(),
                                applyAlpha(0x44FFFFFF, alpha)
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Путь картинки:
     * 1) projects/<project>/hud/<imagePath>
     * 2) projects/<project>/<imagePath>
     * 3) imagePath как абсолютный/полный путь к файлу
     */
    private fun resolveImage(project: String, imagePath: String): Tex? {
        if (imagePath.isBlank()) return null
        val key = "$project|$imagePath"
        texCache[key]?.let { return it }

        val root = HudRegistry.projectsRoot()
        val candidates = listOf(
            File(File(File(root, project), "hud"), imagePath),
            File(File(root, project), imagePath),
            File(imagePath)
        )
        val file = candidates.firstOrNull { it.isFile && it.length() > 0 } ?: return null

        return try {
            val bytes = file.readBytes()
            val hash = MessageDigest.getInstance("SHA-1").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            val image = NativeImage.read(bytes)
            val id = ScriptFX.id("hud_img/$hash")
            val texture = DynamicTexture({ "scriptfx hud $hash" }, image)
            texture.upload()
            Minecraft.getInstance().textureManager.register(id, texture)
            Tex(id, image.width, image.height).also { texCache[key] = it }
        } catch (_: Exception) {
            null
        }
    }

    fun clearTextures() {
        val tm = Minecraft.getInstance().textureManager
        texCache.values.forEach { tm.release(it.id) }
        texCache.clear()
    }

    private fun anchorPos(
        anchor: HudAnchor, offX: Float, offY: Float, w: Float, h: Float, sw: Int, sh: Int
    ): Pair<Float, Float> {
        val x = when (anchor) {
            HudAnchor.TOP_LEFT, HudAnchor.CENTER_LEFT, HudAnchor.BOTTOM_LEFT -> offX
            HudAnchor.TOP_CENTER, HudAnchor.CENTER, HudAnchor.BOTTOM_CENTER -> (sw - w) / 2f + offX
            HudAnchor.TOP_RIGHT, HudAnchor.CENTER_RIGHT, HudAnchor.BOTTOM_RIGHT -> sw - w - offX
        }
        val y = when (anchor) {
            HudAnchor.TOP_LEFT, HudAnchor.TOP_CENTER, HudAnchor.TOP_RIGHT -> offY
            HudAnchor.CENTER_LEFT, HudAnchor.CENTER, HudAnchor.CENTER_RIGHT -> (sh - h) / 2f + offY
            HudAnchor.BOTTOM_LEFT, HudAnchor.BOTTOM_CENTER, HudAnchor.BOTTOM_RIGHT -> sh - h - offY
        }
        return x to y
    }

    private fun applyAlpha(argb: Int, alpha: Float): Int {
        val a = (argb ushr 24) and 0xFF
        val na = (a * alpha).toInt().coerceIn(0, 255)
        return (na shl 24) or (argb and 0x00FFFFFF)
    }
}