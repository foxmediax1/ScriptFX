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
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import java.util.UUID
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
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

    /**
     * Игрок, с которым NPC ведёт диалог.
     * Только сервер, не сохраняется.
     */
    @Volatile
    var dialogFocus: UUID? = null

    companion object {

        val DATA_ANIM: EntityDataAccessor<String> =
            SynchedEntityData.defineId(
                ScriptNpcEntity::class.java,
                EntityDataSerializers.STRING
            )

        val DATA_INVULN: EntityDataAccessor<Boolean> =
            SynchedEntityData.defineId(
                ScriptNpcEntity::class.java,
                EntityDataSerializers.BOOLEAN
            )

        val DATA_LOOK: EntityDataAccessor<Boolean> =
            SynchedEntityData.defineId(
                ScriptNpcEntity::class.java,
                EntityDataSerializers.BOOLEAN
            )

        val DATA_MODE: EntityDataAccessor<String> =
            SynchedEntityData.defineId(
                ScriptNpcEntity::class.java,
                EntityDataSerializers.STRING
            )

        fun createAttributes(): AttributeSupplier.Builder =
            createMobAttributes()
                .add(
                    Attributes.MAX_HEALTH,
                    20.0
                )
                .add(
                    Attributes.MOVEMENT_SPEED,
                    0.25
                )
                .add(
                    Attributes.FOLLOW_RANGE,
                    16.0
                )

        // Базовые анимации для встроенной модели — fallback, если кастомная не найдена
        private val KNOWN_ANIMS =
            setOf(
                "idle",
                "running",
                "sprinting",
                "crouching_idle",
                "crouching",
                "swipe",
                "jump",
                "land",
                "stun1",
                "stun1_idle",
                "stun1_gettingUp",
                "lying_idle",
                "lying_running"
            )

        fun sanitizeAnim(
            name: String,
            isCustomModel: Boolean = false
        ): String {
            val normalized = name.ifBlank { "idle" }

            // Для кастомных моделей не ограничиваем — пусть GeckoLib сам найдёт в файле
            if (isCustomModel) return normalized

            // Для встроенной модели проверяем список
            return if (normalized in KNOWN_ANIMS) normalized else "idle"
        }
    }

    /** Цель ходьбы (только сервер). null = стоит. */
    @Volatile var walkTargetX: Double? = null
    @Volatile var walkTargetY: Double? = null
    @Volatile var walkTargetZ: Double? = null
    @Volatile var walkSpeed: Float = 0.25f
    @Volatile var walkAnimName: String = "running"
    @Volatile var idleAnimName: String = "idle"

    override fun defineSynchedData(
        builder: SynchedEntityData.Builder
    ) {
        super.defineSynchedData(builder)

        builder.define(
            DATA_ANIM,
            "idle"
        )

        builder.define(
            DATA_INVULN,
            false
        )

        builder.define(
            DATA_LOOK,
            true
        )

        builder.define(
            DATA_MODE,
            NpcMode.INTERACT.id
        )
    }

    fun setAnim(
        name: String
    ) {
        // Определяем, кастомная ли модель
        val isCustom = NpcCustoms.isCustoms(modelPath)
        entityData.set(
            DATA_ANIM,
            sanitizeAnim(name, isCustom)
        )
    }

    fun currentAnim(): String =
        entityData.get(DATA_ANIM)

    fun setInvulnerableFlag(
        value: Boolean
    ) {
        entityData.set(
            DATA_INVULN,
            value
        )

        isInvulnerable = value
    }

    fun setLookAtPlayer(
        value: Boolean
    ) {
        entityData.set(
            DATA_LOOK,
            value
        )
    }

    override fun isInvulnerableTo(
        level: ServerLevel,
        source: DamageSource
    ): Boolean =
        entityData.get(DATA_INVULN) ||
                super.isInvulnerableTo(
                    level,
                    source
                )

    override fun tick() {

        super.tick()

        if (level().isClientSide) {
            return
        }

        // --- ходьба ---
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
                val nx = dx / dist * sp
                val ny = dy / dist * sp
                val nz = dz / dist * sp
                setPos(x + nx, y + ny, z + nz)
                // поворот по направлению
                val yaw = Math.toDegrees(kotlin.math.atan2(-dx, dz)).toFloat()
                yRot = yaw
                yBodyRot = yaw
                yHeadRot = yaw
                if (currentAnim() != walkAnimName) setAnim(walkAnimName)
            }
            return // пока идём — не lookAt игрока
        }

        val focusId = dialogFocus

        if (focusId != null) {

            val player =
                (level() as ServerLevel)
                    .getPlayerByUUID(focusId)

            if (player != null) {

                lookAt(
                    player,
                    45f,
                    45f
                )

                yHeadRot = yRot
                yBodyRot = yRot

                return
            }
        }

        if (
            entityData.get(DATA_LOOK)
        ) {

            val player =
                level().getNearestPlayer(
                    this,
                    12.0
                ) ?: return

            lookAt(
                player,
                30f,
                30f
            )
        }
    }

    override fun registerControllers(
        controllers: AnimatableManager.ControllerRegistrar
    ) {
        controllers.add(
            AnimationController<ScriptNpcEntity>(
                "main"
            ) { state: AnimationTest<ScriptNpcEntity> ->

                val animName = currentAnim()
                val animation = RawAnimation.begin()
                    .thenLoop(animName)

                state.setAndContinue(animation)
            }
        )
    }

    override fun getAnimatableInstanceCache():
            AnimatableInstanceCache =
        cache

    override fun addAdditionalSaveData(
        output: ValueOutput
    ) {

        super.addAdditionalSaveData(
            output
        )

        output.putString(
            "NpcId",
            npcId
        )

        output.putString(
            "Model",
            modelPath
        )

        output.putString(
            "Texture",
            texturePath
        )

        output.putString(
            "AnimationFile",
            animationPath
        )

        output.putString(
            "Anim",
            currentAnim()
        )

        output.putBoolean(
            "Invuln",
            entityData.get(DATA_INVULN)
        )

        output.putBoolean(
            "Look",
            entityData.get(DATA_LOOK)
        )

        output.putString(
            "Mode",
            entityData.get(DATA_MODE)
        )
    }

    override fun readAdditionalSaveData(
        input: ValueInput
    ) {

        super.readAdditionalSaveData(
            input
        )

        npcId =
            input.getStringOr(
                "NpcId",
                ""
            )

        modelPath =
            input.getStringOr(
                "Model",
                modelPath
            )

        texturePath =
            input.getStringOr(
                "Texture",
                texturePath
            )

        animationPath =
            input.getStringOr(
                "AnimationFile",
                animationPath
            )

        setAnim(
            input.getStringOr(
                "Anim",
                "idle"
            )
        )

        setInvulnerableFlag(
            input.getBooleanOr(
                "Invuln",
                false
            )
        )

        setLookAtPlayer(
            input.getBooleanOr(
                "Look",
                true
            )
        )

        setMode(
            NpcMode.from(
                input.getStringOr(
                    "Mode",
                    NpcMode.INTERACT.id
                )
            )
        )
    }

    override fun getTypeName(): Component =
        customName
            ?: Component.literal(
                npcId.ifBlank {
                    "NPC"
                }
            )

    fun setMode(
        mode: NpcMode
    ) {
        entityData.set(
            DATA_MODE,
            mode.id
        )
    }

    fun currentMode(): NpcMode =
        NpcMode.from(
            entityData.get(DATA_MODE)
        )

    fun startWalk(x: Double, y: Double, z: Double, speed: Float = 0.25f, anim: String = "running") {
        walkTargetX = x
        walkTargetY = y
        walkTargetZ = z
        walkSpeed = speed.coerceIn(0.05f, 1.5f)
        walkAnimName = anim.ifBlank { "running" }
        setAnim(walkAnimName)
        dialogFocus = null // пока идёт — не смотрит в диалог
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
        // чтобы клиент видел
        setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0f)
    }

    fun clearHeldItem() {
        setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
    }

    fun dropItems(stack: ItemStack, count: Int) {
        if (count <= 0 || stack.isEmpty) return
        val level = level()
        if (level.isClientSide) return
        val n = count.coerceIn(0, 100)
        var left = n
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