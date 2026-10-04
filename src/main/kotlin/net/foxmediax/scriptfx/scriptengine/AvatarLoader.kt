package net.foxmediax.scriptfx.scriptengine

import net.fabricmc.loader.api.FabricLoader
import java.io.File

/** Ищет PNG-аватарки в config/scriptfx/projects/<проект>/avatars/ */
object AvatarLoader {

    const val MAX_BYTES = 64 * 1024
    private val PNG_SIGNATURE =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /** bytes == null означает "файл не найден или некорректен" (до /scriptfx reload). */
    private class Cached(val file: File?, val modified: Long, val bytes: ByteArray?)

    private val cache = HashMap<String, Cached>()

    fun clearCache() = cache.clear()

    fun load(fileName: String): ByteArray? {
        val name = fileName.trim()
        if (name.isEmpty() || name.contains('/') || name.contains('\\') ||
            name.contains(':') || name.contains("..")
        ) {
            ScriptFXLog.warn("Аватар '$fileName': недопустимое имя файла")
            return null
        }

        val cached = cache[name]
        if (cached != null) {
            if (cached.bytes == null) return null
            val file = cached.file
            if (file != null && file.isFile && file.lastModified() == cached.modified) {
                return cached.bytes
            }
        }

        val fresh = readFromDisk(name)
        cache[name] = fresh
        return fresh.bytes
    }

    private fun readFromDisk(name: String): Cached {
        val projectsDir = FabricLoader.getInstance().configDir.resolve("scriptfx/projects").toFile()
        val file = projectsDir.listFiles()
            ?.map { File(File(it, "avatars"), name) }
            ?.firstOrNull { it.isFile }

        if (file == null) {
            ScriptFXLog.warn("Аватар '$name' не найден (ищу в projects/*/avatars/)")
            return Cached(null, 0L, null)
        }
        if (file.length() > MAX_BYTES) {
            ScriptFXLog.warn("Аватар '$name' больше ${MAX_BYTES / 1024} КБ — пропущен")
            return Cached(file, file.lastModified(), null)
        }
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            ScriptFXLog.error("Не удалось прочитать аватар '$name'", e)
            return Cached(file, file.lastModified(), null)
        }
        if (bytes.size < 8 || !bytes.copyOf(8).contentEquals(PNG_SIGNATURE)) {
            ScriptFXLog.warn("Аватар '$name' не является PNG")
            return Cached(file, file.lastModified(), null)
        }
        return Cached(file, file.lastModified(), bytes)
    }
}