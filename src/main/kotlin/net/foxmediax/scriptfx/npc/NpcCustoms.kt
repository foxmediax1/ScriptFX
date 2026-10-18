package net.foxmediax.scriptfx.npc

import java.io.File

object NpcCustoms {

    const val PREFIX = "customs:"

    fun isCustoms(path: String): Boolean =
        path.startsWith(PREFIX, ignoreCase = true)

    fun sanitizePath(path: String): String =
        path.lowercase()
            .replace('\\', '/')
            .replace(Regex("[^a-z0-9/._-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')

    fun strip(path: String): String {
        var s = path.trim()
        if (s.startsWith(PREFIX, ignoreCase = true)) {
            s = s.substring(PREFIX.length)
        }
        s = s
            .removePrefix("customs/")
            .removePrefix("models/")
            .removePrefix("animations/")
            .removePrefix("textures/")
            .removeSuffix(".geo.json")
            .removeSuffix(".animation.json")
            .removeSuffix(".json")
            .removeSuffix(".png")
            .removeSuffix(".geo")
            .removeSuffix(".animation")
            .trim()
        if ('/' in s) s = s.substringAfterLast('/')
        return s
    }

    fun modelFile(project: String, npcId: String, path: String): File? {
        val name = strip(path)
        if (name.isBlank()) return null
        val dir = NpcRegistry.customsModels(project, npcId)
        return listOf(
            File(dir, "$name.geo.json"),
            File(dir, "$name.json"),
            File(dir, "${name.lowercase()}.geo.json"),
            File(dir, "${name.lowercase()}.json")
        ).firstOrNull { it.isFile }
            ?: dir.listFiles()?.firstOrNull {
                it.isFile && it.name.startsWith(name, ignoreCase = true) &&
                        (it.name.endsWith(".geo.json", true) || it.name.endsWith(".json", true))
            }
    }

    fun animFile(project: String, npcId: String, path: String): File? {
        val name = strip(path)
        if (name.isBlank()) return null
        val dir = NpcRegistry.customsAnimations(project, npcId)
        return listOf(
            File(dir, "$name.animation.json"),
            File(dir, "$name.json"),
            File(dir, "${name.lowercase()}.animation.json")
        ).firstOrNull { it.isFile }
            ?: dir.listFiles()?.firstOrNull {
                it.isFile && it.name.startsWith(name, ignoreCase = true) && it.name.endsWith(".json", true)
            }
    }

    fun textureFile(project: String, npcId: String, path: String): File? {
        val name = strip(path)
        if (name.isBlank()) return null
        val dir = NpcRegistry.customsTextures(project, npcId)
        return listOf(
            File(dir, "$name.png"),
            File(dir, "${name.lowercase()}.png"),
            File(dir, name)
        ).firstOrNull { it.isFile }
            ?: dir.listFiles()?.firstOrNull {
                it.isFile && it.name.startsWith(name, ignoreCase = true) && it.extension.equals("png", true)
            }
    }
}