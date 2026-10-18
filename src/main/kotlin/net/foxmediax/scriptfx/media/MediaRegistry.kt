package net.foxmediax.scriptfx.media

import net.fabricmc.loader.api.FabricLoader
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import java.io.File

data class SoundEntry(
    val id: String,
    val file: File,
    val project: String
)

data class VideoMeta(
    val id: String,
    val project: String,
    val dir: File,
    val fps: Float,
    val loop: Boolean,
    val soundId: String?,
    val durationSec: Float?,
    val frames: List<File>,
    val sourceMp4: File? = null
)

object MediaRegistry {

    private val sounds = linkedMapOf<String, SoundEntry>()
    private val videos = linkedMapOf<String, VideoMeta>()

    fun projectsRoot(): File =
        FabricLoader.getInstance().configDir.resolve("scriptfx/projects").toFile()

    fun reload() {
        sounds.clear()
        videos.clear()
        val root = projectsRoot()
        if (!root.isDirectory) return

        root.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
            val project = projectDir.name

            // --- audio/*.ogg ---
            File(projectDir, "audio").listFiles()
                ?.filter { it.isFile && it.extension.equals("ogg", ignoreCase = true) }
                ?.forEach { f ->
                    val id = f.nameWithoutExtension
                    val entry = SoundEntry(id, f, project)
                    putSound(id, entry)
                    putSound("$project/$id", entry)
                }

            // --- video/<id>/ ---
            File(projectDir, "video").listFiles()
                ?.filter { it.isFile && it.extension.equals("mp4", ignoreCase = true) }
                ?.forEach { f ->
                    val id = f.nameWithoutExtension
                    // frames пустые — заполнятся при decode
                    val entry = VideoMeta(
                        id = id,
                        project = project,
                        dir = f, // для mp4 dir = сам файл
                        fps = 24f,
                        loop = false,
                        soundId = null,
                        durationSec = null,
                        frames = emptyList(),
                        sourceMp4 = f
                    )
                    putVideo(id, entry)
                    putVideo("$project/$id", entry)
                    ScriptFXLog.info("Video (mp4): $project/$id")
                }
        }
        ScriptFXLog.info("Media: ${sounds.size} sound keys, ${videos.size} video keys")
    }

    private fun putSound(key: String, entry: SoundEntry) {
        sounds[key] = entry
    }

    private fun putVideo(key: String, entry: VideoMeta) {
        videos[key] = entry
    }

    fun sound(id: String): SoundEntry? {
        val k = id.removeSurrounding("\"").trim()
        return sounds[k]
    }

    fun video(id: String): VideoMeta? {
        val k = id.removeSurrounding("\"").trim()
        return videos[k]
    }

    fun listSounds(): List<SoundEntry> =
        sounds.values.distinctBy { it.file.absolutePath }.sortedBy { "${it.project}/${it.id}" }

    fun listVideos(): List<VideoMeta> =
        videos.values.distinctBy { it.dir.absolutePath }.sortedBy { "${it.project}/${it.id}" }

    fun registerCacheSound(id: String, file: File, project: String) {
        sounds[id] = SoundEntry(id, file, project)
    }
}