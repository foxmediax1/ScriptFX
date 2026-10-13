package net.foxmediax.scriptfx.client.particles

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import kotlin.math.sqrt

object ParticleWorldRenderer {

    fun init() {
        // 26.1 Fabric API
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register { ctx ->
            render(ctx)
        }
    }

    private fun render(ctx: LevelRenderContext) {
        val mc = Minecraft.getInstance()
        if (mc.level == null) return

        val camera = mc.gameRenderer.mainCamera
        val camPos = camera.position()

        // yaw / pitch камеры (подставь имена из IDE, если другие)
        val yawRad = Math.toRadians(camera.yRot().toDouble())
        val pitchRad = Math.toRadians(camera.xRot().toDouble())

        val lookX = (-kotlin.math.sin(yawRad) * kotlin.math.cos(pitchRad)).toFloat()
        val lookY = (-kotlin.math.sin(pitchRad)).toFloat()
        val lookZ = (kotlin.math.cos(yawRad) * kotlin.math.cos(pitchRad)).toFloat()

        // right = look × (0,1,0)
        var rx = lookY * 0f - lookZ * 1f
        var ry = lookZ * 0f - lookX * 0f
        var rz = lookX * 1f - lookY * 0f
        // = (-lookZ, 0, lookX)
        rx = -lookZ
        ry = 0f
        rz = lookX
        var rlen = kotlin.math.sqrt(rx * rx + ry * ry + rz * rz).coerceAtLeast(1e-6f)
        rx /= rlen; ry /= rlen; rz /= rlen

        // billUp = right × look
        var ux = ry * lookZ - rz * lookY
        var uy = rz * lookX - rx * lookZ
        var uz = rx * lookY - ry * lookX
        var ulen = kotlin.math.sqrt(ux * ux + uy * uy + uz * uz).coerceAtLeast(1e-6f)
        ux /= ulen; uy /= ulen; uz /= ulen

        val consumers: MultiBufferSource = ctx.bufferSource()
        val pose: PoseStack = ctx.poseStack()

        data class BatchKey(val texture: String, val material: String)
        val batches = linkedMapOf<BatchKey, MutableList<LiveParticle>>()

        ParticleRuntime.forEachParticle { em, p ->
            val key = BatchKey(em.effect.texture, em.effect.material)
            batches.getOrPut(key) { mutableListOf() }.add(p)
        }
        if (batches.isEmpty()) return

        for ((key, list) in batches) {
            val tex = parseTexture(key.texture)
            val vc = getBuffer(consumers, key.material, tex) ?: continue
            val mat = pose.last().pose()

            for (p in list) {
                val x = (p.x - camPos.x).toFloat()
                val y = (p.y - camPos.y).toFloat()
                val z = (p.z - camPos.z).toFloat()
                val hx = p.sizeX * 0.5f
                val hy = p.sizeY * 0.5f

                val x0 = x - rx * hx - ux * hy
                val y0 = y - ry * hx - uy * hy
                val z0 = z - rz * hx - uz * hy
                val x1 = x + rx * hx - ux * hy
                val y1 = y + ry * hx - uy * hy
                val z1 = z + rz * hx - uz * hy
                val x2 = x + rx * hx + ux * hy
                val y2 = y + ry * hx + uy * hy
                val z2 = z + rz * hx + uz * hy
                val x3 = x - rx * hx + ux * hy
                val y3 = y - ry * hx + uy * hy
                val z3 = z - rz * hx + uz * hy

                val color = argb(p.a, p.r, p.g, p.b)
                val light = 0xF000F0

                vert(vc, mat, x0, y0, z0, p.uvU0, p.uvV1, color, light)
                vert(vc, mat, x1, y1, z1, p.uvU1, p.uvV1, color, light)
                vert(vc, mat, x2, y2, z2, p.uvU1, p.uvV0, color, light)
                vert(vc, mat, x3, y3, z3, p.uvU0, p.uvV0, color, light)
            }
        }

        (consumers as? MultiBufferSource.BufferSource)?.endBatch()
    }

    private fun vert(
        vc: VertexConsumer,
        mat: Matrix4f,
        x: Float, y: Float, z: Float,
        u: Float, v: Float,
        argb: Int,
        light: Int
    ) {
        // Порядок PARTICLE: position, uv, color, light (без overlay/normal)
        vc.addVertex(mat, x, y, z)
            .setUv(u, v)
            .setColor(argb)
            .setLight(light)
    }
    private fun argb(a: Float, r: Float, g: Float, b: Float): Int {
        val A = (a.coerceIn(0f, 1f) * 255).toInt()
        val R = (r.coerceIn(0f, 1f) * 255).toInt()
        val G = (g.coerceIn(0f, 1f) * 255).toInt()
        val B = (b.coerceIn(0f, 1f) * 255).toInt()
        return (A shl 24) or (R shl 16) or (G shl 8) or B
    }

