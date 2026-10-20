package net.foxmediax.scriptfx.client

import com.geckolib.model.GeoModel
import com.geckolib.renderer.GeoEntityRenderer
import com.geckolib.renderer.base.GeoRenderState
import net.foxmediax.scriptfx.npc.NpcCustoms
import net.foxmediax.scriptfx.npc.NpcRegistry
import net.foxmediax.scriptfx.npc.ScriptNpcEntity
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
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

        var path = body
            .removePrefix("geckolib/")
            .removePrefix("models/")
            .removePrefix("animations/")
            .removeSuffix(".json")
            .removeSuffix(".geo")
            .removeSuffix(".animation")
        path = path.removeSuffix(".animation").removeSuffix(".geo")
        path = NpcCustoms.sanitizePath(path)
        if (path.isBlank()) return fallback

        val nsSafe = ns.lowercase().replace(Regex("[^a-z0-9_]"), "_")
        return "$nsSafe:$path"
    }

    fun apply(entity: ScriptNpcEntity) {
        entity.syncPathsFromData()

        val found = NpcRegistry.findById(entity.npcId)
        val project = found?.first

        val modelRaw = entity.modelPath
        val animRaw = entity.animationPath
        val texRaw = entity.texturePath

        if (project != null && NpcCustoms.isCustoms(modelRaw)) {
            val h = LocalNpcAssets.ensure(
                project,
                entity.npcId,
                modelRaw,
                animRaw.ifBlank { modelRaw },
                texRaw.ifBlank { modelRaw }
            )
            if (h != null) {
                model = h.modelId.toString()
                animation = h.animId.toString()
                texture = h.textureId.toString()
                return
            }
            ScriptFXLog.warn(
                "customs load failed npc=${entity.npcId} project=$project model=$modelRaw → default"
            )
            model = DEFAULT_MODEL
            animation = DEFAULT_ANIMATION
            texture = DEFAULT_TEXTURE
            return
        }

        model = normalizeModelOrAnim(modelRaw, DEFAULT_MODEL)
        animation = normalizeModelOrAnim(animRaw, DEFAULT_ANIMATION)
        texture = when {
            NpcCustoms.isCustoms(texRaw) && project != null -> {
                LocalNpcAssets.ensure(
                    project,
                    entity.npcId,
                    modelRaw.ifBlank { texRaw },
                    animRaw.ifBlank { texRaw },
                    texRaw
                )?.textureId?.toString() ?: DEFAULT_TEXTURE
            }
            NpcCustoms.isCustoms(texRaw) -> DEFAULT_TEXTURE
            texRaw.isNotBlank() -> try {
                Identifier.parse(texRaw).toString()
            } catch (_: Exception) {
                DEFAULT_TEXTURE
            }
            else -> DEFAULT_TEXTURE
        }
    }
}

class ScriptNpcModel : GeoModel<ScriptNpcEntity>() {

    override fun getModelResource(renderState: GeoRenderState): Identifier {
        return safeId(CurrentNpcResources.model, CurrentNpcResources.DEFAULT_MODEL)
    }

    override fun getTextureResource(renderState: GeoRenderState): Identifier {
        return safeId(CurrentNpcResources.texture, CurrentNpcResources.DEFAULT_TEXTURE)
    }

    override fun getAnimationResource(animatable: ScriptNpcEntity): Identifier {
        return safeId(CurrentNpcResources.animation, CurrentNpcResources.DEFAULT_ANIMATION)
    }

    private fun safeId(raw: String, fallback: String): Identifier {
        return try {
            Identifier.parse(raw)
        } catch (_: Exception) {
            Identifier.parse(fallback)
        }
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