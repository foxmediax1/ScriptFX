package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.View
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.LinearLayout
import icyllis.modernui.widget.ScrollView
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_ACCENT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_BTN
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_BTN_SELECTED
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_CONTENT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_DIVIDER
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_MUTED
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_SIDEBAR
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.COL_TEXT
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.MATCH
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.WRAP
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.dp
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.lp
import net.foxmediax.scriptfx.client.mui.panel.PanelUi.px1
import net.foxmediax.scriptfx.client.particles.ParticleRuntime
import net.foxmediax.scriptfx.network.ParticlePayload
import net.foxmediax.scriptfx.particles.ParticleEffect
import net.foxmediax.scriptfx.particles.ParticleRegistry
import net.minecraft.client.Minecraft
import java.io.File

/**
 * UI «Частицы»:
 * список | превью | свойства (Основное / Визуал / Параметры)
 */
class ParticlesPage(host: PanelHost) : PanelPage(host) {

    override val breadcrumb: String get() = "Частицы"

    private lateinit var ctx: Context
    private lateinit var overlays: OverlayHost
    private lateinit var listBox: LinearLayout
    private lateinit var previewTitle: TextView
    private lateinit var previewHint: TextView
    private lateinit var propsHost: LinearLayout
    private lateinit var tabMain: TextView
    private lateinit var tabVisual: TextView
    private lateinit var tabParams: TextView

    private var project: String? = null
    private var selected: ParticleEffect? = null
    private var currentTab = 0 // 0 main, 1 visual, 2 params

    private var uiSize = 1.5f
    private var uiDuration = 3.0f

    override fun onDestroy() {
        if (this::overlays.isInitialized) overlays.clear()
    }

    override fun createView(ctx: Context): View {
        this.ctx = ctx
        ParticleRegistry.reload()
        ensureProject()

        val root = FrameLayout(ctx).apply {
            background = ColorDrawable(COL_CONTENT)
        }
        val overlayLayer = FrameLayout(ctx)
        overlays = OverlayHost(ctx, overlayLayer)

        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        body.addView(buildList(ctx), lp(dp(ctx, 240), MATCH))
        body.addView(vLine(ctx), lp(px1(ctx), MATCH))
        body.addView(buildPreview(ctx), LinearLayout.LayoutParams(0, MATCH, 1.2f))
        body.addView(vLine(ctx), lp(px1(ctx), MATCH))
        body.addView(buildProps(ctx), lp(dp(ctx, 300), MATCH))

        root.addView(body, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(overlayLayer, FrameLayout.LayoutParams(MATCH, MATCH))

        refreshList()
        ParticleRegistry.list().firstOrNull()?.let { select(it) } ?: showEmptyProps()
        return root
    }

    private fun ensureProject() {
        val projects = ParticleRegistry.listProjects()
        project = projects.firstOrNull()
        if (project == null) {
            val root = ParticleRegistry.projectsRoot()
            val def = File(root, "default")
            def.mkdirs()
            File(def, "particles").mkdirs()
            ParticleRegistry.reload()
            project = "default"
        }
    }

    // ------------------------------------------------------------------
    // LEFT
    // ------------------------------------------------------------------

    private fun buildList(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_SIDEBAR)
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
        }

        col.addView(TextView(ctx).apply {
            text = "Эффекты"
            textSize = 13f
            setTextColor(COL_ACCENT)
        }, lp(MATCH, WRAP))

