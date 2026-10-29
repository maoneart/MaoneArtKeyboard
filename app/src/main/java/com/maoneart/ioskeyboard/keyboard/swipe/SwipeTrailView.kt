package com.maoneart.ioskeyboard.keyboard.swipe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class SwipeTrailView(context: Context) : View(context) {

    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4080FF") // iOS electric blue swipe trail
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 14f
    }

    private class TimedPoint(val x: Float, val y: Float, val time: Long)

    private val points = mutableListOf<TimedPoint>()
    private val path = Path()

    fun setTrailColor(color: Int) {
        trailPaint.color = color
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
        val cutoff = now - 250 // trail expires after 250ms
        while (points.isNotEmpty() && points.first().time < cutoff) {
            points.removeAt(0)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2) return

        val now = System.currentTimeMillis()
        cleanOldPoints(now)
        if (points.size < 2) return

        path.reset()
        val first = points.first()
        path.moveTo(first.x, first.y)

        for (i in 1 until points.size) {
            val p = points[i]
            path.lineTo(p.x, p.y)
        }

        canvas.drawPath(path, trailPaint)

        // Keep animating fade out
        if (points.isNotEmpty()) {
            postInvalidateOnAnimation()
        }
    }
}
