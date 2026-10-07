package net.foxmediax.scriptfx.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.foxmediax.scriptfx.ScriptFX
import net.minecraft.resources.Identifier

data class NpcInteractKeyPayload(val key: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<NpcInteractKeyPayload>(
            Identifier.fromNamespaceAndPath(ScriptFX.MOD_ID, "npc_interact_key")
        )
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, NpcInteractKeyPayload> =
            StreamCodec.of(
                { buf, p -> buf.writeUtf(p.key) },
                { buf -> NpcInteractKeyPayload(buf.readUtf()) }
            )
    }
}