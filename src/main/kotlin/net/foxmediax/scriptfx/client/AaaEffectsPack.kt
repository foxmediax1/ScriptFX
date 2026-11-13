package net.foxmediax.scriptfx.client

import net.fabricmc.loader.api.FabricLoader
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.Minecraft
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence

/**
 * Resource pack для AAA Particles (.efkefc).
 *
 * Источник (удобно для автора):
 *   config/scriptfx/aaa_effects/
 *     my_fire.efkefc
 *     Texture/...          (если эффект ссылается на Texture/)
 *     sub/boom.efkefc
 *
 * Пак, который видит игра:
 *   resourcepacks/scriptfx_aaa_effects/
 *     pack.mcmeta
 *     assets/scriptfx/effeks/...
 *
 * ID в скрипте:
 *   aaa_particle "scriptfx:my_fire"
 *   aaa_particle "scriptfx:sub/boom"
 */
object AaaEffectsPack {

    private const val PACK_FOLDER_NAME = "scriptfx_aaa_effects"
    private const val NAMESPACE = "scriptfx"

    private val sourceDir: Path by lazy {
        FabricLoader.getInstance().configDir
            .resolve("scriptfx")
            .resolve("aaa_effects")
    }

    private val resourcePackDir: Path by lazy {
        FabricLoader.getInstance().gameDir
            .resolve("resourcepacks")
            .resolve(PACK_FOLDER_NAME)
    }

    private val effeksDir: Path by lazy {
        resourcePackDir.resolve("assets").resolve(NAMESPACE).resolve("effeks")
    }

    /** Создаёт папки, копирует эффекты, включает pack. */
    fun initAndEnable(forceReload: Boolean = false) {
        try {
            ensureSourceLayout()
            syncFromSource()
            writePackMcmeta()
            tryEnableResourcePack(forceReload)
        } catch (t: Throwable) {
            ScriptFXLog.warn("AaaEffectsPack.init: ${t.message}")
            t.printStackTrace()
        }
    }

    private fun ensureSourceLayout() {
        Files.createDirectories(sourceDir)
        val readme = sourceDir.resolve("README.txt")
        if (!Files.exists(readme)) {
            Files.writeString(
                readme,
                """
                ScriptFX — AAA Particles effects
                ================================
                Клади сюда .efkefc и связанные текстуры.

                Пример:
                  config/scriptfx/aaa_effects/my_fire.efkefc
                  config/scriptfx/aaa_effects/Texture/particle.png

                В скрипте:
                  aaa_particle "scriptfx:my_fire" ~ ~1 ~ 1.0

                Подпапки тоже работают:
                  config/scriptfx/aaa_effects/combat/slash.efkefc
                  → aaa_particle "scriptfx:combat/slash"

                После добавления файлов: перезайди в мир или F3+T
                (или перезапусти клиент).
                """.trimIndent()
            )
        }
    }

    /**
     * Копирует всё из config/scriptfx/aaa_effects
     * в resourcepacks/scriptfx_aaa_effects/assets/scriptfx/effeks/
     */
    fun syncFromSource() {
        Files.createDirectories(effeksDir)

        if (!Files.isDirectory(sourceDir)) return

        // Чистим старые файлы пака (кроме pack.mcmeta уровнем выше)
        if (Files.isDirectory(effeksDir)) {
            Files.walk(effeksDir).use { stream ->
                stream.sorted(Comparator.reverseOrder())
                    .filter { it != effeksDir }
                    .forEach { Files.deleteIfExists(it) }
            }
        }

        var count = 0
        Files.walk(sourceDir).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() }
                .filter { it.name != "README.txt" }
                .forEach { src ->
                    val rel = sourceDir.relativize(src)
                    val dest = effeksDir.resolve(rel.toString())
                    Files.createDirectories(dest.parent)
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING)
                    count++
                }
        }
        ScriptFXLog.info("AaaEffectsPack: synced $count file(s) → ${effeksDir.toAbsolutePath()}")
    }

    private fun writePackMcmeta() {
        Files.createDirectories(resourcePackDir)
        val packMcmeta = resourcePackDir.resolve("pack.mcmeta")
        Files.writeString(
            packMcmeta,
            """
            {
              "pack": {
                "description": "ScriptFX AAA Particles Effects",
                "pack_format": 48
              }
            }
            """.trimIndent()
        )
    }

    fun tryEnableResourcePack(forceReload: Boolean = false) {
        val mc = Minecraft.getInstance() ?: return
        // options ещё null на самом раннем init — как у LocalNpcAssets
        if (mc.options == null) {
            ScriptFXLog.info("AaaEffectsPack: options still null, skip enable (will retry on join)")
            return
        }

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
                    "AaaEffectsPack: pack '$PACK_FOLDER_NAME' not found " +
                            "(dir=${resourcePackDir.toAbsolutePath()})"
                )
                return
            }

            val selected = repo.selectedIds.toMutableList()
            val alreadyOn = selected.contains(pack.id)

            if (!alreadyOn) {
                selected.add(pack.id)
                repo.setSelected(selected)
                ScriptFXLog.info("AaaEffectsPack: enabled pack ${pack.id}")
                mc.options.updateResourcePacks(repo)
            } else if (forceReload) {
                ScriptFXLog.info("AaaEffectsPack: pack already on, reloading resources")
                mc.reloadResourcePacks()
            }
        } catch (t: Throwable) {
            ScriptFXLog.warn("AaaEffectsPack.tryEnableResourcePack: ${t.message}")
            t.printStackTrace()
        }
    }
}