package net.foxmediax.scriptfx.client

import com.geckolib.model.GeoModel
import com.geckolib.renderer.GeoEntityRenderer
import com.geckolib.renderer.base.GeoRenderState
import net.foxmediax.scriptfx.npc.ScriptNpcEntity
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.resources.Identifier

object CurrentNpcResources {
    @JvmField var model: String = "scriptfx:female_models"
    @JvmField var texture: String = "scriptfx:textures/npc/temple_skins.png"
    @JvmField var animation: String = "scriptfx:female_models"

    fun apply(entity: ScriptNpcEntity) {
        model = entity.modelPath.ifBlank { "scriptfx:female_models" }
        texture = entity.texturePath.ifBlank { "scriptfx:textures/npc/temple_skins.png" }
        animation = entity.animationPath.ifBlank { "scriptfx:female_models" }
    }
}

class ScriptNpcModel : GeoModel<ScriptNpcEntity>() {

    override fun getModelResource(renderState: GeoRenderState): Identifier =
        Identifier.parse(CurrentNpcResources.model)

    override fun getTextureResource(renderState: GeoRenderState): Identifier =
        Identifier.parse(CurrentNpcResources.texture)

    override fun getAnimationResource(animatable: ScriptNpcEntity): Identifier =
        Identifier.parse(
            animatable.animationPath.ifBlank { CurrentNpcResources.animation }
        )
}

class ScriptNpcRenderer(
    ctx: EntityRendererProvider.Context
) : GeoEntityRenderer<ScriptNpcEntity, LivingEntityRenderState>(ctx, ScriptNpcModel())