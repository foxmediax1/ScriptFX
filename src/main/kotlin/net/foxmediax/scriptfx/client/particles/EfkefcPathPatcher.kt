package net.foxmediax.scriptfx.client.particles

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

object EfkefcPathPatcher {

    private val LOG: Logger = LoggerFactory.getLogger("ScriptFX/Efkefc")

    /**
     * Матчит абсолютный Windows-путь с любым разделителем: / или \.
     */
    private val ABS_PATH_REGEX = Regex(
        """[A-Za-z]:[\\/](?:[^\\/:*?"<>|\r\n\x00]+[\\/])*[^\\/:*?"<>|\r\n\x00]+"""
    )

    fun patch(file: Path): Boolean {
        if (!Files.exists(file)) return false
        val bytes = Files.readAllBytes(file)
        var changed = false

        changed = patchUtf8(bytes) or changed
        changed = patchUtf16Le(bytes) or changed
        changed = patchUtf16Be(bytes) or changed

        if (changed) {
            Files.write(file, bytes)
            LOG.info("EfkefcPathPatcher: patched {}", file.fileName)
        } else {
            // Диагностика: чем на самом деле богат файл
            val probe = if (file.fileName.toString().contains("Gun8")) diagnose(bytes) else ""
            if (probe.isNotEmpty()) LOG.info("DIAG {}: {}", file.fileName, probe)
        }
        return changed
    }

    // ── UTF-8 ────────────────────────────────────────────────────────────
    private fun patchUtf8(bytes: ByteArray): Boolean {
        val text = String(bytes, Charsets.ISO_8859_1)
        val matches = ABS_PATH_REGEX.findAll(text).toList().reversed()
        var changed = false
        for (m in matches) {
            val original = m.value
            val relative = toRelative(original) ?: continue
            if (relative.length >= original.length) continue
            val padded = relative.padEnd(original.length, '\u0000')
            val start = m.range.first
            for (i in padded.indices) {
                val code = padded[i].code
                if (code > 0xFF) continue
                bytes[start + i] = code.toByte()
            }
            changed = true
            LOG.debug("  UTF-8: '{}' → '{}'", original, relative)
        }
        return changed
    }

    // ── UTF-16LE ─────────────────────────────────────────────────────────
    private fun patchUtf16Le(bytes: ByteArray): Boolean = patchUtf16(bytes, littleEndian = true)

    // ── UTF-16BE ─────────────────────────────────────────────────────────
    private fun patchUtf16Be(bytes: ByteArray): Boolean = patchUtf16(bytes, littleEndian = false)

    private fun patchUtf16(bytes: ByteArray, littleEndian: Boolean): Boolean {
        val b0: Byte = if (littleEndian) 0 else 1  // индекс NUL внутри пары
        val b1: Byte = if (littleEndian) 1 else 0

        var changed = false
        var i = 0
        while (i + 6 <= bytes.size) {
            // X,0,:,0,\,0  (LE)   или   0,X,0,:,0,\  (BE)
            if (isDriveLetter(bytes[i + if (littleEndian) 0 else 1]) &&
                bytes[i + b1] == 0.toByte() &&
                bytes[i + (if (littleEndian) 2 else 3)] == ':'.code.toByte() &&
                bytes[i + (if (littleEndian) 3 else 2)] == 0.toByte() &&
                bytes[i + (if (littleEndian) 4 else 5)] in listOf('\\'.code.toByte(), '/'.code.toByte()) &&
                bytes[i + (if (littleEndian) 5 else 4)] == 0.toByte()
            ) {
                var j = i
                while (j + 1 < bytes.size &&
                    bytes[j + b1] == 0.toByte() &&
                    bytes[j + b0].toInt() != 0 &&
                    isValidPathChar(bytes[j + b0].toInt())
                ) {
                    j += 2
                }
                if (j > i + 6) {
                    val chunk = bytes.copyOfRange(i, j)
                    val original = String(chunk, if (littleEndian) Charsets.UTF_16LE else Charsets.UTF_16BE)
                    val relative = toRelative(original)
                    if (relative != null && relative.length < original.length) {
                        val padded = relative.padEnd(original.length, '\u0000')
                        val paddedBytes = padded.toByteArray(if (littleEndian) Charsets.UTF_16LE else Charsets.UTF_16BE)
                        if (paddedBytes.size <= j - i) {
                            System.arraycopy(paddedBytes, 0, bytes, i, paddedBytes.size)
                            changed = true
                            LOG.debug("  UTF-16: '{}' → '{}'", original, relative)
                        }
                    }
                }
                i = j
            } else {
                i++
            }
        }
        return changed
    }

    private fun isDriveLetter(b: Byte): Boolean {
        val c = b.toInt().toChar()
        return c in 'A'..'Z' || c in 'a'..'z'
    }

    private fun isValidPathChar(code: Int): Boolean {
        val c = code.toChar()
        return c.isLetterOrDigit() || c in "\\/:._- +()[]{}!@#\$%^&="
    }

    private fun toRelative(absolute: String): String? {
        val parts = absolute.split('\\', '/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val fileName = parts.last()
        if (fileName.length == 2 && fileName[1] == ':') return null
        val folder = parts.getOrNull(parts.size - 2) ?: return fileName
        if (folder.length == 2 && folder[1] == ':') return fileName
        return "$folder/$fileName"
    }

    // ── Диагностика: показывает, в каких кодировках лежит кусок пути ─────
    private fun diagnose(bytes: ByteArray): String {
        val probes = listOf("Line01", "Users", "Dmitry1", "Parts", "C:")
        return probes.joinToString(" ") { probe ->
            val u8 = contains(bytes, probe.toByteArray(Charsets.UTF_8))
            val le = contains(bytes, probe.toByteArray(Charsets.UTF_16LE))
            val be = contains(bytes, probe.toByteArray(Charsets.UTF_16BE))
            "$probe[u8=$u8,u16le=$le,u16be=$be]"
        }
    }

    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        for (i in 0..haystack.size - needle.size) {
            var ok = true
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) { ok = false; break }
            }
            if (ok) return true
        }
        return false
    }
}