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
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Клиентское состояние катсцены, интерполяция камеры, letterbox, Ctrl+Alt+End
 * и диалоговая камера (подлёт / отлёт к NPC).
 *
 * Логика: раз в клиентский тик двигаем tick*, старое уходит в prev*.
 * Каждый кадр mixin вызывает sample() — смешивание prev/tick.
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

    /** FOV-приближение для диалога (и без полной катсцены). */
    var dialogFovActive = false
        private set

    /** Клиентская камера диалога (без серверной катсцены). */
    var dialogCam = false
        private set

    val cameraControlled: Boolean
        get() = active || dialogCam

    private var dialogRestoreFov = 70f
    private var pendingDialogFovClear = false
    private var dialogReturnTimeout = -1

    // ---- Состояние на последнем тике и на предыдущем ----
    private var tickX = 0.0
    private var tickY = 0.0
    private var tickZ = 0.0
    private var tickYaw = 0f
    private var tickPitch = 0f
    private var prevX = 0.0
    private var prevY = 0.0
    private var prevZ = 0.0
    private var prevYaw = 0f
    private var prevPitch = 0f

    private var tickFov: Float? = null
    private var prevFov: Float? = null

    private var lastTickNanos = System.nanoTime()
    private var tickCounter = 0L

    // ---- Анимация движения ----
    private var moveFromX = 0.0
    private var moveFromY = 0.0
    private var moveFromZ = 0.0
    private var moveFromYaw = 0f
    private var moveFromPitch = 0f
    private var moveToX = 0.0
    private var moveToY = 0.0
    private var moveToZ = 0.0
    private var moveToYaw = 0f
    private var moveToPitch = 0f
    private var moveStartTick = 0L
    private var moveDurationTicks = 0
    private var isMoving = false

    // ---- Анимация FOV ----
    private var fovFrom = 70f
    private var fovTo = 70f
    private var fovStartTick = 0L
    private var fovDurationTicks = 0
    private var fovAnimating = false

    // ---- Точка возврата после диалога ----
    private var retX = 0.0
    private var retY = 0.0
    private var retZ = 0.0
    private var retYaw = 0f
    private var retPitch = 0f

    private var onMoveDone: (() -> Unit)? = null

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
                val target = if (payload.value <= 0f) baseFov() else payload.value.coerceIn(10f, 170f)
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

    private fun snapTo(x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
        tickX = x; tickY = y; tickZ = z; tickYaw = yaw; tickPitch = pitch
        prevX = x; prevY = y; prevZ = z; prevYaw = yaw; prevPitch = pitch
        camX = x; camY = y; camZ = z; camYaw = yaw; camPitch = pitch
    }

    private fun baseFov(): Float =
        Minecraft.getInstance().options.fov().get().toFloat()

    // ------------------------------------------------------------------
    // Тики
    // ------------------------------------------------------------------

    private fun onTick(client: Minecraft) {
        if (client.isPaused) return
        if (!active && !dialogCam && !dialogFovActive) return

        tickCounter++
        lastTickNanos = System.nanoTime()

        prevX = tickX; prevY = tickY; prevZ = tickZ
        prevYaw = tickYaw; prevPitch = tickPitch
        prevFov = tickFov

        if (active || dialogCam) advanceMove()
        advanceFov()

        // watchdog: если return «завис» — принудительный сброс
        if (dialogReturnTimeout >= 0) {
            dialogReturnTimeout--
            if (dialogReturnTimeout < 0 && (dialogCam || dialogFovActive)) {
                val cb = onMoveDone
                onMoveDone = null
                clearDialogCamera()
                cb?.invoke()
            }
        }
    }

    private fun advanceMove() {
        if (!isMoving) return
        val elapsed = (tickCounter - moveStartTick).toInt()
        if (elapsed >= moveDurationTicks) {
            tickX = moveToX; tickY = moveToY; tickZ = moveToZ
            tickYaw = moveToYaw; tickPitch = moveToPitch
            isMoving = false
            val cb = onMoveDone
            onMoveDone = null
            cb?.invoke()
            return
        }
        val t = elapsed.toFloat() / moveDurationTicks.coerceAtLeast(1)
        val s = smoothstep(t)
        tickX = lerp(moveFromX, moveToX, s)
        tickY = lerp(moveFromY, moveToY, s)
        tickZ = lerp(moveFromZ, moveToZ, s)
        tickYaw = lerpAngle(moveFromYaw, moveToYaw, s)
        tickPitch = lerp(moveFromPitch, moveToPitch, s)
    }

    private fun advanceFov() {
        if (!fovAnimating) {
            if (pendingDialogFovClear && !active) {
                pendingDialogFovClear = false
                dialogFovActive = false
                tickFov = null
                prevFov = null
                fovOverride = null
            }
            return
        }
        val elapsed = tickCounter - fovStartTick
        if (elapsed >= fovDurationTicks) {
            tickFov = fovTo
            fovAnimating = false
            if (pendingDialogFovClear && !active) {
                pendingDialogFovClear = false
                dialogFovActive = false
                tickFov = null
                prevFov = null
                fovOverride = null
            }
            return
        }
        val t = elapsed.toFloat() / fovDurationTicks.coerceAtLeast(1)
        tickFov = lerp(fovFrom, fovTo, smoothstep(t))
    }

    /** Вызывается из CameraMixin раз за кадр. */
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

        // без NpcDialogClient.close() — иначе цикл close → return → clear
        clearDialogCamera()
    }

    /** Полный сброс диалоговой камеры + возврат взгляда игроку. */
    fun clearDialogCamera() {
        dialogCam = false
        dialogFovActive = false
        pendingDialogFovClear = false
        dialogReturnTimeout = -1
        isMoving = false
        fovAnimating = false
        tickFov = null
        prevFov = null
        fovOverride = null
        onMoveDone = null

        val p = Minecraft.getInstance().player
        if (p != null) {
            p.yRot = retYaw
            p.xRot = retPitch
            p.yRotO = retYaw
            p.xRotO = retPitch
            p.yHeadRot = retYaw
        }
    }

    // ------------------------------------------------------------------
    // Рендер letterbox
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

    // ------------------------------------------------------------------
    // Диалог: FOV-only
    // ------------------------------------------------------------------

    fun startDialogFocus(targetFov: Float = 42f, durationTicks: Int = 8) {
        val base = (tickFov ?: baseFov()).coerceIn(10f, 170f)
        dialogRestoreFov = base
        val target = targetFov.coerceIn(10f, 170f)

        fovFrom = base
        fovTo = target
        fovStartTick = tickCounter
        fovDurationTicks = durationTicks.coerceAtLeast(1)
        fovAnimating = true

        if (tickFov == null) {
            tickFov = base
            prevFov = base
        }
        dialogFovActive = true
        lastTickNanos = System.nanoTime()
    }

    fun endDialogFocus(durationTicks: Int = 8) {
        if (!dialogFovActive) return

        val from = tickFov ?: baseFov()
        val to = if (active) from else dialogRestoreFov

        if (!active && kotlin.math.abs(from - to) < 0.05f) {
            clearDialogCamera()
            return
        }

        fovFrom = from
        fovTo = to
        fovStartTick = tickCounter
        fovDurationTicks = durationTicks.coerceAtLeast(1)
        fovAnimating = true
        lastTickNanos = System.nanoTime()

        if (!active) {
            dialogFovActive = true
            pendingDialogFovClear = true
        }
    }

    // ------------------------------------------------------------------
    // Диалог: подлёт / отлёт
    // ------------------------------------------------------------------

    /**
     * Подлёт к NPC. Камера едет от глаз игрока к точке между игроком и NPC
     * и смотрит на NPC. Стартовый угол = текущий взгляд игрока.
     */
    fun startDialogApproach(
        npcX: Double,
        npcY: Double,
        npcZ: Double,
        durationTicks: Int = 12,
        approach: Float = 0.40f, // больше не используем как долю пути
        targetFov: Float = 42f,
        distance: Double = 1.6,  // блоков от NPC
        onDone: (() -> Unit)? = null
    ) {
        val mc = Minecraft.getInstance()
        val p = mc.player ?: run {
            onDone?.invoke()
            return
        }

        val fromX = p.x
        val fromY = p.eyeY
        val fromZ = p.z
        val fromYaw = p.yRot
        val fromPitch = p.xRot

        retX = fromX; retY = fromY; retZ = fromZ
        retYaw = fromYaw; retPitch = fromPitch

        // точка на луче NPC → игрок, на расстоянии distance от NPC
        var dx = fromX - npcX
        var dz = fromZ - npcZ
        val len = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001)
        dx /= len
        dz /= len

        val dist = distance.coerceIn(1.0, 3.5)
        val toX = npcX + dx * dist
        val toY = npcY // глаза NPC
        val toZ = npcZ + dz * dist

        val (lookYaw, lookPitch) = lookAngles(toX, toY, toZ, npcX, npcY, npcZ)

        tickX = fromX; tickY = fromY; tickZ = fromZ
        tickYaw = fromYaw; tickPitch = fromPitch
        prevX = fromX; prevY = fromY; prevZ = fromZ
        prevYaw = fromYaw; prevPitch = fromPitch

        moveFromX = fromX; moveFromY = fromY; moveFromZ = fromZ
        moveFromYaw = fromYaw; moveFromPitch = fromPitch
        moveToX = toX; moveToY = toY; moveToZ = toZ
        moveToYaw = lookYaw; moveToPitch = lookPitch
        moveStartTick = tickCounter
        moveDurationTicks = durationTicks.coerceAtLeast(1)
        isMoving = true
        onMoveDone = onDone

        val base = baseFov()
        dialogRestoreFov = base
        fovFrom = base
        fovTo = targetFov.coerceIn(30f, 60f)
        fovStartTick = tickCounter
        fovDurationTicks = durationTicks.coerceAtLeast(1)
        fovAnimating = true
        tickFov = base
        prevFov = base
        dialogFovActive = true

        dialogCam = true
        dialogReturnTimeout = -1
        lastTickNanos = System.nanoTime()
    }

    /** Отлёт обратно к сохранённой позе игрока. */
    fun startDialogReturn(
        durationTicks: Int = 12,
        onDone: (() -> Unit)? = null
    ) {
        if (!dialogCam && !dialogFovActive) {
            clearDialogCamera()
            onDone?.invoke()
            return
        }

        if (!dialogCam) {
            clearDialogCamera()
            onDone?.invoke()
            return
        }

        moveFromX = tickX; moveFromY = tickY; moveFromZ = tickZ
        moveFromYaw = tickYaw; moveFromPitch = tickPitch
        moveToX = retX; moveToY = retY; moveToZ = retZ
        moveToYaw = retYaw; moveToPitch = retPitch
        moveStartTick = tickCounter
        moveDurationTicks = durationTicks.coerceAtLeast(1)
        isMoving = true

        val fromFov = tickFov ?: baseFov()
        fovFrom = fromFov
        fovTo = dialogRestoreFov
        fovStartTick = tickCounter
        fovDurationTicks = durationTicks.coerceAtLeast(1)
        fovAnimating = true
        dialogFovActive = true

        dialogReturnTimeout = durationTicks + 5

        onMoveDone = {
            clearDialogCamera()
            onDone?.invoke()
        }
        lastTickNanos = System.nanoTime()
    }

    private fun lookAngles(
        ox: Double, oy: Double, oz: Double,
        tx: Double, ty: Double, tz: Double
    ): Pair<Float, Float> {
        val dx = tx - ox
        val dy = ty - oy
        val dz = tz - oz
        val horiz = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = Math.toDegrees(-atan2(dy, horiz)).toFloat()
        return yaw to pitch.coerceIn(-90f, 90f)
    }
}