package net.foxmediax.scriptfx.npc

import com.geckolib.animatable.GeoEntity
import com.geckolib.animatable.instance.AnimatableInstanceCache
import com.geckolib.animatable.manager.AnimatableManager
import com.geckolib.animation.AnimationController
import com.geckolib.animation.RawAnimation
import com.geckolib.animation.state.AnimationTest
import com.geckolib.util.GeckoLibUtil
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import java.util.UUID
import kotlin.math.sqrt

class ScriptNpcEntity(
    type: EntityType<out ScriptNpcEntity>,
    level: Level
) : Mob(type, level), GeoEntity {

    private val cache: AnimatableInstanceCache =
        GeckoLibUtil.createInstanceCache(this)

    var npcId: String = ""
    var modelPath: String = "scriptfx:female_models"
    var texturePath: String = "scriptfx:textures/npc/temple_female_skins.png"
    var animationPath: String = "scriptfx:female_models"

    @Volatile
    var dialogFocus: UUID? = null

    companion object {

        val DATA_ANIM: EntityDataAccessor<String> =
            SynchedEntityData.defineId(ScriptNpcEntity::class.java, EntityDataSerializers.STRING)
        val DATA_INVULN: EntityDataAccessor<Boolean> =
            SynchedEntityData.defineId(ScriptNpcEntity::class.java, EntityDataSerializers.BOOLEAN)
        val DATA_LOOK: EntityDataAccessor<Boolean> =
            SynchedEntityData.defineId(ScriptNpcEntity::class.java, EntityDataSerializers.BOOLEAN)
        val DATA_MODE: EntityDataAccessor<String> =
            SynchedEntityData.defineId(ScriptNpcEntity::class.java, EntityDataSerializers.STRING)

        val DATA_NPC_ID: EntityDataAccessor<String> =
            SynchedEntityData.defineId(ScriptNpcEntity::class.java, EntityDataSerializers.STRING)
        val DATA_MODEL: EntityDataAccessor<String> =
            SynchedEntityData.defineId(ScriptNpcEntity::class.java, EntityDataSerializers.STRING)
        val DATA_TEXTURE: EntityDataAccessor<String> =
            SynchedEntityData.defineId(ScriptNpcEntity::class.java, EntityDataSerializers.STRING)
        val DATA_ANIM_PATH: EntityDataAccessor<String> =
            SynchedEntityData.defineId(ScriptNpcEntity::class.java, EntityDataSerializers.STRING)

        fun createAttributes(): AttributeSupplier.Builder =
            createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 16.0)

        private val KNOWN_ANIMS = setOf(
            "idle", "running", "sprinting", "crouching_idle", "crouching",
            "swipe", "jump", "land", "stun1", "stun1_idle", "stun1_gettingUp",
            "lying_idle", "lying_running"
        )

        fun sanitizeAnim(name: String, isCustomModel: Boolean = false): String {
            val normalized = name.ifBlank { "idle" }
            if (isCustomModel) return normalized
            return if (normalized in KNOWN_ANIMS) normalized else "idle"
        }
    }

    @Volatile var walkTargetX: Double? = null
    @Volatile var walkTargetY: Double? = null
    @Volatile var walkTargetZ: Double? = null
    @Volatile var walkSpeed: Float = 0.25f
    @Volatile var walkAnimName: String = "running"
    @Volatile var idleAnimName: String = "idle"

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(DATA_ANIM, "idle")
        builder.define(DATA_INVULN, false)
        builder.define(DATA_LOOK, true)
        builder.define(DATA_MODE, NpcMode.INTERACT.id)
        builder.define(DATA_NPC_ID, "")
        builder.define(DATA_MODEL, "scriptfx:female_models")
        builder.define(DATA_TEXTURE, "scriptfx:textures/npc/temple_female_skins.png")
        builder.define(DATA_ANIM_PATH, "scriptfx:female_models")
    }

    fun syncPathsFromData() {
        if (!level().isClientSide) return
        npcId = entityData.get(DATA_NPC_ID)
        modelPath = entityData.get(DATA_MODEL)
        texturePath = entityData.get(DATA_TEXTURE)
        animationPath = entityData.get(DATA_ANIM_PATH)
    }

    fun applyNpcId(id: String) {
        npcId = id
        if (!level().isClientSide) entityData.set(DATA_NPC_ID, id)
    }

    fun applyModelPath(v: String) {
        modelPath = v
        if (!level().isClientSide) entityData.set(DATA_MODEL, v)
    }

    fun applyTexturePath(v: String) {
        texturePath = v
        if (!level().isClientSide) entityData.set(DATA_TEXTURE, v)
    }

    fun applyAnimationPath(v: String) {
        animationPath = v
        if (!level().isClientSide) entityData.set(DATA_ANIM_PATH, v)
    }

    fun setAnim(name: String) {
        val isCustom = NpcCustoms.isCustoms(modelPath)
        entityData.set(DATA_ANIM, sanitizeAnim(name, isCustom))
    }

    fun currentAnim(): String = entityData.get(DATA_ANIM)

    fun setInvulnerableFlag(value: Boolean) {
        entityData.set(DATA_INVULN, value)
        isInvulnerable = value
    }

    fun setLookAtPlayer(value: Boolean) {
        entityData.set(DATA_LOOK, value)
    }

    override fun isInvulnerableTo(level: ServerLevel, source: DamageSource): Boolean =
        entityData.get(DATA_INVULN) || super.isInvulnerableTo(level, source)

    override fun tick() {
        super.tick()
        if (level().isClientSide) return

        val tx = walkTargetX
        val ty = walkTargetY
        val tz = walkTargetZ
        if (tx != null && ty != null && tz != null) {
            val dx = tx - x
            val dy = ty - y
            val dz = tz - z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist < 0.35) {
                setPos(tx, ty, tz)
                stopWalk(restoreIdle = true)
            } else {
                val sp = walkSpeed.toDouble()
                setPos(x + dx / dist * sp, y + dy / dist * sp, z + dz / dist * sp)
                val yaw = Math.toDegrees(kotlin.math.atan2(-dx, dz)).toFloat()
                yRot = yaw
                yBodyRot = yaw
                yHeadRot = yaw
                if (currentAnim() != walkAnimName) setAnim(walkAnimName)
            }
            return
        }

        val focusId = dialogFocus
        if (focusId != null) {
            val player = (level() as ServerLevel).getPlayerByUUID(focusId)
            if (player != null) {
                lookAt(player, 45f, 45f)
                yHeadRot = yRot
                yBodyRot = yRot
                return
            }
        }

        if (entityData.get(DATA_LOOK)) {
            val player = level().getNearestPlayer(this, 12.0) ?: return
            lookAt(player, 30f, 30f)
        }
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController("main") { state: AnimationTest<ScriptNpcEntity> ->
                state.setAndContinue(RawAnimation.begin().thenLoop(currentAnim()))
            }
        )
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    override fun addAdditionalSaveData(output: ValueOutput) {
        super.addAdditionalSaveData(output)
        output.putString("NpcId", npcId)
        output.putString("Model", modelPath)
        output.putString("Texture", texturePath)
        output.putString("AnimationFile", animationPath)
        output.putString("Anim", currentAnim())
        output.putBoolean("Invuln", entityData.get(DATA_INVULN))
        output.putBoolean("Look", entityData.get(DATA_LOOK))
        output.putString("Mode", entityData.get(DATA_MODE))
    }

    override fun readAdditionalSaveData(input: ValueInput) {
        super.readAdditionalSaveData(input)
        npcId = input.getStringOr("NpcId", "")
        modelPath = input.getStringOr("Model", modelPath)
        texturePath = input.getStringOr("Texture", texturePath)
        animationPath = input.getStringOr("AnimationFile", animationPath)
        setAnim(input.getStringOr("Anim", "idle"))
        setInvulnerableFlag(input.getBooleanOr("Invuln", false))
        setLookAtPlayer(input.getBooleanOr("Look", true))
        setMode(NpcMode.from(input.getStringOr("Mode", NpcMode.INTERACT.id)))

        if (!level().isClientSide) {
            entityData.set(DATA_NPC_ID, npcId)
            entityData.set(DATA_MODEL, modelPath)
            entityData.set(DATA_TEXTURE, texturePath)
            entityData.set(DATA_ANIM_PATH, animationPath)
        }
    }

    override fun getTypeName(): Component =
        customName ?: Component.literal(npcId.ifBlank { "NPC" })

    fun setMode(mode: NpcMode) {
        entityData.set(DATA_MODE, mode.id)
    }

    fun currentMode(): NpcMode =
        NpcMode.from(entityData.get(DATA_MODE))

    fun startWalk(x: Double, y: Double, z: Double, speed: Float = 0.25f, anim: String = "running") {
        walkTargetX = x
        walkTargetY = y
        walkTargetZ = z
        walkSpeed = speed.coerceIn(0.05f, 1.5f)
        walkAnimName = anim.ifBlank { "running" }
        setAnim(walkAnimName)
        dialogFocus = null
    }

    fun stopWalk(restoreIdle: Boolean = true) {
        walkTargetX = null
        walkTargetY = null
        walkTargetZ = null
        deltaMovement = net.minecraft.world.phys.Vec3.ZERO
        if (restoreIdle) setAnim(idleAnimName)
    }

    fun setHeldItem(stack: ItemStack) {
        setItemInHand(InteractionHand.MAIN_HAND, stack)
        setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0f)
    }

    fun clearHeldItem() {
        setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
    }

    fun dropItems(stack: ItemStack, count: Int) {
        if (count <= 0 || stack.isEmpty) return
        val level = level()
        if (level.isClientSide) return
        var left = count.coerceIn(0, 100)
        while (left > 0) {
            val take = left.coerceAtMost(stack.maxStackSize)
            val drop = stack.copy().also { it.count = take }
            val item = ItemEntity(level, x, y + 0.5, z, drop)
            item.setDefaultPickUpDelay()
            level.addFreshEntity(item)
            left -= take
        }
    }
}