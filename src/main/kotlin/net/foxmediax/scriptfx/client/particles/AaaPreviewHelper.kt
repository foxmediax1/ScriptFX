package net.foxmediax.scriptfx.client.particles

import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Превью AAA Particles для UI (Minecraft 26.1).
 *
 * FBO/старый RenderTarget API в 26.1 убран (GpuTexture + RenderPass).
 * Поэтому превью = спавн эффекта в мире перед камерой через AaaParticleBridge.
 * Orbit/zoom влияют на точку спавна относительно взгляда игрока.
 */
object AaaPreviewHelper {

    data class Bounds(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        val width get() = x1 - x0
        val height get() = y1 - y0
        val valid get() = width > 16 && height > 16
    }

    @Volatile var bounds: Bounds? = null
        private set

    @Volatile var effectId: Identifier? = null
        private set

    @Volatile var loadError: String? = null
        private set

    @Volatile var playing: Boolean = false
        private set

    /** Угол вокруг точки превью (горизонталь), градусы */
    var yaw: Float = 0f
        private set

    /** Наклон, градусы */
    var pitch: Float = 0f
        private set

    /** Дистанция от игрока до точки эффекта */
    var distance: Float = 3.5f
        private set

    var scale: Float = 1f
        private set

    var looping: Boolean = true

    /** Интервал перезапуска one-shot (сек) */
    var loopSeconds: Float = 3.5f

    private var loopAccum = 0f
    private var lastTickNs = 0L

    // ------------------------------------------------------------------ UI API

    fun setBounds(x0: Int, y0: Int, x1: Int, y1: Int) {
        bounds = if (x1 - x0 < 16 || y1 - y0 < 16) null else Bounds(x0, y0, x1, y1)
    }

    fun clearBounds() {
        bounds = null
    }

    fun drag(dx: Float, dy: Float) {
        yaw += dx * 0.45f
        pitch = (pitch + dy * 0.45f).coerceIn(-60f, 60f)
    }

    fun zoom(delta: Float) {
        distance = (distance - delta * 0.25f).coerceIn(1.5f, 10f)
    }

    fun setScale(v: Float) {
        scale = v.coerceIn(0.05f, 8f)
    }

    fun resetCamera() {
        yaw = 0f
        pitch = 0f
        distance = 3.5f
    }

    fun play(id: Identifier) {
        if (!AaaParticleBridge.available) {
            loadError = "AAA Particles не загружен"
            playing = false
            return
        }
        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.player == null) {
            loadError = "Нет мира — зайди в singleplayer"
            playing = false
            return
        }

        effectId = id
        loadError = null
        playing = true
        loopAccum = 0f
        lastTickNs = System.nanoTime()

        spawnOnce()
        ScriptFXLog.info("AaaPreview: play $id scale=$scale dist=$distance")
    }

    fun stop() {
        playing = false
        loopAccum = 0f
        // Мировые эмиттеры AAA гасятся только через их native stop / выход из мира.
        // Повторный play пересоздаёт эмиттер.
        ScriptFXLog.info("AaaPreview: stopped")
    }

    fun clear() {
        stop()
        effectId = null
        bounds = null
        loadError = null
        resetCamera()
        scale = 1f
    }

    /**
     * Вызывать каждый клиентский тик (из ScriptFXClient) — loop restart.
     */
    fun clientTick() {
        if (!playing) return
        val id = effectId ?: return
        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.player == null) {
            loadError = "Нет мира"
            playing = false
            return
        }

        val now = System.nanoTime()
        val dt = if (lastTickNs == 0L) 0.05f
        else ((now - lastTickNs) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.2f)
        lastTickNs = now

        if (!looping) return
        loopAccum += dt
        if (loopAccum >= loopSeconds) {
            loopAccum = 0f
            spawnOnce()
        }
    }

    /**
     * HUD-хук. В 26.1 без FBO ничего не рисуем — эффект виден в мире.
     */
    fun render() {
        // no-op: world-space preview
    }

    // ------------------------------------------------------------------ spawn

    private fun spawnOnce() {
        val id = effectId ?: return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: run {
            loadError = "Нет игрока"
            return
        }

        try {
            val pos = previewWorldPos(player.position(), player.yRot, player.xRot)
            AaaParticleBridge.play(
                id.toString(),
                pos.x,
                pos.y,
                pos.z,
                scale
            )
            loadError = null
        } catch (t: Throwable) {
            loadError = t.message ?: "spawn failed"
            ScriptFXLog.warn("AaaPreview spawn failed: ${t.message}")
        }
    }

    /**
     * Точка перед игроком + offset от orbit (yaw/pitch UI).
     */
    private fun previewWorldPos(playerPos: Vec3, playerYaw: Float, playerPitch: Float): Vec3 {
        val totalYaw = playerYaw + yaw
        val totalPitch = (playerPitch + pitch).coerceIn(-89f, 89f)
        val yawRad = Math.toRadians(-totalYaw.toDouble() - 180.0) // MC look
        val pitchRad = Math.toRadians(-totalPitch.toDouble())

        // Minecraft look vector
        val look = playerLook(playerYaw, playerPitch)
        val base = playerPos.add(
            look.x * distance,
            look.y * distance + 1.0,
            look.z * distance
        )

        // лёгкий orbit offset в плоскости «вокруг» точки
        val orbitYaw = Math.toRadians(yaw.toDouble())
        val orbitPitch = Math.toRadians(pitch.toDouble())
        val ox = sin(orbitYaw) * 0.35
        val oy = sin(orbitPitch) * 0.25
        val oz = cos(orbitYaw) * 0.35 - 0.35

        // подавляем unused warning
        @Suppress("UNUSED_EXPRESSION")
        yawRad; pitchRad

        return base.add(ox, oy, oz)
    }

    private fun playerLook(yaw: Float, pitch: Float): Vec3 {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        val cosPitch = cos(pitchRad)
        val x = -sin(yawRad) * cosPitch
        val y = -sin(pitchRad)
        val z = cos(yawRad) * cosPitch
        return Vec3(x, y, z)
    }
}