package net.foxmediax.scriptfx.client.mui.video

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.KeyEvent
import icyllis.modernui.view.MotionEvent
import icyllis.modernui.view.View
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.media.MediaClient
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_BTN
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_MUTED
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_TEXT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.dp
import net.foxmediax.scriptfx.media.MediaRegistry
import net.foxmediax.scriptfx.media.Mp4Decoder
import net.foxmediax.scriptfx.media.VideoMeta
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import java.util.Locale
import java.util.concurrent.TimeUnit

class UiVideoPlayerOverlay(
    context: Context,
    private val videoId: String,
    private val onClose: (UiVideoPlayerOverlay) -> Unit,
) : FrameLayout(context) {

    private lateinit var loadingBox: LinearLayout
    private lateinit var surface: UiVideoSurfaceView
    private lateinit var controls: LinearLayout
    private lateinit var closeBtn: TextView
    private lateinit var playPauseBtn: TextView
    private lateinit var seekBar: SeekBarView
    private lateinit var timeView: TextView
    private lateinit var volumeBar: SeekBarView
    private lateinit var errorView: TextView

    private var meta: VideoMeta? = null
    private var frameFiles: List<java.io.File> = emptyList()
    private var fps: Float = 24f
    private var playing = false
    private var startMs = 0L
    private var basePositionMs = 0L
    private var closed = false

    private var audioKey: String? = null
    private var audioPlaying = false
    private var currentVolume: Float = 0.8f

    // UI auto-hide
    private var uiVisible = true
    private var hideRunnable: Runnable? = null
    private val hideDelayMs = 2500L

    private val ticker = object : Runnable {
        override fun run() {
            if (!playing || closed) return
            val elapsed = System.currentTimeMillis() - startMs
            val posMs = basePositionMs + elapsed
            val idx = ((posMs / 1000f) * fps).toInt()
            if (idx in frameFiles.indices) {
                surface.showFrame(idx)
                updateSeekUi(posMs)
                postDelayed(this, (1000f / fps).toLong().coerceAtLeast(16L))
            } else {
                playing = false
                stopAudio()
                updatePlayPause()
                showUi()
            }
        }
    }

    init {
        background = ColorDrawable(0xCC000000.toInt())
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true

        loadingBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        loadingBox.addView(LoadingSpinnerView(context),
            LinearLayout.LayoutParams(dp(context, 64), dp(context, 64)))
        loadingBox.addView(TextView(context).apply {
            text = "Загрузка видео…"
            textSize = 15f
            setTextColor(COL_TEXT)
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 16), 0, 0)
        })
        addView(loadingBox, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))

        surface = UiVideoSurfaceView(context).apply { visibility = View.GONE }
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        errorView = TextView(context).apply {
            textSize = 14f
            setTextColor(0xFFFF6B6B.toInt())
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 24))
        }
        addView(errorView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        closeBtn = TextView(context).apply {
            text = "✕"; textSize = 22f; setTextColor(COL_TEXT); gravity = Gravity.CENTER
            background = ColorDrawable(0x66000000)
            isClickable = true
            setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6))
            setOnClickListener { dismiss() }
        }
        addView(closeBtn, LayoutParams(dp(context, 44), dp(context, 44), Gravity.TOP or Gravity.RIGHT).apply {
            topMargin = dp(context, 16); rightMargin = dp(context, 16)
        })

        controls = buildControls(context)
        addView(controls, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            bottomMargin = dp(context, 24); leftMargin = dp(context, 24); rightMargin = dp(context, 24)
        })

        loadVideo()
    }

    // ─── UI auto-hide ────────────────────────────────────────────────

    private fun showUi() {
        if (closed) return
        uiVisible = true
        controls.visibility = View.VISIBLE
        closeBtn.visibility = View.VISIBLE
        scheduleHide()
    }

    private fun hideUi() {
        uiVisible = false
        controls.visibility = View.GONE
        closeBtn.visibility = View.GONE
    }

    private fun scheduleHide() {
        hideRunnable?.let { removeCallbacks(it) }
        val r = Runnable {
            if (!playing || closed) return@Runnable
            hideUi()
        }
        hideRunnable = r
        postDelayed(r, hideDelayMs)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER,
            MotionEvent.ACTION_HOVER_MOVE -> {
                if (!uiVisible) showUi() else scheduleHide()
            }
        }
        return super.onHoverEvent(event)
    }

    // ─── Controls ────────────────────────────────────────────────────

    private fun buildControls(ctx: Context): LinearLayout {
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(0xAA000000.toInt())
            setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12))
        }
        panel.addView(TextView(ctx).apply {
            text = videoId.substringAfterLast('/').substringAfterLast('\\')
            textSize = 13f; setTextColor(COL_MUTED); setSingleLine(true)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(ctx, 8), 0, 0)
        }
        playPauseBtn = TextView(ctx).apply {
            text = "▶"; textSize = 20f; setTextColor(COL_TEXT); gravity = Gravity.CENTER
            background = ColorDrawable(COL_BTN); isClickable = true
            setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6))
            setOnClickListener { if (playing) pause() else play() }
        }
        row.addView(playPauseBtn, LinearLayout.LayoutParams(dp(ctx, 44), dp(ctx, 40)))

        timeView = TextView(ctx).apply {
            text = "00:00 / 00:00"; textSize = 12f; setTextColor(COL_MUTED)
            setPadding(dp(ctx, 12), 0, dp(ctx, 12), 0)
        }
        row.addView(timeView)

        seekBar = SeekBarView(ctx).apply {
            onSeek = { f -> seekTo((durationMs() * f).toLong()) }
        }
        row.addView(seekBar, LinearLayout.LayoutParams(0, dp(ctx, 32), 1f))

        volumeBar = SeekBarView(ctx).apply {
            initialFraction = currentVolume
            onSeek = { f ->
                currentVolume = f
                applyVolume(f)
            }
        }
        row.addView(volumeBar, LinearLayout.LayoutParams(dp(ctx, 100), dp(ctx, 32)))

        panel.addView(row)
        return panel
    }

    // ─── Загрузка ────────────────────────────────────────────────────

    private fun loadVideo() {
        Thread({
            try {
                val videoMeta = MediaRegistry.video(videoId)
                if (videoMeta == null) {
                    post { showError("Видео '$videoId' не найдено") }
                    return@Thread
                }
                val decoded = Mp4Decoder.ensureDecodedAsync(videoMeta)
                    .get(120, TimeUnit.SECONDS)
                if (decoded == null) {
                    post { showError("Декодирование вернуло null") }
                    return@Thread
                }
                post { onDecoded(decoded) }
            } catch (t: Throwable) {
                ScriptFXLog.warn("UiVideoPlayerOverlay.loadVideo[bg]: ${t.message}")
                post { showError("Ошибка декодирования: ${t.message}") }
            }
        }, "ScriptFX-VideoLoad").apply { isDaemon = true }.start()
    }

    private fun onDecoded(meta: VideoMeta) {
        this.meta = meta
        this.frameFiles = meta.frames
        this.fps = if (meta.fps > 0f) meta.fps else 24f
        if (frameFiles.isEmpty()) { showError("Нет кадров"); return }

        loadingBox.visibility = View.GONE
        surface.visibility = View.VISIBLE
        controls.visibility = View.VISIBLE
        closeBtn.visibility = View.VISIBLE
        surface.setFrames(frameFiles)

        audioKey = meta.soundId
        ScriptFXLog.info("UiVideoPlayerOverlay.onDecoded: frames=${frameFiles.size} fps=$fps soundId=${meta.soundId}")

        play()
    }

    // ─── AUDIO ────────────────────────────────────────────────────────

    private fun startAudio() {
        val sid = audioKey ?: return
        if (audioPlaying) return
        val entry = MediaRegistry.sound(sid) ?: run {
            ScriptFXLog.warn("UiVideoPlayerOverlay: sound entry '$sid' not found")
            return
        }
        try {
            MediaClient.playFile(sid, entry.file, currentVolume, 1f, false, false)
            audioPlaying = true
            ScriptFXLog.info("UiVideoPlayerOverlay: audio start key=$sid vol=$currentVolume")
        } catch (t: Throwable) {
            ScriptFXLog.warn("UiVideoPlayerOverlay: audio start failed: ${t.message}")
        }
    }

    private fun stopAudio() {
        val sid = audioKey ?: return
        if (!audioPlaying) return
        try {
            MediaClient.stopSound(sid)
            audioPlaying = false
            ScriptFXLog.info("UiVideoPlayerOverlay: audio stop")
        } catch (t: Throwable) {
            ScriptFXLog.warn("UiVideoPlayerOverlay: audio stop failed: ${t.message}")
        }
    }

    private fun applyVolume(volume: Float) {
        val sid = audioKey ?: return
        ScriptFXLog.info("UiVideoPlayerOverlay.applyVolume: $volume (audioPlaying=$audioPlaying)")
        if (!audioPlaying) return
        MediaClient.setVolume(sid, volume)
    }

    // ─── Управление ──────────────────────────────────────────────────

    private fun play() {
        if (frameFiles.isEmpty()) return
        ScriptFXLog.info("UiVideoPlayerOverlay.play: pos=${basePositionMs}ms")
        playing = true
        startMs = System.currentTimeMillis()

        val sid = audioKey
        if (sid != null && audioPlaying) {
            MediaClient.resumeSound(sid, currentVolume)
        } else {
            startAudio()
        }

        post(ticker)
        updatePlayPause()
        showUi()
    }

    private fun pause() {
        if (!playing) return
        playing = false
        // Сохраняем текущую позицию — иначе play() сбросит на 0.
        basePositionMs += System.currentTimeMillis() - startMs
        ScriptFXLog.info("UiVideoPlayerOverlay.pause: pos=${basePositionMs}ms")
        removeCallbacks(ticker)
        audioKey?.let { MediaClient.pauseSound(it) }
        updatePlayPause()
        showUi()
    }

    private fun seekTo(ms: Long) {
        basePositionMs = ms.coerceAtLeast(0)
        startMs = System.currentTimeMillis()
        ScriptFXLog.info("UiVideoPlayerOverlay.seekTo: pos=${basePositionMs}ms")
        if (audioPlaying) {
            stopAudio()
            if (playing) startAudio()
        }
        if (playing) { removeCallbacks(ticker); post(ticker) }
    }

    private fun durationMs(): Long = (frameFiles.size / fps * 1000).toLong().coerceAtLeast(1)

    private fun updateSeekUi(posMs: Long) {
        val dur = durationMs()
        seekBar.fraction = posMs.toFloat() / dur
        timeView.text = "${fmt(posMs)} / ${fmt(dur)}"
    }

    private fun fmt(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60)
    }

    private fun updatePlayPause() {
        playPauseBtn.text = if (playing) "⏸" else "▶"
    }

    private fun showError(message: String) {
        loadingBox.visibility = View.GONE
        controls.visibility = View.GONE
        closeBtn.visibility = View.GONE
        surface.visibility = View.GONE
        errorView.text = message
        errorView.visibility = View.VISIBLE
        ScriptFXLog.warn("UiVideoPlayerOverlay: $message")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == 111) { dismiss(); return true }
        return super.onKeyDown(keyCode, event)
    }

    private fun dismiss() {
        if (closed) return
        closed = true
        hideRunnable?.let { removeCallbacks(it) }
        removeCallbacks(ticker)
        stopAudio()
        try { surface.release() } catch (_: Throwable) {}
        onClose(this)
    }
}