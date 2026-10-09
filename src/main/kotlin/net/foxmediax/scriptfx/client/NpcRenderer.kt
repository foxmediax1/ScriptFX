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
    const val DEFAULT_TEXTURE = "scriptfx:textures/npc/temple_skins.png"
    const val DEFAULT_ANIMATION = "scriptfx:female_models"

    @JvmField
    var model: String = DEFAULT_MODEL

    @JvmField
    var texture: String = DEFAULT_TEXTURE

    @JvmField
    var animation: String = DEFAULT_ANIMATION

    fun apply(entity: ScriptNpcEntity) {
        model = entity.modelPath
            .ifBlank { DEFAULT_MODEL }

        texture = entity.texturePath
            .ifBlank { DEFAULT_TEXTURE }

        animation = entity.animationPath
            .ifBlank { DEFAULT_ANIMATION }
    }
}

class ScriptNpcModel : GeoModel<ScriptNpcEntity>() {

    override fun getModelResource(
        renderState: GeoRenderState
    ): Identifier {
        return Identifier.parse(CurrentNpcResources.model)
    }

    override fun getTextureResource(
        renderState: GeoRenderState
    ): Identifier {
        return Identifier.parse(CurrentNpcResources.texture)
    }

    override fun getAnimationResource(
        animatable: ScriptNpcEntity
    ): Identifier {
        return Identifier.parse(
            animatable.animationPath
                .ifBlank { CurrentNpcResources.DEFAULT_ANIMATION }
        )
    }
}

class ScriptNpcRenderer(
    ctx: EntityRendererProvider.Context
) : GeoEntityRenderer<ScriptNpcEntity, LivingEntityRenderState>(
    ctx,
    ScriptNpcModel()
)