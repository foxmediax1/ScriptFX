package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.view.Gravity
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_ACCENT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_MUTED
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_TEXT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.dp

/**
 * Заглушка вместо 3D-предпросмотра.
 *
 * Почему: в Minecraft 26.1 / Modern UI 3.13 UI-рендер идёт через
 * deferred Granite-архитектуру, а EffekseerManager (AAA Particles)
 * делает прямые GL-вызовы. Склеить их без кастомного DrawTask нельзя.
 * Поэтому предпросмотр выполняется в мире: «▶ Play» спавнит эффект
 * перед игроком.
 */
class AaaPreviewView(context: Context) : LinearLayout(context) {

    private val nameView: TextView
    private val hintView: TextView

    var effectId: String? = null
        private set

    init {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = ColorDrawable(0xFF08080F.toInt())

        nameView = TextView(context).apply {
            text = "Эффект не выбран"
            textSize = 18f
            setTextColor(COL_TEXT)
            gravity = Gravity.CENTER
        }
        addView(
            nameView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        hintView = TextView(context).apply {
            text = "Выбери AAA-эффект и нажми «▶ Play»\n— он появится в мире перед тобой"
            textSize = 12f
            setTextColor(COL_MUTED)
            gravity = Gravity.CENTER
            setPadding(dp(context, 16), dp(context, 8), dp(context, 16), 0)
        }
        addView(
            hintView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val playHint = TextView(context).apply {
            text = "▶ Play"
            textSize = 16f
            setTextColor(COL_ACCENT)
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 16), 0, 0)
        }
        addView(
            playHint,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    fun setEffect(id: String?) {
        effectId = id
        nameView.text = id ?: "Эффект не выбран"
        invalidate()
    }

    fun setScale(s: Float) { /* не актуально в этом варианте */ }
    fun play() { /* спавн делает ParticlesPage через AaaParticleBridge */ }
    fun stop() { }
}