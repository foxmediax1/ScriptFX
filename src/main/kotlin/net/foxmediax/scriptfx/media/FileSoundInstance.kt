package net.foxmediax.scriptfx.client.media

import com.mojang.authlib.minecraft.client.MinecraftClient
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance
import net.foxmediax.scriptfx.config.ScriptFXConfig
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.AudioStream
import net.minecraft.client.sounds.JOrbisAudioStream
import net.minecraft.client.sounds.SoundBufferLibrary
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundSource
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.CompletableFuture

class FileSoundInstance(
    private val file: File,
    volume: Float,
    pitch: Float,
    private val shouldLoop: Boolean,
    /** true = умножать на ScriptFXConfig.videoVolume (ролики). */
    private val useVideoSlider: Boolean = false
) : AbstractSoundInstance(
    Identifier.fromNamespaceAndPath("scriptfx", "file_stream"),
    SoundSource.MASTER,
    SoundInstance.createUnseededRandom()
), FabricSoundInstance {

    private var requestVolume = volume.coerceIn(0f, 2f)
    private var mutedByPause = false

    init {
        refreshVolume()
        this.pitch = pitch.coerceIn(0.1f, 2f)
        this.looping = shouldLoop
        this.relative = true
        this.attenuation = SoundInstance.Attenuation.NONE
    }

    fun setExternalVolume(v: Float) {
        requestVolume = v.coerceIn(0f, 2f)
        refreshVolume()
        syncChannelVolume()
    }

    fun refreshFromConfig() {
        refreshVolume()
        syncChannelVolume()
    }

    fun refreshVolume() {
        if (mutedByPause) {
            this.volume = 0f
            return
        }
        val slider = if (useVideoSlider) {
            ScriptFXConfig.videoVolume.coerceIn(0f, 1f)
        } else {
            1f
        }
        this.volume = (requestVolume * slider).coerceIn(0f, 2f)
    }

    fun setPaused(paused: Boolean) {
        mutedByPause = paused
        refreshVolume()
        syncChannelVolume()
    }

    /**
     * Пушит текущее this.volume в уже играющий Channel:
     * updateSourceVolume() перечитывает sound.getVolume() у всех звуков
     * данного SoundSource и обновляет громкость их каналов.
     */
    private fun syncChannelVolume() {
        val client = Minecraft.getInstance() ?: return
        val master = client.options.getSoundSourceVolume(SoundSource.MASTER)
        client.soundManager.refreshCategoryVolume(SoundSource.MASTER)
    }

    override fun getAudioStream(
        loader: SoundBufferLibrary,
        id: Identifier,
        repeatInstantly: Boolean
    ): CompletableFuture<AudioStream> {
        return CompletableFuture.supplyAsync {
            try {
                if (!file.isFile) throw IllegalStateException("missing ${file.absolutePath}")
                JOrbisAudioStream(BufferedInputStream(FileInputStream(file)))
            } catch (t: Throwable) {
                ScriptFXLog.warn("FileSoundInstance: ${file.name}: ${t.message}")
                throw t
            }
        }
    }
}