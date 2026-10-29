package com.maoneart.ioskeyboard.keyboard.swipe

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.maoneart.ioskeyboard.R
import kotlin.math.hypot

class QuickPathContainerLayout(
    context: Context,
    private val isSwipeEnabled: Boolean,
    private val isKeyPopupEnabled: Boolean,
    private val themeMode: String = "auto",
    private val isDark: Boolean = false,
    private val findKeyAtCoords: (Float, Float) -> Pair<String, Rect>?,
    private val onSwipeCompleted: (List<Char>) -> Unit,
    private val onSwipeCharDetected: () -> Unit,
    private val onSwipeLiveUpdate: (List<Char>) -> Unit = {}
) : FrameLayout(context) {

    private var startX = 0f
    private var startY = 0f
    private var isSwiping = false
    private val swipedChars = mutableListOf<Char>()
    private val swipeTrailView = SwipeTrailView(context).apply {
        isClickable = false
        isFocusable = false
    }

    private val touchSlop = 12f * resources.displayMetrics.density
    private val containerLoc = IntArray(2)

    // Authentic Apple iOS Key Preview Bubble
    private val tvPopupChar = TextView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
        setPadding(0, (7 * resources.displayMetrics.density).toInt(), 0, 0)
        textSize = 27f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private val popupBubble = FrameLayout(context).apply {
        visibility = View.GONE
        isClickable = false
        isFocusable = false
        elevation = 16f * resources.displayMetrics.density
        addView(tvPopupChar)
    }

    init {
        val isWoody = (themeMode == "woody")
        when {
            isWoody -> {
                popupBubble.background = ContextCompat.getDrawable(context, R.drawable.ios_key_popup_woody)
                tvPopupChar.setTextColor(Color.parseColor("#FFF3E0"))
            }
            isDark -> {
                popupBubble.background = ContextCompat.getDrawable(context, R.drawable.ios_key_popup_dark)
                tvPopupChar.setTextColor(Color.WHITE)
            }
            else -> {
                popupBubble.background = ContextCompat.getDrawable(context, R.drawable.ios_key_popup_light)
                tvPopupChar.setTextColor(Color.BLACK)
            }
        }

        addView(
            swipeTrailView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        addView(
            popupBubble,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        )
    }

    fun setKeyboardContentView(content: View) {
        // Keyboard keys go underneath the swipe trail and popup bubble
        addView(content, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setTrailColor(color: Int) {
        swipeTrailView.setTrailColor(color)
    }

    private fun showKeyPopup(keyLabel: String, keyRect: Rect) {
        if (!isKeyPopupEnabled) return
        getLocationOnScreen(containerLoc)
        val popupW = (48 * resources.displayMetrics.density).toInt()
        val popupH = (60 * resources.displayMetrics.density).toInt()
        val keyCenterX = (keyRect.left + keyRect.right) / 2
        val targetX = (keyCenterX - containerLoc[0]) - popupW / 2
        val targetY = (keyRect.top - containerLoc[1]) - popupH + (8 * resources.displayMetrics.density).toInt()

        val lp = popupBubble.layoutParams as LayoutParams
        lp.width = popupW
        lp.height = popupH
        lp.leftMargin = targetX.coerceAtLeast(0)
        lp.topMargin = targetY.coerceAtLeast(0)
        popupBubble.layoutParams = lp

        tvPopupChar.text = keyLabel
        popupBubble.visibility = View.VISIBLE
    }

    private fun hideKeyPopup() {
        if (popupBubble.visibility != View.GONE) {
            popupBubble.visibility = View.GONE
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX
                startY = ev.rawY
                isSwiping = false
                swipedChars.clear()
                val keyInfo = findKeyAtCoords(ev.rawX, ev.rawY)
                if (keyInfo != null && keyInfo.first.isNotEmpty() && keyInfo.first[0].isLetter()) {
                    swipedChars.add(keyInfo.first[0].lowercaseChar())
                    showKeyPopup(keyInfo.first, keyInfo.second)
                } else {
                    hideKeyPopup()
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isSwiping) {
                    val dist = hypot((ev.rawX - startX).toDouble(), (ev.rawY - startY).toDouble()).toFloat()
                    if (isSwipeEnabled && dist > touchSlop) {
                        // Start QuickPath Swipe!
                        hideKeyPopup()
                        if (swipedChars.isEmpty()) {
                            val startKey = findKeyAtCoords(startX, startY) ?: findKeyAtCoords(ev.rawX, ev.rawY)
                            if (startKey != null && startKey.first.isNotEmpty() && startKey.first[0].isLetter()) {
                                swipedChars.add(startKey.first[0].lowercaseChar())
                            }
                        }

                        if (swipedChars.isNotEmpty()) {
                            isSwiping = true
                            swipeTrailView.addPoint(ev.x, ev.y)
                            val currentKey = findKeyAtCoords(ev.rawX, ev.rawY)
                            if (currentKey != null && currentKey.first.isNotEmpty() && currentKey.first[0].isLetter()) {
                                val c = currentKey.first[0].lowercaseChar()
                                if (swipedChars.last() != c) {
                                    swipedChars.add(c)
                                    onSwipeCharDetected()
                                    onSwipeLiveUpdate(ArrayList(swipedChars))
                                }
                            }
                            return true // Intercept touch! Letter key gets ACTION_CANCEL
                        }
                    } else if (!isSwiping) {
                        // Gentle slide: update popup location to hovered key
                        val currentKey = findKeyAtCoords(ev.rawX, ev.rawY)
                        if (currentKey != null && currentKey.first.isNotEmpty() && currentKey.first[0].isLetter()) {
                            showKeyPopup(currentKey.first, currentKey.second)
                        }
                    }
                }
                return isSwiping
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                hideKeyPopup()
                isSwiping = false
                swipedChars.clear()
                swipeTrailView.clearTrail()
                return false
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (isSwiping && isSwipeEnabled) {
                    hideKeyPopup()
                    swipeTrailView.addPoint(ev.x, ev.y)
                    val keyInfo = findKeyAtCoords(ev.rawX, ev.rawY)
                    if (keyInfo != null && keyInfo.first.isNotEmpty() && keyInfo.first[0].isLetter()) {
                        val c = keyInfo.first[0].lowercaseChar()
                        if (swipedChars.isEmpty() || swipedChars.last() != c) {
                            swipedChars.add(c)
                            onSwipeCharDetected()
                            onSwipeLiveUpdate(ArrayList(swipedChars))
                        }
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                hideKeyPopup()
                swipeTrailView.clearTrail()
                if (isSwiping && swipedChars.size >= 2) {
                    onSwipeCompleted(ArrayList(swipedChars))
                }
                isSwiping = false
                swipedChars.clear()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                hideKeyPopup()
                swipeTrailView.clearTrail()
                isSwiping = false
                swipedChars.clear()
                return true
            }
        }
        return super.onTouchEvent(ev)
    }
}
