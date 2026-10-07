package net.foxmediax.scriptfx.client

import com.geckolib.model.GeoModel
import com.geckolib.renderer.GeoEntityRenderer
import com.geckolib.renderer.base.GeoRenderState
import net.foxmediax.scriptfx.npc.ScriptNpcEntity
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.resources.Identifier

class ScriptNpcModel : GeoModel<ScriptNpcEntity>() {

    // GeckoLib 5: без geo/ и без .geo.json
    override fun getModelResource(renderState: GeoRenderState): Identifier =
        Identifier.parse("scriptfx:female_models")

    override fun getTextureResource(renderState: GeoRenderState): Identifier =
        Identifier.parse("scriptfx:textures/npc/temple_skins.png")

    // GeckoLib 5: без animations/ и без .animation.json
    override fun getAnimationResource(animatable: ScriptNpcEntity): Identifier =
        Identifier.parse("scriptfx:female_models")
}

class ScriptNpcRenderer(
    ctx: EntityRendererProvider.Context
) : GeoEntityRenderer<ScriptNpcEntity, LivingEntityRenderState>(ctx, ScriptNpcModel())