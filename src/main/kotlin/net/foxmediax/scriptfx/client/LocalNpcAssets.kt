package net.foxmediax.scriptfx.client

import com.google.gson.JsonParser
import com.mojang.blaze3d.platform.NativeImage
import net.foxmediax.scriptfx.npc.NpcCustoms
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object LocalNpcAssets {

    data class Handle(
        val modelId: Identifier,
        val animId: Identifier,
        val textureId: Identifier
    )

    private val handles = ConcurrentHashMap<String, Handle>()
    private val models = ConcurrentHashMap<Identifier, Any>()   // BakedGeoModel
    private val anims = ConcurrentHashMap<Identifier, Any>()    // BakedAnimations

    fun bakedModel(id: Identifier): Any? = models[id]
    fun bakedAnim(id: Identifier): Any? = anims[id]

    fun ensure(
        project: String,
        npcId: String,
        modelPath: String,
        animPath: String,
        texPath: String
    ): Handle? {
        val name = NpcCustoms.sanitizePath(NpcCustoms.strip(modelPath))
        if (name.isBlank()) return null
        val key = "${NpcCustoms.sanitizePath(project)}/${NpcCustoms.sanitizePath(npcId)}/$name"
        handles[key]?.let { return it }

        val modelFile = NpcCustoms.modelFile(project, npcId, modelPath)
        if (modelFile == null) {
            ScriptFXLog.warn("LocalNpcAssets: model file not found for $key (path=$modelPath)")
            return null
        }
        val animFile = NpcCustoms.animFile(project, npcId, animPath.ifBlank { modelPath })
        val texFile = NpcCustoms.textureFile(project, npcId, texPath.ifBlank { modelPath })

        val modelId = id("local/$key/model")
        val animId = id("local/$key/anim")
        val textureId = loadTexture(key, texFile)
            ?: Identifier.fromNamespaceAndPath("scriptfx", "textures/npc/temple_female_skins.png")

        if (!loadModel(modelId, modelFile)) {
            ScriptFXLog.warn("LocalNpcAssets: failed bake model ${modelFile.absolutePath}")
            return null
        }
        if (animFile != null) {
            loadAnim(animId, animFile)
        } else {
            ScriptFXLog.warn("LocalNpcAssets: no anim file for $key")
        }

        val h = Handle(modelId, animId, textureId)
        handles[key] = h
        ScriptFXLog.info("LocalNpcAssets: OK $key model=${modelFile.name}")
        return h
    }

    private fun id(path: String) =
        Identifier.fromNamespaceAndPath("scriptfx", NpcCustoms.sanitizePath(path))

    private fun loadTexture(key: String, file: File?): Identifier? {
        if (file == null || !file.isFile) return null
        val mc = Minecraft.getInstance() ?: return null
        return try {
            val texId = id("dyn_npc/$key")
            file.inputStream().use { ins ->
                val img = NativeImage.read(ins)
                mc.textureManager.register(texId, DynamicTexture({ key }, img))
            }
            texId
        } catch (t: Throwable) {
            ScriptFXLog.warn("LocalNpcAssets texture: ${t.message}")
            null
        }
    }

    private fun loadModel(id: Identifier, file: File): Boolean {
        if (models.containsKey(id)) return true
        return try {
            val json = JsonParser.parseString(file.readText()).asJsonObject
            val baked = bakeModel(id, json) ?: return false
            models[id] = baked
            injectModelCache(id, baked)
            true
        } catch (t: Throwable) {
            ScriptFXLog.warn("loadModel: ${t.message}")
            false
        }
    }

    private fun loadAnim(id: Identifier, file: File): Boolean {
        if (anims.containsKey(id)) return true
        return try {
            val json = JsonParser.parseString(file.readText()).asJsonObject
            val baked = bakeAnim(id, json) ?: return false
            anims[id] = baked
            injectAnimCache(id, baked)
            true
        } catch (t: Throwable) {
            ScriptFXLog.warn("loadAnim: ${t.message}")
            false
        }
    }

    // --- GeckoLib 5 (com.geckolib) bake via reflection ---

    private fun bakeModel(id: Identifier, json: com.google.gson.JsonObject): Any? {
        // 1) private static bakeModel on GeckoLibResources
        val r = invokeStatic(
            "com.geckolib.cache.GeckoLibResources",
            listOf("bakeModel", "bakeGeoModel"),
            arrayOf(id, json)
        )
        if (r != null) return r

        // 2) Geometry / factory — подставь по IDEA, если 1 пусто
        ScriptFXLog.warn(
            "LocalNpcAssets: bakeModel not found. " +
                    "Dump methods: " + methodNames("com.geckolib.cache.GeckoLibResources")
        )
        return null
    }

    private fun bakeAnim(id: Identifier, json: com.google.gson.JsonObject): Any? {
        val r = invokeStatic(
            "com.geckolib.cache.GeckoLibResources",
            listOf("bakeAnimations", "bakeAnimation", "bakeAnims"),
            arrayOf(id, json)
        )
        if (r != null) return r
        ScriptFXLog.warn("LocalNpcAssets: bakeAnim not found")
        return null
    }

    private fun injectModelCache(id: Identifier, baked: Any) {
        try {
            val cache = invokeStatic("com.geckolib.cache.GeckoLibResources", listOf("getBakedModels"), emptyArray())
                ?: return
            putIntoCache(cache, id, baked)
        } catch (t: Throwable) {
            ScriptFXLog.warn("injectModelCache: ${t.message}")
        }
    }

    private fun injectAnimCache(id: Identifier, baked: Any) {
        try {
            val cache = invokeStatic(
                "com.geckolib.cache.GeckoLibResources",
                listOf("getBakedAnimations", "getAnimations"),
                emptyArray()
            ) ?: return
            putIntoCache(cache, id, baked)
        } catch (t: Throwable) {
            ScriptFXLog.warn("injectAnimCache: ${t.message}")
        }
    }

    private fun putIntoCache(cache: Any, id: Identifier, baked: Any) {
        for (m in cache.javaClass.methods) {
            if (m.parameterCount == 2 && m.name in listOf("put", "add", "register", "cache", "addModel")) {
                try {
                    m.isAccessible = true
                    m.invoke(cache, id, baked)
                    return
                } catch (_: Throwable) {
                }
            }
        }
        for (f in cache.javaClass.declaredFields) {
            try {
                f.isAccessible = true
                val v = f.get(cache) ?: continue
                if (v is MutableMap<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    (v as MutableMap<Any, Any>)[id] = baked
                    return
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun invokeStatic(className: String, names: List<String>, args: Array<Any>): Any? {
        return try {
            val c = Class.forName(className)
            for (m in c.declaredMethods) {
                if (m.name !in names) continue
                if (m.parameterCount != args.size) continue
                try {
                    m.isAccessible = true
                    return m.invoke(null, *args)
                } catch (_: Throwable) {
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun methodNames(className: String): String =
        try {
            Class.forName(className).declaredMethods.joinToString { it.name + "/" + it.parameterCount }
        } catch (_: Throwable) {
            "n/a"
        }

    fun clear() {
        handles.clear()
        models.clear()
        anims.clear()
    }
}