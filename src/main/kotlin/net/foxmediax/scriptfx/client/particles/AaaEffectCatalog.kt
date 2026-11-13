package net.foxmediax.scriptfx.client.particles

import net.fabricmc.loader.api.FabricLoader
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.resources.Identifier
import java.nio.file.Files
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence

/**
 * Список AAA-эффектов:
 * - EffectRegistry (уже в resource pack)
 * - config/scriptfx/aaa_effects/**/*.efkefc → scriptfx:<relative>
 */
object AaaEffectCatalog {

    data class Entry(
        val id: Identifier,
        val label: String,
        val source: Source
    )

    enum class Source { REGISTRY, CONFIG }

    private val configRoot by lazy {
        FabricLoader.getInstance().configDir.resolve("scriptfx").resolve("aaa_effects")
    }

    fun list(): List<Entry> {
        val out = LinkedHashMap<String, Entry>()

        // 1) Registry (reflection — на случай отсутствия AAA)
        try {
            if (AaaParticleBridge.available) {
                val reg = Class.forName("mod.chloeprime.aaaparticles.api.client.EffectRegistry")
                @Suppress("UNCHECKED_CAST")
                val entries = reg.getMethod("entries").invoke(null) as Collection<Map.Entry<*, *>>
                for (e in entries) {
                    val key = e.key ?: continue
                    val id = when (key) {
                        is Identifier -> key
                        else -> Identifier.parse(key.toString())
                    }
                    out[id.toString()] = Entry(id, id.path.substringAfterLast('/'), Source.REGISTRY)
                }
            }
        } catch (t: Throwable) {
            ScriptFXLog.warn("AaaEffectCatalog: registry scan: ${t.message}")
        }

        // 2) config folder
        try {
            if (!Files.isDirectory(configRoot)) {
                Files.createDirectories(configRoot)
            }
            Files.walk(configRoot).use { stream ->
                stream.asSequence()
                    .filter { it.isRegularFile() && it.name.endsWith(".efkefc", ignoreCase = true) }
                    .forEach { path ->
                        val rel = configRoot.relativize(path).toString()
                            .replace('\\', '/')
                            .removeSuffix(".efkefc")
                            .removeSuffix(".EFKEFC")
                        if (rel.isBlank()) return@forEach
                        val id = Identifier.fromNamespaceAndPath("scriptfx", rel)
                        out.putIfAbsent(
                            id.toString(),
                            Entry(id, rel.substringAfterLast('/'), Source.CONFIG)
                        )
                    }
            }
        } catch (t: Throwable) {
            ScriptFXLog.warn("AaaEffectCatalog: config scan: ${t.message}")
        }

        return out.values.sortedBy { it.id.toString() }
    }
}