package com.example.idlepowerhelper

import android.content.Context
import android.graphics.*
import android.view.View

/**
 * Fullscreen transparent overlay that draws the calibrated 4×4 grid and the
 * last attempted swipe move as an arrow so bad moves are immediately visible.
 */
class GridDebugView(context: Context) : View(context) {

    var bounds: GridBounds? = null
    private var cellTypes: Array<IntArray>? = null
    private var lastMove: MergeMove? = null
    // Pixel coordinates of the last swipe (for display)
    private var lastFromX = 0f; private var lastFromY = 0f
    private var lastToX   = 0f; private var lastToY   = 0f

    // ── Paints ────────────────────────────────────────────────────────────────

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FF00FF")
        strokeWidth = 4f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(40, 255, 0, 255)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }
    private val typePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        textSize = 22f
        textAlign = Paint.Align.CENTER
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF4444")
    }
    private val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        textSize = 20f
        typeface = Typeface.MONOSPACE
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#00FF00")   // bright green arrow
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }
    private val arrowHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00FF00")
    }
    private val srcHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(120, 255, 165, 0)  // orange = source cell
    }
    private val dstHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(120, 0, 255, 0)    // green  = destination cell
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun updateTypes(types: Array<IntArray>?) {
        cellTypes = types
        postInvalidate()
    }

    /** Call before executing each swipe so the arrow updates instantly. */
    fun updateMove(move: MergeMove?) {
        lastMove = move
        if (move != null) {
            val b = bounds
            if (b != null) {
                val (fx, fy) = b.cellCenter(move.fromRow, move.fromCol)
                val (tx, ty) = b.cellCenter(move.toRow,   move.toCol)
                lastFromX = fx; lastFromY = fy
                lastToX   = tx; lastToY   = ty
            }
        }
        postInvalidate()
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val b = bounds ?: return

        // Background tint over grid area
        canvas.drawRect(b.left.toFloat(), b.top.toFloat(),
                        b.right.toFloat(), b.bottom.toFloat(), fillPaint)

        // Highlight source (orange) and destination (green) cells
        lastMove?.let { m ->
            highlightCell(canvas, b, m.fromRow, m.fromCol, srcHighlightPaint)
            highlightCell(canvas, b, m.toRow,   m.toCol,   dstHighlightPaint)
        }

        // Draw each cell
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                val left   = (b.left + col * b.cellWidth).toFloat()
                val top    = (b.top  + row * b.cellHeight).toFloat()
                val right  = left + b.cellWidth
                val bottom = top  + b.cellHeight
                val cx     = (left + right)  / 2f
                val cy     = (top  + bottom) / 2f

                canvas.drawRect(left, top, right, bottom, borderPaint)
                canvas.drawText("$row,$col", cx, cy - 6f, labelPaint)

                val type = cellTypes?.getOrNull(row)?.getOrNull(col)
                if (type != null) {
                    val typeStr = if (type == -1) "empty" else "T$type"
                    canvas.drawText(typeStr, cx, cy + 22f, typePaint)
                }
            }
        }

        // Corner markers
        val markerR = 10f
        for ((mx, my) in listOf(
            b.left.toFloat()  to b.top.toFloat(),
            b.right.toFloat() to b.top.toFloat(),
            b.left.toFloat()  to b.bottom.toFloat(),
            b.right.toFloat() to b.bottom.toFloat()
        )) { canvas.drawCircle(mx, my, markerR, cornerPaint) }

        // Draw swipe arrow from source centre → destination centre
        lastMove?.let { m ->
            val (fx, fy) = b.cellCenter(m.fromRow, m.fromCol)
            val (tx, ty) = b.cellCenter(m.toRow,   m.toCol)
            canvas.drawLine(fx, fy, tx, ty, arrowPaint)
            // Arrowhead circle at destination
            canvas.drawCircle(tx, ty, 18f, arrowHeadPaint)
        }

        // ── Info overlay: bounds + last swipe pixel coords ──────────────────
        val infoLines = mutableListOf(
            "Grid L=${b.left} T=${b.top}",
            "     R=${b.right} B=${b.bottom}",
            "Cell ${b.cellWidth}×${b.cellHeight}"
        )
        lastMove?.let { m ->
            infoLines += "Swipe(${m.fromRow},${m.fromCol})→(${m.toRow},${m.toCol})"
            infoLines += "  from px(${lastFromX.toInt()},${lastFromY.toInt()})"
            infoLines += "  to   px(${lastToX.toInt()},${lastToY.toInt()})"
        }
        var infoY = 60f
        for (line in infoLines) {
            canvas.drawText(line, 10f, infoY, infoPaint)
            infoY += 26f
        }
    }

    private fun highlightCell(canvas: Canvas, b: GridBounds,
                               row: Int, col: Int, paint: Paint) {
        val left   = (b.left + col * b.cellWidth).toFloat()
        val top    = (b.top  + row * b.cellHeight).toFloat()
        canvas.drawRect(left, top,
                        left + b.cellWidth, top + b.cellHeight, paint)
    }
}
