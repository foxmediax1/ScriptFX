package net.foxmediax.scriptfx.client.ui

import net.foxmediax.scriptfx.gui.FileBrowser
import net.foxmediax.scriptfx.gui.FileEntry
import java.io.File

object StudioSession {
    val browser = FileBrowser()

    var selectedProject: File? = null
        private set

    fun listProjects(): List<File> {
        browser.goTo(browser.projectsDir)
        browser.refresh()
        return browser.entries
            .filter { it.isDirectory }
            .map { it.file }
            .sortedBy { it.name.lowercase() }
    }

    fun selectProject(dir: File?) {
        selectedProject = dir?.takeIf { it.isDirectory }
        if (dir != null && dir.isDirectory) {
            browser.goTo(dir)
        }
    }

    fun createProject(name: String): File? {
        val n = name.trim()
        if (n.isEmpty()) return null
        browser.createProject(n)
        val dir = File(browser.projectsDir, n)
        if (dir.isDirectory) {
            selectProject(dir)
            return dir
        }
        return null
    }

    fun deleteProject(dir: File): Boolean {
        if (!dir.isDirectory) return false
        val ok = browser.delete(FileEntry(dir))
        if (ok && selectedProject?.canonicalFile == dir.canonicalFile) {
            selectedProject = null
        }
        return ok
    }
}