    private fun parseTexture(raw: String): Identifier {
        var t = raw.trim()
        if (!t.endsWith(".png")) t = "$t.png"
        // Snowstorm часто пишет без "textures/"
        if (!t.contains("textures/") && t.contains(":")) {
            val ns = t.substringBefore(':')
            val path = t.substringAfter(':')
            if (!path.startsWith("textures/")) {
                t = "$ns:textures/$path"
            }
        }
        return runCatching {
            Identifier.parse(t)
        }.getOrElse {
            Identifier.parse("minecraft:textures/particle/particles.png")
        }
    }

    private var cachedRt: Any? = null
    private var loggedOnce = false

    private fun getBuffer(
        consumers: MultiBufferSource,
        material: String,
        texture: Identifier
    ): VertexConsumer? {
        return try {
            val rt = cachedRt ?: discoverParticleRenderType(material).also { cachedRt = it }
            if (rt == null) {
                if (!loggedOnce) {
                    loggedOnce = true
                    System.err.println("[ScriptFX] particle RenderType not found — check discoverParticleRenderType()")
                }
                return null
            }
            val gb = consumers.javaClass.methods.first {
                it.name == "getBuffer" && it.parameterCount == 1
            }
            gb.invoke(consumers, rt) as VertexConsumer
        } catch (t: Throwable) {
            if (!loggedOnce) {
                loggedOnce = true
                t.printStackTrace()
            }
            null
        }
    }

    private fun discoverParticleRenderType(material: String): Any? {
        val mc = Minecraft.getInstance()

        // 1) Поля ParticleEngine: Map или List sheet'ов
        val engine = mc.particleEngine
        val engCl = engine.javaClass
        for (f in engCl.declaredFields) {
            f.isAccessible = true
            val v = runCatching { f.get(engine) }.getOrNull() ?: continue
            when (v) {
                is Map<*, *> -> {
                    for (key in v.keys) {
                        if (key == null) continue
                        val name = key.toString().lowercase()
                        if (name.contains("translucent") || name.contains("particle")) {
                            val layer = extractRenderLayer(key)
                            if (layer != null) return layer
                        }
                    }
                    // любой ключ с renderType
                    for (key in v.keys) {
                        val layer = extractRenderLayer(key)
                        if (layer != null) return layer
                    }
                }
                is List<*> -> {
                    for (key in v) {
                        val layer = extractRenderLayer(key)
                        if (layer != null) return layer
                    }
                }
            }
        }

        // 2) Статические поля в net.minecraft.client.particle.*
        val classNames = listOf(
            "net.minecraft.client.particle.ParticleTextureSheet",
            "net.minecraft.client.particle.ParticleRenderType",
            "net.minecraft.client.particle.ParticleRenderTypes",
            "net.minecraft.client.particle.BillboardParticle\$RenderType"
        )
        for (cn in classNames) {
            val cl = runCatching { Class.forName(cn) }.getOrNull() ?: continue
            for (f in cl.fields) {
                val n = f.name.lowercase()
                val wantTranslucent = !material.lowercase().contains("opaque")
                if (wantTranslucent && (n.contains("translucent") || n.contains("particle"))) {
                    val key = runCatching { f.get(null) }.getOrNull()
                    val layer = extractRenderLayer(key)
                    if (layer != null) return layer
                }
                if (!wantTranslucent && n.contains("opaque")) {
                    val key = runCatching { f.get(null) }.getOrNull()
                    val layer = extractRenderLayer(key)
                    if (layer != null) return layer
                }
            }
            // любой static sheet
            for (f in cl.fields) {
                if (java.lang.reflect.Modifier.isStatic(f.modifiers)) {
                    val key = runCatching { f.get(null) }.getOrNull()
                    val layer = extractRenderLayer(key)
                    if (layer != null) return layer
                }
            }
        }
        return null
    }

    private fun extractRenderLayer(sheetOrLayer: Any?): Any? {
        if (sheetOrLayer == null) return null
        // уже RenderType/RenderLayer?
        val cn = sheetOrLayer.javaClass.name.lowercase()
        if (cn.contains("render") && (cn.contains("type") || cn.contains("layer"))) {
            return sheetOrLayer
        }
        // record component / method renderType()
        for (m in sheetOrLayer.javaClass.methods) {
            if (m.parameterCount == 0 &&
                (m.name == "renderType" || m.name == "getRenderType" ||
                        m.name == "renderLayer" || m.name == "getRenderLayer")
            ) {
                val r = runCatching { m.invoke(sheetOrLayer) }.getOrNull()
                if (r != null) return r
            }
        }
        for (f in sheetOrLayer.javaClass.declaredFields) {
            f.isAccessible = true
            val n = f.name.lowercase()
            if (n.contains("render") || n == "type" || n == "layer") {
                val r = runCatching { f.get(sheetOrLayer) }.getOrNull()
                if (r != null) return r
            }
        }
        // иногда в getBuffer кладут сам sheet
        return sheetOrLayer
    }
}
