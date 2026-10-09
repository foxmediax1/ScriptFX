package net.foxmediax.scriptfx.client.mui.panel

import icyllis.modernui.core.Context
import icyllis.modernui.graphics.drawable.ColorDrawable
import icyllis.modernui.view.Gravity
import icyllis.modernui.view.View
import icyllis.modernui.widget.FrameLayout
import icyllis.modernui.widget.TextView
import net.foxmediax.scriptfx.gui.PanelSection

/** Что страница может попросить у панели. */
interface PanelHost {
    fun navigate(section: PanelSection?)              // с проверкой выхода из текущей страницы
    fun requestLeave(proceed: () -> Unit)             // выполнить proceed, если страница отпускает
    fun refreshBreadcrumb()
    fun closePanel()
    fun openSettings()
    fun openDocumentation()
    fun openScriptHints()
}

abstract class PanelPage(protected val host: PanelHost) {
    abstract fun createView(ctx: Context): View

    /** Хвост хлебных крошек; null — взять название раздела. */
    open val breadcrumb: String? = null

    /** Кнопка ▲: true, если страница сама поднялась на уровень выше. */
    open fun goUp(): Boolean = false

    /** Страница с несохранёнными данными покажет диалог и вызовет proceed позже. */
    open fun requestLeave(proceed: () -> Unit) = proceed()

    open fun onDestroy() {}

    open val interceptsEscape: Boolean get() = false

    open fun onEscape() {}
}

private fun centeredText(ctx: Context, text: String): View = FrameLayout(ctx).apply {
    background = ColorDrawable(0xFF000000.toInt())
    addView(
        TextView(ctx).apply {
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(PanelUi.COL_MUTED)
        },
        FrameLayout.LayoutParams(PanelUi.WRAP, PanelUi.WRAP, Gravity.CENTER)
    )
}

class HomePage(host: PanelHost) : PanelPage(host) {
    override fun createView(ctx: Context): View = centeredText(
        ctx,
        "Это главное меню мода, для\nработы с проектом откройте\nсоответствующий раздел,\nкоторый находится слева.\n\n←"
    )
}

class PlaceholderPage(host: PanelHost, private val name: String) : PanelPage(host) {
    override fun createView(ctx: Context): View =
        centeredText(ctx, "Раздел «$name» — в разработке")
}