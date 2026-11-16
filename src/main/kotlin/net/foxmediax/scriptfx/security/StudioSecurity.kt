package net.foxmediax.scriptfx.security

import net.fabricmc.loader.api.FabricLoader
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.Minecraft
import net.minecraft.world.level.storage.LevelResource
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

object StudioSecurity {

    private val secureRandom = SecureRandom()
    private val configPath = FabricLoader.getInstance().configDir.resolve("scriptfx-security.json")

    @Volatile
    var unlockedThisSession: Boolean = false
        private set

    data class Store(
        var enabled: Boolean = false,
        var saltHex: String = "",
        var hashHex: String = "",
        var tokenObfuscated: String = ""
    )

    private var cache: Store = Store()
    private var loaded = false

    fun isEnabled(): Boolean {
        ensureLoaded()
        return cache.enabled && cache.hashHex.isNotBlank()
    }

    fun needsUnlock(): Boolean = isEnabled() && !unlockedThisSession

    fun markUnlocked() { unlockedThisSession = true }

    fun lockSession() { unlockedThisSession = false }

    fun setPassword(password: String) {
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val hash = sha256(salt + password.toByteArray(Charsets.UTF_8))
        cache = Store(
            enabled = true,
            saltHex = salt.toHex(),
            hashHex = hash.toHex(),
            tokenObfuscated = obfuscate(hash.toHex())
        )
        unlockedThisSession = true
        loaded = true
        persist()
        ScriptFXLog.info("StudioSecurity: пароль установлен")
    }

    fun clearPassword() {
        cache = Store()
        unlockedThisSession = true
        loaded = true
        persist()
        worldFile()?.delete()
        ScriptFXLog.info("StudioSecurity: пароль снят")
    }

    fun verify(password: String): Boolean {
        ensureLoaded()
        if (!cache.enabled || cache.hashHex.isBlank()) return true
        val salt = cache.saltHex.fromHex() ?: return false
        val hash = sha256(salt + password.toByteArray(Charsets.UTF_8)).toHex()
        val ok = hash.equals(cache.hashHex, ignoreCase = true)
        if (ok) unlockedThisSession = true
        return ok
    }

    fun load() {
        cache = readFile(configPath.toFile())
            ?: worldFile()?.let { readFile(it) }
                    ?: Store()
        loaded = true
        if (cache.enabled) persist()
    }

    private fun ensureLoaded() {
        if (!loaded) load()
    }

    private fun persist() {
        val json = """
            {
              "enabled": ${cache.enabled},
              "saltHex": "${cache.saltHex}",
              "hashHex": "${cache.hashHex}",
              "token": "${cache.tokenObfuscated}"
            }
        """.trimIndent()
        try {
            configPath.parent?.createDirectories()
            configPath.writeText(json)
        } catch (t: Throwable) {
            ScriptFXLog.warn("StudioSecurity: config write: ${t.message}")
        }
        try {
            worldFile()?.writeText(json)
        } catch (t: Throwable) {
            ScriptFXLog.warn("StudioSecurity: world write: ${t.message}")
        }
    }

    private fun worldFile(): File? {
        val server = Minecraft.getInstance().singleplayerServer ?: return null
        return try {
            val root = server.getWorldPath(LevelResource.ROOT).toFile()
            File(root, "scriptfx-studio.lock")
        } catch (_: Throwable) {
            null
        }
    }

    private fun readFile(f: File): Store? {
        if (!f.isFile) return null
        return try {
            val t = f.readText()
            val enabled = Regex(""""enabled"\s*:\s*(true|false)""").find(t)?.groupValues?.get(1) == "true"
            val salt = Regex(""""saltHex"\s*:\s*"([^"]*)"""").find(t)?.groupValues?.get(1).orEmpty()
            val hash = Regex(""""hashHex"\s*:\s*"([^"]*)"""").find(t)?.groupValues?.get(1).orEmpty()
            val token = Regex(""""token"\s*:\s*"([^"]*)"""").find(t)?.groupValues?.get(1).orEmpty()
            Store(enabled, salt, hash, token)
        } catch (_: Throwable) {
            null
        }
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray? = try {
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    } catch (_: Throwable) {
        null
    }

    private fun obfuscate(hex: String): String {
        val alphabet = "!@#$%^&*_+-=[]{}|;:,.<>?"
        val sb = StringBuilder()
        for (c in hex) {
            sb.append(c)
            if (secureRandom.nextBoolean()) {
                sb.append(alphabet[secureRandom.nextInt(alphabet.length)])
            }
        }
        return sb.toString()
    }
}