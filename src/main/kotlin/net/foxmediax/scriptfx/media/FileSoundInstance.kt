package net.foxmediax.scriptfx.client.media

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
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import javax.sound.sampled.AudioFormat

/**
 * Звук из файла (OGG).
 *
 * @param startOffsetMs  с какой позиции начинать воспроизведение (seek)
 * @param useVideoSlider умножать на ScriptFXConfig.videoVolume
 */
class FileSoundInstance(
    private val file: File,
    volume: Float,
    pitch: Float,
    private val shouldLoop: Boolean,
    private val useVideoSlider: Boolean = false,
    private val startOffsetMs: Long = 0L,
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

    fun isPaused(): Boolean = mutedByPause

    private fun syncChannelVolume() {
        val client = Minecraft.getInstance() ?: return
        try {
            client.soundManager.refreshCategoryVolume(SoundSource.MASTER)
        } catch (_: Throwable) {
        }
    }

    override fun getAudioStream(
        loader: SoundBufferLibrary,
        id: Identifier,
        repeatInstantly: Boolean
    ): CompletableFuture<AudioStream> {
        val offsetMs = startOffsetMs.coerceAtLeast(0L)
        return CompletableFuture.supplyAsync {
            try {
                if (!file.isFile) throw IllegalStateException("missing ${file.absolutePath}")
                val raw = JOrbisAudioStream(BufferedInputStream(FileInputStream(file)))
                if (offsetMs <= 0L) {
                    raw
                } else {
                    SkippingAudioStream(raw, offsetMs)
                }
            } catch (t: Throwable) {
                ScriptFXLog.warn("FileSoundInstance: ${file.name}: ${t.message}")
                throw t
            }
        }
    }
}

/**
 * Пропускает [skipMs] миллисекунд PCM из [inner] (API 26.1: read(expectedSize): ByteBuffer).
 */
private class SkippingAudioStream(
    private val inner: AudioStream,
    skipMs: Long,
) : AudioStream {

    private val format: AudioFormat = inner.format
    private var bytesRemainingToSkip: Long

    init {
        val frameSize = format.frameSize.coerceAtLeast(1)
        val frameRate = format.frameRate.coerceAtLeast(1f)
        val framesToSkip = (skipMs / 1000.0 * frameRate).toLong().coerceAtLeast(0L)
        bytesRemainingToSkip = framesToSkip * frameSize

        // Skip сразу при создании потока
        while (bytesRemainingToSkip > 0) {
            val chunk = minOf(bytesRemainingToSkip, 8192L).toInt()
            val buf = try {
                inner.read(chunk)
            } catch (_: Throwable) {
                null
            }
            if (buf == null || !buf.hasRemaining()) {
                bytesRemainingToSkip = 0
                break
            }
            bytesRemainingToSkip -= buf.remaining().toLong()
        }
    }

    override fun getFormat(): AudioFormat = format

    /**
     * Minecraft 26.1: AudioStream.read(expectedSize: Int): ByteBuffer
     */
    override fun read(expectedSize: Int): ByteBuffer {
        // На случай, если skip не доехал в init
        while (bytesRemainingToSkip > 0) {
            val chunk = minOf(bytesRemainingToSkip, expectedSize.toLong().coerceAtLeast(1L)).toInt()
            val skipped = try {
                inner.read(chunk)
            } catch (_: Throwable) {
                ByteBuffer.allocate(0)
            }
            if (!skipped.hasRemaining()) {
                bytesRemainingToSkip = 0
                break
            }
            bytesRemainingToSkip -= skipped.remaining().toLong()
        }
        return inner.read(expectedSize)
    }

    override fun close() {
        try {
            inner.close()
        } catch (_: Throwable) {
        }
    }
}