package net.foxmediax.scriptfx.client.media

import com.mojang.blaze3d.platform.NativeImage
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.foxmediax.scriptfx.media.MediaRegistry
import net.foxmediax.scriptfx.media.Mp4Decoder
import net.foxmediax.scriptfx.media.VideoMeta
import net.foxmediax.scriptfx.network.MediaEndPayload
import net.foxmediax.scriptfx.network.MediaPayload
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future

object VideoPlayer {

    private const val MAX_FRAME_WIDTH = 1280
    private const val FADE_IN_MS = 700L
    private const val FADE_OUT_MS = 700L

    private enum class FadePhase { NONE, TO_BLACK, PLAYING, FROM_BLACK }

    private data class Session(
        val id: String,
        val frameFiles: List<File>,
        val fps: Float,
        val loop: Boolean,
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val preview: Boolean,
        val texId: Identifier,
        var texture: DynamicTexture?,
        val soundKey: String?,
        var currentIdx: Int = -1,
        var startMs: Long = System.currentTimeMillis(),
        @Volatile var prefetchIdx: Int = -1,
        @Volatile var prefetchImage: NativeImage? = null,
        @Volatile var prefetchJob: Future<*>? = null
    )

    private val sessions = ArrayList<Session>()
    private val frameListCache = ConcurrentHashMap<String, List<File>>()

