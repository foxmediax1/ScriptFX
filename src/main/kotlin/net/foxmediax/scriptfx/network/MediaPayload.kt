package net.foxmediax.scriptfx.network

import io.netty.buffer.ByteBuf
import net.foxmediax.scriptfx.ScriptFX
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Сервер → клиент (медиа).
 *
 * action:
 *   sound_play | sound_stop | sound_stop_all
 *   video_play | video_stop | video_stop_all
 *
 * id — ключ из MediaRegistry (beep или project/beep)
 * volume / pitch / loop — для звука
 * x,y,w,h — область видео в долях экрана 0..1; w=0 и h=0 → fullscreen
 * preview — true = превью в UI (без mute; spectator только на сервере при обычном video_play)
 */
data class MediaPayload(
    val action: String,
    val id: String,
    val volume: Float,
    val pitch: Float,
    val loop: Boolean,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val preview: Boolean
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        const val SOUND_PLAY = "sound_play"
        const val SOUND_STOP = "sound_stop"
        const val SOUND_STOP_ALL = "sound_stop_all"
        const val VIDEO_PLAY = "video_play"
        const val VIDEO_STOP = "video_stop"
        const val VIDEO_STOP_ALL = "video_stop_all"

        val TYPE: CustomPacketPayload.Type<MediaPayload> =
            CustomPacketPayload.Type(ScriptFX.id("media"))

        val CODEC: StreamCodec<ByteBuf, MediaPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, MediaPayload::action,
            ByteBufCodecs.STRING_UTF8, MediaPayload::id,
            ByteBufCodecs.FLOAT, MediaPayload::volume,
            ByteBufCodecs.FLOAT, MediaPayload::pitch,
            ByteBufCodecs.BOOL, MediaPayload::loop,
            ByteBufCodecs.FLOAT, MediaPayload::x,
            ByteBufCodecs.FLOAT, MediaPayload::y,
            ByteBufCodecs.FLOAT, MediaPayload::w,
            ByteBufCodecs.FLOAT, MediaPayload::h,
            ByteBufCodecs.BOOL, MediaPayload::preview,
            ::MediaPayload
        )

        fun soundPlay(id: String, volume: Float = 1f, pitch: Float = 1f, loop: Boolean = false) =
            MediaPayload(SOUND_PLAY, id, volume, pitch, loop, 0f, 0f, 0f, 0f, false)

        fun soundStop(id: String) =
            MediaPayload(SOUND_STOP, id, 1f, 1f, false, 0f, 0f, 0f, 0f, false)

        fun soundStopAll() =
            MediaPayload(SOUND_STOP_ALL, "", 1f, 1f, false, 0f, 0f, 0f, 0f, false)

        fun videoPlay(
            id: String,
            x: Float = 0f, y: Float = 0f, w: Float = 0f, h: Float = 0f,
            preview: Boolean = false
        ) = MediaPayload(VIDEO_PLAY, id, 1f, 1f, false, x, y, w, h, preview)

        fun videoStop(id: String) =
            MediaPayload(VIDEO_STOP, id, 1f, 1f, false, 0f, 0f, 0f, 0f, false)

        fun videoStopAll() =
            MediaPayload(VIDEO_STOP_ALL, "", 1f, 1f, false, 0f, 0f, 0f, 0f, false)
    }
}