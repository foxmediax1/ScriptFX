package net.foxmediax.scriptfx.client

import net.foxmediax.scriptfx.npc.NpcDefinition
import net.foxmediax.scriptfx.npc.NpcEntities
import net.foxmediax.scriptfx.npc.ScriptNpcEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EntitySpawnReason

object NpcPreviewHelper {

    data class Bounds(
        val x0: Int,
        val y0: Int,
        val x1: Int,
        val y1: Int
    )

    @Volatile
    var definition: NpcDefinition? = null
        private set

    @Volatile
    var bounds: Bounds? = null
        private set

    var angleX: Float = 0.4f
        private set

    var angleY: Float = -0.2f
        private set

    private var previewEntity: ScriptNpcEntity? = null
    private var boundKey: String? = null

    fun setDefinition(
        def: NpcDefinition?
    ) {

        if (definition?.id != def?.id) {
            angleX = 0.4f
            angleY = -0.2f
        }

        definition = def

        if (def == null) {
            previewEntity = null
            boundKey = null
        }
    }

    fun setBounds(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int
    ) {

        if (
            x1 - x0 < 8 ||
            y1 - y0 < 8
        ) {
            bounds = null
            return
        }

        bounds =
            Bounds(
                x0,
                y0,
                x1,
                y1
            )
    }

    fun clearBounds() {
        bounds = null
    }

    fun clear() {
        definition = null
        bounds = null
        previewEntity = null
        boundKey = null

        angleX = 0.4f
        angleY = -0.2f
    }

    fun drag(
        dx: Float,
        dy: Float
    ) {
        angleX += dx * 0.01f

        angleY =
            (
                    angleY +
                            dy * 0.01f
                    )
                .coerceIn(
                    -1.0f,
                    1.0f
                )

    }

    private fun createPreviewEntity(
        def: NpcDefinition
    ): ScriptNpcEntity? {

        val mc =
            Minecraft.getInstance()

        val level =
            mc.level
                ?: return null

        val entity =
            NpcEntities.SCRIPT_NPC.create(
                level,
                EntitySpawnReason.LOAD
            )
                ?: return null

        entity.setPos(0.0, 0.0, 0.0)
        entity.xo = 0.0
        entity.yo = 0.0
        entity.zo = 0.0
        entity.tickCount = 20 // чтобы анимация не стартовала с 0-го кадра «пустой»

        entity.npcId =
            def.id

        entity.modelPath =
            def.model
                .takeIf { it.isNotBlank() }
                ?: CurrentNpcResources.DEFAULT_MODEL

        entity.texturePath =
            def.texture
                .takeIf { it.isNotBlank() }
                ?: CurrentNpcResources.DEFAULT_TEXTURE

        entity.animationPath =
            def.animation
                .takeIf { it.isNotBlank() }
                ?: CurrentNpcResources.DEFAULT_ANIMATION

        entity.setAnim(
            def.defaultAnim
                .ifBlank {
                    "idle"
                }
        )

        entity.setLookAtPlayer(false)

        entity.setInvulnerableFlag(true)

        entity.setNoGravity(true)

        entity.isNoAi = true

        entity.noPhysics = true

        entity.yRot = 0f
        entity.yBodyRot = 0f
        entity.yHeadRot = 0f

        entity.yRotO = 0f
        entity.yBodyRotO = 0f
        entity.yHeadRotO = 0f

        if (def.nametag) {

            entity.customName =
                Component.literal(
                    def.displayName
                )

            entity.isCustomNameVisible = true

        } else {

            entity.customName = null

            entity.isCustomNameVisible = false
        }

        return entity
    }

    private fun ensureEntity(
        def: NpcDefinition
    ): ScriptNpcEntity? {

        val key =
            listOf(
                def.id,
                def.model,
                def.texture,
                def.animation,
                def.defaultAnim,
                def.displayName,
                def.nametag
            ).joinToString("|")

        if (
            previewEntity != null &&
            boundKey == key
        ) {
            return previewEntity
        }

        val created =
            createPreviewEntity(def)
                ?: return null

        previewEntity = created
        boundKey = key

        return created
    }

    /**
     * Вызывается из ScreenEvents.afterExtract.
     *
     * ВАЖНО:
     * этот метод должен выполняться после extract ModernUI,
     * иначе ModernUI может перекрыть 3D-модель.
     */
    fun renderInGui(graphics: GuiGraphicsExtractor) {
        val def = definition ?: return
        val b = bounds ?: return
        val entity = ensureEntity(def) ?: return

        val width = b.x1 - b.x0
        val height = b.y1 - b.y0
        if (width <= 8 || height <= 8) return

        val size = (minOf(width, height) * 0.45f)
            .toInt()
            .coerceIn(40, 90)

        val cx = (b.x0 + b.x1) / 2f
        val cy = (b.y0 + b.y1) / 2f

        val mouseX = cx - angleX * 40f
        val mouseY = cy - angleY * 40f

        try {
            CurrentNpcResources.apply(entity)

            InventoryScreen.extractEntityInInventoryFollowsMouse(
                graphics,
                b.x0,
                b.y0,
                b.x1,
                b.y1,
                size,
                0.0f,
                mouseX,
                mouseY,
                entity
            )
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun clientTick() {
        val entity = previewEntity ?: return
        entity.tickCount++
        entity.yRotO = entity.yRot
        entity.yBodyRotO = entity.yBodyRot
        entity.yHeadRotO = entity.yHeadRot
    }
}