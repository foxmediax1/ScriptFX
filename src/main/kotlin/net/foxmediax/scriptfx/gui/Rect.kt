package net.foxmediax.scriptfx.gui

internal data class Rect(val x1: Int, val y1: Int, val x2: Int, val y2: Int) {
    fun contains(px: Int, py: Int) = px in x1 until x2 && py in y1 until y2
}