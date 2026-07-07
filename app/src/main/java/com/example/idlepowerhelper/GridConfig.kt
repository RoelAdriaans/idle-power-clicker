package com.example.idlepowerhelper

import android.content.Context

/**
 * Stores the pixel coordinates of the 4×4 battery grid on screen.
 * All values are absolute pixel coordinates (not percentages).
 */
data class GridBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width  get() = right - left
    val height get() = bottom - top
    val cellWidth  get() = width  / 4
    val cellHeight get() = height / 4

    /** Returns the screen (x, y) pixel centre of the cell at [row],[col]. */
    fun cellCenter(row: Int, col: Int): Pair<Float, Float> {
        val x = left + col * cellWidth  + cellWidth  / 2f
        val y = top  + row * cellHeight + cellHeight / 2f
        return x to y
    }
}

object GridConfig {
    private const val PREFS = "grid_config"

    fun save(context: Context, bounds: GridBounds) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putInt("left",   bounds.left)
            putInt("top",    bounds.top)
            putInt("right",  bounds.right)
            putInt("bottom", bounds.bottom)
            apply()
        }
    }

    fun load(context: Context): GridBounds? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("left")) return null
        return GridBounds(
            left   = p.getInt("left",   0),
            top    = p.getInt("top",    0),
            right  = p.getInt("right",  0),
            bottom = p.getInt("bottom", 0)
        )
    }

    /**
     * Reasonable default that matches the layout visible in the game screenshot.
     * The grid occupies roughly the lower 49–96 % of the screen, almost full width.
     */
    fun default(screenWidth: Int, screenHeight: Int) = GridBounds(
        left   = (screenWidth  * 0.04).toInt(),
        top    = (screenHeight * 0.47).toInt(),
        right  = (screenWidth  * 0.96).toInt(),
        bottom = (screenHeight * 0.96).toInt()
    )
}
