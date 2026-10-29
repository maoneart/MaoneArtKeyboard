package com.maoneart.ioskeyboard.keyboard.swipe

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.view.Gravity
import android.view.HapticFeedbackConstants
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
    private val findKeyViewAtCoords: (Float, Float) -> View? = { _, _ -> null },
    private val onFallbackKeyAction: (String, View?) -> Unit = { _, _ -> },
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

    private val touchSlop = 26f * resources.displayMetrics.density
    private var activeFallbackView: View? = null
    private val keyPopupPositions = HashMap<View, Pair<Float, Float>>()

    fun clearPopupPositionCache() {
        keyPopupPositions.clear()
    }

    private val popupW = (48 * resources.displayMetrics.density).toInt()
    private val popupH = (60 * resources.displayMetrics.density).toInt()
    private val popupOffsetTop = (8 * resources.displayMetrics.density).toInt()

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
        elevation = 20f * resources.displayMetrics.density
        addView(tvPopupChar)
    }

    init {
        clipChildren = false
        clipToPadding = false
        isMotionEventSplittingEnabled = true

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
        // Fixed dimensions once: zero requestLayout() passes during rapid typing
        addView(
            popupBubble,
            LayoutParams(popupW, popupH)
        )
    }

    fun setKeyboardContentView(content: View) {
        if (content is android.view.ViewGroup) {
            content.clipChildren = false
            content.clipToPadding = false
            content.isMotionEventSplittingEnabled = true
        }
        // Keyboard keys go underneath the swipe trail and popup bubble
        addView(content, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setTrailColor(color: Int) {
        swipeTrailView.setTrailColor(color)
    }

    /**
     * Ultra-Snappy 120 FPS Key Bubble:
     * Uses In-Memory Hardware-Accelerated translationX / translationY cache.
     * Zero JNI hierarchy traversals and zero requestLayout() passes during rapid typing.
     */
    fun showPopupForKey(keyView: View, label: String) {
        if (!isKeyPopupEnabled) return

        var pos = keyPopupPositions[keyView]
        if (pos == null) {
            val loc = IntArray(2)
            val myLoc = IntArray(2)
            keyView.getLocationOnScreen(loc)
            getLocationOnScreen(myLoc)

            val keyCenterX = loc[0] + keyView.width / 2
            val targetX = (keyCenterX - myLoc[0]) - popupW / 2f
            val targetY = (loc[1] - myLoc[1]) - popupH + popupOffsetTop.toFloat()
            pos = Pair(targetX, targetY)
            keyPopupPositions[keyView] = pos
        }

        popupBubble.translationX = pos.first
        popupBubble.translationY = pos.second

        tvPopupChar.text = label
        if (popupBubble.visibility != View.VISIBLE) {
            popupBubble.visibility = View.VISIBLE
        }
    }

    fun hidePopup() {
        if (popupBubble.visibility != View.GONE) {
            popupBubble.visibility = View.GONE
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isSwipeEnabled) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val pIdx = ev.actionIndex
                startX = try { ev.getX(pIdx) } catch (_: Throwable) { ev.x }
                startY = try { ev.getY(pIdx) } catch (_: Throwable) { ev.y }
                val rawX = try { ev.getRawX(pIdx) } catch (_: Throwable) { ev.rawX }
                val rawY = try { ev.getRawY(pIdx) } catch (_: Throwable) { ev.rawY }

                isSwiping = false
                swipedChars.clear()
                val keyInfo = findKeyAtCoords(rawX, rawY)
                if (keyInfo != null && keyInfo.first.isNotEmpty() && keyInfo.first[0].isLetter()) {
                    swipedChars.add(keyInfo.first[0].lowercaseChar())
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isSwiping) {
                    val dist = hypot((ev.rawX - startX).toDouble(), (ev.rawY - startY).toDouble()).toFloat()
                    // Swipe only triggers when finger moves beyond touchSlop AND visits another distinct letter key
                    if (isSwipeEnabled && dist > touchSlop) {
                        val currentKey = findKeyAtCoords(ev.rawX, ev.rawY)
                        if (currentKey != null && currentKey.first.isNotEmpty() && currentKey.first[0].isLetter()) {
                            val c = currentKey.first[0].lowercaseChar()
                            if (swipedChars.isEmpty()) {
                                swipedChars.add(c)
                            } else if (swipedChars.last() != c) {
                                // Genuinely moving to another key -> start QuickPath gesture!
                                hidePopup()
                                isSwiping = true
                                swipeTrailView.addPoint(ev.x, ev.y)
                                swipedChars.add(c)
                                onSwipeCharDetected()
                                onSwipeLiveUpdate(ArrayList(swipedChars))
                                return true // Intercept touch! Letter key gets ACTION_CANCEL
                            }
                        }
                    }
                }
                return isSwiping
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                hidePopup()
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
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val pIdx = ev.actionIndex
                val pRawX = try { ev.getRawX(pIdx) } catch (_: Throwable) { ev.rawX }
                val pRawY = try { ev.getRawY(pIdx) } catch (_: Throwable) { ev.rawY }

                // Fallback Hit-Resolver: Catches touches landing in margins, padding, or dead gaps
                val keyInfo = findKeyAtCoords(pRawX, pRawY)
                if (keyInfo != null && keyInfo.first.isNotEmpty()) {
                    val targetView = findKeyViewAtCoords(pRawX, pRawY)
                    targetView?.isPressed = true
                    try {
                        targetView?.performHapticFeedback(
                            HapticFeedbackConstants.KEYBOARD_TAP,
                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                        )
                    } catch (_: Throwable) {}
                    if (targetView != null) {
                        showPopupForKey(targetView, keyInfo.first)
                    }
                    onFallbackKeyAction(keyInfo.first, targetView)
                    activeFallbackView = targetView
                    return true
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isSwiping && isSwipeEnabled) {
                    hidePopup()
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
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                hidePopup()
                activeFallbackView?.isPressed = false
                activeFallbackView = null
                swipeTrailView.clearTrail()
                if (isSwiping && swipedChars.size >= 2) {
                    onSwipeCompleted(ArrayList(swipedChars))
                }
                isSwiping = false
                swipedChars.clear()
                return true
            }
        }
        return super.onTouchEvent(ev)
    }
}
