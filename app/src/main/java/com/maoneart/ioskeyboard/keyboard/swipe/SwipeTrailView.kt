package com.maoneart.ioskeyboard.keyboard.swipe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * Authentic Apple iOS QuickPath Fluid Ribbon Trail.
 * Renders smooth tapered stroke with gradual alpha fade from tail to finger tip.
 */
class SwipeTrailView(context: Context) : View(context) {

    private var baseColor: Int = Color.parseColor("#007AFF") // iOS Blue
    private val density = resources.displayMetrics.density

    private val minStrokeWidth = 4.5f * density
    private val maxStrokeWidth = 9.5f * density
    private val trailDurationMs = 280L

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private class TimedPoint(val x: Float, val y: Float, val time: Long)

    private val points = mutableListOf<TimedPoint>()

    fun setTrailColor(color: Int) {
        baseColor = color
        invalidate()
    }

    fun addPoint(x: Float, y: Float) {
        val now = System.currentTimeMillis()
        points.add(TimedPoint(x, y, now))
        cleanOldPoints(now)
        invalidate()
    }

    fun clearTrail() {
        points.clear()
        invalidate()
    }

    private fun cleanOldPoints(now: Long) {
        val cutoff = now - trailDurationMs
        while (points.isNotEmpty() && points.first().time < cutoff) {
            points.removeAt(0)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        cleanOldPoints(now)
        if (points.size < 2) return

        val r = Color.red(baseColor)
        val g = Color.green(baseColor)
        val b = Color.blue(baseColor)
        val baseAlpha = Color.alpha(baseColor).takeIf { it > 0 } ?: 210

        val total = points.size - 1
        for (i in 1 until points.size) {
            val pPrev = points[i - 1]
            val pCurr = points[i]

            // Ratio: 0.0 at oldest tail point, 1.0 at latest finger position
            val ratio = i.toFloat() / total
            val ageMs = (now - pCurr.time).coerceAtLeast(0)
            val timeFade = (1.0f - (ageMs.toFloat() / trailDurationMs)).coerceIn(0.1f, 1.0f)

            val alpha = (baseAlpha * ratio * timeFade).toInt().coerceIn(20, 255)
            val strokeWidth = minStrokeWidth + (maxStrokeWidth - minStrokeWidth) * ratio

            segmentPaint.color = Color.argb(alpha, r, g, b)
            segmentPaint.strokeWidth = strokeWidth

            canvas.drawLine(pPrev.x, pPrev.y, pCurr.x, pCurr.y, segmentPaint)
        }

        // Continue animating fade-out until tail clears
        if (points.isNotEmpty()) {
            postInvalidateOnAnimation()
        }
    }
}
