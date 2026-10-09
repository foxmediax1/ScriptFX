package net.foxmediax.scriptfx.client

import com.geckolib.model.GeoModel
import com.geckolib.renderer.GeoEntityRenderer
import com.geckolib.renderer.base.GeoRenderState
import net.foxmediax.scriptfx.npc.ScriptNpcEntity
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.resources.Identifier

object CurrentNpcResources {

    const val DEFAULT_MODEL =
        "scriptfx:geckolib/models/female_models.geo.json"

    const val DEFAULT_TEXTURE =
        "scriptfx:textures/npc/temple_skins.png"

    const val DEFAULT_ANIMATION =
        "scriptfx:geckolib/animations/female_models.animation.json"

    @JvmField
    var model: String = DEFAULT_MODEL

    @JvmField
    var texture: String = DEFAULT_TEXTURE

    @JvmField
    var animation: String = DEFAULT_ANIMATION

    fun apply(
        entity: ScriptNpcEntity
    ) {
        model =
            entity.modelPath
                .takeIf { it.isNotBlank() }
                ?: DEFAULT_MODEL

        texture =
            entity.texturePath
                .takeIf { it.isNotBlank() }
                ?: DEFAULT_TEXTURE

        animation =
            entity.animationPath
                .takeIf { it.isNotBlank() }
                ?: DEFAULT_ANIMATION
    }
}

class ScriptNpcModel :
    GeoModel<ScriptNpcEntity>() {

    override fun getModelResource(
        renderState: GeoRenderState
    ): Identifier =
        Identifier.parse(
            CurrentNpcResources.model
        )

    override fun getTextureResource(
        renderState: GeoRenderState
    ): Identifier =
        Identifier.parse(
            CurrentNpcResources.texture
        )

    override fun getAnimationResource(
        animatable: ScriptNpcEntity
    ): Identifier =
        Identifier.parse(
            animatable.animationPath
                .takeIf { it.isNotBlank() }
                ?: CurrentNpcResources.DEFAULT_ANIMATION
        )
}

class ScriptNpcRenderer(
    ctx: EntityRendererProvider.Context
) : GeoEntityRenderer<
        ScriptNpcEntity,
        LivingEntityRenderState
        >(
    ctx,
    ScriptNpcModel()
)