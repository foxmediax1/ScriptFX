package net.foxmediax.scriptfx.scriptengine

import net.fabricmc.loader.api.FabricLoader
import java.io.File

/** Ищет PNG-аватарки в config/scriptfx/projects/<проект>/avatars/ */
object AvatarLoader {

    const val MAX_BYTES = 64 * 1024
    private val PNG_SIGNATURE =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun load(fileName: String): ByteArray? {
        val name = fileName.trim()
        if (name.isEmpty() || name.contains('/') || name.contains('\\') ||
            name.contains(':') || name.contains("..")
        ) {
            ScriptFXLog.warn("Аватар '$fileName': недопустимое имя файла")
            return null
        }

        val projectsDir = FabricLoader.getInstance().configDir.resolve("scriptfx/projects").toFile()
        val file = projectsDir.listFiles()
            ?.map { File(File(it, "avatars"), name) }
            ?.firstOrNull { it.isFile }

        if (file == null) {
            ScriptFXLog.warn("Аватар '$name' не найден (ищу в projects/*/avatars/)")
            return null
        }
        if (file.length() > MAX_BYTES) {
            ScriptFXLog.warn("Аватар '$name' больше ${MAX_BYTES / 1024} КБ — пропущен")
            return null
        }
        val bytes = file.readBytes()
        if (bytes.size < 8 || !bytes.copyOf(8).contentEquals(PNG_SIGNATURE)) {
            ScriptFXLog.warn("Аватар '$name' не является PNG")
            return null
        }
        return bytes
    }
}