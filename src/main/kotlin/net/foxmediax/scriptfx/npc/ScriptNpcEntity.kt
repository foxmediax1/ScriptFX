package net.foxmediax.scriptfx.npc

import com.geckolib.animatable.GeoEntity
import com.geckolib.animatable.instance.AnimatableInstanceCache
import com.geckolib.animation.AnimatableManager
import com.geckolib.animation.AnimationController
import com.geckolib.animation.PlayState
import com.geckolib.animation.RawAnimation
import com.geckolib.util.GeckoLibUtil
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.Level

class ScriptNpcEntity(
    type: EntityType<out ScriptNpcEntity>,
    level: Level
) : Mob(type, level), GeoEntity {

    private val cache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    var npcId: String = ""
    var modelPath: String = "scriptfx:geo/female_models.geo.json"
    var texturePath: String = "scriptfx:textures/npc/temple_skins.png"
    var animationPath: String = "scriptfx:animations/female_models.animation.json"

    companion object {
        val DATA_ANIM: EntityDataAccessor<String> =
            SynchedEntityData.defineId(ScriptNpcEntity::class.java, EntityDataSerializers.STRING)
        val DATA_INVULN: EntityDataAccessor<Boolean> =
            SynchedEntityData.defineId(ScriptNpcEntity::class.java, EntityDataSerializers.BOOLEAN)
        val DATA_LOOK: EntityDataAccessor<Boolean> =
            SynchedEntityData.defineId(ScriptNpcEntity::class.java, EntityDataSerializers.BOOLEAN)

        fun createAttributes(): AttributeSupplier.Builder =
            createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 16.0)
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(DATA_ANIM, "idle")
        builder.define(DATA_INVULN, false)
        builder.define(DATA_LOOK, true)
    }

    fun setAnim(name: String) {
        entityData.set(DATA_ANIM, name)
    }

    fun currentAnim(): String = entityData.get(DATA_ANIM)

    fun setInvulnerableFlag(v: Boolean) {
        entityData.set(DATA_INVULN, v)
        isInvulnerable = v
    }

    fun setLookAtPlayer(v: Boolean) {
        entityData.set(DATA_LOOK, v)
    }

    override fun isInvulnerableTo(source: DamageSource): Boolean {
        return entityData.get(DATA_INVULN) || super.isInvulnerableTo(source)
    }

    override fun tick() {
        super.tick()
        if (!level().isClientSide && entityData.get(DATA_LOOK)) {
            val player = level().getNearestPlayer(this, 12.0) ?: return
            lookAt(player, 30f, 30f)
        }
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController(this, "main", 5) { state ->
                val anim = currentAnim().ifBlank { "idle" }
                state.controller.setAnimation(RawAnimation.begin().thenLoop(anim))
                PlayState.CONTINUE
            }
        )
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    override fun addAdditionalSaveData(tag: CompoundTag) {
        super.addAdditionalSaveData(tag)
        tag.putString("NpcId", npcId)
        tag.putString("Model", modelPath)
        tag.putString("Texture", texturePath)
        tag.putString("AnimationFile", animationPath)
        tag.putString("Anim", currentAnim())
        tag.putBoolean("Invuln", entityData.get(DATA_INVULN))
        tag.putBoolean("Look", entityData.get(DATA_LOOK))
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag)
        npcId = tag.getString("NpcId")
        modelPath = tag.getString("Model").ifBlank { modelPath }
        texturePath = tag.getString("Texture").ifBlank { texturePath }
        animationPath = tag.getString("AnimationFile").ifBlank { animationPath }
        setAnim(tag.getString("Anim").ifBlank { "idle" })
        setInvulnerableFlag(tag.getBoolean("Invuln"))
        setLookAtPlayer(tag.getBoolean("Look"))
        if (npcId.isNotBlank() && !level().isClientSide) {
            // восстановить runtime после релога мира
            NpcRuntime.get(npcId)?.let { /* already tracked */ }
                ?: run {
                    // можно заново положить в NpcRuntime при необходимости
                }
        }
    }

    override fun getTypeName(): Component =
        if (customName != null) customName!! else Component.literal(npcId.ifBlank { "NPC" })
}