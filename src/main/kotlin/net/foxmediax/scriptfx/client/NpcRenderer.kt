package net.foxmediax.scriptfx.client

import com.geckolib.model.GeoModel
import com.geckolib.renderer.GeoEntityRenderer
import com.geckolib.renderer.base.GeoRenderState
import net.foxmediax.scriptfx.npc.ScriptNpcEntity
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.resources.Identifier

object CurrentNpcResources {

    const val DEFAULT_MODEL = "scriptfx:female_models"
    const val DEFAULT_ANIMATION = "scriptfx:female_models"
    const val DEFAULT_TEXTURE = "scriptfx:textures/npc/temple_skins.png"

    @JvmField
    var model: String = DEFAULT_MODEL

    @JvmField
    var texture: String = DEFAULT_TEXTURE

    @JvmField
    var animation: String = DEFAULT_ANIMATION

    /**
     * GeckoLib 5: id вида namespace:name
     * (файл: assets/<ns>/geckolib/models/<name>.geo.json)
     */
    fun normalizeModelOrAnim(raw: String, fallback: String): String {
        if (raw.isBlank()) return fallback

        val ns: String
        val body: String
        if (':' in raw) {
            val parts = raw.split(':', limit = 2)
            ns = parts[0]
            body = parts[1]
        } else {
            ns = "scriptfx"
            body = raw
        }

        var p = body
            .removePrefix("geckolib/")
            .removePrefix("models/")
            .removePrefix("animations/")
            .removeSuffix(".json")
            .removeSuffix(".geo")
            .removeSuffix(".animation")

        // ещё раз на случай "...models.animation" / "...geo.json" уже частично срезанных
        p = p.removeSuffix(".animation")
        p = p.removeSuffix(".geo")

        if (p.isBlank()) return fallback
        return "$ns:$p"
    }
    fun apply(entity: ScriptNpcEntity) {
        model = normalizeModelOrAnim(entity.modelPath, DEFAULT_MODEL)
        texture = entity.texturePath.ifBlank { DEFAULT_TEXTURE }
        animation = normalizeModelOrAnim(entity.animationPath, DEFAULT_ANIMATION)
    }
}

class ScriptNpcModel : GeoModel<ScriptNpcEntity>() {

    override fun getModelResource(renderState: GeoRenderState): Identifier {
        return Identifier.parse(CurrentNpcResources.model)
    }

    override fun getTextureResource(renderState: GeoRenderState): Identifier {
        return Identifier.parse(CurrentNpcResources.texture)
    }

    override fun getAnimationResource(animatable: ScriptNpcEntity): Identifier {
        // Берём уже нормализованный путь из CurrentNpcResources
        // (apply() вызывается перед GUI-рендером и при мировом рендере нужно вызывать apply тоже)
        return Identifier.parse(CurrentNpcResources.animation)
    }
}

class ScriptNpcRenderer(
    ctx: EntityRendererProvider.Context
) : GeoEntityRenderer<ScriptNpcEntity, LivingEntityRenderState>(
    ctx,
    ScriptNpcModel()
) {
    override fun extractRenderState(
        entity: ScriptNpcEntity,
        state: LivingEntityRenderState,
        partialTick: Float
    ) {
        CurrentNpcResources.apply(entity)
        super.extractRenderState(entity, state, partialTick)
    }
}