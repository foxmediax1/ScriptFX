package net.foxmediax.scriptfx.media

import net.fabricmc.loader.api.FabricLoader
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object Mp4Decoder {

    var ffmpegPath: String? = null

    /** Ключ videoId -> future декодирования (чтобы не запускать ffmpeg дважды). */
    private val decoding = ConcurrentHashMap<String, CompletableFuture<VideoMeta?>>()

    private fun cacheRoot(): File =
        FabricLoader.getInstance().configDir.resolve("scriptfx/cache/video").toFile()
            .apply { mkdirs() }

    private fun ffmpegBin(): String = ffmpegPath?.takeIf { it.isNotBlank() } ?: "ffmpeg"

    /**
     * Неблокирующий вариант: декодирует в общем пуле, повторные вызовы того же
     * видео возвращают тот же future. Вызывайте из скриптов через WaitUntil { future.isDone }.
     */
    fun ensureDecodedAsync(meta: VideoMeta): CompletableFuture<VideoMeta?> {
        val key = "${meta.project}/${meta.id}"
        return decoding.computeIfAbsent(key) {
            CompletableFuture.supplyAsync { ensureDecoded(meta) }
                .whenComplete { _, _ -> decoding.remove(key) }
        }
    }

    fun ensureDecoded(meta: VideoMeta): VideoMeta? {
        val mp4 = meta.sourceMp4 ?: return meta
        if (!mp4.isFile) {
            ScriptFXLog.warn("Mp4Decoder: missing ${mp4.path}")
            return null
        }

        val outDir = File(cacheRoot(), "${meta.project}_${meta.id}")
        val framesDir = File(outDir, "frames")
        val audioFile = File(outDir, "audio.ogg")
        val stampFile = File(outDir, "source.stamp")
        val stamp = "${mp4.absolutePath}|${mp4.lastModified()}|${mp4.length()}"

        val needRebuild = !framesDir.isDirectory ||
                framesDir.listFiles()?.none { it.extension.equals("png", true) } != false ||
                !stampFile.isFile ||
                runCatching { stampFile.readText() }.getOrNull() != stamp

        if (needRebuild) {
            ScriptFXLog.info("Mp4Decoder: decoding ${mp4.name} …")
            outDir.mkdirs()
            framesDir.mkdirs()
            framesDir.listFiles()?.forEach { it.delete() }

            val pattern = File(framesDir, "%06d.png").absolutePath
            val ok = runFfmpeg(
                listOf(
                    ffmpegBin(), "-y", "-i", mp4.absolutePath,
                    "-vf", "fps=24", "-q:v", "3", pattern
                ),
                600
            )
            if (!ok) {
                ScriptFXLog.warn("Mp4Decoder: ffmpeg failed — install ffmpeg and add to PATH")
                return null
            }

            runFfmpeg(
                listOf(
                    ffmpegBin(), "-y", "-i", mp4.absolutePath,
                    "-vn", "-c:a", "libvorbis", "-q:a", "6", audioFile.absolutePath
                ),
                300
            )
            stampFile.writeText(stamp)
        }

        val frames = framesDir.listFiles()
            ?.filter { it.extension.equals("png", true) }
            ?.sortedBy { it.name }
            .orEmpty()
        if (frames.isEmpty()) return null

        val soundId = if (audioFile.isFile) {
            val sid = "_cache_${meta.project}_${meta.id}"
            MediaRegistry.registerCacheSound(sid, audioFile, meta.project)
            sid
        } else meta.soundId

        return meta.copy(
            fps = 24f,
            durationSec = frames.size / 24f,
            frames = frames,
            soundId = soundId,
            dir = outDir
        )
    }

    private fun runFfmpeg(cmd: List<String>, timeoutSec: Long): Boolean {
        return try {
            val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val output = StringBuilder()
            Thread {
                p.inputStream.bufferedReader().use { r ->
                    var line: String? = r.readLine()
                    while (line != null) {
                        if (output.length < 4096) output.appendLine(line)
                        line = r.readLine()
                    }
                }
            }.apply { isDaemon = true }.start()

            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                ScriptFXLog.warn("Mp4Decoder: ffmpeg timeout (${timeoutSec}s) для ${cmd[2]}")
                return false
            }
            if (p.exitValue() != 0) {
                ScriptFXLog.warn("Mp4Decoder: ffmpeg exit=${p.exitValue()} для ${cmd[2]}: ${output.take(500)}")
                return false
            }
            true
        } catch (e: Exception) {
            ScriptFXLog.warn("Mp4Decoder: ${e.message}")
            false
        }
    }
}