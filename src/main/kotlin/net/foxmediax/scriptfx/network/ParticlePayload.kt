package net.foxmediax.scriptfx.network

import io.netty.buffer.ByteBuf
import net.foxmediax.scriptfx.ScriptFX
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Сервер → клиент.
 * action: play | stop | stop_all | attach | detach
 * targetNpcId — для attach/detach (иначе "")
 * x,y,z — позиция (play) или offset (attach)
 */
data class ParticlePayload(
    val action: String,
    val effectId: String,
    val targetNpcId: String,
    val x: Double,
    val y: Double,
    val z: Double
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        const val PLAY = "play"
        const val STOP = "stop"
        const val STOP_ALL = "stop_all"
        const val ATTACH = "attach"
        const val DETACH = "detach"

        val TYPE: CustomPacketPayload.Type<ParticlePayload> =
            CustomPacketPayload.Type(ScriptFX.id("particle"))

        val CODEC: StreamCodec<ByteBuf, ParticlePayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ParticlePayload::action,
            ByteBufCodecs.STRING_UTF8, ParticlePayload::effectId,
            ByteBufCodecs.STRING_UTF8, ParticlePayload::targetNpcId,
            ByteBufCodecs.DOUBLE, ParticlePayload::x,
            ByteBufCodecs.DOUBLE, ParticlePayload::y,
            ByteBufCodecs.DOUBLE, ParticlePayload::z,
            ::ParticlePayload
        )
    }
}