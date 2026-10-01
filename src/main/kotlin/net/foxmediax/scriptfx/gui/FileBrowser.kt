package net.foxmediax.scriptfx.gui

import net.fabricmc.loader.api.FabricLoader
import java.io.File

data class FileEntry(
    val file: File,
    val name: String = file.name,
    val isDirectory: Boolean = file.isDirectory
)

class FileBrowser {
    val rootDir: File = FabricLoader.getInstance().configDir.resolve("scriptfx").toFile().apply { mkdirs() }
    val projectsDir: File = File(rootDir, "projects").apply { mkdirs() }

    /** Начальная и корневая папка браузера - выше неё подняться нельзя. */
    var currentDir: File = projectsDir
        private set

    var entries: List<FileEntry> = emptyList()
        private set

    init { refresh() }

    /** Лежит ли путь внутри projects (или это сама projects). */
    private fun isInsideProjects(file: File): Boolean {
        val target = file.canonicalFile
        val root = projectsDir.canonicalFile
        return target == root || target.path.startsWith(root.path + File.separator)
    }

    /** Имя без путей и служебных символов - чтобы нельзя было выйти за пределы projects. */
    private fun isSafeName(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed == "." || trimmed == "..") return false
        if (trimmed.contains('/') || trimmed.contains('\\') || trimmed.contains(':')) return false
        return true
    }

    fun refresh() {
        if (!currentDir.exists() || !currentDir.isDirectory) {
            projectsDir.mkdirs()
            currentDir = projectsDir
        }
        entries = (currentDir.listFiles()?.toList() ?: emptyList())
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .map { FileEntry(it) }
    }

    fun canGoUp(): Boolean = currentDir != projectsDir

    fun goUp() {
        if (!canGoUp()) return
        val parent = currentDir.parentFile
        currentDir = if (parent != null && isInsideProjects(parent)) parent else projectsDir
        refresh()
    }

    fun goInto(entry: FileEntry) {
        if (entry.isDirectory && isInsideProjects(entry.file)) {
            currentDir = entry.file
            refresh()
        }
    }

    fun goTo(dir: File) {
        if (!isInsideProjects(dir)) return
        currentDir = dir
        refresh()
    }

    fun rename(entry: FileEntry, newName: String): Boolean {
        if (!isSafeName(newName)) return false
        val target = File(entry.file.parentFile, newName.trim())
        if (!isInsideProjects(target)) return false
        val ok = entry.file.renameTo(target)
        if (ok) refresh()
        return ok
    }

    fun delete(entry: FileEntry): Boolean {
        if (!isInsideProjects(entry.file) || entry.file.canonicalFile == projectsDir.canonicalFile) return false
        val ok = entry.file.deleteRecursively()
        if (ok) refresh()
        return ok
    }

    fun createFile(name: String) {
        if (!isSafeName(name)) return
        val target = File(currentDir, name.trim())
        if (!target.exists()) target.createNewFile()
        refresh()
    }

    fun createFolder(name: String) {
        if (!isSafeName(name)) return
        val target = File(currentDir, name.trim())
        if (!target.exists()) target.mkdirs()
        refresh()
    }

    fun createScriptFile(name: String) {
        if (!isSafeName(name)) return
        val trimmed = name.trim()
        val fileName = if (trimmed.endsWith(".sfxs")) trimmed else "$trimmed.sfxs"
        val target = File(currentDir, fileName)
        if (!target.exists()) target.createNewFile()
        refresh()
    }

    /** Всегда создаёт проект в projects/, независимо от того, где сейчас открыт браузер. */
    fun createProject(name: String) {
        if (!isSafeName(name)) return
        val projectDir = File(projectsDir, name.trim())
        if (!projectDir.exists()) {
            projectDir.mkdirs()
            File(projectDir, "scripts").mkdirs()
            File(projectDir, "avatars").mkdirs()
        }
        goTo(projectDir)
    }

    fun isAtProjectsRoot(): Boolean = currentDir == projectsDir

    fun isInsideProjectRoot(): Boolean = currentDir.parentFile == projectsDir

    fun isInsideProjectScripts(): Boolean =
        currentDir.name == "scripts" && currentDir.parentFile?.parentFile == projectsDir

    fun breadcrumbPath(): String =
        currentDir.toRelativeString(rootDir).ifEmpty { "" }.replace(File.separatorChar, '/')
}