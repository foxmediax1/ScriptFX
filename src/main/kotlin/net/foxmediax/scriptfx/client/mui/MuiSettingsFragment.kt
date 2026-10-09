package net.foxmediax.scriptfx.client.mui

import icyllis.modernui.core.Context
import icyllis.modernui.fragment.Fragment
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.text.Editable
import icyllis.modernui.text.TextWatcher
import icyllis.modernui.util.DataSet
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.KeyEvent
import icyllis.modernui.view.LayoutInflater
import icyllis.modernui.view.View
import icyllis.modernui.view.ViewGroup
import icyllis.modernui.widget.Button
import icyllis.modernui.widget.EditText
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.SeekBar
import icyllis.modernui.widget.Switch
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.CenterMessageOverlay
import net.foxmediax.scriptfx.config.MessageDisplayMode
import net.foxmediax.scriptfx.config.ScriptFXConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.glfw.GLFW
import kotlin.math.min

/**
 * Экран настроек мода на Modern UI (замена Cloth Config).
 * Значения пишутся в ScriptFXConfig сразу, на диск сохраняются при закрытии.
 * Всё, что трогает Minecraft (смена экрана), идёт через Minecraft.execute (основной поток).
 */
class MuiSettingsFragment(
    private val parent: Screen?,
    private val onBack: (() -> Unit)?
) : Fragment() {

    constructor(parent: Screen?) : this(parent, null)
    constructor() : this(null, null)

    private enum class ColorTarget { BG, BORDER }

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }

    private var pageSettings: View? = null
    private var pagePicker: View? = null

    private val hexFields = HashMap<ColorTarget, EditText>()
    private val swatches = HashMap<ColorTarget, View>()
    private var previewBorder: FrameLayout? = null
    private var previewInner: TextView? = null

    // страница палитры
    private var picker: ColorPickerView? = null
    private var pickerHex: EditText? = null
    private var pickerPreview: View? = null
    private var pickerTarget = ColorTarget.BG
    private var draft = 0
    private var suppress = false

    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: DataSet?
    ): View {
        val ctx = requireContext()
        val root = FrameLayout(ctx)

        val panel = FrameLayout(ctx).apply {
            background = ColorDrawable(0xE61A1A1E.toInt())
        }

        val settings = buildSettingsPage(ctx)
        val pickerPage = buildPickerPage(ctx)
        pageSettings = settings
        pagePicker = pickerPage
        panel.addView(settings, FrameLayout.LayoutParams(MATCH, MATCH))
        panel.addView(pickerPage, FrameLayout.LayoutParams(MATCH, MATCH))
        pickerPage.visibility = View.GONE

        refreshPreview()

        val maxH = (ctx.resources.displayMetrics.heightPixels * 0.88f).toInt()
        root.addView(
            panel,
            FrameLayout.LayoutParams(dp(ctx, 460), min(dp(ctx, 540), maxH), Gravity.CENTER)
        )
        return root
    }

    override fun onDestroyView() {
        ScriptFXConfig.save()
        pageSettings = null
        pagePicker = null
        hexFields.clear()
        swatches.clear()
        previewBorder = null
        previewInner = null
        picker = null
        pickerHex = null
        pickerPreview = null
        super.onDestroyView()
    }

    // ------------------------------------------------------------------
    // Страница настроек

    private fun buildSettingsPage(ctx: Context): View {
        val page = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        page.addView(
            TextView(ctx).apply {
                text = "Настройки ScriptFX"
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 12)
            },
            lp(MATCH, WRAP)
        )

        val list = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 4, 4, 4)
        }
        val scroll = ScrollView(ctx)
        scroll.addView(list, ViewGroup.LayoutParams(MATCH, WRAP))
        page.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // --- Общие ---
        list.addView(sectionTitle(ctx, "Общие"), lp(MATCH, WRAP))

        val modes = MessageDisplayMode.values()
        val modeBtn = Button(ctx).apply {
            text = ScriptFXConfig.messageMode.displayName
            setOnClickListener {
                val next = modes[(ScriptFXConfig.messageMode.ordinal + 1) % modes.size]
                ScriptFXConfig.messageMode = next
                text = next.displayName
            }
        }
        list.addView(row(ctx, "Вид сообщений скриптов и чата", modeBtn), lp(MATCH, WRAP))

        list.addView(
            switchRow(ctx, "Системные сообщения по центру", ScriptFXConfig.centerSystemMessages) {
                ScriptFXConfig.centerSystemMessages = it
            }, lp(MATCH, WRAP)
        )
        list.addView(
            switchRow(ctx, "Показывать подсказки", ScriptFXConfig.showHints) {
                ScriptFXConfig.showHints = it
            }, lp(MATCH, WRAP)
        )
        list.addView(
            switchRow(ctx, "Обводка NPC в диалоге", ScriptFXConfig.npcOutline) {
                ScriptFXConfig.npcOutline = it
            }, lp(MATCH, WRAP)
        )

        // максимум скриптов: 1..50
        val seekLabel = TextView(ctx).apply {
            text = "Макс. количество скриптов: ${ScriptFXConfig.maxScripts}"
            textSize = 14f
            setPadding(0, 8, 0, 4)
        }
        val seek = SeekBar(ctx).apply {
            max = 49
            progress = ScriptFXConfig.maxScripts - 1
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                ScriptFXConfig.maxScripts = progress + 1
                seekLabel.text = "Макс. количество скриптов: ${progress + 1}"
            }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })
        list.addView(seekLabel, lp(MATCH, WRAP))
        list.addView(seek, lp(MATCH, WRAP))

        val themeEdit = EditText(ctx).apply {
            setSingleLine(true)
            textSize = 14f
            setText(ScriptFXConfig.theme)
        }
        themeEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                ScriptFXConfig.theme = s.toString()
            }
        })
        escHandler(themeEdit)
        list.addView(row(ctx, "Тема оформления", themeEdit, dp(ctx, 140)), lp(MATCH, WRAP))

        // --- Сообщения: цвета ---
        list.addView(sectionTitle(ctx, "Цвета сообщений"), lp(MATCH, WRAP))
        list.addView(colorRow(ctx, "Фон сообщения", ColorTarget.BG), lp(MATCH, WRAP))
        list.addView(colorRow(ctx, "Обводка сообщения", ColorTarget.BORDER), lp(MATCH, WRAP))

        // приблизительный предпросмотр
        val border = FrameLayout(ctx).apply { setPadding(2, 2, 2, 2) }
        val inner = TextView(ctx).apply {
            text = "[Пример]: Так выглядит сообщение"
            textSize = 13f
            setPadding(12, 8, 12, 8)
        }
        border.addView(inner, FrameLayout.LayoutParams(MATCH, WRAP))
        previewBorder = border
        previewInner = inner
        list.addView(border, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = 8 })

        val showBtn = Button(ctx).apply {
            text = "Показать в игре"
            setOnClickListener {
                val mc = Minecraft.getInstance()
                mc.execute { CenterMessageOverlay.preview() }
            }
        }
        list.addView(showBtn, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = 8 })

        // --- Готово ---
        val done = Button(ctx).apply {
            text = "Готово"
            setOnClickListener { closeScreen() }
        }
        page.addView(done, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = 12 })

        return page
    }

    private fun colorRow(ctx: Context, label: String, target: ColorTarget): View {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 6, 0, 6)
        }
        box.addView(
            TextView(ctx).apply { text = label; textSize = 14f; setPadding(0, 0, 0, 4) },
            lp(MATCH, WRAP)
        )

        val line = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // образец цвета: клик открывает палитру
        val swatch = View(ctx).apply {
            background = ColorDrawable(colorOf(target))
            setOnClickListener { openPicker(target) }
        }
        swatches[target] = swatch
        line.addView(swatch, lp(dp(ctx, 40), dp(ctx, 24)).apply { rightMargin = 8 })

        // HEX-поле
        val hex = EditText(ctx).apply {
            setSingleLine(true)
            textSize = 14f
            setText(ColorHex.format(colorOf(target)))
        }
        hexFields[target] = hex
        hex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (suppress) return
                val parsed = ColorHex.parse(s.toString(), colorOf(target) ushr 24) ?: return
                setColorOf(target, parsed)
                swatches[target]?.background = ColorDrawable(parsed)
                refreshPreview()
            }
        })
        escHandler(hex)
        line.addView(hex, lp(0, WRAP).also { it.weight = 1f })

        val paletteBtn = Button(ctx).apply {
            text = "Палитра"
            setOnClickListener { openPicker(target) }
        }
        line.addView(paletteBtn, lp(WRAP, WRAP).apply { leftMargin = 8 })

        box.addView(line, lp(MATCH, WRAP))
        return box
    }

    // ------------------------------------------------------------------
    // Страница палитры

    private fun buildPickerPage(ctx: Context): View {
        val page = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(20, 20, 20, 20)
        }

        page.addView(
            TextView(ctx).apply {
                text = "Палитра"
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 16)
            },
            lp(MATCH, WRAP)
        )

        val pk = ColorPickerView(ctx, dp(ctx, 160), dp(ctx, 20), dp(ctx, 8))
        picker = pk
        page.addView(pk, lp(pk.totalWidth, pk.totalHeight))

        val hexRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 16)
        }
        val prev = View(ctx)
        pickerPreview = prev
        hexRow.addView(prev, lp(dp(ctx, 40), dp(ctx, 24)).apply { rightMargin = 8 })

        val hex = EditText(ctx).apply {
            setSingleLine(true)
            textSize = 14f
        }
        pickerHex = hex
        hex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (suppress) return
                val parsed = ColorHex.parse(s.toString(), draft ushr 24) ?: return
                draft = parsed
                picker?.setColor(parsed)
                pickerPreview?.background = ColorDrawable(parsed)
            }
        })
        escHandler(hex)
        hexRow.addView(hex, lp(dp(ctx, 150), WRAP))
        page.addView(hexRow, lp(WRAP, WRAP))

        pk.onChange = { argb ->
            draft = argb
            suppress = true
            pickerHex?.setText(ColorHex.format(argb))
            suppress = false
            pickerPreview?.background = ColorDrawable(argb)
        }

        val buttons = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val apply = Button(ctx).apply {
            text = "Применить"
            setOnClickListener { applyPicked() }
        }
        val cancel = Button(ctx).apply {
            text = "Отмена"
            setOnClickListener { backToSettings() }
        }
        buttons.addView(apply, lp(WRAP, WRAP))
        buttons.addView(cancel, lp(WRAP, WRAP).apply { leftMargin = 8 })
        page.addView(buttons, lp(WRAP, WRAP))

        return page
    }

    private fun openPicker(target: ColorTarget) {
        pickerTarget = target
        draft = colorOf(target)
        picker?.setColor(draft)
        suppress = true
        pickerHex?.setText(ColorHex.format(draft))
        suppress = false
        pickerPreview?.background = ColorDrawable(draft)
        pageSettings?.visibility = View.GONE
        pagePicker?.visibility = View.VISIBLE
    }

    private fun applyPicked() {
        setColorOf(pickerTarget, draft)
        suppress = true
        hexFields[pickerTarget]?.setText(ColorHex.format(draft))
        suppress = false
        swatches[pickerTarget]?.background = ColorDrawable(draft)
        refreshPreview()
        backToSettings()
    }

    private fun backToSettings() {
        pagePicker?.visibility = View.GONE
        pageSettings?.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------------

    private fun colorOf(t: ColorTarget): Int =
        if (t == ColorTarget.BG) ScriptFXConfig.messageBg else ScriptFXConfig.messageBorder

    private fun setColorOf(t: ColorTarget, argb: Int) {
        if (t == ColorTarget.BG) ScriptFXConfig.messageBg = argb
        else ScriptFXConfig.messageBorder = argb
    }

    private fun refreshPreview() {
        previewBorder?.background = ColorDrawable(ScriptFXConfig.messageBorder)
        previewInner?.background = ColorDrawable(ScriptFXConfig.messageBg)
    }

    private fun closeScreen() {
        val mc = Minecraft.getInstance()
        val back = onBack
        mc.execute { if (back != null) back() else mc.setScreen(parent) }
    }

    /** ESC в поле ввода: назад из палитры или закрыть настройки (с первого нажатия). */
    private fun escHandler(edit: EditText) {
        edit.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (pagePicker?.visibility == View.VISIBLE) backToSettings() else closeScreen()
                true
            } else false
        }
    }

    // --- мелкие помощники построения ---

    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)

    private fun sectionTitle(ctx: Context, title: String) = TextView(ctx).apply {
        text = title
        textSize = 15f
        setPadding(0, 14, 0, 6)
    }

    private fun row(ctx: Context, label: String, control: View, controlW: Int = WRAP): LinearLayout {
        val r = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 6, 0, 6)
        }
        r.addView(TextView(ctx).apply { text = label; textSize = 14f }, lp(0, WRAP).also { it.weight = 1f })
        r.addView(control, lp(controlW, WRAP))
        return r
    }

    private fun switchRow(ctx: Context, label: String, initial: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val sw = Switch(ctx)
        sw.isChecked = initial
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
        return row(ctx, label, sw)
    }

    private fun dp(ctx: Context, value: Int): Int {
        val density = ctx.resources.displayMetrics.density
        return (value * density + 0.5f).toInt()
    }
}