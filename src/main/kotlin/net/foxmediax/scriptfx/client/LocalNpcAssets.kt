package net.foxmediax.scriptfx.client

import com.mojang.blaze3d.platform.NativeImage
import net.fabricmc.loader.api.FabricLoader
import net.foxmediax.scriptfx.npc.NpcCustoms
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * Копирует geo/anim/png NPC в resourcepacks/scriptfx_customs
 * и регистрирует текстуру через DynamicTexture (мир/превью без F3+T для текстуры).
 *
 * Model/anim: Identifier customs:<name> → GeckoLib ищет
 * assets/customs/geckolib/models/<name>.geo.json в активном resource pack.
 * Пак нужно один раз включить (или autoEnablePack из ScriptFXClient).
 */
object LocalNpcAssets {

    data class Handle(
        val modelId: Identifier,
        val animId: Identifier,
        val textureId: Identifier
    )

    private val handles = ConcurrentHashMap<String, Handle>()

    private val resourcePackDir: Path by lazy {
        FabricLoader.getInstance().gameDir.resolve("resourcepacks/scriptfx_customs")
    }

    private val assetsDir: Path by lazy {
        resourcePackDir.resolve("assets/customs")
    }

    fun packDir(): Path = resourcePackDir

    fun ensure(
        project: String,
        npcId: String,
        modelPath: String,
        animPath: String,
        texPath: String
    ): Handle? {
        val name = NpcCustoms.sanitizePath(NpcCustoms.strip(modelPath))
        if (name.isBlank()) {
            ScriptFXLog.warn("LocalNpcAssets: blank model name from '$modelPath'")
            return null
        }
        val key = "${NpcCustoms.sanitizePath(project)}/${NpcCustoms.sanitizePath(npcId)}/$name"
        handles[key]?.let { return it }

        val modelFile = NpcCustoms.modelFile(project, npcId, modelPath)
        if (modelFile == null || !modelFile.isFile) {
            ScriptFXLog.warn(
                "LocalNpcAssets: model file not found key=$key path=$modelPath " +
                        "dir=${net.foxmediax.scriptfx.npc.NpcRegistry.customsModels(project, npcId).absolutePath}"
            )
            return null
        }

        ScriptFXLog.info("LocalNpcAssets: copying ${modelFile.name} → $name")

        return try {
            val modelsDir = assetsDir.resolve("geckolib/models")
            val animsDir = assetsDir.resolve("geckolib/animations")
            val texturesDir = assetsDir.resolve("textures")

            Files.createDirectories(modelsDir)
            Files.createDirectories(animsDir)
            Files.createDirectories(texturesDir)

            val packMcmeta = resourcePackDir.resolve("pack.mcmeta")
            if (!Files.exists(packMcmeta)) {
                Files.createDirectories(resourcePackDir)
                // pack_format: при несовпадении с 26.1 подними/опусти число в IDEA по ванили
                Files.writeString(
                    packMcmeta,
                    """
                    {
                      "pack": {
                        "description": "ScriptFX Custom NPC Assets",
                        "pack_format": 46
                      }
                    }
                    """.trimIndent()
                )
            }

            Files.copy(
                modelFile.toPath(),
                modelsDir.resolve("$name.geo.json"),
                StandardCopyOption.REPLACE_EXISTING
            )

            val animFile = NpcCustoms.animFile(project, npcId, animPath.ifBlank { modelPath })
            if (animFile != null && animFile.isFile) {
                Files.copy(
                    animFile.toPath(),
                    animsDir.resolve("$name.animation.json"),
                    StandardCopyOption.REPLACE_EXISTING
                )
            } else {
                ScriptFXLog.warn("LocalNpcAssets: animation not found for $key (geo only)")
            }

            val texFile = NpcCustoms.textureFile(project, npcId, texPath.ifBlank { modelPath })
            if (texFile != null && texFile.isFile) {
                Files.copy(
                    texFile.toPath(),
                    texturesDir.resolve("$name.png"),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }

            val modelId = Identifier.fromNamespaceAndPath("customs", name)
            val animId = Identifier.fromNamespaceAndPath("customs", name)

            val defaultTex = Identifier.fromNamespaceAndPath(
                "scriptfx",
                "textures/npc/temple_female_skin.png"
            )
            val textureId = if (texFile != null && texFile.isFile) {
                registerDynamicTexture(key, texFile) ?: defaultTex
            } else {
                ScriptFXLog.warn("LocalNpcAssets: texture missing for $key")
                defaultTex
            }

            val h = Handle(modelId, animId, textureId)
            handles[key] = h
            ScriptFXLog.info("LocalNpcAssets: OK $key model=$modelId tex=$textureId")
            h
        } catch (t: Throwable) {
            ScriptFXLog.warn("LocalNpcAssets: failed: ${t.message}")
            t.printStackTrace()
            null
        }
    }

    /** Сброс кэша handle (после смены файлов на диске). */
    fun invalidate(project: String, npcId: String, modelPath: String) {
        val name = NpcCustoms.sanitizePath(NpcCustoms.strip(modelPath))
        val key = "${NpcCustoms.sanitizePath(project)}/${NpcCustoms.sanitizePath(npcId)}/$name"
        handles.remove(key)
    }

    private fun registerDynamicTexture(key: String, file: java.io.File): Identifier? {
        return try {
            val mc = Minecraft.getInstance() ?: return null
            val safe = key.lowercase().replace(Regex("[^a-z0-9_/.-]"), "_")
            val id = Identifier.fromNamespaceAndPath("scriptfx", "dyn_npc/$safe")
            file.inputStream().use { ins ->
                val img = NativeImage.read(ins)
                mc.textureManager.register(id, DynamicTexture({ safe }, img))
            }
            ScriptFXLog.info("LocalNpcAssets: dyn texture $id")
            id
        } catch (t: Throwable) {
            ScriptFXLog.warn("LocalNpcAssets dyn texture: ${t.message}")
            null
        }
    }

    /**
     * Пытается включить пак scriptfx_customs в списке ресурспаков клиента.
     * API 26.1 может отличаться — при ошибке компиляции поправь имена методов в IDEA.
     */
    fun tryEnableResourcePack() {
        val dir = resourcePackDir.toAbsolutePath()
        if (Files.isDirectory(dir)) {
            ScriptFXLog.info(
                "LocalNpcAssets: pack folder ready at $dir — " +
                        "enable 'ScriptFX Custom NPC Assets' in Resource Packs if customs models are missing"
            )
        }
    }

    fun clear() {
        handles.clear()
    }
}