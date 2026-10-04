package net.foxmediax.scriptfx.client

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.foxmediax.scriptfx.ScriptFX
import net.foxmediax.scriptfx.network.CutsceneInterruptPayload
import net.foxmediax.scriptfx.network.CutscenePayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.lwjgl.glfw.GLFW

/**
 * Клиентское состояние катсцены, интерполяция камеры, letterbox и Ctrl+Alt+End.
 *
 * Логика: раз в клиентский тик двигаем "тиковое" состояние (tick*), старое значение
 * уходит в prev*. Каждый кадр mixin вызывает sample(), которая смешивает prev и tick
 * по доле тика, прошедшей с момента последнего тика.
 */
object CutsceneClient {

    var active = false
        private set
    var locked = false
        private set
    var hudHidden = false
        private set
    var letterbox = false
        private set
    var letterboxHeight = 0.15f
        private set

    // ---- Значения для миксина (обновляются в sample()) ----
    var camX = 0.0
        private set
    var camY = 0.0
        private set
    var camZ = 0.0
        private set
    var camYaw = 0f
        private set
    var camPitch = 0f
        private set
    var fovOverride: Float? = null
        private set

    // ---- Состояние на последнем тике и на предыдущем ----
    private var tickX = 0.0; private var tickY = 0.0; private var tickZ = 0.0
    private var tickYaw = 0f; private var tickPitch = 0f
    private var prevX = 0.0; private var prevY = 0.0; private var prevZ = 0.0
    private var prevYaw = 0f; private var prevPitch = 0f

    private var tickFov: Float? = null
    private var prevFov: Float? = null

    private var lastTickNanos = System.nanoTime()
    private var tickCounter = 0L

    // ---- Анимация движения ----
    private var moveFromX = 0.0; private var moveFromY = 0.0; private var moveFromZ = 0.0
    private var moveFromYaw = 0f; private var moveFromPitch = 0f
    private var moveToX = 0.0; private var moveToY = 0.0; private var moveToZ = 0.0
    private var moveToYaw = 0f; private var moveToPitch = 0f
    private var moveStartTick = 0L
    private var moveDurationTicks = 0
    private var isMoving = false

    // ---- Анимация FOV ----
    private var fovFrom = 70f
    private var fovTo = 70f
    private var fovStartTick = 0L
    private var fovDurationTicks = 0
    private var fovAnimating = false

    /** Состояние F1 до катсцены. */
    private var savedHideGui: Boolean? = null

