package net.foxmediax.scriptfx.client.media

import com.mojang.blaze3d.platform.NativeImage
import net.foxmediax.scriptfx.media.MediaRegistry
import net.foxmediax.scriptfx.media.Mp4Decoder
import net.foxmediax.scriptfx.media.VideoMeta
import net.foxmediax.scriptfx.network.MediaPayload
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.io.FileInputStream

object VideoPlayer {

    private data class Session(
        val id: String,
        val frameIds: List<Identifier>,
        val fps: Float,
        val loop: Boolean,
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val preview: Boolean,
        var startMs: Long = System.currentTimeMillis()
    )

    private val sessions = ArrayList<Session>()
    private val cache = HashMap<String, List<Identifier>>()

    fun hasActive(): Boolean = sessions.isNotEmpty()

    fun play(payload: MediaPayload) {
        MediaRegistry.reload()
        var meta: VideoMeta = MediaRegistry.video(payload.id) ?: run {
            ScriptFXLog.warn("VideoPlayer: '${payload.id}' not found")
            return
        }

        if (meta.sourceMp4 != null) {
            meta = Mp4Decoder.ensureDecoded(meta) ?: return
        }
        if (meta.frames.isEmpty()) {
            ScriptFXLog.warn("VideoPlayer: no frames for '${payload.id}'")
            return
        }

        stop(meta.id)
        stop(payload.id)

        val key = "${meta.project}/${meta.id}"
        val frameIds = cache.getOrPut(key) { loadFrames(key, meta.frames) }
        if (frameIds.isEmpty()) {
            ScriptFXLog.warn("VideoPlayer: failed to load textures for $key")
            return
        }

        sessions.add(
            Session(
                id = meta.id,
                frameIds = frameIds,
                fps = meta.fps.coerceAtLeast(1f),
                loop = meta.loop,
                x = payload.x,
                y = payload.y,
                w = payload.w,
                h = payload.h,
                preview = payload.preview
            )
        )

        meta.soundId?.let { sid ->
            MediaClient.handle(MediaPayload.soundPlay(sid, 1f, 1f, meta.loop))
        }
        ScriptFXLog.info("VideoPlayer: play $key (${frameIds.size} frames)")
    }

    fun stop(id: String) {
        val k = id.trim()
        if (k.isEmpty()) return
        if (sessions.isEmpty()) MediaClient.endVideoMute()
        sessions.removeAll { it.id == k || it.id == k.substringAfterLast('/') }
    }

    fun stopAll() {
        sessions.clear()
        MediaClient.endVideoMute()
    }

    /**
     * @param blit (textureId, x, y, width, height) — реализуй через свой GuiGraphics/DrawContext
     */
    fun render(screenW: Int, screenH: Int, blit: (Identifier, Int, Int, Int, Int) -> Unit) {
        if (sessions.isEmpty()) return
        val now = System.currentTimeMillis()
        val it = sessions.iterator()
        while (it.hasNext()) {
            val s = it.next()
            val elapsedSec = (now - s.startMs) / 1000.0
            var idx = (elapsedSec * s.fps).toInt()
            if (idx >= s.frameIds.size) {
                if (s.loop) {
                    s.startMs = now
                    idx = 0
                } else {
                    it.remove()
                    if (sessions.isEmpty()) {
                        MediaClient.endVideoMute()
                    }
                    continue
                }
            }
            idx = idx.coerceIn(0, s.frameIds.size - 1)
            val tex = s.frameIds[idx]

            val fx: Int
            val fy: Int
            val fw: Int
            val fh: Int
            if (s.w <= 0f || s.h <= 0f) {
                fx = 0; fy = 0; fw = screenW; fh = screenH
            } else {
                fx = (s.x * screenW).toInt()
                fy = (s.y * screenH).toInt()
                fw = (s.w * screenW).toInt().coerceAtLeast(1)
                fh = (s.h * screenH).toInt().coerceAtLeast(1)
            }
            blit(tex, fx, fy, fw, fh)
        }
    }

    private fun loadFrames(cacheKey: String, files: List<java.io.File>): List<Identifier> {
        val mc = Minecraft.getInstance()
        val list = ArrayList<Identifier>()
        val safe = cacheKey.replace(Regex("[^a-z0-9_/.-]"), "_")
        files.forEachIndexed { i, file ->
            val texId = Identifier.fromNamespaceAndPath("scriptfx", "dyn_video/$safe/$i")
            try {
                FileInputStream(file).use { ins ->
                    val img = NativeImage.read(ins)
                    val dyn = DynamicTexture({ "scriptfx-video-$i" }, img)
                    mc.textureManager.register(texId, dyn)
                    list.add(texId)
                }
            } catch (e: Exception) {
                ScriptFXLog.warn("VideoPlayer: skip ${file.name}: ${e.message}")
            }
        }
        return list
    }
}