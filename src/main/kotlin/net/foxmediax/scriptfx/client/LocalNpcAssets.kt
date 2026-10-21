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

    fun tryEnableResourcePack() {
        val dir = resourcePackDir.toAbsolutePath()
        if (!Files.isDirectory(dir)) {
            ScriptFXLog.warn("LocalNpcAssets: resource pack dir not found: $dir")
            return
        }

        try {
            val mc = Minecraft.getInstance()
            mc.execute {
                try {
                    mc.reloadResourcePacks()
                    ScriptFXLog.info("LocalNpcAssets: reloaded resource packs")
                } catch (t: Throwable) {
                    ScriptFXLog.warn("LocalNpcAssets: reloadResourcePacks failed: ${t.message}")
                }
            }
        } catch (t: Throwable) {
            ScriptFXLog.warn("LocalNpcAssets: tryEnableResourcePack failed: ${t.message}")
        }
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

        ScriptFXLog.info("LocalNpcAssets: copying ${modelFile.name}")

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

            val textureId = if (texFile != null && texFile.isFile) {
                registerDynamicTexture(key, texFile)
                    ?: Identifier.fromNamespaceAndPath("scriptfx", "textures/npc/temple_female_skins.png")
            } else {
                Identifier.fromNamespaceAndPath("scriptfx", "textures/npc/temple_female_skins.png")
            }

            val handle = Handle(modelId, animId, textureId)
            handles[key] = handle

            tryEnableResourcePack()
            ScriptFXLog.info("LocalNpcAssets: OK $key model=$modelId anim=$animId tex=$textureId")
            handle
        } catch (t: Throwable) {
            ScriptFXLog.warn("LocalNpcAssets: failed: ${t.message}")
            t.printStackTrace()
            null
        }
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

    fun clear() {
        handles.clear()
    }
}