    fun init() {
        ClientPlayNetworking.registerGlobalReceiver(CutscenePayload.TYPE) { payload, _ ->
            receive(payload)
        }

        HudElementRegistry.addLast(ScriptFX.id("cutscene_letterbox"), HudElement { graphics, _ ->
            renderLetterbox(graphics)
        })

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            onTick(client)
            checkInterruptKey(client)
        }
    }

    // ------------------------------------------------------------------
    // Пакеты
    // ------------------------------------------------------------------

    private fun receive(payload: CutscenePayload) {
        val mc = Minecraft.getInstance()
        when (payload.action) {
            CutscenePayload.START -> {
                if (!active) savedHideGui = mc.options.hideGui
                active = true
                locked = false
                hudHidden = false
                letterbox = false
                isMoving = false
                fovAnimating = false
                tickFov = null
                prevFov = null
                val player = mc.player
                if (player != null) {
                    snapTo(player.x, player.eyeY, player.z, player.yRot, player.xRot)
                }
                lastTickNanos = System.nanoTime()
            }

            CutscenePayload.END, CutscenePayload.INTERRUPT_ACK -> clear()

            CutscenePayload.SET -> {
                isMoving = false
                snapTo(payload.x, payload.y, payload.z, payload.yaw, payload.pitch)
            }

            CutscenePayload.MOVE -> {
                moveFromX = tickX; moveFromY = tickY; moveFromZ = tickZ
                moveFromYaw = tickYaw; moveFromPitch = tickPitch
                moveToX = payload.x; moveToY = payload.y; moveToZ = payload.z
                moveToYaw = payload.yaw; moveToPitch = payload.pitch
                moveDurationTicks = payload.durationTicks.coerceAtLeast(1)
                moveStartTick = tickCounter
                isMoving = true
            }

            CutscenePayload.LOCK -> locked = payload.flag

            CutscenePayload.HUD -> {
                hudHidden = payload.flag
                mc.options.hideGui = payload.flag
            }

            CutscenePayload.LETTERBOX -> {
                letterbox = payload.flag
                letterboxHeight = payload.value.coerceIn(0f, 0.4f)
            }

            CutscenePayload.FOV -> {
                val target = payload.value.coerceIn(10f, 170f)
                if (payload.durationTicks <= 0) {
                    fovAnimating = false
                    tickFov = target
                    prevFov = target
                } else {
                    fovFrom = tickFov ?: baseFov()
                    fovTo = target
                    fovStartTick = tickCounter
                    fovDurationTicks = payload.durationTicks
                    fovAnimating = true
                }
            }
        }
    }

    /** Мгновенно ставит камеру в точку без интерполяции. */
    private fun snapTo(x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
        tickX = x; tickY = y; tickZ = z; tickYaw = yaw; tickPitch = pitch
        prevX = x; prevY = y; prevZ = z; prevYaw = yaw; prevPitch = pitch
        camX = x; camY = y; camZ = z; camYaw = yaw; camPitch = pitch
    }

    /** FOV из настроек игрока (без эффектов вроде спринта). */
    private fun baseFov(): Float = Minecraft.getInstance().options.fov().get().toFloat()

    // ------------------------------------------------------------------
    // Тики и кадры
    // ------------------------------------------------------------------

    private fun onTick(client: Minecraft) {
        if (!active) return
        if (client.isPaused) return   // сервер в паузе не тикает, клиент не должен убегать вперёд

        tickCounter++
        lastTickNanos = System.nanoTime()

        prevX = tickX; prevY = tickY; prevZ = tickZ
        prevYaw = tickYaw; prevPitch = tickPitch
        prevFov = tickFov

        advanceMove()
        advanceFov()
    }

    private fun advanceMove() {
        if (!isMoving) return
        val elapsed = tickCounter - moveStartTick
        if (elapsed >= moveDurationTicks) {
            tickX = moveToX; tickY = moveToY; tickZ = moveToZ
            tickYaw = moveToYaw; tickPitch = moveToPitch
            isMoving = false
            return
        }
        val t = elapsed.toFloat() / moveDurationTicks
        val s = smoothstep(t)
        tickX = lerp(moveFromX, moveToX, s)
        tickY = lerp(moveFromY, moveToY, s)
        tickZ = lerp(moveFromZ, moveToZ, s)
        tickYaw = lerpAngle(moveFromYaw, moveToYaw, s)
        tickPitch = lerp(moveFromPitch, moveToPitch, s)
    }

    private fun advanceFov() {
        if (!fovAnimating) return
        val elapsed = tickCounter - fovStartTick
        if (elapsed >= fovDurationTicks) {
            tickFov = fovTo
            fovAnimating = false
            return
        }
        val t = elapsed.toFloat() / fovDurationTicks
        tickFov = lerp(fovFrom, fovTo, smoothstep(t))
    }

    /** Вызывается из CameraMixin раз за кадр, перед чтением camX/camY/... и fovOverride. */
    fun sample() {
        val partial = ((System.nanoTime() - lastTickNanos) / 50_000_000f).coerceIn(0f, 1f)
        camX = lerp(prevX, tickX, partial)
        camY = lerp(prevY, tickY, partial)
        camZ = lerp(prevZ, tickZ, partial)
        camYaw = lerpAngle(prevYaw, tickYaw, partial)
        camPitch = lerp(prevPitch, tickPitch, partial)

        val current = tickFov
        fovOverride = if (current == null) null else lerp(prevFov ?: current, current, partial)
    }

    // ------------------------------------------------------------------
    // Прерывание и сброс
    // ------------------------------------------------------------------

    private fun checkInterruptKey(client: Minecraft) {
        if (!active) return

        val window = (client.window as net.foxmediax.scriptfx.mixin.client.WindowAccessor).handle

        val ctrl = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
        val alt = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS
        val end = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_END) == GLFW.GLFW_PRESS

        if (ctrl && alt && end) {
            if (ClientPlayNetworking.canSend(CutsceneInterruptPayload.TYPE)) {
                ClientPlayNetworking.send(CutsceneInterruptPayload())
            }
            clear()
        }
    }

    fun clear() {
        if (hudHidden) {
            Minecraft.getInstance().options.hideGui = savedHideGui ?: false
        }
        savedHideGui = null

        active = false
        locked = false
        hudHidden = false
        letterbox = false
        isMoving = false
        fovAnimating = false
        tickFov = null
        prevFov = null
        fovOverride = null
    }

    // ------------------------------------------------------------------
    // Рендер
    // ------------------------------------------------------------------

    private fun renderLetterbox(graphics: GuiGraphicsExtractor) {
        if (!active || !letterbox) return
        val mc = Minecraft.getInstance()
        val w = mc.window.guiScaledWidth
        val h = mc.window.guiScaledHeight
        val bar = (h * letterboxHeight).toInt().coerceAtLeast(1)

        graphics.fill(0, 0, w, bar, 0xFF000000.toInt())
        graphics.fill(0, h - bar, w, h, 0xFF000000.toInt())
    }

    // ------------------------------------------------------------------
    // Математика
    // ------------------------------------------------------------------

    private fun smoothstep(t: Float) = t * t * (3f - 2f * t)
    private fun lerp(a: Double, b: Double, t: Float) = a + (b - a) * t
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun lerpAngle(from: Float, to: Float, t: Float): Float {
        var diff = (to - from) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return from + diff * t
    }
}