package net.foxmediax.scriptfx.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

/**
 * Полноценный центрированный чат (замена ванильного ChatScreen
 * при MessageDisplayMode.CENTER).
 *
 * — затемнение всего экрана (HUD почти не видно)
 * — одна панель по центру
 * — история сообщений внутри панели
 * — поле ввода внизу панели
 */
class CenteredChatScreen(
    private val initialText: String = ""
) : Screen(Component.empty()) {

    private lateinit var input: EditBox
    private var scrollOffset = 0
    private var historyIndex = -1
    private val sentHistory = mutableListOf<String>()

    private companion object {
        // Размеры панели (доля экрана)
        const val PANEL_WIDTH_RATIO  = 0.62
        const val PANEL_HEIGHT_RATIO = 0.48
        const val PANEL_MAX_W = 420
        const val PANEL_MIN_W = 220
        const val PANEL_MAX_H = 280
        const val PANEL_MIN_H = 140

        const val PAD = 8
        const val LINE_GAP = 1
        const val INPUT_H = 14
        const val TITLE_H = 14

        // Цвета (Int)
        val COL_DIM      = 0xC0101018.toInt()   // затемнение всего экрана
        val COL_PANEL    = 0xE0181820.toInt()   // фон панели
        val COL_BORDER   = 0xFF4A4A5A.toInt()
        val COL_TITLE_BG = 0xFF1E1E28.toInt()
        val COL_INPUT_BG = 0xFF0C0C12.toInt()
        val COL_TEXT     = 0xFFFFFFFF.toInt()
        val COL_HINT     = 0xFF808080.toInt()
        val COL_SCROLL   = 0xFF606070.toInt()
    }

    override fun isPauseScreen(): Boolean = false
    override fun shouldCloseOnEsc(): Boolean = true

    // Скрываем ванильный HUD, пока открыт наш чат

    override fun init() {
        super.init()

        val (px, py, pw, ph) = panelRect()
        val inputY = py + ph - PAD - INPUT_H
        val inputW = pw - PAD * 2 - 6   // запас под скроллбар

        input = EditBox(font, px + PAD, inputY, inputW, INPUT_H, Component.literal("chat"))
        input.setMaxLength(256)
        input.value = initialText
        input.setBordered(false)
        input.setTextColor(0xFFFFFF)
        addRenderableWidget(input)
        setInitialFocus(input)
    }

    /** x, y, w, h панели */
    private fun panelRect(): IntArray {
        val pw = (width * PANEL_WIDTH_RATIO).toInt().coerceIn(PANEL_MIN_W, PANEL_MAX_W)
        val ph = (height * PANEL_HEIGHT_RATIO).toInt().coerceIn(PANEL_MIN_H, PANEL_MAX_H)
        val px = width / 2 - pw / 2
        val py = height / 2 - ph / 2
        return intArrayOf(px, py, pw, ph)
    }

    override fun extractBackground(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        // полное затемнение — HUD и мир почти не видны
        g.fill(0, 0, width, height, COL_DIM)
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val font = Minecraft.getInstance().font
        val (px, py, pw, ph) = panelRect()

        // --- панель ---
        g.fill(px, py, px + pw, py + ph, COL_PANEL)
        // рамка
        g.fill(px, py, px + pw, py + 1, COL_BORDER)
        g.fill(px, py + ph - 1, px + pw, py + ph, COL_BORDER)
        g.fill(px, py, px + 1, py + ph, COL_BORDER)
        g.fill(px + pw - 1, py, px + pw, py + ph, COL_BORDER)

        // --- заголовок ---
        g.fill(px + 1, py + 1, px + pw - 1, py + 1 + TITLE_H, COL_TITLE_BG)
        g.text(font, Component.literal("Чат"), px + PAD, py + 3, COL_TEXT, false)

        // --- область сообщений ---
        val msgTop = py + TITLE_H + 4
        val msgBottom = py + ph - PAD - INPUT_H - 6
        val msgH = msgBottom - msgTop
        val textW = pw - PAD * 2 - 8
        val lineH = font.lineHeight + LINE_GAP

        val history = collectHistory(font, textW)
        val maxVisible = max(1, msgH / lineH)
        val maxScroll = max(0, history.size - maxVisible)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        val start = max(0, history.size - maxVisible - scrollOffset)
        val end = min(history.size, start + maxVisible)

        var ty = msgTop
        for (i in start until end) {
            val line = history[i]
            g.text(font, line, px + PAD, ty, COL_TEXT, false)
            ty += lineH
        }

        // скроллбар
        if (maxScroll > 0) {
            val trackX = px + pw - 5
            val trackY = msgTop
            val trackH = msgH
            val thumbH = max(12, trackH * maxVisible / history.size)
            val thumbY = trackY + ((trackH - thumbH) * (1f - scrollOffset.toFloat() / maxScroll)).toInt()
            g.fill(trackX, trackY, trackX + 3, trackY + trackH, 0xFF202028.toInt())
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, COL_SCROLL)
        }

        // --- поле ввода (фон) ---
        val inX = input.x
        val inY = input.y
        val inW = input.width
        val inH = input.height
        g.fill(inX - 2, inY - 2, inX + inW + 2, inY + inH + 2, COL_INPUT_BG)
        g.fill(inX - 2, inY - 2, inX + inW + 2, inY - 1, COL_BORDER)
        g.fill(inX - 2, inY + inH + 1, inX + inW + 2, inY + inH + 2, COL_BORDER)
        g.fill(inX - 2, inY - 2, inX - 1, inY + inH + 2, COL_BORDER)
        g.fill(inX + inW + 1, inY - 2, inX + inW + 2, inY + inH + 2, COL_BORDER)

        if (input.value.isEmpty()) {
            g.text(
                font,
                Component.literal("Напишите сообщение...").withStyle(Style.EMPTY.withColor(0x808080)),
                inX,
                inY + 2,
                COL_HINT,
                false
            )
        }

        super.extractRenderState(g, mouseX, mouseY, partialTick)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        when (event.key()) {
            GLFW.GLFW_KEY_ESCAPE -> {
                onClose()
                return true
            }
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                val text = input.value.trim()
                if (text.isNotEmpty()) {
                    sentHistory.add(text)
                    if (sentHistory.size > 50) sentHistory.removeAt(0)
                    historyIndex = -1
                    Minecraft.getInstance().player?.connection?.sendChat(text)
                }
                onClose()
                return true
            }
            GLFW.GLFW_KEY_UP -> {
                if (sentHistory.isNotEmpty()) {
                    historyIndex = min(historyIndex + 1, sentHistory.lastIndex)
                    input.value = sentHistory[sentHistory.lastIndex - historyIndex]
                    input.moveCursorToEnd(false)
                }
                return true
            }
            GLFW.GLFW_KEY_DOWN -> {
                if (historyIndex > 0) {
                    historyIndex--
                    input.value = sentHistory[sentHistory.lastIndex - historyIndex]
                    input.moveCursorToEnd(false)
                } else {
                    historyIndex = -1
                    input.value = ""
                }
                return true
            }
            GLFW.GLFW_KEY_PAGE_UP -> {
                scrollOffset += 3
                return true
            }
            GLFW.GLFW_KEY_PAGE_DOWN -> {
                scrollOffset = max(0, scrollOffset - 3)
                return true
            }
        }
        return super.keyPressed(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY > 0) scrollOffset++
        else if (scrollY < 0) scrollOffset = max(0, scrollOffset - 1)
        return true
    }

    override fun onClose() {
        Minecraft.getInstance().setScreen(null)
    }

    // ---------------- история ----------------

    /** Плоский список строк (каждая строка чата уже с переносами). */
    private fun collectHistory(font: Font, maxTextW: Int): List<FormattedCharSequence> {
        val result = mutableListOf<FormattedCharSequence>()
        val chat = Minecraft.getInstance().gui.chat

        val all = try {
            chat.recentChat
        } catch (_: Exception) {
            emptyList<Any>()
        }

        for (msg in all.takeLast(50)) {
            val component: Component = when (msg) {
                is Component -> msg
                is String -> Component.literal(msg)
                else -> Component.literal(msg.toString())
            }
            result.addAll(font.split(component, maxTextW))
        }
        return result
    }
}