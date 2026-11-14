package net.foxmediax.scriptfx.client.particles

import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * World-space превью AAA Particles.
 *
 * UI-превью живёт в отдельном AaaUiPreview — здесь только спавн в мире
 * перед камерой игрока (кнопка «▶ Спавн в мире»).
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

    var yaw: Float = 0f
        private set

    var pitch: Float = 0f
        private set

    var distance: Float = 3.5f
        private set

    var scale: Float = 1f
        private set

    var looping: Boolean = true
    var loopSeconds: Float = 3.5f

    private var loopAccum = 0f
    private var lastTickNs = 0L

    fun setBounds(x0: Int, y0: Int, x1: Int, y1: Int) {
        bounds = if (x1 - x0 < 16 || y1 - y0 < 16) null else Bounds(x0, y0, x1, y1)
    }

    fun clearBounds() { bounds = null }

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
     * HUD-хук, вызывается из ScriptFXClient каждый кадр.
     *
     * В 26.1 нет FBO-превью в HUD: world-space эффекты рисует сам AAA Particles
     * в мировом проходе, а UI-превью — AaaUiPreview в CustomDrawable.
     * Поэтому здесь ничего не делаем, но метод нужен для совместимости.
     */
    fun render() {
        // no-op
    }

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

    private fun previewWorldPos(playerPos: Vec3, playerYaw: Float, playerPitch: Float): Vec3 {
        val look = playerLook(playerYaw, playerPitch)
        val base = playerPos.add(
            look.x * distance,
            look.y * distance + 1.0,
            look.z * distance
        )

        val orbitYaw = Math.toRadians(yaw.toDouble())
        val orbitPitch = Math.toRadians(pitch.toDouble())
        val ox = sin(orbitYaw) * 0.35
        val oy = sin(orbitPitch) * 0.25
        val oz = cos(orbitYaw) * 0.35 - 0.35

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