package net.foxmediax.scriptfx.client

import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.fabricmc.loader.api.FabricLoader
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object ProjectGeckoPack {

    private val genRoot: File =
        FabricLoader.getInstance().configDir.resolve("scriptfx/generated_pack").toFile()

    private val projectsRoot: File =
        FabricLoader.getInstance().configDir.resolve("scriptfx/projects").toFile()

    /** sf_myproject — namespace для проекта myproject */
    fun namespaceFor(project: String): String {
        val clean = project.lowercase().replace(Regex("[^a-z0-9_]"), "_")
        return "sf_$clean"
    }

    fun modelId(project: String, name: String) = "${namespaceFor(project)}:$name"
    fun animId(project: String, name: String) = "${namespaceFor(project)}:$name"
    fun textureId(project: String, name: String) =
        "${namespaceFor(project)}:textures/$name.png"

    fun sync() {
        try {
            if (genRoot.exists()) genRoot.deleteRecursively()
            genRoot.mkdirs()

            // pack.mcmeta
            File(genRoot, "pack.mcmeta").writeText(
                """
                {
                  "pack": {
                    "description": "ScriptFX project GeckoLib assets",
                    "pack_format": 48
                  }
                }
                """.trimIndent()
            )

            val root = projectsRoot
            if (!root.isDirectory) return

            root.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
                val project = projectDir.name
                val gecko = File(projectDir, "geckolib")
                if (!gecko.isDirectory) return@forEach

                val ns = namespaceFor(project)
                val assetsNs = File(genRoot, "assets/$ns")

                copyDir(File(gecko, "models"), File(assetsNs, "geckolib/models"))
                copyDir(File(gecko, "animations"), File(assetsNs, "geckolib/animations"))
                copyDir(File(gecko, "textures"), File(assetsNs, "textures"))

                ScriptFXLog.debug("ProjectGeckoPack: synced $project → $ns")
            }

            ScriptFXLog.info("ProjectGeckoPack: generated pack at ${genRoot.absolutePath}")
        } catch (t: Throwable) {
            ScriptFXLog.error("ProjectGeckoPack.sync failed", t)
        }
    }

    private fun copyDir(from: File, to: File) {
        if (!from.isDirectory) return
        to.mkdirs()
        from.listFiles()?.forEach { f ->
            if (f.isFile) {
                Files.copy(f.toPath(), File(to, f.name).toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    fun initClient() {
        sync()
        // После F3+T / смены паков — пересобрать
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
            object : SimpleSynchronousResourceReloadListener {
                override fun getFabricId(): Identifier =
                    Identifier.fromNamespaceAndPath("scriptfx", "project_gecko_pack")

                override fun onResourceManagerReload(manager: ResourceManager) {
                    sync()
                }
            }
        )
    }
}