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

    var currentDir: File = rootDir
        private set

    var entries: List<FileEntry> = emptyList()
        private set

    init { refresh() }

    fun refresh() {
        entries = (currentDir.listFiles()?.toList() ?: emptyList())
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .map { FileEntry(it) }
    }

    fun canGoUp(): Boolean = currentDir != rootDir

    fun goUp() {
        if (canGoUp()) {
            currentDir = currentDir.parentFile ?: rootDir
            refresh()
        }
    }

    fun goInto(entry: FileEntry) {
        if (entry.isDirectory) {
            currentDir = entry.file
            refresh()
        }
    }

    fun goTo(dir: File) {
        currentDir = dir
        refresh()
    }

    fun rename(entry: FileEntry, newName: String): Boolean {
        if (newName.isBlank()) return false
        val target = File(entry.file.parentFile, newName)
        val ok = entry.file.renameTo(target)
        if (ok) refresh()
        return ok
    }

    fun delete(entry: FileEntry): Boolean {
        val ok = entry.file.deleteRecursively()
        if (ok) refresh()
        return ok
    }

    fun createFile(name: String) {
        if (name.isBlank()) return
        val target = File(currentDir, name)
        if (!target.exists()) target.createNewFile()
        refresh()
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        val target = File(currentDir, name)
        if (!target.exists()) target.mkdirs()
        refresh()
    }

    fun createScriptFile(name: String) {
        if (name.isBlank()) return
        val fileName = if (name.endsWith(".sfxs")) name else "$name.sfxs"
        val target = File(currentDir, fileName)
        if (!target.exists()) target.createNewFile()
        refresh()
    }

    /** Всегда создаёт проект в projects/, независимо от того, где сейчас открыт браузер. */
    fun createProject(name: String) {
        if (name.isBlank()) return
        val projectDir = File(projectsDir, name)
        if (!projectDir.exists()) {
            projectDir.mkdirs()
            File(projectDir, "scripts").mkdirs()
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