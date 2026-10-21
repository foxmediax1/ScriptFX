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

object NpcAssetResolver {

    const val DEFAULT_MODEL = "scriptfx:female_models"
    const val DEFAULT_ANIMATION = "scriptfx:female_models"
    const val DEFAULT_TEXTURE = "scriptfx:textures/npc/temple_female_skins.png"

    data class Resolved(
        val model: Identifier,
        val texture: Identifier,
        val animation: Identifier,
        val customLoaded: Boolean
    )

    fun resolveFor(entity: ScriptNpcEntity): Resolved {
        entity.syncPathsFromData()

        val found = NpcRegistry.findById(entity.npcId)
        val project = found?.first

        val modelRaw = entity.modelPath
        val animRaw = entity.animationPath
        val texRaw = entity.texturePath

        // 1) Кастомные ресурсы: грузятся только для конкретного NPC и проекта
        if (project != null && NpcCustoms.isCustoms(modelRaw)) {
            val handle = LocalNpcAssets.ensure(
                project = project,
                npcId = entity.npcId,
                modelPath = modelRaw,
                animPath = animRaw.ifBlank { modelRaw },
                texPath = texRaw.ifBlank { modelRaw }
            )

            if (handle != null) {
                return Resolved(
                    model = handle.modelId,
                    texture = handle.textureId,
                    animation = handle.animId,
                    customLoaded = true
                )
            }

            ScriptFXLog.warn(
                "NpcAssetResolver: custom model failed for npc=${entity.npcId}, project=$project, fallback to defaults"
            )

            return Resolved(
                model = safeId(DEFAULT_MODEL),
                texture = safeId(DEFAULT_TEXTURE),
                animation = safeId(DEFAULT_ANIMATION),
                customLoaded = false
            )
        }

        // 2) Стандартный путь: нормализуем модель/анимацию/текстуру
        val modelId = safeId(normalizeResourcePath(modelRaw, DEFAULT_MODEL))
        val animId = safeId(normalizeResourcePath(animRaw, DEFAULT_ANIMATION))

        val textureId = when {
            project != null && NpcCustoms.isCustoms(texRaw) -> {
                val handle = LocalNpcAssets.ensure(
                    project = project,
                    npcId = entity.npcId,
                    modelPath = modelRaw.ifBlank { texRaw },
                    animPath = animRaw.ifBlank { texRaw },
                    texPath = texRaw
                )

                handle?.textureId ?: safeId(DEFAULT_TEXTURE)
            }

            NpcCustoms.isCustoms(texRaw) -> safeId(DEFAULT_TEXTURE)

            texRaw.isNotBlank() -> safeId(texRaw)

            else -> safeId(DEFAULT_TEXTURE)
        }

        return Resolved(
            model = modelId,
            texture = textureId,
            animation = animId,
            customLoaded = false
        )
    }

    private fun normalizeResourcePath(raw: String, fallback: String): String {
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

    private fun safeId(raw: String): Identifier {
        return try {
            Identifier.parse(raw)
        } catch (_: Exception) {
            Identifier.parse(DEFAULT_MODEL)
        }
    }
}

class ScriptNpcModel : GeoModel<ScriptNpcEntity>() {

    override fun getModelResource(renderState: GeoRenderState): Identifier {
        val entity = renderState.entity as? ScriptNpcEntity ?: return Identifier.parse(NpcAssetResolver.DEFAULT_MODEL)
        return NpcAssetResolver.resolveFor(entity).model
    }

    override fun getTextureResource(renderState: GeoRenderState): Identifier {
        val entity = renderState.entity as? ScriptNpcEntity ?: return Identifier.parse(NpcAssetResolver.DEFAULT_TEXTURE)
        return NpcAssetResolver.resolveFor(entity).texture
    }

    override fun getAnimationResource(animatable: ScriptNpcEntity): Identifier {
        return NpcAssetResolver.resolveFor(animatable).animation
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
        // Никакого глобального CurrentNpcResources больше нет.
        // Каждый NPC решает свои ресурсы локально через NpcAssetResolver.
        super.extractRenderState(entity, state, partialTick)
    }
}