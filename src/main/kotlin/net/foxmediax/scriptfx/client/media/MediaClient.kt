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

    // ─── Точечное управление одним звуком ────────────────────────────

    /** Возобновление одного звука. */
    fun resumeSound(key: String) {
        val k = key.trim()
        if (k.isEmpty()) return
        val inst = sounds[k] ?: return
        runCatching {
            inst.setPaused(false)
            ScriptFXLog.info("MediaClient: resumeSound key=$k")
        }
    }

    /**
     * Пытается выставить громкость у FileSoundInstance:
     *  1. setVolume(float)         — если есть прямой сеттер
     *  2. updateVolume(float)
     *  3. поле volume + refreshVolume()
     * Если ничего не сработало — бросает исключение, чтобы вызывающий
     * код узнал, что громкость менять нельзя.
     */
    private fun applyInstanceVolume(inst: FileSoundInstance, volume: Float) {
        val cls = inst.javaClass
        ScriptFXLog.info("applyInstanceVolume: try ${cls.simpleName} vol=$volume")

        // 1. Прямые сеттеры.
        for (name in listOf("setVolume", "updateVolume", "setGain")) {
            val m = cls.methods.firstOrNull {
                it.name == name && it.parameterCount == 1 &&
                        it.parameterTypes[0] == java.lang.Float.TYPE
            } ?: continue
            m.isAccessible = true
            m.invoke(inst, volume)
            ScriptFXLog.info("applyInstanceVolume: OK via $name")
            return
        }

        // 2. Поле + refreshVolume().
        var field: java.lang.reflect.Field? = null
        var c: Class<*>? = cls
        while (c != null && c != Any::class.java) {
            field = c.declaredFields.firstOrNull {
                it.type == java.lang.Float.TYPE &&
                        (it.name == "volume" || it.name == "mVolume" || it.name == "gain")
            }
            if (field != null) break
            c = c.superclass
        }
        if (field != null) {
            field.isAccessible = true
            field.setFloat(inst, volume)
            try {
                inst.refreshVolume()
            } catch (_: Throwable) {}
            ScriptFXLog.info("applyInstanceVolume: OK via field ${field.name}")
            return
        }

        // 3. Рефлексия по интерфейсу net.minecraft.client.resources.sounds.SoundInstance.
        try {
            val m = inst.javaClass.methods.firstOrNull {
                it.name == "setVolume" && it.parameterCount == 1
            }
            if (m != null) {
                m.isAccessible = true
                m.invoke(inst, volume)
                ScriptFXLog.info("applyInstanceVolume: OK via any setVolume")
                return
            }
        } catch (_: Throwable) {}

        throw IllegalStateException(
            "FileSoundInstance: no setVolume/updateVolume, no volume field, no setVolume method"
        )
    }

    /** Меняет громкость уже играющего звука [key]. */
    fun setVolume(key: String, volume: Float) {
        val k = key.trim()
        if (k.isEmpty()) return
        val inst = sounds[k] ?: run {
            ScriptFXLog.warn("MediaClient.setVolume: no sound for key=$k")
            return
        }
        val v = volume.coerceIn(0f, 1f)
        try {
            applyInstanceVolume(inst, v)
            ScriptFXLog.info("MediaClient: setVolume key=$k vol=$v")
        } catch (t: Throwable) {
            ScriptFXLog.warn("MediaClient: setVolume failed key=$k: ${t.message}")
        }
    }

    /** Пауза одного звука. Пытаемся setPaused, затем глушим громкостью. */
    fun pauseSound(key: String) {
        val k = key.trim()
        if (k.isEmpty()) return
        val inst = sounds[k] ?: run {
            ScriptFXLog.warn("MediaClient.pauseSound: no sound for key=$k")
            return
        }
        try {
            inst.setPaused(true)
            ScriptFXLog.info("MediaClient: pauseSound key=$k (setPaused)")
        } catch (t: Throwable) {
            ScriptFXLog.warn("MediaClient.pauseSound: setPaused failed: ${t.message}")
        }
        // Страховка: в MC нет per-instance pause, поэтому глушим громкостью.
        try {
            applyInstanceVolume(inst, 0f)
            ScriptFXLog.info("MediaClient: pauseSound key=$k (volume=0)")
        } catch (t: Throwable) {
            ScriptFXLog.warn("MediaClient.pauseSound: volume mute failed: ${t.message}")
        }
    }

    /** Возобновление одного звука с восстановлением громкости. */
    fun resumeSound(key: String, volume: Float = 1f) {
        val k = key.trim()
        if (k.isEmpty()) return
        val inst = sounds[k] ?: run {
            ScriptFXLog.warn("MediaClient.resumeSound: no sound for key=$k")
            return
        }
        try {
            inst.setPaused(false)
        } catch (_: Throwable) {}
        try {
            applyInstanceVolume(inst, volume.coerceIn(0f, 1f))
            ScriptFXLog.info("MediaClient: resumeSound key=$k vol=$volume")
        } catch (t: Throwable) {
            ScriptFXLog.warn("MediaClient.resumeSound: volume restore failed: ${t.message}")
        }
    }

    /** true, если звук сейчас на паузе. */
    fun isSoundPaused(key: String): Boolean {
        val k = key.trim()
        if (k.isEmpty()) return false
        val inst = sounds[k] ?: return false
        // Пытаемся через рефлексию, чтобы не ловить ошибку компиляции,
        // если метода isPaused() в FileSoundInstance нет.
        return try {
            val m = inst.javaClass.methods.firstOrNull {
                it.name == "isPaused" && it.parameterCount == 0 &&
                        it.returnType == java.lang.Boolean.TYPE
            }
            m?.invoke(inst) as? Boolean ?: false
        } catch (_: Throwable) {
            false
        }
    }

    fun clear() {
        stopAllSounds()
        soundsPaused = false
        VideoPlayer.stopAll()
        endVideoMute()
    }
}