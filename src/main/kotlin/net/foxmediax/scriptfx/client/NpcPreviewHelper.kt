package net.foxmediax.scriptfx.client

import net.foxmediax.scriptfx.npc.NpcDefinition
import net.foxmediax.scriptfx.npc.NpcEntities
import net.foxmediax.scriptfx.npc.ScriptNpcEntity
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
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

    /**
     * Анимации стандартных моделей ScriptFX
     * (совпадают с ScriptNpcEntity.KNOWN_ANIMS).
     */
    val DEFAULT_PREVIEW_ANIMS = listOf(
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

    /** Текущий список анимаций для UI-кнопок. */
    @Volatile
    var previewAnims: List<String> = DEFAULT_PREVIEW_ANIMS
        private set

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

    @Volatile
    var currentAnim: String = "idle"
        private set

    private var previewEntity: ScriptNpcEntity? = null
    private var boundKey: String? = null

    @Volatile
    var loadError: String? = null
        private set

    @Volatile
    var suppressRender: Boolean = false

    fun setPreviewAnims(anims: List<String>) {
        previewAnims = if (anims.isEmpty()) DEFAULT_PREVIEW_ANIMS else anims.distinct()
    }

    fun resetPreviewAnims() {
        previewAnims = DEFAULT_PREVIEW_ANIMS
    }

    fun setDefinition(def: NpcDefinition?) {
        if (definition?.id != def?.id) {
            angleX = 0.4f
            angleY = -0.2f
        }

        definition = def
        loadError = null

        if (def == null) {
            previewEntity = null
            boundKey = null
            currentAnim = "idle"
            resetPreviewAnims()
            return
        }

        previewEntity = null
        boundKey = null
        currentAnim = def.defaultAnim.ifBlank { "idle" }

        // customs — свободные имена; стандарт — только KNOWN_ANIMS
        if (def.model.startsWith("customs:", ignoreCase = true)) {
            val base = linkedSetOf("idle")
            if (def.defaultAnim.isNotBlank()) base += def.defaultAnim
            setPreviewAnims(base.toList())
        } else {
            resetPreviewAnims()
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
        loadError = null
        angleX = 0.4f
        angleY = -0.2f
        currentAnim = "idle"
        resetPreviewAnims()
    }

    fun drag(dx: Float, dy: Float) {
        angleX += dx * 0.01f
        angleY = (angleY + dy * 0.01f).coerceIn(-1.0f, 1.0f)
    }

    fun resetCamera() {
        angleX = 0.4f
        angleY = -0.2f
    }

    fun playAnim(name: String) {
        val anim = name.ifBlank { "idle" }
        currentAnim = anim

        val entity = previewEntity
        if (entity != null) {
            try {
                entity.setAnim(anim)
            } catch (t: Throwable) {
                ScriptFXLog.warn("NpcPreview: setAnim('$anim') failed: ${t.message}")
            }
            return
        }

        // entity ещё нет — применится при создании
        definition?.let { ensureEntity(it) }
    }

    fun cycleAnim(): String {
        val list = previewAnims.ifEmpty { DEFAULT_PREVIEW_ANIMS }
        val idx = list.indexOf(currentAnim).let { if (it < 0) 0 else it }
        val next = list[(idx + 1) % list.size]
        playAnim(next)
        return next
    }

    private fun createPreviewEntity(def: NpcDefinition): ScriptNpcEntity? {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: run {
            loadError = "Нет мира (зайди в мир для превью)"
            return null
        }

        val entity = NpcEntities.SCRIPT_NPC.create(level, EntitySpawnReason.LOAD) ?: run {
            loadError = "Не удалось создать entity"
            return null
        }

        entity.setPos(0.0, 0.0, 0.0)
        entity.xo = 0.0
        entity.yo = 0.0
        entity.zo = 0.0
        entity.tickCount = 20

        entity.npcId = def.id

        entity.modelPath = def.model.takeIf { it.isNotBlank() }
            ?: CurrentNpcResources.DEFAULT_MODEL

        entity.texturePath = def.texture.takeIf { it.isNotBlank() }
            ?: CurrentNpcResources.DEFAULT_TEXTURE

        entity.animationPath = def.animation.takeIf { it.isNotBlank() }
            ?: CurrentNpcResources.DEFAULT_ANIMATION

        entity.entityData.set(ScriptNpcEntity.DATA_NPC_ID, entity.npcId)
        entity.entityData.set(ScriptNpcEntity.DATA_MODEL, entity.modelPath)
        entity.entityData.set(ScriptNpcEntity.DATA_TEXTURE, entity.texturePath)
        entity.entityData.set(ScriptNpcEntity.DATA_ANIM_PATH, entity.animationPath)

        val anim = currentAnim.ifBlank { def.defaultAnim.ifBlank { "idle" } }
        currentAnim = anim
        entity.setAnim(anim)

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
            entity.customName = Component.literal(def.displayName)
            entity.isCustomNameVisible = true
        } else {
            entity.customName = null
            entity.isCustomNameVisible = false
        }

        ScriptFXLog.info(
            "NpcPreview: created entity for ${def.id}, model=${entity.modelPath}, " +
                    "tex=${entity.texturePath}, animFile=${entity.animationPath}, play=$anim"
        )
        loadError = null
        return entity
    }

    private fun ensureEntity(def: NpcDefinition): ScriptNpcEntity? {
        val key = listOf(
            def.id,
            def.model,
            def.texture,
            def.animation,
            def.defaultAnim,
            def.displayName,
            def.nametag,
            currentAnim
        ).joinToString("|")

        if (previewEntity != null && boundKey == key) {
            return previewEntity
        }

        // если сменилась только анимация — не пересоздаём entity
        val softKey = listOf(
            def.id, def.model, def.texture, def.animation, def.defaultAnim, def.displayName, def.nametag
        ).joinToString("|")
        if (previewEntity != null && boundKey?.substringBeforeLast("|") == softKey) {
            try {
                previewEntity?.setAnim(currentAnim)
            } catch (_: Throwable) {
            }
            boundKey = key
            return previewEntity
        }

        val created = createPreviewEntity(def) ?: return null
        previewEntity = created
        boundKey = key
        return created
    }

    fun renderInGui(graphics: GuiGraphicsExtractor) {
        if (suppressRender) return
        val def = definition ?: return
        val b = bounds ?: return
        val entity = ensureEntity(def) ?: return

        val width = b.x1 - b.x0
        val height = b.y1 - b.y0
        if (width <= 16 || height <= 16) return

        val mc = Minecraft.getInstance()
        val sw = mc.window.guiScaledWidth
        val sh = mc.window.guiScaledHeight
        if (width > sw * 0.55f || height > sh * 0.85f) return

        val size = (minOf(width, height) * 0.38f)
            .toInt()
            .coerceIn(32, 72)

        val cx = (b.x0 + b.x1) / 2f
        val cy = (b.y0 + b.y1) / 2f + height * 0.05f

        val mouseX = cx - angleX * 40f
        val mouseY = cy - angleY * 40f

        try {
            CurrentNpcResources.apply(entity)

            graphics.enableScissor(b.x0, b.y0, b.x1, b.y1)
            try {
                InventoryScreen.extractEntityInInventoryFollowsMouse(
                    graphics,
                    b.x0,
                    b.y0,
                    b.x1,
                    b.y1,
                    size,
                    0.0625f,
                    mouseX,
                    mouseY,
                    entity
                )
            } finally {
                graphics.disableScissor()
            }
        } catch (t: Throwable) {
            loadError = t.message ?: "Ошибка рендера"
            ScriptFXLog.error("NpcPreview render failed", t)
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