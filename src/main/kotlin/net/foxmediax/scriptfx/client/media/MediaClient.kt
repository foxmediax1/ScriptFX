package net.foxmediax.scriptfx.client.media

import net.foxmediax.scriptfx.media.MediaRegistry
import net.foxmediax.scriptfx.network.MediaPayload
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.Minecraft
import net.minecraft.client.OptionInstance
import net.minecraft.sounds.SoundSource

object MediaClient {

    private val sounds = linkedMapOf<String, FileSoundInstance>()

    /** Сохранённые громкости категорий на время видео */
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
                // звук ролика из meta тоже гасим по id, если знаем
                if (!VideoPlayer.hasActive()) endVideoMute()
            }
            MediaPayload.VIDEO_STOP_ALL -> {
                VideoPlayer.stopAll()
                endVideoMute()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Sound
    // -------------------------------------------------------------------------

    private fun playSound(payload: MediaPayload) {
        MediaRegistry.reload()
        val entry = MediaRegistry.sound(payload.id)
        if (entry == null) {
            ScriptFXLog.warn("MediaClient: sound '${payload.id}' not found")
            return
        }
        if (!entry.file.isFile) {
            ScriptFXLog.warn("MediaClient: missing ${entry.file}")
            return
        }
        stopSound(payload.id)
        val inst = FileSoundInstance(
            entry.file,
            payload.volume,
            payload.pitch,
            payload.loop
        )
        sounds[payload.id] = inst
        sounds[entry.id] = inst
        Minecraft.getInstance().soundManager.play(inst)
        ScriptFXLog.info("MediaClient: play ${entry.project}/${entry.id}")
    }

    private fun stopSound(id: String) {
        val key = id.trim()
        if (key.isEmpty()) return
        val inst = sounds.remove(key)
        sounds.entries.removeIf { it.value === inst }
        if (inst != null) {
            Minecraft.getInstance().soundManager.stop(inst)
        }
    }

    private fun stopAllSounds() {
        val all = sounds.values.distinct()
        sounds.clear()
        val sm = Minecraft.getInstance().soundManager
        all.forEach { sm.stop(it) }
    }

    // -------------------------------------------------------------------------
    // Video mute: всё кроме MASTER → 0
    // FileSoundInstance на MASTER остаётся слышен
    // -------------------------------------------------------------------------

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

    /**
     * 26.1: Options.getSoundSourceOption(SoundSource) → OptionInstance<Double>
     * Если имя метода другое — поправь по IDEA (getSoundCategoryVolume / soundSourceVolumes).
     */
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
            // fallback: поле Map / array
            null
        } catch (_: Throwable) {
            null
        }
    }

    fun clear() {
        stopAllSounds()
        VideoPlayer.stopAll()
        endVideoMute()
    }
}