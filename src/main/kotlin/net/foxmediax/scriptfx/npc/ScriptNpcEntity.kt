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

class ScriptNpcEntity(
    type: EntityType<out ScriptNpcEntity>,
    level: Level
) : Mob(type, level), GeoEntity {

    private val cache: AnimatableInstanceCache =
        GeckoLibUtil.createInstanceCache(this)

    var npcId: String = ""

    var modelPath: String = "scriptfx:female_models"
    var texturePath: String = "scriptfx:textures/npc/temple_skins.png"
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
            name: String
        ): String {

            val normalized =
                name.ifBlank {
                    "idle"
                }

            return if (
                normalized in KNOWN_ANIMS
            ) {
                normalized
            } else {
                "idle"
            }
        }
    }

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
        entityData.set(
            DATA_ANIM,
            sanitizeAnim(name)
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

        /*
         * Диалог:
         * NPC всегда разворачивается к собеседнику.
         */
        val focusId =
            dialogFocus

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

        /*
         * Обычный режим LookAt.
         */
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

                val animation = RawAnimation.begin()
                    .thenLoop(sanitizeAnim(currentAnim()))

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
}