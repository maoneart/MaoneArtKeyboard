package com.maoneart.ioskeyboard.keyboard.swipe

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.hypot

class QuickPathContainerLayout(
    context: Context,
    private val isSwipeEnabled: Boolean,
    private val findLetterAtCoords: (Float, Float) -> String?,
    private val onSwipeCompleted: (List<Char>) -> Unit,
    private val onSwipeCharDetected: () -> Unit
) : FrameLayout(context) {

    private var startX = 0f
    private var startY = 0f
    private var isSwiping = false
    private val swipedChars = mutableListOf<Char>()
    private val swipeTrailView = SwipeTrailView(context).apply {
        isClickable = false
        isFocusable = false
    }

    private val touchSlop = 22f * resources.displayMetrics.density

    init {
        addView(
            swipeTrailView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun setKeyboardContentView(content: View) {
        // Keyboard keys go underneath the swipe trail
        addView(content, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setTrailColor(color: Int) {
        swipeTrailView.setTrailColor(color)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isSwipeEnabled) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX
                startY = ev.rawY
                isSwiping = false
                swipedChars.clear()
                val ch = findLetterAtCoords(ev.rawX, ev.rawY)
                if (!ch.isNullOrEmpty() && ch[0].isLetter()) {
                    swipedChars.add(ch[0].lowercaseChar())
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (swipedChars.isNotEmpty() && !isSwiping) {
                    val dist = hypot((ev.rawX - startX).toDouble(), (ev.rawY - startY).toDouble()).toFloat()
                    if (dist > touchSlop) {
                        isSwiping = true
                        swipeTrailView.addPoint(ev.x, ev.y)
                        return true // Intercept touch! Letter key gets ACTION_CANCEL
                    }
                }
                return isSwiping
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isSwiping = false
                swipedChars.clear()
                swipeTrailView.clearTrail()
                return false
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!isSwipeEnabled) return super.onTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                swipeTrailView.addPoint(ev.x, ev.y)
                val ch = findLetterAtCoords(ev.rawX, ev.rawY)
                if (!ch.isNullOrEmpty() && ch[0].isLetter()) {
                    val c = ch[0].lowercaseChar()
                    if (swipedChars.isEmpty() || swipedChars.last() != c) {
                        swipedChars.add(c)
                        onSwipeCharDetected()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                swipeTrailView.clearTrail()
                if (isSwiping && swipedChars.size >= 2) {
                    onSwipeCompleted(ArrayList(swipedChars))
                }
                isSwiping = false
                swipedChars.clear()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                swipeTrailView.clearTrail()
                isSwiping = false
                swipedChars.clear()
                return true
            }
        }
        return super.onTouchEvent(ev)
    }
}
