package net.foxmediax.scriptfx.client.media

import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance
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

/**
 * Поток .ogg с диска. Не глушит остальные звуки игры.
 */
class FileSoundInstance(
    private val file: File,
    volume: Float,
    pitch: Float,
    private val shouldLoop: Boolean
) : AbstractSoundInstance(
    Identifier.fromNamespaceAndPath("scriptfx", "file_stream"),
    SoundSource.MASTER,
    SoundInstance.createUnseededRandom()
), FabricSoundInstance {

    init {
        this.volume = volume.coerceIn(0f, 2f)
        this.pitch = pitch.coerceIn(0.1f, 2f)
        this.looping = shouldLoop
        this.relative = true
        this.attenuation = SoundInstance.Attenuation.NONE
    }

    override fun getAudioStream(
        loader: SoundBufferLibrary,
        id: Identifier,
        repeatInstantly: Boolean
    ): CompletableFuture<AudioStream> {
        return CompletableFuture.supplyAsync {
            val input = BufferedInputStream(FileInputStream(file))
            // Если JOrbisAudioStream нет — в IDEA ищи *AudioStream(InputStream)
            JOrbisAudioStream(input)
        }
    }
}