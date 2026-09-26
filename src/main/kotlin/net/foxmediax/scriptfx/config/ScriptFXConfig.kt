package net.foxmediax.scriptfx.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.io.IOException
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object ScriptFXConfig {
    // Сами настройки — просто поля с дефолтными значениями
    var showHints: Boolean = true
    var maxScripts: Int = 10
    var theme: String = "dark"

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath = FabricLoader.getInstance().configDir.resolve("scriptfx.json")

    private data class Data(
        var showHints: Boolean = true,
        var maxScripts: Int = 10,
        var theme: String = "dark"
    )

    fun load() {
        if (!configPath.exists()) {
            save()
            return
        }
        try {
            val data = gson.fromJson(configPath.readText(), Data::class.java)
            showHints = data.showHints
            maxScripts = data.maxScripts
            theme = data.theme
        } catch (e: IOException) {
            save() // если файл повреждён — пересоздаём с дефолтами
        }
    }

    fun save() {
        val data = Data(showHints, maxScripts, theme)
        configPath.writeText(gson.toJson(data))
    }
}