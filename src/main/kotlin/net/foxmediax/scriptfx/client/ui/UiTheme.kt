package net.foxmediax.scriptfx.client.ui

/**
 * Цвета и метрики 1:1 с PanelUi (Modern UI студия).
 */
object UiTheme {
    // --- PanelUi colors ---
    const val BG = 0xFF000000.toInt()           // COL_CONTENT
    const val BG_PANEL = 0xF01E1E1E.toInt()     // COL_PANEL
    const val BG_SIDEBAR = 0xFF1A1A1A.toInt()   // COL_SIDEBAR
    const val BG_HEADER = 0xFF252525.toInt()    // COL_HEADER
    const val BG_CARD = 0xFF101014.toInt()      // COL_CARD
    const val BG_BTN = 0xFF101010.toInt()       // COL_BTN
    const val BG_HOVER = 0xFF1A1A22.toInt()
    const val BG_ACTIVE = 0xFF2A2140.toInt()    // COL_BTN_SELECTED

    const val ACCENT = 0xFFB026FF.toInt()       // COL_ACCENT
    const val ACCENT_SOFT = 0xFFC8B4FF.toInt()  // UiStyle.ACCENT (текст/обводка)
    const val ACCENT_DIM = 0xFF7A5CE0.toInt()

    const val TEXT = 0xFFEDEDED.toInt()
    const val TEXT_MUTED = 0xFFAAAAAA.toInt()
    const val TEXT_DIM = 0xFF666666.toInt()
    const val BORDER = 0xFF3A3A3A.toInt()       // COL_DIVIDER

    const val SUCCESS = 0xFF55FF88.toInt()
    const val WARNING = 0xFFFFAA55.toInt()
    const val DANGER = 0xFFFF5555.toInt()
    const val DANGER_TEXT = 0xFFFF8888.toInt()
    const val DANGER_BG = 0xFF3A1515.toInt()

    // --- metrics (логические px экрана MC, не dp ModernUI) ---
    const val SIDEBAR_W = 200
    const val TOPBAR_H = 36
    const val PAD = 12
    const val BTN_H = 28
    const val RADIUS_HINT = 6 // скругление пока прямоугольниками; позже Arc3D
}