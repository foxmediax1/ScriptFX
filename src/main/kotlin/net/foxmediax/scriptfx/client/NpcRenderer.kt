package net.foxmediax.scriptfx.client

import com.geckolib.model.GeoModel
import com.geckolib.renderer.GeoEntityRenderer
import com.geckolib.renderer.base.GeoRenderState
import net.foxmediax.scriptfx.npc.NpcCustoms
import net.foxmediax.scriptfx.npc.NpcRegistry
import net.foxmediax.scriptfx.npc.ScriptNpcEntity
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.resources.Identifier

object CurrentNpcResources {

    const val DEFAULT_MODEL = "scriptfx:female_models"
    const val DEFAULT_ANIMATION = "scriptfx:female_models"
    const val DEFAULT_TEXTURE = "scriptfx:textures/npc/temple_female_skins.png"

    @JvmField var model: String = DEFAULT_MODEL
    @JvmField var texture: String = DEFAULT_TEXTURE
    @JvmField var animation: String = DEFAULT_ANIMATION

    fun normalizeModelOrAnim(raw: String, fallback: String): String {
        if (raw.isBlank()) return fallback
        if (NpcCustoms.isCustoms(raw)) return raw

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
        p = p.removeSuffix(".animation").removeSuffix(".geo")
        if (p.isBlank()) return fallback
        return "$ns:$p"
    }

    fun apply(entity: ScriptNpcEntity) {
        val found = NpcRegistry.findById(entity.npcId)
        val project = found?.first

        var modelRaw = entity.modelPath
        var animRaw = entity.animationPath
        var texRaw = entity.texturePath

        if (project != null && NpcCustoms.isCustoms(modelRaw)) {
            val body = NpcCustoms.internalBody(project, entity.npcId, modelRaw)
            modelRaw = "scriptfx:$body"
        }
        if (project != null && NpcCustoms.isCustoms(animRaw)) {
            val body = NpcCustoms.internalBody(project, entity.npcId, animRaw)
            animRaw = "scriptfx:$body"
        }

        model = normalizeModelOrAnim(modelRaw, DEFAULT_MODEL)
        animation = normalizeModelOrAnim(animRaw, DEFAULT_ANIMATION)

        texture = when {
            project != null && NpcCustoms.isCustoms(texRaw) -> {
                NpcCustomsLoader.dynamicTextureId(project, entity.npcId, texRaw)?.toString()
                    ?: run {
                        val body = NpcCustoms.internalBody(project, entity.npcId, texRaw)
                        "scriptfx:textures/$body.png"
                    }
            }
            texRaw.isNotBlank() -> texRaw
            else -> DEFAULT_TEXTURE
        }
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