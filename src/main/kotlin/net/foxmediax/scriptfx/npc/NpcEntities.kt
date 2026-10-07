package net.foxmediax.scriptfx.npc

import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.ai.attributes.Attributes
import net.foxmediax.scriptfx.ScriptFX

object NpcEntities {
    lateinit var SCRIPT_NPC: EntityType<ScriptNpcEntity>
        private set

    fun register() {
        SCRIPT_NPC = EntityType.Builder
            .of(::ScriptNpcEntity, MobCategory.MISC)
            .sized(0.6f, 1.8f)
            .clientTrackingRange(10)
            .updateInterval(3)
            .build()
            .also {
                Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(ScriptFX.MOD_ID, "script_npc"),
                    it
                )
            }

        FabricDefaultAttributeRegistry.register(
            SCRIPT_NPC,
            ScriptNpcEntity.createAttributes()
        )
    }
}