    private val decodePool = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "scriptfx-video-frame").apply { isDaemon = true }
    }

    private var fadePhase = FadePhase.NONE
    private var fadeStartMs = 0L
    @Volatile private var fadeAlpha = 0f

    private var pendingPlay: MediaPayload? = null
    private var pendingMeta: VideoMeta? = null
    private var pendingEndId: String? = null
    private var pendingEndWasPreview = false

    fun hasActive(): Boolean =
        sessions.isNotEmpty() || fadePhase != FadePhase.NONE

    fun overlayBlackAlpha(): Float = fadeAlpha.coerceIn(0f, 1f)

    fun play(payload: MediaPayload) {
        val mc = Minecraft.getInstance()
        if (!isRenderThread(mc)) {
            mc.execute { play(payload) }
            return
        }

        // Только lookup видео; cache-sounds не трогаем через полный wipe логики sound
        MediaRegistry.reload()
        val meta0 = MediaRegistry.video(payload.id) ?: run {
            ScriptFXLog.warn("VideoPlayer: '${payload.id}' not found")
            return
        }

        if (meta0.sourceMp4 != null) {
            val future = Mp4Decoder.ensureDecodedAsync(meta0)
            if (!future.isDone) {
                ScriptFXLog.info("VideoPlayer: decoding '${payload.id}'…")
            }
            future.whenComplete { decoded, err ->
                if (err != null) {
                    ScriptFXLog.warn("VideoPlayer: decode error: ${err.message}")
                    return@whenComplete
                }
                if (decoded == null) {
                    ScriptFXLog.warn("VideoPlayer: decode failed for '${payload.id}'")
                    return@whenComplete
                }
                mc.execute { beginPlayWithFade(payload, decoded) }
            }
            return
        }

        beginPlayWithFade(payload, meta0)
    }

    private fun beginPlayWithFade(payload: MediaPayload, meta: VideoMeta) {
        if (sessions.isNotEmpty()) {
            sessions.toList().forEach { releaseSession(it) }
            sessions.clear()
        }
        pendingPlay = payload
        pendingMeta = meta
        pendingEndId = null
        fadePhase = FadePhase.TO_BLACK
        fadeStartMs = System.currentTimeMillis()
        fadeAlpha = 0f
        ScriptFXLog.info("VideoPlayer: fade to black → '${meta.id}'")
    }

    private fun startSession(payload: MediaPayload, meta: VideoMeta) {
        if (meta.frames.isEmpty()) {
            ScriptFXLog.warn("VideoPlayer: no frames for '${payload.id}'")
            startFadeFromBlack(null, true)
            return
        }

        stopSessionsOnly()

        val key = "${meta.project}/${meta.id}"
        val files = frameListCache.getOrPut(key) {
            meta.frames.filter { it.isFile }.sortedBy { it.name }
        }
        if (files.isEmpty()) {
            ScriptFXLog.warn("VideoPlayer: empty frame list for $key")
            startFadeFromBlack(null, true)
            return
        }

        val safe = key.lowercase().replace(Regex("[^a-z0-9_/.-]"), "_")
        val texId = Identifier.fromNamespaceAndPath("scriptfx", "dyn_video_stream/$safe")

        val placeholder = NativeImage(2, 2, false)
        val dyn = DynamicTexture({ "scriptfx-video-$safe" }, placeholder)
        Minecraft.getInstance().textureManager.register(texId, dyn)

        val audioFile = File(meta.dir, "audio.ogg").takeIf { it.isFile && it.length() > 0L }
        val soundKey = when {
            audioFile != null -> meta.soundId ?: "_video_audio_${meta.project}_${meta.id}"
            else -> null
        }

        val session = Session(
            id = meta.id,
            frameFiles = files,
            fps = meta.fps.coerceAtLeast(1f),
            loop = meta.loop,
            x = payload.x,
            y = payload.y,
            w = payload.w,
            h = payload.h,
            preview = payload.preview,
            texId = texId,
            texture = dyn,
            soundKey = soundKey
        )
        sessions.add(session)
        ensureFrame(session, 0)

        if (audioFile != null && soundKey != null) {
            ScriptFXLog.info("VideoPlayer: audio → ${audioFile.absolutePath}")
            MediaClient.playFile(soundKey, audioFile, 1f, 1f, meta.loop, videoSlider = true)
        } else {
            ScriptFXLog.warn(
                "VideoPlayer: нет audio.ogg в ${meta.dir.absolutePath}"
            )
        }

        ScriptFXLog.info(
            "VideoPlayer: play $key (${files.size} frames, preview=${payload.preview})"
        )
    }

    fun stop(id: String) {
        val k = id.trim()
        if (k.isEmpty()) return
        val removed = sessions.filter { it.id == k || it.id == k.substringAfterLast('/') }
        sessions.removeAll(removed.toSet())
        removed.forEach { releaseSession(it) }

        pendingPlay = null
        pendingMeta = null

        if (fadePhase == FadePhase.TO_BLACK || fadePhase == FadePhase.PLAYING || removed.isNotEmpty()) {
            startFadeFromBlack(endId = null, preview = true)
        } else if (sessions.isEmpty() && fadePhase == FadePhase.NONE) {
            MediaClient.endVideoMute()
        }
    }

    fun stopAll() {
        sessions.toList().forEach { releaseSession(it) }
        sessions.clear()
        pendingPlay = null
        pendingMeta = null
        if (fadePhase != FadePhase.NONE && fadePhase != FadePhase.FROM_BLACK) {
            startFadeFromBlack(null, true)
        } else {
            fadePhase = FadePhase.NONE
            fadeAlpha = 0f
            MediaClient.endVideoMute()
        }
    }

    private fun stopSessionsOnly() {
        sessions.toList().forEach { releaseSession(it) }
        sessions.clear()
    }

    private fun releaseSession(s: Session) {
        s.soundKey?.let { MediaClient.stopSound(it) }
        s.prefetchJob?.cancel(true)
        s.prefetchJob = null
        try {
            s.prefetchImage?.close()
        } catch (_: Throwable) {
        }
        s.prefetchImage = null

        val mc = Minecraft.getInstance()
        try {
            mc.textureManager.release(s.texId)
        } catch (_: Throwable) {
        }
        try {
            s.texture?.close()
        } catch (_: Throwable) {
        }
        s.texture = null
    }

    private fun tickFade() {
        val now = System.currentTimeMillis()
        when (fadePhase) {
            FadePhase.NONE -> Unit

            FadePhase.TO_BLACK -> {
                val t = ((now - fadeStartMs).toFloat() / FADE_IN_MS).coerceIn(0f, 1f)
                fadeAlpha = smooth(t)
                if (t >= 1f) {
                    fadeAlpha = 1f
                    val p = pendingPlay
                    val m = pendingMeta
                    pendingPlay = null
                    pendingMeta = null
                    fadePhase = FadePhase.PLAYING
                    if (p != null && m != null) {
                        startSession(p, m)
                    } else {
                        startFadeFromBlack(null, true)
                    }
                }
            }

            FadePhase.PLAYING -> {
                fadeAlpha = 1f
            }

            FadePhase.FROM_BLACK -> {
                val t = ((now - fadeStartMs).toFloat() / FADE_OUT_MS).coerceIn(0f, 1f)
                fadeAlpha = 1f - smooth(t)
                if (t >= 1f) {
                    fadeAlpha = 0f
                    fadePhase = FadePhase.NONE
                    val endId = pendingEndId
                    val wasPreview = pendingEndWasPreview
                    pendingEndId = null
                    if (!wasPreview && !endId.isNullOrBlank()) {
                        notifyServerVideoEnded(endId)
                    }
                    if (sessions.isEmpty()) {
                        MediaClient.endVideoMute()
                    }
                    ScriptFXLog.info("VideoPlayer: fade from black done")
                }
            }
        }
    }

    private fun startFadeFromBlack(endId: String?, preview: Boolean) {
        pendingEndId = endId
        pendingEndWasPreview = preview
        fadePhase = FadePhase.FROM_BLACK
        fadeStartMs = System.currentTimeMillis()
        fadeAlpha = 1f
    }

    private fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    fun render(screenW: Int, screenH: Int, blit: (Identifier, Int, Int, Int, Int) -> Unit) {
        tickFade()
        if (sessions.isEmpty()) return

        val now = System.currentTimeMillis()
        val it = sessions.iterator()
        while (it.hasNext()) {
            val s = it.next()
            val elapsedSec = (now - s.startMs) / 1000.0
            var idx = (elapsedSec * s.fps).toInt()

            if (idx >= s.frameFiles.size) {
                if (s.loop) {
                    s.startMs = now
                    idx = 0
                } else {
                    val endedId = s.id
                    val wasPreview = s.preview
                    it.remove()
                    releaseSession(s)
                    startFadeFromBlack(endedId, wasPreview)
                    continue
                }
            }

            idx = idx.coerceIn(0, s.frameFiles.size - 1)
            ensureFrame(s, idx)
            schedulePrefetch(s, idx + 1)

            val fx: Int
            val fy: Int
            val fw: Int
            val fh: Int
            if (s.w <= 0f || s.h <= 0f) {
                fx = 0
                fy = 0
                fw = screenW
                fh = screenH
            } else {
                fx = (s.x * screenW).toInt()
                fy = (s.y * screenH).toInt()
                fw = (s.w * screenW).toInt().coerceAtLeast(1)
                fh = (s.h * screenH).toInt().coerceAtLeast(1)
            }
            blit(s.texId, fx, fy, fw, fh)
        }
    }

    private fun ensureFrame(s: Session, idx: Int) {
        if (idx == s.currentIdx) return
        val file = s.frameFiles.getOrNull(idx) ?: return

        val prefetched = if (s.prefetchIdx == idx) s.prefetchImage else null
        if (prefetched != null) {
            s.prefetchImage = null
            s.prefetchIdx = -1
            uploadFrame(s, prefetched, idx)
            return
        }

        val img = readFrameScaled(file) ?: return
        uploadFrame(s, img, idx)
    }

    private fun uploadFrame(s: Session, img: NativeImage, idx: Int) {
        val tex = s.texture
        try {
            if (tex == null) {
                img.close()
                return
            }
            val old = try {
                tex.pixels
            } catch (_: Throwable) {
                null
            }

            val sameSize = old != null &&
                    old.width == img.width &&
                    old.height == img.height

            if (sameSize) {
                try {
                    tex.setPixels(img)
                    tex.upload()
                } catch (t: Throwable) {
                    ScriptFXLog.warn("VideoPlayer: upload failed, recreate: ${t.message}")
                    recreateTexture(s, img)
                }
            } else {
                recreateTexture(s, img)
            }
            s.currentIdx = idx
        } catch (t: Throwable) {
            ScriptFXLog.warn("VideoPlayer: frame $idx failed: ${t.message}")
            try {
                img.close()
            } catch (_: Throwable) {
            }
        }
    }

    private fun recreateTexture(s: Session, img: NativeImage) {
        val mc = Minecraft.getInstance()
        try {
            mc.textureManager.release(s.texId)
        } catch (_: Throwable) {
        }
        try {
            s.texture?.close()
        } catch (_: Throwable) {
        }
        val dyn = DynamicTexture({ "scriptfx-video-${s.id}" }, img)
        mc.textureManager.register(s.texId, dyn)
        s.texture = dyn
    }

    private fun schedulePrefetch(s: Session, nextIdx: Int) {
        if (nextIdx < 0 || nextIdx >= s.frameFiles.size) return
        if (s.prefetchIdx == nextIdx && (s.prefetchImage != null || s.prefetchJob?.isDone == false)) {
            return
        }
        s.prefetchJob?.cancel(true)
        try {
            s.prefetchImage?.close()
        } catch (_: Throwable) {
        }
        s.prefetchImage = null
        s.prefetchIdx = nextIdx
        val file = s.frameFiles[nextIdx]
        s.prefetchJob = decodePool.submit {
            val img = readFrameScaled(file) ?: return@submit
            if (s.prefetchIdx != nextIdx) {
                img.close()
                return@submit
            }
            s.prefetchImage = img
        }
    }

    private fun readFrameScaled(file: File): NativeImage? {
        return try {
            FileInputStream(file).use { ins ->
                val raw = NativeImage.read(ins)
                if (raw.width <= MAX_FRAME_WIDTH) return raw

                val scale = MAX_FRAME_WIDTH.toDouble() / raw.width
                val nw = MAX_FRAME_WIDTH
                val nh = (raw.height * scale).toInt().coerceAtLeast(1)
                val scaled = NativeImage(nw, nh, false)
                for (y in 0 until nh) {
                    val sy = (y / scale).toInt().coerceIn(0, raw.height - 1)
                    for (x in 0 until nw) {
                        val sx = (x / scale).toInt().coerceIn(0, raw.width - 1)
                        copyPixel(raw, sx, sy, scaled, x, y)
                    }
                }
                raw.close()
                scaled
            }
        } catch (e: Exception) {
            ScriptFXLog.warn("VideoPlayer: read ${file.name}: ${e.message}")
            null
        }
    }

    private fun copyPixel(src: NativeImage, sx: Int, sy: Int, dst: NativeImage, dx: Int, dy: Int) {
        try {
            val get = src.javaClass.methods.firstOrNull {
                (it.name == "getPixelRGBA" || it.name == "getPixel") && it.parameterCount == 2
            }
            val set = dst.javaClass.methods.firstOrNull {
                (it.name == "setPixelRGBA" || it.name == "setPixel") && it.parameterCount == 3
            }
            if (get != null && set != null) {
                val c = get.invoke(src, sx, sy)
                set.invoke(dst, dx, dy, c)
            }
        } catch (_: Throwable) {
        }
    }

    private fun notifyServerVideoEnded(videoId: String) {
        try {
            if (ClientPlayNetworking.canSend(MediaEndPayload.TYPE)) {
                ClientPlayNetworking.send(MediaEndPayload(videoId))
            }
        } catch (t: Throwable) {
            ScriptFXLog.warn("VideoPlayer: media_end failed: ${t.message}")
        }
    }

    private fun isRenderThread(mc: Minecraft): Boolean =
        try {
            mc.isSameThread
        } catch (_: Throwable) {
            false
        }
}