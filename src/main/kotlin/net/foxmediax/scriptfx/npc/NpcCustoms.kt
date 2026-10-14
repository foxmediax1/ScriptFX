package net.foxmediax.scriptfx.npc

import java.io.File

object NpcCustoms {

    const val PREFIX = "customs:"

    fun isCustoms(path: String): Boolean =
        path.startsWith(PREFIX, ignoreCase = true)

    fun strip(path: String): String {
        var s = path.trim()
        if (s.startsWith(PREFIX, ignoreCase = true)) {
            s = s.substring(PREFIX.length)
        }
        return s
            .removePrefix("customs/")
            .removeSuffix(".geo.json")
            .removeSuffix(".animation.json")
            .removeSuffix(".json")
            .removeSuffix(".png")
            .removeSuffix(".geo")
            .removeSuffix(".animation")
            .trim()
    }

    fun modelFile(project: String, npcId: String, path: String): File? {
        val name = strip(path)
        if (name.isBlank()) return null
        val dir = NpcRegistry.customsModels(project, npcId)
        return listOf(File(dir, "$name.geo.json"), File(dir, "$name.json"))
            .firstOrNull { it.isFile }
    }

    fun animFile(project: String, npcId: String, path: String): File? {
        val name = strip(path)
        if (name.isBlank()) return null
        val dir = NpcRegistry.customsAnimations(project, npcId)
        return listOf(File(dir, "$name.animation.json"), File(dir, "$name.json"))
            .firstOrNull { it.isFile }
    }

    fun textureFile(project: String, npcId: String, path: String): File? {
        val name = strip(path)
        if (name.isBlank()) return null
        val dir = NpcRegistry.customsTextures(project, npcId)
        return listOf(File(dir, "$name.png"), File(dir, name))
            .firstOrNull { it.isFile }
    }

    /** Identifier path body for internal pack: npc_customs/<project>/<npcId>/<name> */
    fun internalBody(project: String, npcId: String, path: String): String {
        val name = strip(path)
        return "npc_customs/$project/$npcId/$name"
    }
}