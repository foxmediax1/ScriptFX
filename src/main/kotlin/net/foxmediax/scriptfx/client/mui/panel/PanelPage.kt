package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.View
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.CutsceneClient
import net.foxmediax.scriptfx.client.NpcCustomsLoader
import net.foxmediax.scriptfx.gui.Documentation
import net.foxmediax.scriptfx.gui.PanelSection
import net.foxmediax.scriptfx.npc.NpcDefinition
import net.foxmediax.scriptfx.npc.NpcRegistry
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.Minecraft
import net.foxmediax.scriptfx.client.NpcPreviewHelper
import net.foxmediax.scriptfx.client.mui.UiStyle
import net.foxmediax.scriptfx.particles.ParticleRegistry
import net.foxmediax.scriptfx.client.particles.ParticleRuntime
import net.foxmediax.scriptfx.network.ParticlePayload
import net.foxmediax.scriptfx.particles.ParticleEffect
import net.foxmediax.scriptfx.npc.NpcModelPreset
import java.awt.Canvas

interface PanelHost {
    fun navigate(section: PanelSection?)
    fun requestLeave(proceed: () -> Unit)
    fun refreshBreadcrumb()
    fun closePanel()
    fun openSettings()
    fun openDocumentation()
    fun openScriptHints()
}

abstract class PanelPage(protected val host: PanelHost) {
    abstract fun createView(ctx: Context): View
    open val breadcrumb: String? = null
    open fun goUp(): Boolean = false
    open fun requestLeave(proceed: () -> Unit) = proceed()
    open fun onDestroy() {}
    open val interceptsEscape: Boolean get() = false
    open fun onEscape() {}
}

class PlaceholderPage(host: PanelHost, private val name: String) : PanelPage(host) {
    override val breadcrumb: String? get() = name

    override fun createView(ctx: Context): View {
        val root = FrameLayout(ctx)
        root.background = ColorDrawable(0xFF000000.toInt())
        val tv = TextView(ctx).apply {
            text = "Раздел «$name» — в разработке"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(PanelUi.COL_MUTED)
        }
        root.addView(
            tv,
            FrameLayout.LayoutParams(PanelUi.WRAP, PanelUi.WRAP, Gravity.CENTER)
        )
        return root
    }
}

class LogsPage(host: PanelHost) : PanelPage(host) {

        override val breadcrumb: String? get() = "Логи"

        private lateinit var box: LinearLayout
        private lateinit var scroll: ScrollView
        private var lastCount = -1

        override fun createView(ctx: Context): View {
            val root = PanelUi.pageRoot(ctx)

            root.addView(
                PanelUi.toolbar(
                    ctx,
                    "Логи ScriptFX",
                    "Обновить" to { refresh() }
                ),
                PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
            )

            scroll = ScrollView(ctx)
            box = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, PanelUi.dp(ctx, 8), 0, 0)
            }
            scroll.addView(box, PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP))
            root.addView(scroll, LinearLayout.LayoutParams(PanelUi.MATCH, 0, 1f))

            refresh()
            // лёгкий polling, пока страница жива
            box.post(object : Runnable {
                override fun run() {
                    if (!box.isAttachedToWindow) return
                    refresh()
                    box.postDelayed(this, 1500L)
                }
            })
            return root
        }

        private fun refresh() {
            if (!this::box.isInitialized) return
            box.removeAllViews()
            val ctx = box.context
            val entries = ScriptFXLog.getRecentLogs()

            if (entries.isEmpty()) {
                box.addView(
                    PanelUi.mutedText(ctx, "Лог пуст"),
                    PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
                )
                return
            }

            for (line in entries.takeLast(200)) {
                val color = when {
                    line.startsWith("[ERROR]") -> PanelUi.COL_ERROR
                    line.startsWith("[WARN]") -> PanelUi.COL_WARNING
                    line.startsWith("[DEBUG]") -> PanelUi.COL_MUTED
                    else -> PanelUi.COL_TEXT
                }

                box.addView(
                    TextView(ctx).apply {
                        text = line
                        textSize = 12f
                        setTextColor(color)
                        setPadding(0, PanelUi.dp(ctx, 1), 0, PanelUi.dp(ctx, 1))
                    },
                    PanelUi.lp(PanelUi.MATCH, PanelUi.WRAP)
                )
            }
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }