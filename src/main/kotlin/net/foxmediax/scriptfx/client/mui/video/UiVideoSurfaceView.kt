package net.foxmediax.scriptfx.client.mui.video

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.BitmapFactory
import icyllis.modernui.graphics.Canvas
import icyllis.modernui.graphics.Image
import icyllis.modernui.graphics.Paint
import icyllis.modernui.graphics.Rect
import icyllis.modernui.graphics.RectF
import icyllis.modernui.view.View
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import java.io.File
import java.util.concurrent.Executors

class UiVideoSurfaceView(context: Context) : View(context) {

    private val paint = Paint()
    private var frameFiles: List<File> = emptyList()
    private var currentIdx = 0

    /** Последний успешно отрисованный кадр — чтобы не было чёрных промежутков. */
    private var lastDrawn: Image? = null

    private val maxCacheSize = 48
    private val cache = object : LinkedHashMap<Int, Image>(maxCacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Image>?): Boolean {
            if (size > maxCacheSize) {
                val e = eldest?.value
                if (e !== lastDrawn) {
                    try { e?.close() } catch (_: Throwable) {}
                }
                return true
            }
            return false
        }
    }
    private val pending = HashSet<Int>()
    private val executor = Executors.newFixedThreadPool(3) { r ->
        Thread(r, "ScriptFX-VideoDecode").apply { isDaemon = true }
    }

    fun setFrames(files: List<File>) {
        release()
        frameFiles = files
        currentIdx = 0
        if (frameFiles.isNotEmpty()) requestDecode(0)
        // Небольшой prefetch, чтобы первый кадр был готов до первого onDraw.
        for (i in 1..minOf(3, frameFiles.lastIndex)) requestDecode(i)
        invalidate()
    }

    fun showFrame(idx: Int) {
        if (idx !in frameFiles.indices) return
        currentIdx = idx
        if (!cache.containsKey(idx)) requestDecode(idx)
        // Prefetch на 3 кадра вперёд — сглаживает неравномерное декодирование.
        for (k in 1..3) {
            val n = idx + k
            if (n <= frameFiles.lastIndex && !cache.containsKey(n)) requestDecode(n)
        }
        invalidate()
    }

    private fun requestDecode(idx: Int) {
        if (idx !in frameFiles.indices) return
        synchronized(pending) {
            if (!pending.add(idx)) return
        }
        val file = frameFiles[idx]
        executor.submit {
            try {
                val bmp = BitmapFactory.decodeFile(file)
                if (bmp == null) {
                    synchronized(pending) { pending.remove(idx) }
                    return@submit
                }
                post {
                    try {
                        val img = Image.createTextureFromBitmap(bmp)
                        if (img != null) {
                            cache[idx] = img
                            invalidate()
                        }
                    } catch (t: Throwable) {
                        ScriptFXLog.warn("UiVideoSurfaceView: createImage failed idx=$idx: ${t.message}")
                    } finally {
                        try { bmp.close() } catch (_: Throwable) {}
                        synchronized(pending) { pending.remove(idx) }
                    }
                }
            } catch (t: Throwable) {
                ScriptFXLog.warn("UiVideoSurfaceView: decode failed idx=$idx: ${t.message}")
                synchronized(pending) { pending.remove(idx) }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (frameFiles.isEmpty()) return

        // Если текущий кадр готов — берём его и обновляем lastDrawn.
        // Если нет — рисуем lastDrawn, чтобы не было мерцания.
        val current = cache[currentIdx]
        if (current != null) {
            lastDrawn = current
        }
        val img = current ?: lastDrawn ?: return

        val iw = img.width
        val ih = img.height
        if (iw <= 0 || ih <= 0) return

        val aspect = iw.toFloat() / ih.toFloat()
        val vw = width.toFloat()
        val vh = height.toFloat()
        val viewAspect = vw / vh
        val dstW: Float
        val dstH: Float
        if (viewAspect > aspect) {
            dstH = vh
            dstW = vh * aspect
        } else {
            dstW = vw
            dstH = vw / aspect
        }
        val left = (vw - dstW) / 2f
        val top = (vh - dstH) / 2f

        canvas.drawImage(
            img,
            Rect(0, 0, iw, ih),
            RectF(left, top, left + dstW, top + dstH),
            paint
        )
    }

    fun release() {
        cache.values.forEach { try { it.close() } catch (_: Throwable) {} }
        cache.clear()
        lastDrawn = null
        synchronized(pending) { pending.clear() }
    }
}