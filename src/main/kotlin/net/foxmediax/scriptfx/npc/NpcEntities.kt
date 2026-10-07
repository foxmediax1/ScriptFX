package net.foxmediax.scriptfx.npc

import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.foxmediax.scriptfx.ScriptFX
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory

object NpcEntities {
    lateinit var SCRIPT_NPC: EntityType<ScriptNpcEntity>
        private set

    fun register() {
        val id = Identifier.fromNamespaceAndPath(ScriptFX.MOD_ID, "script_npc")
        val key = ResourceKey.create(Registries.ENTITY_TYPE, id)

        SCRIPT_NPC = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            id,
            EntityType.Builder
                .of(::ScriptNpcEntity, MobCategory.MISC)
                .sized(0.6f, 1.8f)
                .clientTrackingRange(10)
                .updateInterval(3)
                .build(key)
        )

        FabricDefaultAttributeRegistry.register(
            SCRIPT_NPC,
            ScriptNpcEntity.createAttributes()
        )
    }
}