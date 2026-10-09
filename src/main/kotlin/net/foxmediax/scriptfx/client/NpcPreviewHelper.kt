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

    data class Bounds(val x0: Int, val y0: Int, val x1: Int, val y1: Int)

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

    fun setDefinition(def: NpcDefinition?) {
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

    fun setBounds(x0: Int, y0: Int, x1: Int, y1: Int) {
        if (x1 - x0 < 8 || y1 - y0 < 8) {
            bounds = null
            return
        }
        bounds = Bounds(x0, y0, x1, y1)
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

    fun drag(dx: Float, dy: Float) {
        angleX += dx * 0.01f
        angleY = (angleY + dy * 0.01f).coerceIn(-1.0f, 1.0f)
    }

    private fun ensureEntity(def: NpcDefinition): ScriptNpcEntity? {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return null

        val key = listOf(
            def.id, def.model, def.texture, def.animation,
            def.defaultAnim, def.displayName, def.nametag
        ).joinToString("|")

        val existing = previewEntity
        if (existing != null && boundKey == key) {
            CurrentNpcResources.apply(existing)
            return existing
        }

        val created = NpcEntities.SCRIPT_NPC.create(level, EntitySpawnReason.LOAD) ?: return null
        created.npcId = def.id
        created.modelPath = def.model
        created.texturePath = def.texture
        created.animationPath = def.animation
        created.setAnim(def.defaultAnim.ifBlank { "idle" })
        created.setLookAtPlayer(false)
        created.setInvulnerableFlag(true)
        created.setNoGravity(true)
        created.isNoAi = true
        created.yRot = 0f
        created.yBodyRot = 0f
        created.yHeadRot = 0f
        if (def.nametag) {
            created.customName = Component.literal(def.displayName)
            created.isCustomNameVisible = true
        } else {
            created.customName = null
            created.isCustomNameVisible = false
        }

        CurrentNpcResources.apply(created)
        previewEntity = created
        boundKey = key
        return created
    }

    /**
     * Вызывать из ScreenEvents.afterExtract — graphics экрана, поверх ModernUI.
     */
    fun renderInGui(graphics: GuiGraphicsExtractor) {
        val def = definition ?: return
        val b = bounds ?: return
        val entity = ensureEntity(def) ?: return

        entity.tickCount++

        val size = ((b.x1 - b.x0).coerceAtMost(b.y1 - b.y0) * 0.45f)
            .toInt()
            .coerceIn(40, 90)

        val cx = (b.x0 + b.x1) / 2f
        val cy = (b.y0 + b.y1) / 2f
        val mouseX = cx - angleX * 40f
        val mouseY = cy - angleY * 40f

        try {
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
}