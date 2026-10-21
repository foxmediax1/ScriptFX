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

    private const val PACK_FOLDER_NAME = "scriptfx_customs"

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

        val modelFile = NpcCustoms.modelFile(project, npcId, modelPath)
        if (modelFile == null) {
            ScriptFXLog.warn("LocalNpcAssets: model file not found for $key")
            handles.remove(key)
            return null
        }

        val destGeo = assetsDir.resolve("geckolib/models/$name.geo.json")
        val cached = handles[key]
        // Отдаём кэш только если geo уже лежит в паке
        if (cached != null && Files.isRegularFile(destGeo)) {
            return cached
        }

        ScriptFXLog.info("LocalNpcAssets: copying ${modelFile.name} → $key")

        return try {
            val modelsDir = assetsDir.resolve("geckolib/models")
            val animsDir = assetsDir.resolve("geckolib/animations")
            val texturesDir = assetsDir.resolve("textures")

            Files.createDirectories(modelsDir)
            Files.createDirectories(animsDir)
            Files.createDirectories(texturesDir)

            writePackMcmeta()

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
                    ?: Identifier.fromNamespaceAndPath(
                        "scriptfx",
                        "textures/npc/temple_female_skin.png"
                    )
            } else {
                Identifier.fromNamespaceAndPath(
                    "scriptfx",
                    "textures/npc/temple_female_skin.png"
                )
            }

            val handle = Handle(modelId, animId, textureId)
            handles[key] = handle
            ScriptFXLog.info("LocalNpcAssets: OK $key model=$modelId tex=$textureId")

            // Пак должен быть включён, иначе GeckoLib не видит geo/anim
            tryEnableResourcePack(forceReload = true)

            handle
        } catch (t: Throwable) {
            ScriptFXLog.warn("LocalNpcAssets: failed: ${t.message}")
            t.printStackTrace()
            null
        }
    }

    private fun writePackMcmeta() {
        Files.createDirectories(resourcePackDir)
        val packMcmeta = resourcePackDir.resolve("pack.mcmeta")
        Files.writeString(
            packMcmeta,
            """
            {
              "pack": {
                "description": "ScriptFX Custom NPC Assets",
                "pack_format": 48
              }
            }
            """.trimIndent()
        )
    }

    /**
     * Включает folder-pack `resourcepacks/scriptfx_customs`.
     * Без этого GeckoLib не находит `customs:*.geo.json`.
     */
    fun tryEnableResourcePack(forceReload: Boolean = false) {
        val mc = Minecraft.getInstance() ?: return
        try {
            writePackMcmeta()

            val repo = mc.resourcePackRepository
            repo.reload()

            val pack = repo.availablePacks.firstOrNull { p ->
                val id = p.id
                id == "file/$PACK_FOLDER_NAME" ||
                        id.endsWith(PACK_FOLDER_NAME) ||
                        id.contains(PACK_FOLDER_NAME)
            }

            if (pack == null) {
                ScriptFXLog.warn(
                    "LocalNpcAssets: pack '$PACK_FOLDER_NAME' not found in repository " +
                            "(dir=${resourcePackDir.toAbsolutePath()})"
                )
                return
            }

            val selected = repo.selectedIds.toMutableList()
            val alreadyOn = selected.contains(pack.id)

            if (!alreadyOn) {
                selected.add(pack.id)
                repo.setSelected(selected)
                ScriptFXLog.info("LocalNpcAssets: enabled pack ${pack.id}")
                // Применяет список и перезагружает ресурсы
                mc.options.updateResourcePacks(repo)
            } else if (forceReload) {
                ScriptFXLog.info("LocalNpcAssets: pack already on, reloading resources")
                mc.reloadResourcePacks()
            }
        } catch (t: Throwable) {
            ScriptFXLog.warn("LocalNpcAssets.tryEnableResourcePack: ${t.message}")
            t.printStackTrace()
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