        col.addView(TextView(ctx).apply {
            text = "Проект: ${project ?: "—"}"
            textSize = 11f
            setTextColor(COL_MUTED)
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 8))
        }, lp(MATCH, WRAP))

        col.addView(
            PanelUi.accentButton(ctx, "+ Создать эффект") { createEffectDialog() },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) }
        )
        col.addView(
            PanelUi.flatButton(ctx, "Обновить") {
                ParticleRegistry.reload()
                refreshList()
            },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 8) }
        )
        col.addView(
            PanelUi.flatButton(ctx, "Стоп все") {
                ParticleRuntime.handlePayload(
                    ParticlePayload(ParticlePayload.STOP_ALL, "", "", 0.0, 0.0, 0.0)
                )
                previewHint.text = "Все эмиттеры остановлены"
            },
            lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 10) }
        )

        listBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx)
        scroll.addView(listBox, lp(MATCH, WRAP))
        col.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return col
    }

    private fun refreshList() {
        listBox.removeAllViews()
        val effects = ParticleRegistry.list()
        if (effects.isEmpty()) {
            listBox.addView(
                PanelUi.mutedText(
                    ctx,
                    "Нет эффектов.\nНажми «Создать эффект»\nили положи .particle.json"
                ),
                lp(MATCH, WRAP)
            )
            return
        }

        for (fx in effects) {
            val active = selected?.identifier == fx.identifier
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ColorDrawable(if (active) COL_BTN_SELECTED else COL_BTN)
                setPadding(dp(ctx, 10), dp(ctx, 10), dp(ctx, 10), dp(ctx, 10))
                isClickable = true
                setOnClickListener { select(fx) }
            }
            val icon = when {
                fx.identifier.contains("fire", true) || fx.identifier.contains("flame", true) -> "🔥"
                fx.identifier.contains("snow", true) || fx.identifier.contains("ice", true) -> "❄️"
                fx.identifier.contains("portal", true) -> "🌀"
                fx.identifier.contains("spark", true) || fx.identifier.contains("star", true) -> "✨"
                else -> "🟣"
            }
            val shortName = fx.identifier.substringAfterLast(':').substringAfterLast('.')
            row.addView(TextView(ctx).apply {
                text = "$icon  $shortName"
                textSize = 13f
                setTextColor(COL_TEXT)
                setSingleLine(true)
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            listBox.addView(row, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 6) })
        }
    }

    // ------------------------------------------------------------------
    // CENTER
    // ------------------------------------------------------------------

    private fun buildPreview(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_CONTENT)
        }

        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ColorDrawable(COL_SIDEBAR)
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
        }
        previewTitle = TextView(ctx).apply {
            text = "Превью"
            textSize = 13f
            setTextColor(COL_TEXT)
        }
        bar.addView(previewTitle, LinearLayout.LayoutParams(0, WRAP, 1f))
        bar.addView(
            PanelUi.flatButton(ctx, "▶ Play") { playSelected() },
            lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 6) }
        )
        bar.addView(
            PanelUi.flatButton(ctx, "■ Stop") {
                ParticleRuntime.handlePayload(
                    ParticlePayload(ParticlePayload.STOP_ALL, "", "", 0.0, 0.0, 0.0)
                )
                previewHint.text = "Остановлено"
            },
            lp(WRAP, WRAP)
        )
        col.addView(bar, lp(MATCH, WRAP))

        val stage = FrameLayout(ctx).apply {
            background = ColorDrawable(0xFF12081F.toInt())
        }
        val stageInner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 16))
        }
        stageInner.addView(TextView(ctx).apply {
            text = "✦"
            textSize = 48f
            gravity = Gravity.CENTER
            setTextColor(COL_ACCENT)
        }, lp(WRAP, WRAP))
        previewHint = TextView(ctx).apply {
            text = "Выбери эффект и нажми Play\n(проигрывается у игрока)"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(COL_MUTED)
            setPadding(0, dp(ctx, 10), 0, 0)
        }
        stageInner.addView(previewHint, lp(MATCH, WRAP))
        stage.addView(stageInner, FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER))
        col.addView(stage, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return col
    }

    // ------------------------------------------------------------------
    // RIGHT
    // ------------------------------------------------------------------

    private fun buildProps(ctx: Context): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(COL_SIDEBAR)
            setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
        }

        val tabs = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tabMain = tab(ctx, "Основное", 0)
        tabVisual = tab(ctx, "Визуал", 1)
        tabParams = tab(ctx, "Параметры", 2)
        tabs.addView(tabMain, lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 6) })
        tabs.addView(tabVisual, lp(WRAP, WRAP).apply { rightMargin = dp(ctx, 6) })
        tabs.addView(tabParams, lp(WRAP, WRAP))
        col.addView(tabs, lp(MATCH, WRAP).apply { bottomMargin = dp(ctx, 12) })

        propsHost = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx)
        scroll.addView(propsHost, lp(MATCH, WRAP))
        col.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return col
    }

    private fun tab(ctx: Context, title: String, index: Int): TextView =
        TextView(ctx).apply {
            text = title
            textSize = 12f
            setTextColor(COL_TEXT)
            background = ColorDrawable(if (currentTab == index) COL_BTN_SELECTED else COL_BTN)
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
            isClickable = true
            setOnClickListener {
                currentTab = index
                refreshTabs()
                renderProps()
            }
        }

    private fun refreshTabs() {
        fun style(tv: TextView, idx: Int) {
            tv.background = ColorDrawable(if (currentTab == idx) COL_BTN_SELECTED else COL_BTN)
        }
        if (this::tabMain.isInitialized) {
            style(tabMain, 0)
            style(tabVisual, 1)
            style(tabParams, 2)
        }
    }

    private fun select(fx: ParticleEffect) {
        selected = fx
        uiSize = ((fx.sizeX + fx.sizeY) * 0.5f).coerceIn(0.1f, 5f)
        uiDuration = fx.activeTime.coerceIn(0.1f, 30f)
        previewTitle.text = fx.identifier.substringAfterLast(':')
        previewHint.text = "Готов: ${fx.identifier}"
        refreshList()
        currentTab = 0
        refreshTabs()
        renderProps()
    }

    private fun showEmptyProps() {
        selected = null
        if (!this::propsHost.isInitialized) return
        propsHost.removeAllViews()
        propsHost.addView(PanelUi.mutedText(ctx, "Эффект не выбран"), lp(MATCH, WRAP))
    }

    private fun renderProps() {
        propsHost.removeAllViews()
        val fx = selected
        if (fx == null) {
            showEmptyProps()
            return
        }
        when (currentTab) {
            0 -> renderMain(fx)
            1 -> renderVisual(fx)
            2 -> renderParams(fx)
        }
    }

    private fun renderMain(fx: ParticleEffect) {
        propsHost.addView(label("Название"), lp(MATCH, WRAP))
        propsHost.addView(value(fx.identifier), fieldLp())

        propsHost.addView(label("Тип"), lp(MATCH, WRAP))
        val type = when (fx.rateMode) {
            ParticleEffect.RateMode.INSTANT -> "Взрыв / Instant"
            ParticleEffect.RateMode.STEADY -> "Поток / Steady"
        }
        propsHost.addView(value(type), fieldLp())

        propsHost.addView(label("Lifetime mode"), lp(MATCH, WRAP))
        propsHost.addView(value(fx.lifetimeMode.name), fieldLp())

        propsHost.addView(label("Размер (UI)"), lp(MATCH, WRAP))
        propsHost.addView(sliderRow(uiSize, 0.1f, 5f) { uiSize = it }, fieldLp())

        propsHost.addView(label("Длительность (UI)"), lp(MATCH, WRAP))
        propsHost.addView(sliderRow(uiDuration, 0.5f, 15f) { uiDuration = it }, fieldLp())

        val entry = ParticleRegistry.getEntry(fx.identifier)
        if (entry != null) {
            propsHost.addView(label("Файл"), lp(MATCH, WRAP))
            propsHost.addView(
                value("${entry.project}/particles/${entry.file.name}"),
                fieldLp()
            )
        }

        propsHost.addView(
            PanelUi.accentButton(ctx, "▶ Проиграть") { playSelected() },
            lp(MATCH, WRAP).apply { topMargin = dp(ctx, 4) }
        )
        propsHost.addView(
            PanelUi.dangerButton(ctx, "Удалить эффект") { deleteSelected() },
            lp(MATCH, WRAP).apply { topMargin = dp(ctx, 8) }
        )
    }

    private fun renderVisual(fx: ParticleEffect) {
        propsHost.addView(label("Material"), lp(MATCH, WRAP))
        propsHost.addView(value(fx.material), fieldLp())

        propsHost.addView(label("Texture"), lp(MATCH, WRAP))
        propsHost.addView(value(fx.texture), fieldLp())

        propsHost.addView(label("Color RGBA"), lp(MATCH, WRAP))
        propsHost.addView(
            value("%.2f / %.2f / %.2f / %.2f".format(fx.colorR, fx.colorG, fx.colorB, fx.colorA)),
            fieldLp()
        )

        propsHost.addView(label("Size X/Y"), lp(MATCH, WRAP))
        propsHost.addView(value("%.3f / %.3f".format(fx.sizeX, fx.sizeY)), fieldLp())

        propsHost.addView(label("UV"), lp(MATCH, WRAP))
        propsHost.addView(
            value("x=%.1f y=%.1f w=%.1f h=%.1f".format(fx.uvX, fx.uvY, fx.uvW, fx.uvH)),
            fieldLp()
        )

        fx.flipbook?.let {
            propsHost.addView(label("Flipbook frames"), lp(MATCH, WRAP))
            propsHost.addView(value(it.maxFrame.toString()), fieldLp())
        }
    }

    private fun renderParams(fx: ParticleEffect) {
        propsHost.addView(label("Spawn rate"), lp(MATCH, WRAP))
        propsHost.addView(value(fx.spawnRate.toString()), fieldLp())

        propsHost.addView(label("Max particles"), lp(MATCH, WRAP))
        propsHost.addView(value(fx.maxParticles.toString()), fieldLp())

        propsHost.addView(label("Active time"), lp(MATCH, WRAP))
        propsHost.addView(value("${fx.activeTime}s"), fieldLp())

        propsHost.addView(label("Particle lifetime"), lp(MATCH, WRAP))
        propsHost.addView(value("${fx.particleLifetime}s"), fieldLp())

        propsHost.addView(label("Initial speed"), lp(MATCH, WRAP))
        propsHost.addView(value(fx.initialSpeed.toString()), fieldLp())

        propsHost.addView(label("Acceleration"), lp(MATCH, WRAP))
        propsHost.addView(
            value("%.2f / %.2f / %.2f".format(fx.accelX, fx.accelY, fx.accelZ)),
            fieldLp()
        )

        propsHost.addView(label("Shape"), lp(MATCH, WRAP))
        propsHost.addView(value(fx.shape::class.simpleName ?: "—"), fieldLp())
    }

    // ------------------------------------------------------------------
    // create / delete / play
    // ------------------------------------------------------------------

    private fun createEffectDialog() {
        val p = project ?: return
        overlays.input("Имя эффекта (без пробелов)", "magic_burst") { raw ->
            val name = raw.trim()
            if (name.isEmpty()) return@input
            overlays.actions(
                "Тип эффекта",
                listOf(
                    "Взрыв (instant)" to {
                        val id = ParticleRegistry.create(p, name, ParticleRegistry.CreateType.BURST)
                        afterCreate(id)
                        Unit
                    },
                    "Поток (steady)" to {
                        val id = ParticleRegistry.create(p, name, ParticleRegistry.CreateType.STEADY)
                        afterCreate(id)
                        Unit
                    }
                )
            )
        }
    }

    private fun afterCreate(id: String?) {
        if (id == null) {
            previewHint.text = "Не удалось создать (уже есть?)"
            return
        }
        refreshList()
        ParticleRegistry.get(id)?.let { select(it) }
        previewHint.text = "Создан: $id"
    }

    private fun deleteSelected() {
        val fx = selected ?: return
        overlays.confirm(
            "Удалить эффект «${fx.identifier}» и файл?",
            "Удалить",
            "Отмена",
            onYes = {
                ParticleRegistry.delete(fx.identifier)
                selected = null
                refreshList()
                showEmptyProps()
                previewTitle.text = "Превью"
                previewHint.text = "Удалён"
                Unit
            }
        )
    }

    private fun playSelected() {
        val fx = selected ?: run {
            previewHint.text = "Сначала выбери эффект"
            return
        }
        val mc = Minecraft.getInstance()
        val player = mc.player
        if (player == null) {
            previewHint.text = "Нужен мир / игрок"
            return
        }
        ParticleRuntime.handlePayload(
            ParticlePayload(
                ParticlePayload.PLAY,
                fx.identifier,
                "",
                player.x,
                player.y + 1.0,
                player.z
            )
        )
        previewHint.text = "Play: ${fx.identifier}\nsize≈$uiSize  duration≈$uiDuration"
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun label(t: String) = PanelUi.mutedText(ctx, t, 11f)

    private fun value(t: String) = TextView(ctx).apply {
        text = t
        textSize = 13f
        setTextColor(COL_TEXT)
        setPadding(0, dp(ctx, 4), 0, 0)
    }

    private fun fieldLp() = lp(MATCH, WRAP).apply {
        topMargin = dp(ctx, 2)
        bottomMargin = dp(ctx, 12)
    }

    private fun sliderRow(
        value: Float,
        min: Float,
        max: Float,
        onChange: (Float) -> Unit
    ): View {
        var cur = value
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ColorDrawable(COL_BTN)
            setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
        }
        val valueView = TextView(ctx).apply {
            text = "%.1f".format(cur)
            textSize = 12f
            setTextColor(COL_TEXT)
            gravity = Gravity.CENTER
        }
        fun apply(delta: Float) {
            cur = (cur + delta).coerceIn(min, max)
            valueView.text = "%.1f".format(cur)
            onChange(cur)
        }
        row.addView(TextView(ctx).apply {
            text = "−"
            textSize = 16f
            setTextColor(COL_TEXT)
            setPadding(dp(ctx, 8), 0, dp(ctx, 8), 0)
            isClickable = true
            setOnClickListener { apply(-0.1f) }
        }, lp(WRAP, WRAP))
        row.addView(valueView, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(TextView(ctx).apply {
            text = "+"
            textSize = 16f
            setTextColor(COL_TEXT)
            setPadding(dp(ctx, 8), 0, dp(ctx, 8), 0)
            isClickable = true
            setOnClickListener { apply(0.1f) }
        }, lp(WRAP, WRAP))
        return row
    }

    private fun vLine(ctx: Context) =
        View(ctx).apply { background = ColorDrawable(COL_DIVIDER) }
}