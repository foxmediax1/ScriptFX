package net.foxmediax.scriptfx.client

import net.fabricmc.loader.api.FabricLoader
import net.foxmediax.scriptfx.npc.NpcCustoms
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.resources.Identifier
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

object LocalNpcAssets {

    data class Handle(
        val modelId: Identifier,
        val animId: Identifier,
        val textureId: Identifier
    )

    private val handles = ConcurrentHashMap<String, Handle>()

    // Папка ресурспака в gameDir/resourcepacks
    private val resourcePackDir: Path by lazy {
        FabricLoader.getInstance().gameDir.resolve("resourcepacks/scriptfx_customs")
    }

    private val assetsDir: Path by lazy {
        resourcePackDir.resolve("assets/customs")
    }

    fun ensure(
        project: String,
        npcId: String,
        modelPath: String,
        animPath: String,
        texPath: String
    ): Handle? {
        val name = NpcCustoms.sanitizePath(NpcCustoms.strip(modelPath))
        if (name.isBlank()) return null
        val key = "${NpcCustoms.sanitizePath(project)}/${NpcCustoms.sanitizePath(npcId)}/$name"
        handles[key]?.let { return it }

        val modelFile = NpcCustoms.modelFile(project, npcId, modelPath)
        if (modelFile == null) {
            ScriptFXLog.warn("LocalNpcAssets: model file not found for $key")
            return null
        }

        ScriptFXLog.info("LocalNpcAssets: copying ${modelFile.name} to resource pack")

        try {
            // Создаём структуру папок GeckoLib: geckolib/models/ и geckolib/animations/
            val modelsDir = assetsDir.resolve("geckolib/models")
            val animsDir = assetsDir.resolve("geckolib/animations")
            val texturesDir = assetsDir.resolve("textures")

            Files.createDirectories(modelsDir)
            Files.createDirectories(animsDir)
            Files.createDirectories(texturesDir)

            // Создаём pack.mcmeta если нет
            val packMcmeta = resourcePackDir.resolve("pack.mcmeta")
            if (!Files.exists(packMcmeta)) {
                Files.createDirectories(resourcePackDir)
                Files.writeString(packMcmeta, """
                    {
                      "pack": {
                        "description": "ScriptFX Custom NPC Assets",
                        "pack_format": 46
                      }
                    }
                """.trimIndent())
                ScriptFXLog.info("LocalNpcAssets: created pack.mcmeta")
            }

            // Копируем файлы с правильными именами
            val modelDest = modelsDir.resolve("$name.geo.json")
            Files.copy(modelFile.toPath(), modelDest, StandardCopyOption.REPLACE_EXISTING)
            ScriptFXLog.info("LocalNpcAssets: copied model -> ${modelDest.fileName}")

            val animFile = NpcCustoms.animFile(project, npcId, animPath.ifBlank { modelPath })
            if (animFile != null && animFile.isFile) {
                val animDest = animsDir.resolve("$name.animation.json")
                Files.copy(animFile.toPath(), animDest, StandardCopyOption.REPLACE_EXISTING)
                ScriptFXLog.info("LocalNpcAssets: copied anim -> ${animDest.fileName}")
            }

            val texFile = NpcCustoms.textureFile(project, npcId, texPath.ifBlank { modelPath })
            if (texFile != null && texFile.isFile) {
                val texDest = texturesDir.resolve("$name.png")
                Files.copy(texFile.toPath(), texDest, StandardCopyOption.REPLACE_EXISTING)
                ScriptFXLog.info("LocalNpcAssets: copied texture -> ${texDest.fileName}")
            }

            // ВАЖНО: GeckoLib ожидает путь БЕЗ расширения и БЕЗ "models/"
            // "customs:hotel_models" -> assets/customs/geckolib/models/hotel_models.geo.json
            val modelId = Identifier.fromNamespaceAndPath("customs", name)
            val animId = Identifier.fromNamespaceAndPath("customs", name)
            val textureId = Identifier.fromNamespaceAndPath("customs", "textures/$name.png")

            val h = Handle(modelId, animId, textureId)
            handles[key] = h

            ScriptFXLog.info("LocalNpcAssets: OK $key")
            ScriptFXLog.info("  Model ID: $modelId")
            ScriptFXLog.info("  Anim ID: $animId")
            ScriptFXLog.info("  Texture ID: $textureId")
            ScriptFXLog.info("  >>> Press F3+T to reload resources! <<<")

            return h

        } catch (t: Throwable) {
            ScriptFXLog.warn("LocalNpcAssets: failed: ${t.message}")
            t.printStackTrace()
            return null
        }
    }

    fun clear() {
        handles.clear()
    }
}