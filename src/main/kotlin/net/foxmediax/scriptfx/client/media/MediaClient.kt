package net.foxmediax.scriptfx.client.media

import net.foxmediax.scriptfx.media.MediaRegistry
import net.foxmediax.scriptfx.network.MediaPayload
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.Minecraft
import net.minecraft.client.OptionInstance
import net.minecraft.sounds.SoundSource
import java.io.File

object MediaClient {

    private val sounds = linkedMapOf<String, FileSoundInstance>()

    /** Пауза кастомных FileSoundInstance. */
    @Volatile
    private var soundsPaused: Boolean = false

    private var savedVolumes: Map<SoundSource, Double>? = null
    private var videoMuteActive = false

    fun handle(payload: MediaPayload) {
        when (payload.action) {
            MediaPayload.SOUND_PLAY -> playSound(payload)
            MediaPayload.SOUND_STOP -> stopSound(payload.id)
            MediaPayload.SOUND_STOP_ALL -> stopAllSounds()

            MediaPayload.VIDEO_PLAY -> {
                if (!payload.preview) beginVideoMute()
                VideoPlayer.play(payload)
            }
            MediaPayload.VIDEO_STOP -> {
                VideoPlayer.stop(payload.id)
                if (!VideoPlayer.hasActive()) endVideoMute()
            }
            MediaPayload.VIDEO_STOP_ALL -> {
                VideoPlayer.stopAll()
                endVideoMute()
            }
        }
    }

    private fun playSound(payload: MediaPayload) {
        val entry = MediaRegistry.sound(payload.id)
        if (entry == null) {
            ScriptFXLog.warn("MediaClient: sound '${payload.id}' not found")
            return
        }
        playFile(payload.id, entry.file, payload.volume, payload.pitch, payload.loop)
    }

    fun playFile(
        key: String,
        file: File,
        volume: Float = 1f,
        pitch: Float = 1f,
        loop: Boolean = false,
        videoSlider: Boolean = false
    ) {
        if (!file.isFile || file.length() <= 0L) {
            ScriptFXLog.warn("MediaClient: playFile missing/empty ${file.path}")
            return
        }
        stopSound(key)

        val inst = FileSoundInstance(file, volume, pitch, loop, useVideoSlider = videoSlider)
        sounds[key] = inst
        if (soundsPaused) {
            runCatching { inst.setPaused(true) }
        }

        val mc = Minecraft.getInstance()
        val run = Runnable {
            try {
                mc.soundManager.play(inst)
                ScriptFXLog.info(
                    "MediaClient: playFile key=$key videoSlider=$videoSlider size=${file.length()}"
                )
            } catch (t: Throwable) {
                ScriptFXLog.warn("MediaClient: play failed: ${t.message}")
            }
        }
        if (try {
                mc.isSameThread
            } catch (_: Throwable) {
                true
            }
        ) {
            run.run()
        } else {
            mc.execute(run)
        }
    }

    fun applyVideoVolumeToPlaying() {
        sounds.values.distinct().forEach { it.refreshVolume() }
    }

    fun pauseSounds() {
        soundsPaused = true
        sounds.values.distinct().forEach {
            runCatching { it.setPaused(true) }
        }
    }

    fun resumeSounds() {
        soundsPaused = false
        sounds.values.distinct().forEach {
            runCatching { it.setPaused(false) }
        }
    }

    fun stopSound(id: String) {
        val key = id.trim()
        if (key.isEmpty()) return
        val inst = sounds.remove(key)
        sounds.entries.removeIf { it.value === inst }
        if (inst != null) {
            try {
                Minecraft.getInstance().soundManager.stop(inst)
            } catch (_: Throwable) {
            }
        }
    }

    private fun stopAllSounds() {
        val all = sounds.values.distinct()
        sounds.clear()
        val sm = Minecraft.getInstance().soundManager
        all.forEach {
            try {
                sm.stop(it)
            } catch (_: Throwable) {
            }
        }
    }

    fun beginVideoMute() {
        if (videoMuteActive) return
        val opts = Minecraft.getInstance().options
        val map = HashMap<SoundSource, Double>()
        for (src in SoundSource.entries) {
            if (src == SoundSource.MASTER) continue
            val opt = soundOption(opts, src) ?: continue
            map[src] = opt.get()
            opt.set(0.0)
        }
        savedVolumes = map
        videoMuteActive = true
        ScriptFXLog.info("MediaClient: video mute ON")
    }

    fun endVideoMute() {
        if (!videoMuteActive) return
        val opts = Minecraft.getInstance().options
        savedVolumes?.forEach { (src, value) ->
            soundOption(opts, src)?.set(value)
        }
        savedVolumes = null
        videoMuteActive = false
        ScriptFXLog.info("MediaClient: video mute OFF")
    }

    @Suppress("UNCHECKED_CAST")
    private fun soundOption(opts: Any, src: SoundSource): OptionInstance<Double>? {
        return try {
            val m = opts.javaClass.methods.firstOrNull { method ->
                method.name == "getSoundSourceOption" &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0].isAssignableFrom(SoundSource::class.java)
            }
            if (m != null) {
                m.isAccessible = true
                return m.invoke(opts, src) as? OptionInstance<Double>
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    fun clear() {
        stopAllSounds()
        soundsPaused = false
        VideoPlayer.stopAll()
        endVideoMute()
    }
}