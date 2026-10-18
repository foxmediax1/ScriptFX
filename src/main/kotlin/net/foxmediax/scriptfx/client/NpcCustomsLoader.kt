package net.foxmediax.scriptfx.client

import com.mojang.blaze3d.platform.NativeImage
import net.fabricmc.loader.api.FabricLoader
import net.foxmediax.scriptfx.npc.NpcCustoms
import net.foxmediax.scriptfx.npc.NpcRegistry
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 1) Копирует customs geo/anim/png во внутреннюю папку
 *    config/scriptfx/internal_npc_assets/assets/scriptfx/...
 * 2) Текстуры дополнительно регистрирует как DynamicTexture (сразу видны).
 *
 * Игрок resource pack не включает. Чтобы GeckoLib увидел geo/anim,
 * папку internal_npc_assets нужно один раз пробросить в ResourceManager
 * (см. registerInternalPack ниже — через ваш способ ALWAYS_ENABLED / PathPack).
 */
object NpcCustomsLoader {

    private val textureIds = HashMap<String, Identifier>()

    val internalRoot: File =
        FabricLoader.getInstance().configDir.resolve("scriptfx/internal_npc_assets").toFile()

    fun syncAll() {
        runCatching {
            if (internalRoot.exists()) internalRoot.deleteRecursively()
            internalRoot.mkdirs()
            File(internalRoot, "pack.mcmeta").writeText(
                """
                {
                  "pack": {
                    "description": "ScriptFX NPC customs (internal)",
                    "pack_format": 48
                  }
                }
                """.trimIndent()
            )

            NpcRegistry.reload()
            for (project in NpcRegistry.listProjects()) {
                for (def in NpcRegistry.listNpcs(project)) {
                    syncNpc(project, def.id)
                }
            }
            ScriptFXLog.info("NpcCustomsLoader: synced → ${internalRoot.absolutePath}")
        }.onFailure {
            ScriptFXLog.error("NpcCustomsLoader.syncAll failed", it)
        }
    }

    fun syncNpc(project: String, npcId: String) {
        val models = NpcRegistry.customsModels(project, npcId)
        val anims = NpcRegistry.customsAnimations(project, npcId)
        val textures = NpcRegistry.customsTextures(project, npcId)
        val base = "assets/scriptfx"
        val proj = NpcCustoms.sanitizePath(project)
        val nid = NpcCustoms.sanitizePath(npcId)

        models.listFiles()?.filter { it.isFile && it.name.endsWith(".json", true) }?.forEach { file ->
            val rawName = file.name
                .removeSuffix(".geo.json")
                .removeSuffix(".json")
            val name = NpcCustoms.sanitizePath(rawName)
            val dest = File(
                internalRoot,
                "$base/geckolib/models/npc_customs/$proj/$nid/$name.geo.json"
            )
            dest.parentFile.mkdirs()
            Files.copy(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        anims.listFiles()?.filter { it.isFile && it.name.endsWith(".json", true) }?.forEach { file ->
            val rawName = file.name
                .removeSuffix(".animation.json")
                .removeSuffix(".json")
            val name = NpcCustoms.sanitizePath(rawName)
            val dest = File(
                internalRoot,
                "$base/geckolib/animations/npc_customs/$proj/$nid/$name.animation.json"
            )
            dest.parentFile.mkdirs()
            Files.copy(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        textures.listFiles()?.filter { it.isFile && it.extension.equals("png", true) }?.forEach { file ->
            val name = NpcCustoms.sanitizePath(file.nameWithoutExtension)
            val dest = File(
                internalRoot,
                "$base/textures/npc_customs/$proj/$nid/$name.png"
            )
            dest.parentFile.mkdirs()
            Files.copy(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
            registerTexture(project, npcId, name, file)
        }
    }

    private fun registerTexture(project: String, npcId: String, name: String, file: File) {
        val key = "$project/$npcId/$name"
        if (textureIds.containsKey(key)) return
        runCatching {
            val mc = Minecraft.getInstance()
            if (mc == null) return
            file.inputStream().use { stream ->
                val image = NativeImage.read(stream)
                val id = Identifier.fromNamespaceAndPath(
                    "scriptfx",
                    "dyn_npc/${project}_${npcId}_$name".lowercase().replace(Regex("[^a-z0-9_/]"), "_")
                )
                mc.textureManager.register(id, DynamicTexture({ name }, image))
                textureIds[key] = id
            }
        }.onFailure {
            ScriptFXLog.warn("NpcCustomsLoader texture fail $key: ${it.message}")
        }
    }

    fun dynamicTextureId(project: String, npcId: String, customsPath: String): Identifier? {
        val name = NpcCustoms.strip(customsPath)
        val key = "$project/$npcId/$name"
        textureIds[key]?.let { return it }
        val file = NpcCustoms.textureFile(project, npcId, customsPath) ?: return null
        registerTexture(project, npcId, name, file)
        return textureIds[key]
    }

    fun clearTextureCache() {
        textureIds.clear()
    }
}