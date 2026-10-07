package net.foxmediax.scriptfx.npc

import com.geckolib.model.DefaultedEntityGeoModel
import com.geckolib.model.GeoModel
import com.geckolib.renderer.GeoEntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.Identifier

class ScriptNpcModel : GeoModel<ScriptNpcEntity>() {
    override fun getModelResource(animatable: ScriptNpcEntity): Identifier =
        Identifier.parse(animatable.modelPath)

    override fun getTextureResource(animatable: ScriptNpcEntity): Identifier =
        Identifier.parse(animatable.texturePath)

    override fun getAnimationResource(animatable: ScriptNpcEntity): Identifier =
        Identifier.parse(animatable.animationPath)
}

class ScriptNpcRenderer(ctx: EntityRendererProvider.Context) :
    GeoEntityRenderer<ScriptNpcEntity>(ctx, ScriptNpcModel())