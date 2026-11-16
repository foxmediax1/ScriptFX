package net.foxmediax.scriptfx.npc

import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Читает имена анимаций из GeckoLib .animation.json
 * по id файла вида scriptfx:female_models.
 */
object NpcAnimationCatalog {

    /** Имена анимаций (без префикса animation.xxx. если он есть — оставляем как в файле). */
    fun listNames(animationFileId: String): List<String> {
        val raw = animationFileId.trim()
        if (raw.isEmpty()) return emptyList()

        val text = readAnimationJson(raw) ?: return emptyList()
        return parseAnimationNames(text)
    }

    fun readAnimationJson(animationFileId: String): String? {
        val id = runCatching {
            if (animationFileId.contains(':')) Identifier.parse(animationFileId)
            else Identifier.fromNamespaceAndPath("scriptfx", animationFileId)
        }.getOrNull() ?: return null

        // 1) Resource pack / jar
        val paths = listOf(
            "animations/${id.path}.animation.json",
            "animations/${id.path}.json",
            "geckolib/animations/${id.path}.animation.json",
            "geckolib/animations/${id.path}.json"
        )
        val rm = Minecraft.getInstance().resourceManager
        for (p in paths) {
            val loc = Identifier.fromNamespaceAndPath(id.namespace, p)
            try {
                val opt = rm.getResource(loc)
                if (opt.isPresent) {
                    opt.get().open().use { stream ->
                        return stream.readBytes().toString(StandardCharsets.UTF_8)
                    }
                }
            } catch (_: Throwable) {
            }
        }

        // 2) config/scriptfx/animations/
        val configRoot = FabricLoader.getInstance().configDir.resolve("scriptfx").resolve("animations")
        val candidates = listOf(
            File(configRoot.toFile(), "${id.path}.animation.json"),
            File(configRoot.toFile(), "${id.path}.json"),
            File(configRoot.toFile(), "${id.namespace}/${id.path}.animation.json")
        )
        for (f in candidates) {
            if (f.isFile) {
                return runCatching { f.readText() }.getOrNull()
            }
        }

        ScriptFXLog.warn("NpcAnimationCatalog: не найден файл анимаций для '$animationFileId'")
        return null
    }

    fun parseAnimationNames(jsonText: String): List<String> {
        return try {
            val root = JsonParser.parseString(jsonText).asJsonObject
            val anims = root.getAsJsonObject("animations") ?: return emptyList()
            anims.keySet()
                .map { key ->
                    // GeckoLib часто: animation.model.idle → берём короткий хвост для UI,
                    // но в defaultAnim пишем имя как в файле (полный key).
                    key
                }
                .sorted()
        } catch (t: Throwable) {
            ScriptFXLog.warn("NpcAnimationCatalog: parse failed: ${t.message}")
            emptyList()
        }
    }

    /** Короткое имя для кнопки в списке. */
    fun shortLabel(fullName: String): String =
        fullName.substringAfterLast('.').ifBlank { fullName }
}