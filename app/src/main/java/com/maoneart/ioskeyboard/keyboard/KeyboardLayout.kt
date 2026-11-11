package com.maoneart.ioskeyboard.keyboard

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import com.maoneart.ioskeyboard.R
import com.maoneart.ioskeyboard.emoji.AnimatedEmojiManager
import com.maoneart.ioskeyboard.emoji.AppleEmojiAssetManager
import com.maoneart.ioskeyboard.emoji.EmojiBoardManager
import com.maoneart.ioskeyboard.emoji.SkinToneManager

class KeyboardLayout(private val context: Context) {

    class ReturnButtonLayout(context: Context) : FrameLayout(context) {
        val textView = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            gravity = Gravity.CENTER
            textSize = 15f
        }
        val imageView = android.widget.ImageView(context).apply {
            layoutParams = LayoutParams(
                (22 * resources.displayMetrics.density).toInt(),
                (22 * resources.displayMetrics.density).toInt(),
                Gravity.CENTER
            )
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }
        init {
            addView(textView)
            addView(imageView)
        }

        fun showIcon(drawableRes: Int, bgRes: Int) {
            textView.visibility = View.GONE
            imageView.visibility = View.VISIBLE
            imageView.setImageResource(drawableRes)
            setBackgroundResource(bgRes)
        }

        fun showText(textStr: String, sizeSp: Float, textColor: Int, bgRes: Int) {
            imageView.visibility = View.GONE
            textView.visibility = View.VISIBLE
            textView.text = textStr
            textView.textSize = sizeSp
            textView.setTextColor(textColor)
            setBackgroundResource(bgRes)
        }
    }

    class SwipeableEmojiContainer(context: Context) : FrameLayout(context) {
        var onSwipeLeft: (() -> Unit)? = null
        var onSwipeRight: (() -> Unit)? = null

        private var startX = 0f
        private var startY = 0f
        private var isHorizontalSwipe = false
        private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop * 1.2f
        private val minSwipeDistance = 35 * resources.displayMetrics.density

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = ev.x
                    startY = ev.y
                    isHorizontalSwipe = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.x - startX
                    val dy = ev.y - startY
                    if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.35f) {
                        isHorizontalSwipe = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
            }
            return super.onInterceptTouchEvent(ev)
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.x - startX
                    val dy = ev.y - startY
                    if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.35f) {
                        isHorizontalSwipe = true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val dx = ev.x - startX
                    val dy = ev.y - startY
                    if (isHorizontalSwipe && Math.abs(dx) >= minSwipeDistance && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                        try {
                            performHapticFeedback(
                                android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                            )
                        } catch (_: Throwable) {}
                        if (dx < 0) {
                            onSwipeLeft?.invoke()
                        } else {
                            onSwipeRight?.invoke()
                        }
                        isHorizontalSwipe = false
                        return true
                    }
                    isHorizontalSwipe = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    isHorizontalSwipe = false
                }
            }
            return true
        }
    }

    private val letterKeyViews = mutableListOf<Pair<TextView, String>>()
    private var shiftButtonRef: ImageButton? = null
    private var returnButtonRef: ReturnButtonLayout? = null
    private var popupController: com.maoneart.ioskeyboard.keyboard.swipe.QuickPathContainerLayout? = null
    private var onKeyActionCallback: ((String) -> Unit)? = null
    private var predictedNextChars: Set<Char> = emptySet()

    private val isLandscape: Boolean
        get() = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Global Spacebar Trackpad Coordination (Touch-anywhere text selection)
    var isSpaceTrackpadActive = false
        private set
    var onTrackpadSelectionToggle: (() -> Unit)? = null
    var onTrackpadStateChanged: ((isActive: Boolean) -> Unit)? = null
    private var cancelCurrentTrackpad: (() -> Unit)? = null

    fun resetTrackpadState() {
        isSpaceTrackpadActive = false
        cancelCurrentTrackpad?.invoke()
        cancelCurrentTrackpad = null
        onTrackpadSelectionToggle = null
        onTrackpadStateChanged?.invoke(false)
    }

    fun clearHitBoxCache() {
        cachedHitBoxes = emptyList()
        popupController?.clearPopupPositionCache()
    }

    fun resetAllPressedStates() {
        for ((tv, _) in letterKeyViews) {
            tv.isPressed = false
        }
        for (box in cachedHitBoxes) {
            box.tv.isPressed = false
        }
        popupController?.resetFallbackPressedViews()
        popupController?.hidePopup()
    }

    fun updatePredictedNextChars(chars: Set<Char>) {
        predictedNextChars = chars
    }

    data class KeyHitBox(
        val tv: TextView,
        val charStr: String,
        val hitRect: android.graphics.Rect,
        val centerX: Float,
        val centerY: Float
    )

    private var cachedHitBoxes: List<KeyHitBox> = emptyList()

    fun rebuildHitBoxCache() {
        val loc = IntArray(2)
        val boxes = mutableListOf<KeyHitBox>()
        for ((tv, charStr) in letterKeyViews) {
            if (!tv.isShown || tv.width == 0) continue
            tv.getLocationOnScreen(loc)
            val l = loc[0]
            val t = loc[1]
            val r = l + tv.width
            val b = t + tv.height
            boxes.add(
                KeyHitBox(
                    tv = tv,
                    charStr = charStr,
                    hitRect = android.graphics.Rect(l, t, r, b),
                    centerX = l + tv.width / 2f,
                    centerY = t + tv.height / 2f
                )
            )
        }
        if (boxes.isNotEmpty()) {
            cachedHitBoxes = boxes
            popupController?.clearPopupPositionCache()
        }
    }

    private var micButtonRef: ImageButton? = null

    fun updateDictationVisual(isListening: Boolean, isDark: Boolean, themeMode: String = "auto") {
        micButtonRef?.let { btn ->
            if (isListening) {
                btn.setColorFilter(Color.parseColor("#FF3B30")) // Distinct Apple Red recording indicator
            } else {
                val isWoody = (themeMode == "woody")
                val normalColor = if (isWoody) Color.parseColor("#FFF3E0") else if (isDark) Color.WHITE else Color.BLACK
                btn.setColorFilter(normalColor)
            }
        }
    }

    // Update letter cases in 0.1ms without rebuilding views
    fun updateShiftState(isShifted: Boolean) {
        for ((tv, baseChar) in letterKeyViews) {
            tv.text = if (isShifted) baseChar.uppercase() else baseChar.lowercase()
        }
    }

    fun updateShiftButtonVisual(isShifted: Boolean, isCapsLock: Boolean, isDark: Boolean, themeMode: String = "auto") {
        shiftButtonRef?.let { btn ->
            val isWoody = (themeMode == "woody")
            val activeColor = if (isWoody) Color.parseColor("#FFA726") else ContextCompat.getColor(context, R.color.ios_blue)
            val normalColor = if (isWoody) Color.parseColor("#FFF3E0") else if (isDark) Color.WHITE else Color.BLACK
            btn.setColorFilter(if (isShifted || isCapsLock) activeColor else normalColor)
        }
    }

    // Dynamic Context-Adaptive Enter / Return / Search Button (iOS Smart Action)
    fun updateReturnButton(editorInfo: EditorInfo?, themeMode: String = "auto", isDark: Boolean = false) {
        val btn = returnButtonRef ?: return
        val isWoody = (themeMode == "woody")
        val specialKeyBg = when {
            isWoody -> R.drawable.key_bg_woody_special
            isDark -> R.drawable.key_bg_special_dark
            else -> R.drawable.key_bg_special_light
        }
        val actionKeyBg = when {
            isWoody -> R.drawable.key_bg_woody_action
            else -> R.drawable.key_bg_blue
        }
        val textColor = when {
            isWoody -> Color.parseColor("#FFF3E0")
            isDark -> Color.WHITE
            else -> Color.BLACK
        }

        if (editorInfo == null) {
            btn.showText("return", 15f, textColor, specialKeyBg)
            return
        }

        val imeAction = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
        val variation = editorInfo.inputType and InputType.TYPE_MASK_VARIATION
        val isMultiLine = (editorInfo.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        val hasNoEnterAction = (editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0

        val isSearchAction = (imeAction == EditorInfo.IME_ACTION_SEARCH) ||
                (variation == InputType.TYPE_TEXT_VARIATION_FILTER) ||
                (editorInfo.actionId == EditorInfo.IME_ACTION_SEARCH)

        // 1. Web Search or Filter: Show Flat White Search Icon with Action BG (100% Dead Center)
        if (isSearchAction) {
            btn.showIcon(R.drawable.ic_search_white, actionKeyBg)
            return
        }

        // 2. Multiline or No Enter Action (WhatsApp, Telegram, Notes): Always show authentic iOS "return"
        if (hasNoEnterAction || isMultiLine) {
            if (isMultiLine && imeAction == EditorInfo.IME_ACTION_SEND && !hasNoEnterAction) {
                btn.showText("Kirim", 14f, Color.WHITE, actionKeyBg)
            } else {
                btn.showText("return", 15f, textColor, specialKeyBg)
            }
            return
        }

        // 3. Browser URL or Web Address: Show "Buka" (Action BG)
        if (imeAction == EditorInfo.IME_ACTION_GO || variation == InputType.TYPE_TEXT_VARIATION_URI) {
            btn.showText("Buka", 14f, Color.WHITE, actionKeyBg)
            return
        }

        // 4. Form Send Action: Show "Kirim" (Action BG)
        if (imeAction == EditorInfo.IME_ACTION_SEND) {
            btn.showText("Kirim", 14f, Color.WHITE, actionKeyBg)
            return
        }

        // 5. Form Next / Done:
        if (imeAction == EditorInfo.IME_ACTION_NEXT) {
            btn.showText("Lanjut", 14f, Color.WHITE, actionKeyBg)
            return
        }
        if (imeAction == EditorInfo.IME_ACTION_DONE) {
            btn.showText("Selesai", 14f, Color.WHITE, actionKeyBg)
            return
        }

        // Default: authentic iOS "return"
        btn.showText("return", 15f, textColor, specialKeyBg)
    }

    // --- 1. QWERTY LAYOUT ---
    fun createQwertyView(
        isShifted: Boolean,
        isDark: Boolean,
        themeMode: String = "auto",
        activeLanguage: String,
        isSwipeEnabled: Boolean = true,
        isKeyPopupEnabled: Boolean = true,
        isDictationEnabled: Boolean = true,
        onKeyAction: (String) -> Unit,
        onCursorMove: (Int, Int, Boolean) -> Unit = { _, _, _ -> },
        onSwipeComplete: (List<Char>) -> Unit = {},
        onSwipeHaptic: () -> Unit = {},
        onSwipeLiveUpdate: (List<Char>) -> Unit = {}
    ): View {
        letterKeyViews.clear()
        cachedHitBoxes = emptyList()
        onKeyActionCallback = onKeyAction

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isMotionEventSplittingEnabled = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER
            setOnTouchListener { _, event ->
                if (isSpaceTrackpadActive) {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                        onTrackpadSelectionToggle?.invoke()
                    }
                    return@setOnTouchListener true
                }
                false
            }
        }

        val row1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        val row2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        val row3 = listOf("z", "x", "c", "v", "b", "n", "m")

        val isWoody = (themeMode == "woody")
        val keyBg = when {
            isWoody -> R.drawable.key_bg_woody
            isDark -> R.drawable.key_bg_ios_dark
            else -> R.drawable.key_bg_ios_light
        }
        val specialKeyBg = when {
            isWoody -> R.drawable.key_bg_woody_special
            isDark -> R.drawable.key_bg_special_dark
            else -> R.drawable.key_bg_special_light
        }
        val textColor = when {
            isWoody -> Color.parseColor("#FFF3E0")
            isDark -> Color.WHITE
            else -> Color.BLACK
        }
        val secondaryTextColor = when {
            isWoody -> Color.parseColor("#C4A587")
            isDark -> Color.parseColor("#98989D")
            else -> Color.parseColor("#8E8E93")
        }

        // Row 1
        val r1Layout = createRow(row1, isShifted, keyBg, textColor, onKeyAction)
        r1Layout.isMotionEventSplittingEnabled = true
        root.addView(r1Layout)

        // Row 2 (Padded for iOS stagger)
        val r2Layout = createRow(row2, isShifted, keyBg, textColor, onKeyAction)
        r2Layout.isMotionEventSplittingEnabled = true
        r2Layout.setPadding(dp(18), 0, dp(18), 0)
        root.addView(r2Layout)

        // Row 3 (Shift + Letters + Backspace)
        val r3Layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isMotionEventSplittingEnabled = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            ).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
        }

        // Shift Button
        val btnShift = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.4f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            setImageResource(R.drawable.ic_shift)
            val shiftActiveColor = if (isWoody) Color.parseColor("#FFA726") else ContextCompat.getColor(context, R.color.ios_blue)
            setColorFilter(if (isShifted) shiftActiveColor else textColor)
            isClickable = true
            isFocusable = false
            setupInstantTouch(this) { onKeyAction("ACTION_SHIFT") }
        }
        shiftButtonRef = btnShift
        r3Layout.addView(btnShift)

        for (ch in row3) {
            val label = if (isShifted) ch.uppercase() else ch
            val keyBtn = createKeyButton(label, 1.0f, keyBg, textColor, onKeyAction)
            letterKeyViews.add(Pair(keyBtn, ch))
            r3Layout.addView(keyBtn)
        }

        // Backspace Button with High-Speed Auto Repeat ("Hapus Ngebut")
        val btnBackspace = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.4f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            setImageResource(R.drawable.ic_backspace)
            setColorFilter(textColor)
            isClickable = true
            isFocusable = false
            setupRapidBackspaceTouch(this) { onKeyAction("ACTION_BACKSPACE") }
        }
        r3Layout.addView(btnBackspace)
        root.addView(r3Layout)

        // Row 4 (123 + iOS Flat Outline Smiley + Space + Dictation Mic + Return/Enter)
        val r4Layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isMotionEventSplittingEnabled = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            ).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
        }

        // 123 Button
        val btn123 = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.25f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            text = "123"
            setTextColor(textColor)
            textSize = 15f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            setupInstantTouch(this) { onKeyAction("ACTION_SYMBOLS") }
        }
        r4Layout.addView(btn123)

        // Emoji Button (Apple iOS Outline Smiley Icon, Monochrome Tint)
        val btnEmoji = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.15f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            setImageResource(R.drawable.ic_emoji_smile)
            setColorFilter(textColor)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            isClickable = true
            isFocusable = false
            setupInstantTouch(this) { onKeyAction("ACTION_EMOJI") }
        }
        r4Layout.addView(btnEmoji)

        // Space Button with Spacebar Trackpad Mode
        val langLabel = if (activeLanguage == "en") "space" else "spasi"
        val spaceWeight = if (isDictationEnabled) 4.45f else 5.4f
        val btnSpace = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, spaceWeight)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, keyBg)
            text = langLabel
            setTextColor(secondaryTextColor)
            textSize = 14f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            setupSpacebarTrackpadTouch(this, langLabel, isDark, themeMode, { onKeyAction(" ") }, onCursorMove)
        }
        r4Layout.addView(btnSpace)

        // Dictation Microphone Button (Apple iOS Monochrome Mic) with Anti-Accidental Touch
        if (isDictationEnabled) {
            val btnMic = ImageButton(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.95f)
                    .apply { setMargins(dp(2), 0, dp(2), 0) }
                background = ContextCompat.getDrawable(context, specialKeyBg)
                setImageResource(R.drawable.ic_mic_ios)
                setColorFilter(textColor)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                isClickable = true
                isFocusable = false
                setupDictationKeyTouch(this) { onKeyAction("ACTION_DICTATION") }
            }
            micButtonRef = btnMic
            r4Layout.addView(btnMic)
        } else {
            micButtonRef = null
        }

        // Return / Enter Action Button (return / Dead Center Search Icon)
        val btnReturn = ReturnButtonLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2.2f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            showText("return", 15f, textColor, specialKeyBg)
            isClickable = true
            isFocusable = false
            setupInstantTouch(this) { onKeyAction("ACTION_RETURN") }
        }
        returnButtonRef = btnReturn
        r4Layout.addView(btnReturn)
        root.addView(r4Layout)

        val container = com.maoneart.ioskeyboard.keyboard.swipe.QuickPathContainerLayout(
            context = context,
            isSwipeEnabled = isSwipeEnabled,
            isKeyPopupEnabled = isKeyPopupEnabled,
            themeMode = themeMode,
            isDark = isDark,
            findKeyAtCoords = { rx, ry -> findKeyAt(rx, ry) },
            findKeyViewAtCoords = { rx, ry -> findKeyViewAt(rx, ry) },
            onFallbackKeyAction = { keyStr, _ -> onKeyAction(keyStr) },
            onSwipeCompleted = onSwipeComplete,
            onSwipeCharDetected = onSwipeHaptic,
            onSwipeLiveUpdate = onSwipeLiveUpdate
        )
        if (isWoody) {
            container.setTrailColor(Color.parseColor("#99FFA726"))
        } else if (isDark) {
            container.setTrailColor(Color.parseColor("#9964D2FF"))
        } else {
            container.setTrailColor(Color.parseColor("#99007AFF"))
        }
        popupController = container
        root.clipChildren = false
        root.clipToPadding = false
        container.setKeyboardContentView(root)

        root.post { rebuildHitBoxCache() }

        return container
    }

    private fun findKeyAt(rawX: Float, rawY: Float): Pair<String, android.graphics.Rect>? {
        var boxes = cachedHitBoxes
        if (boxes.isEmpty()) {
            rebuildHitBoxCache()
            boxes = cachedHitBoxes
        }
        if (boxes.isEmpty()) return null

        val maxSnapRadius = dp(38).toFloat()
        val maxSnapRadiusSq = maxSnapRadius * maxSnapRadius

        // 1. Ken Kocienda's Apple Dynamic Predictive Hit-Testing:
        // Evaluates touch candidate against predicted next characters.
        // If touch lands near the boundary between two keys, the key that matches
        // the predicted next character has its effective hit zone expanded by 40%.
        var bestBox: KeyHitBox? = null
        var bestScore = Float.MAX_VALUE

        for (box in boxes) {
            val dx = rawX - box.centerX
            val dy = rawY - box.centerY
            val distSq = dx * dx + dy * dy
            if (distSq <= maxSnapRadiusSq) {
                val charC = box.charStr.firstOrNull()?.lowercaseChar() ?: ' '
                val isPredicted = predictedNextChars.contains(charC)
                val weight = if (isPredicted) 0.65f else 1.25f
                val weightedDist = distSq * weight
                if (weightedDist < bestScore) {
                    bestScore = weightedDist
                    bestBox = box
                }
            }
        }

        if (bestBox != null) {
            val activeText = bestBox.tv.text.toString().ifEmpty { bestBox.charStr }
            return Pair(activeText, bestBox.hitRect)
        }

        val rx = rawX.toInt()
        val ry = rawY.toInt()
        val tol = dp(4)

        for (box in boxes) {
            val r = box.hitRect
            if (rx in (r.left - tol)..(r.right + tol) &&
                ry in (r.top - tol)..(r.bottom + tol)) {
                val activeText = box.tv.text.toString().ifEmpty { box.charStr }
                return Pair(activeText, r)
            }
        }

        return null
    }

    private fun findKeyViewAt(rawX: Float, rawY: Float): View? {
        var boxes = cachedHitBoxes
        if (boxes.isEmpty()) {
            rebuildHitBoxCache()
            boxes = cachedHitBoxes
        }
        if (boxes.isEmpty()) return null

        val maxSnapRadius = dp(38).toFloat()
        val maxSnapRadiusSq = maxSnapRadius * maxSnapRadius

        var bestBox: KeyHitBox? = null
        var bestScore = Float.MAX_VALUE

        for (box in boxes) {
            val dx = rawX - box.centerX
            val dy = rawY - box.centerY
            val distSq = dx * dx + dy * dy
            if (distSq <= maxSnapRadiusSq) {
                val charC = box.charStr.firstOrNull()?.lowercaseChar() ?: ' '
                val isPredicted = predictedNextChars.contains(charC)
                val weight = if (isPredicted) 0.65f else 1.25f
                val weightedDist = distSq * weight
                if (weightedDist < bestScore) {
                    bestScore = weightedDist
                    bestBox = box
                }
            }
        }

        if (bestBox != null) {
            return bestBox.tv
        }

        val rx = rawX.toInt()
        val ry = rawY.toInt()
        val tol = dp(4)

        for (box in boxes) {
            val r = box.hitRect
            if (rx in (r.left - tol)..(r.right + tol) &&
                ry in (r.top - tol)..(r.bottom + tol)) {
                return box.tv
            }
        }

        return null
    }

    // --- 2. SYMBOLS LAYOUT (123 & #+=) ---
    fun createSymbolsView(
        isMoreSymbols: Boolean,
        isDark: Boolean,
        themeMode: String = "auto",
        isDictationEnabled: Boolean = true,
        onKeyAction: (String) -> Unit,
        onCursorMove: (Int, Int, Boolean) -> Unit = { _, _, _ -> }
    ): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER
            setOnTouchListener { _, event ->
                if (isSpaceTrackpadActive) {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                        onTrackpadSelectionToggle?.invoke()
                    }
                    return@setOnTouchListener true
                }
                false
            }
        }

        val row1 = if (!isMoreSymbols)
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        else
            listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "=")

        val row2 = if (!isMoreSymbols)
            listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\"")
        else
            listOf("_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•")

        val row3 = if (!isMoreSymbols)
            listOf(".", ",", "?", "!", "'")
        else
            listOf(".", ",", "?", "!", "'")

        val isWoody = (themeMode == "woody")
        val keyBg = when {
            isWoody -> R.drawable.key_bg_woody
            isDark -> R.drawable.key_bg_ios_dark
            else -> R.drawable.key_bg_ios_light
        }
        val specialKeyBg = when {
            isWoody -> R.drawable.key_bg_woody_special
            isDark -> R.drawable.key_bg_special_dark
            else -> R.drawable.key_bg_special_light
        }
        val textColor = when {
            isWoody -> Color.parseColor("#FFF3E0")
            isDark -> Color.WHITE
            else -> Color.BLACK
        }
        val secondaryTextColor = when {
            isWoody -> Color.parseColor("#C4A587")
            isDark -> Color.parseColor("#98989D")
            else -> Color.parseColor("#8E8E93")
        }

        root.addView(createRow(row1, false, keyBg, textColor, onKeyAction = onKeyAction))
        root.addView(createRow(row2, false, keyBg, textColor, onKeyAction = onKeyAction))

        // Row 3
        val r3 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            ).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
        }

        val btnToggleMore = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.4f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            text = if (!isMoreSymbols) "#+=" else "123"
            setTextColor(textColor)
            textSize = 14f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            setupInstantTouch(this) { onKeyAction(if (!isMoreSymbols) "ACTION_MORE_SYMBOLS" else "ACTION_SYMBOLS") }
        }
        r3.addView(btnToggleMore)

        // Quick Big Numpad ("1234") Switch Button for Bank Transfer / Numeric Entry
        val btnNumpad = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.3f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            text = "🔢"
            setTextColor(textColor)
            textSize = 15f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            setupInstantTouch(this) { onKeyAction("ACTION_NUMPAD") }
        }
        r3.addView(btnNumpad)

        for (sym in row3) {
            r3.addView(createKeyButton(sym, 1.2f, keyBg, textColor, onKeyAction))
        }

        val btnBackspace = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.4f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            setImageResource(R.drawable.ic_backspace)
            setColorFilter(textColor)
            isClickable = true
            isFocusable = false
            setupRapidBackspaceTouch(this) { onKeyAction("ACTION_BACKSPACE") }
        }
        r3.addView(btnBackspace)
        root.addView(r3)

        // Row 4
        val r4 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            ).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
        }

        val btnAbc = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.25f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            text = "ABC"
            setTextColor(textColor)
            textSize = 15f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            setupInstantTouch(this) { onKeyAction("ACTION_ABC") }
        }
        r4.addView(btnAbc)

        val btnEmoji = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.15f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            setImageResource(R.drawable.ic_emoji_smile)
            setColorFilter(textColor)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            isClickable = true
            isFocusable = false
            setupInstantTouch(this) { onKeyAction("ACTION_EMOJI") }
        }
        r4.addView(btnEmoji)

        val spaceWeight = if (isDictationEnabled) 4.45f else 5.4f
        val btnSpace = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, spaceWeight)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, keyBg)
            text = "spasi"
            setTextColor(secondaryTextColor)
            textSize = 14f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            setupSpacebarTrackpadTouch(this, "spasi", isDark, themeMode, { onKeyAction(" ") }, onCursorMove)
        }
        r4.addView(btnSpace)

        // Dictation Microphone Button (Apple iOS Monochrome Mic) with Anti-Accidental Touch
        if (isDictationEnabled) {
            val btnMic = ImageButton(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.95f)
                    .apply { setMargins(dp(2), 0, dp(2), 0) }
                background = ContextCompat.getDrawable(context, specialKeyBg)
                setImageResource(R.drawable.ic_mic_ios)
                setColorFilter(textColor)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                isClickable = true
                isFocusable = false
                setupDictationKeyTouch(this) { onKeyAction("ACTION_DICTATION") }
            }
            micButtonRef = btnMic
            r4.addView(btnMic)
        }

        // Return / Enter Action Button (return / Dead Center Search Icon)
        val btnReturn = ReturnButtonLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2.2f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            showText("return", 15f, textColor, specialKeyBg)
            isClickable = true
            isFocusable = false
            setupInstantTouch(this) { onKeyAction("ACTION_RETURN") }
        }
        returnButtonRef = btnReturn
        r4.addView(btnReturn)
        root.addView(r4)

        return root
    }

    // --- 2B. DEDICATED iOS NUMERIC KEYPAD (BANK TRANSFER, NOMINAL, REKENING, PIN, PHONE) ---
    fun createNumpadView(
        isDark: Boolean,
        themeMode: String = "auto",
        isPhoneInput: Boolean = false,
        onKeyAction: (String) -> Unit
    ): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isMotionEventSplittingEnabled = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }

        val isWoody = (themeMode == "woody")
        val keyBg = when {
            isWoody -> R.drawable.key_bg_woody
            isDark -> R.drawable.key_bg_ios_dark
            else -> R.drawable.key_bg_ios_light
        }
        val specialKeyBg = when {
            isWoody -> R.drawable.key_bg_woody_special
            isDark -> R.drawable.key_bg_special_dark
            else -> R.drawable.key_bg_special_light
        }
        val actionKeyBg = when {
            isWoody -> R.drawable.key_bg_woody_action
            else -> R.drawable.key_bg_blue
        }
        val textColor = when {
            isWoody -> Color.parseColor("#FFF3E0")
            isDark -> Color.WHITE
            else -> Color.BLACK
        }
        val subColor = when {
            isWoody -> Color.parseColor("#C4A587")
            isDark -> Color.parseColor("#98989D")
            else -> Color.parseColor("#8E8E93")
        }

        fun createNumpadDigitButton(digit: String, subLabel: String, weight: Float = 1.0f): View {
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
                    .apply { setMargins(dp(3), 0, dp(3), 0) }
                background = ContextCompat.getDrawable(context, keyBg)
                isClickable = true
                isFocusable = false

                val tvDigit = TextView(context).apply {
                    text = digit
                    setTextColor(textColor)
                    textSize = if (isLandscape) 21f else 23f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                }
                addView(tvDigit)

                if (subLabel.isNotEmpty()) {
                    val tvSub = TextView(context).apply {
                        text = subLabel
                        setTextColor(subColor)
                        textSize = 9f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                    }
                    addView(tvSub)
                }

                setupInstantTouch(this) { onKeyAction(digit) }
            }
        }

        fun createNumpadSpecialButton(label: String, output: String, bgRes: Int = specialKeyBg, txtColor: Int = textColor, weight: Float = 0.85f): TextView {
            return TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
                    .apply { setMargins(dp(3), 0, dp(3), 0) }
                background = ContextCompat.getDrawable(context, bgRes)
                text = label
                setTextColor(txtColor)
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = false
                setupInstantTouch(this) { onKeyAction(output) }
            }
        }

        // Row 1: 1 | 2 ABC | 3 DEF | - (or +)
        val r1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isMotionEventSplittingEnabled = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f)
                .apply { setMargins(0, dp(2), 0, dp(2)) }
        }
        r1.addView(createNumpadDigitButton("1", " "))
        r1.addView(createNumpadDigitButton("2", "ABC"))
        r1.addView(createNumpadDigitButton("3", "DEF"))
        r1.addView(createNumpadSpecialButton(if (isPhoneInput) "+" else "-", if (isPhoneInput) "+" else "-"))
        root.addView(r1)

        // Row 2: 4 GHI | 5 JKL | 6 MNO | 000 (Ribuan Cepat Transfer Bank!)
        val r2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isMotionEventSplittingEnabled = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f)
                .apply { setMargins(0, dp(2), 0, dp(2)) }
        }
        r2.addView(createNumpadDigitButton("4", "GHI"))
        r2.addView(createNumpadDigitButton("5", "JKL"))
        r2.addView(createNumpadDigitButton("6", "MNO"))
        r2.addView(createNumpadSpecialButton("000", "000"))
        root.addView(r2)

        // Row 3: 7 PQRS | 8 TUV | 9 WXYZ | Backspace
        val r3 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isMotionEventSplittingEnabled = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f)
                .apply { setMargins(0, dp(2), 0, dp(2)) }
        }
        r3.addView(createNumpadDigitButton("7", "PQRS"))
        r3.addView(createNumpadDigitButton("8", "TUV"))
        r3.addView(createNumpadDigitButton("9", "WXYZ"))
        val btnBackspace = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.85f)
                .apply { setMargins(dp(3), 0, dp(3), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            setImageResource(R.drawable.ic_backspace)
            setColorFilter(textColor)
            isClickable = true
            isFocusable = false
            setupRapidBackspaceTouch(this) { onKeyAction("ACTION_BACKSPACE") }
        }
        r3.addView(btnBackspace)
        root.addView(r3)

        // Row 4: ABC | 0 | . , | Return / Selesai
        val r4 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isMotionEventSplittingEnabled = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f)
                .apply { setMargins(0, dp(2), 0, dp(2)) }
        }
        r4.addView(createNumpadSpecialButton("ABC", "ACTION_ABC", weight = 1.0f))
        r4.addView(createNumpadDigitButton("0", "", weight = 1.0f))
        r4.addView(createNumpadSpecialButton(".", ".", bgRes = keyBg, weight = 1.0f))

        val btnReturn = ReturnButtonLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.85f)
                .apply { setMargins(dp(3), 0, dp(3), 0) }
            showText("✓", 18f, Color.WHITE, actionKeyBg)
            isClickable = true
            isFocusable = false
            setupInstantTouch(this) { onKeyAction("ACTION_RETURN") }
        }
        returnButtonRef = btnReturn
        r4.addView(btnReturn)
        root.addView(r4)

        return root
    }

    // --- 3. APPLE EMOJI & WHATSAPP STICKER BOARD LAYOUT ---
    fun createEmojiView(
        isDark: Boolean,
        onEmojiAction: (String) -> Unit,
        onStickerAction: (java.io.File) -> Unit,
        onAddStickerAction: () -> Unit,
        onBackAction: () -> Unit
    ): View {
        val root = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Modal Action Container (Overlay Glassmorphism)
        var modalOverlay: FrameLayout? = null

        fun hideModal() {
            modalOverlay?.let { root.removeView(it) }
            modalOverlay = null
        }

        fun showDeleteStickerModal(stickerFile: java.io.File, onDeleted: () -> Unit) {
            hideModal()
            val overlay = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#99000000"))
                isClickable = true
                isFocusable = true
                setOnClickListener { hideModal() }
            }

            val dialogCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                ).apply { setMargins(dp(28), 0, dp(28), 0) }
                background = ContextCompat.getDrawable(context, if (isDark) R.drawable.key_bg_special_dark else R.drawable.card_bg_ios)
                setPadding(dp(18), dp(16), dp(18), dp(16))
                isClickable = true
            }

            // Preview Image
            val ivPreview = android.widget.ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
                    setMargins(0, 0, 0, dp(10))
                }
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                try {
                    val bmp = android.graphics.BitmapFactory.decodeFile(stickerFile.absolutePath)
                    setImageBitmap(bmp)
                } catch (_: Throwable) {}
            }
            dialogCard.addView(ivPreview)

            val tvTitle = TextView(context).apply {
                text = "Hapus Stiker?"
                textSize = 15.5f
                setTextColor(if (isDark) Color.WHITE else Color.BLACK)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            dialogCard.addView(tvTitle)

            val tvSubtitle = TextView(context).apply {
                text = "Stiker ini akan dihapus permanen dari keyboard."
                textSize = 12f
                setTextColor(if (isDark) Color.parseColor("#98989D") else Color.parseColor("#8E8E93"))
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, dp(12))
            }
            dialogCard.addView(tvSubtitle)

            // Symmetrical 2-Column Action Grid (MaoneArt Glassmorphism Modal Rule)
            val btnRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(42)
                )
            }

            // Button 1: Batal
            val btnCancel = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f).apply {
                    setMargins(0, 0, dp(6), 0)
                }
                background = ContextCompat.getDrawable(context, if (isDark) R.drawable.key_bg_ios_dark else R.drawable.key_bg_special_light)
                text = "Batal"
                setTextColor(if (isDark) Color.WHITE else Color.BLACK)
                textSize = 13.5f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = false
                setupInstantTouch(this) { hideModal() }
            }
            btnRow.addView(btnCancel)

            // Button 2: Hapus
            val btnDelete = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f).apply {
                    setMargins(dp(6), 0, 0, 0)
                }
                background = ContextCompat.getDrawable(context, if (isDark) R.drawable.key_bg_ios_dark else R.drawable.key_bg_special_light)
                text = "🗑️ Hapus"
                setTextColor(Color.parseColor("#FF3B30"))
                textSize = 13.5f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = false
                setupInstantTouch(this) {
                    hideModal()
                    onDeleted()
                }
            }
            btnRow.addView(btnDelete)
            dialogCard.addView(btnRow)

            overlay.addView(dialogCard)
            modalOverlay = overlay
            root.addView(overlay)
        }

        var skinTonePopupOverlay: FrameLayout? = null

        fun hideSkinTonePopup() {
            skinTonePopupOverlay?.let { root.removeView(it) }
            skinTonePopupOverlay = null
        }

        fun showSkinTonePopup(baseEmoji: String, variants: List<String>, anchorView: View) {
            hideSkinTonePopup()
            val overlay = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.TRANSPARENT)
                isClickable = true
                isFocusable = true
                setOnClickListener { hideSkinTonePopup() }
            }

            val popupCard = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(context, if (isDark) R.drawable.key_bg_special_dark else R.drawable.card_bg_ios)
                elevation = dp(8).toFloat()
                setPadding(dp(6), dp(4), dp(6), dp(4))
                isClickable = true
            }

            val allOptions = listOf(baseEmoji) + variants
            for (opt in allOptions) {
                val optFrame = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                        setMargins(dp(2), 0, dp(2), 0)
                    }
                    isClickable = true
                    setOnClickListener {
                        hideSkinTonePopup()
                        onEmojiAction(opt)
                    }
                }

                val optBmp = AppleEmojiAssetManager.getEmojiBitmap(context, opt)
                if (optBmp != null) {
                    val ivOpt = android.widget.ImageView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER)
                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        setImageBitmap(optBmp)
                    }
                    optFrame.addView(ivOpt)
                } else {
                    val tvOpt = TextView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                        text = opt
                        textSize = 20f
                        gravity = Gravity.CENTER
                    }
                    optFrame.addView(tvOpt)
                }
                popupCard.addView(optFrame)
            }

            val loc = IntArray(2)
            anchorView.getLocationInWindow(loc)
            val rootLoc = IntArray(2)
            root.getLocationInWindow(rootLoc)

            val relX = (loc[0] - rootLoc[0]) - dp(60)
            val relY = (loc[1] - rootLoc[1]) - dp(46)

            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = relX.coerceIn(dp(8), (root.width - dp(240)).coerceAtLeast(dp(8)))
                topMargin = relY.coerceAtLeast(dp(8))
            }

            overlay.addView(popupCard, lp)
            skinTonePopupOverlay = overlay
            root.addView(overlay)

            popupCard.scaleX = 0.5f
            popupCard.scaleY = 0.5f
            popupCard.alpha = 0f
            popupCard.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setDuration(130L)
                .setInterpolator(OvershootInterpolator(2.0f))
                .start()
        }

        // Top Category Bar (Full iOS Emoji Library from iphoneemoji.com)
        val tabCategories = listOf(
            Pair("ANIMATED", "⚡ Gerak"),
            Pair("Smileys", "😀"),
            Pair("People", "👋"),
            Pair("Animals", "🐶"),
            Pair("Food", "🍔"),
            Pair("Activity", "⚽"),
            Pair("Travel", "✈️"),
            Pair("Objects", "💡"),
            Pair("Symbols", "💯"),
            Pair("Flags", "🚩"),
            Pair("STICKERS", "🖼️ Stiker"),
            Pair("ADD_STICKER", "➕")
        )

        val contentTabKeys = listOf(
            "ANIMATED", "Smileys", "People", "Animals", "Food", "Activity", "Travel", "Objects", "Symbols", "Flags", "STICKERS"
        )

        var activeCat = "ANIMATED"
        val tabViews = mutableMapOf<String, TextView>()

        val tabsScroll = android.widget.HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(38)
            )
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(if (isDark) ContextCompat.getColor(context, R.color.ios_toolbar_dark) else ContextCompat.getColor(context, R.color.ios_toolbar_light))
        }

        val tabsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(6), 0)
        }

        var switchToCategoryRef: ((String, Int) -> Unit)? = null

        val contentContainer = SwipeableEmojiContainer(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
            onSwipeLeft = {
                val currentIndex = contentTabKeys.indexOf(activeCat)
                if (currentIndex in 0 until contentTabKeys.size - 1) {
                    val nextCat = contentTabKeys[currentIndex + 1]
                    switchToCategoryRef?.invoke(nextCat, 1)
                }
            }
            onSwipeRight = {
                val currentIndex = contentTabKeys.indexOf(activeCat)
                if (currentIndex > 0) {
                    val prevCat = contentTabKeys[currentIndex - 1]
                    switchToCategoryRef?.invoke(prevCat, -1)
                }
            }
        }

        fun applySlideTransition(view: View, direction: Int) {
            if (direction != 0) {
                val startX = if (direction > 0) dp(80).toFloat() else -dp(80).toFloat()
                view.translationX = startX
                view.alpha = 0.45f
                view.animate()
                    .translationX(0f)
                    .alpha(1.0f)
                    .setDuration(160L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }

        fun renderContent(selectedCategory: String, direction: Int = 0) {
            contentContainer.removeAllViews()

            if (selectedCategory == "ANIMATED") {
                // Render Live Animated Apple Emojis Grid (Hardware Animated WebP at 60 FPS)
                val scroll = ScrollView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    isVerticalScrollBarEnabled = true
                    isFillViewport = true
                }

                val layout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(6), dp(4), dp(6), dp(4))
                }

                val items = AnimatedEmojiManager.items
                val columns = 6
                var row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
                }
                var count = 0
                var rowIndex = 0

                for (item in items) {
                    val itemContainer = FrameLayout(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)
                        isClickable = true
                        isFocusable = false

                        val delay = (rowIndex * 20L).coerceAtMost(200L)
                        alpha = 0f
                        scaleX = 0.5f
                        scaleY = 0.5f
                        animate()
                            .alpha(1.0f)
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setStartDelay(delay)
                            .setDuration(220L)
                            .setInterpolator(OvershootInterpolator(2.0f))
                            .start()

                        setOnClickListener { v ->
                            v.animate().cancel()
                            v.scaleX = 1.0f
                            v.scaleY = 1.0f
                            v.rotation = 0f

                            try {
                                v.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                )
                            } catch (_: Throwable) {}

                            val wobbleAngle = if (item.unicode.hashCode() % 2 == 0) 16f else -16f

                            v.animate()
                                .scaleX(1.5f)
                                .scaleY(1.5f)
                                .rotation(wobbleAngle)
                                .setDuration(120L)
                                .setInterpolator(OvershootInterpolator(3.5f))
                                .withEndAction {
                                    v.animate()
                                        .scaleX(1.0f)
                                        .scaleY(1.0f)
                                        .rotation(0f)
                                        .setDuration(180L)
                                        .setInterpolator(DecelerateInterpolator())
                                        .start()
                                }
                                .start()

                            onEmojiAction(item.unicode)
                        }
                    }

                    val ivEmoji = android.widget.ImageView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER)
                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    }

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val source = ImageDecoder.createSource(context.assets, "animated_emojis/${item.fileName}")
                            val drawable = ImageDecoder.decodeDrawable(source)
                            ivEmoji.setImageDrawable(drawable)
                            if (drawable is AnimatedImageDrawable) {
                                drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                                drawable.start()
                            }
                        } else {
                            context.assets.open("animated_emojis/${item.fileName}").use { stream ->
                                val bmp = BitmapFactory.decodeStream(stream)
                                ivEmoji.setImageBitmap(bmp)
                            }
                        }
                        itemContainer.addView(ivEmoji)
                    } catch (_: Throwable) {
                        val bmp = AppleEmojiAssetManager.getEmojiBitmap(context, item.unicode)
                        if (bmp != null) {
                            val ivFallback = android.widget.ImageView(context).apply {
                                layoutParams = FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER)
                                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                setImageBitmap(bmp)
                            }
                            itemContainer.addView(ivFallback)
                        } else {
                            val fallbackTv = TextView(context).apply {
                                text = item.unicode
                                textSize = 25f
                                gravity = Gravity.CENTER
                                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                            }
                            itemContainer.addView(fallbackTv)
                        }
                    }

                    row.addView(itemContainer)
                    count++

                    if (count == columns) {
                        layout.addView(row)
                        rowIndex++
                        row = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
                        }
                        count = 0
                    }
                }

                if (count > 0) {
                    while (count < columns) {
                        val spacer = View(context).apply {
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)
                        }
                        row.addView(spacer)
                        count++
                    }
                    layout.addView(row)
                }

                scroll.addView(layout)
                contentContainer.addView(scroll)
                applySlideTransition(scroll, direction)
            } else if (selectedCategory == "STICKERS") {
                // Render WhatsApp Stickers Grid
                val stickerManager = com.maoneart.ioskeyboard.sticker.StickerManager(context)
                val stickers = stickerManager.getStickers()

                val scroll = ScrollView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }

                val layout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                }

                // Add Sticker Action Button Card
                val btnUploadCard = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(44)
                    ).apply { setMargins(0, 0, 0, dp(10)) }
                    background = ContextCompat.getDrawable(context, if (isDark) R.drawable.key_bg_special_dark else R.drawable.key_bg_special_light)
                    isClickable = true
                    isFocusable = false
                    setOnClickListener { onAddStickerAction() }
                }

                val tvUploadText = TextView(context).apply {
                    text = "➕ Upload Gambar (PNG/JPG) Jadi Stiker WebP"
                    setTextColor(ContextCompat.getColor(context, R.color.ios_blue))
                    textSize = 13.5f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
                btnUploadCard.addView(tvUploadText)
                layout.addView(btnUploadCard)

                if (stickers.isEmpty()) {
                    val tvEmpty = TextView(context).apply {
                        text = "Belum ada stiker tersimpan.\nKetuk tombol di atas untuk memilih foto PNG/JPG dari galeri dan otomatis dijadikan stiker WhatsApp!"
                        setTextColor(Color.GRAY)
                        textSize = 13f
                        gravity = Gravity.CENTER
                        setPadding(dp(16), dp(30), dp(16), dp(30))
                    }
                    layout.addView(tvEmpty)
                } else {
                    var row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(75))
                            .apply { setMargins(0, dp(3), 0, dp(3)) }
                    }
                    var count = 0

                    for (stickerFile in stickers) {
                        val itemFrame = FrameLayout(context).apply {
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)
                                .apply { setMargins(dp(3), 0, dp(3), 0) }
                            background = ContextCompat.getDrawable(context, if (isDark) R.drawable.key_bg_ios_dark else R.drawable.key_bg_ios_light)
                            setPadding(dp(4), dp(4), dp(4), dp(4))
                            isClickable = true
                            isFocusable = false

                            setOnClickListener { v ->
                                v.animate().cancel()
                                v.animate()
                                    .scaleX(1.22f)
                                    .scaleY(1.22f)
                                    .setDuration(100L)
                                    .setInterpolator(OvershootInterpolator(2.5f))
                                    .withEndAction {
                                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120L).start()
                                    }
                                    .start()
                                onStickerAction(stickerFile)
                            }

                            setOnLongClickListener {
                                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                showDeleteStickerModal(stickerFile) {
                                    stickerManager.deleteSticker(stickerFile)
                                    renderContent("STICKERS")
                                }
                                true
                            }
                        }

                        val ivSticker = android.widget.ImageView(context).apply {
                            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                            try {
                                val bmp = android.graphics.BitmapFactory.decodeFile(stickerFile.absolutePath)
                                setImageBitmap(bmp)
                            } catch (_: Throwable) {}
                        }
                        itemFrame.addView(ivSticker)
                        row.addView(itemFrame)
                        count++

                        if (count == 4) {
                            layout.addView(row)
                            row = LinearLayout(context).apply {
                                orientation = LinearLayout.HORIZONTAL
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(75))
                                    .apply { setMargins(0, dp(3), 0, dp(3)) }
                            }
                            count = 0
                        }
                    }

                    if (count > 0) {
                        // Fill remaining spaces in row
                        while (count < 4) {
                            val spacer = View(context).apply {
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)
                                    .apply { setMargins(dp(3), 0, dp(3), 0) }
                            }
                            row.addView(spacer)
                            count++
                        }
                        layout.addView(row)
                    }
                }

                scroll.addView(layout)
                contentContainer.addView(scroll)
                applySlideTransition(scroll, direction)
            } else {
                // Render Emojis for selected category using 120 FPS Hardware-Recycled Grid (Butter-smooth transitions)
                val emojiList = EmojiBoardManager.emojis[selectedCategory] ?: emptyList()

                val recyclerView = androidx.recyclerview.widget.RecyclerView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    layoutManager = androidx.recyclerview.widget.GridLayoutManager(context, if (isLandscape) 14 else 8)
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    clipToPadding = false
                    isVerticalScrollBarEnabled = true
                    setItemViewCacheSize(32)
                }

                val adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
                    override fun getItemCount(): Int = emojiList.size

                    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                        val itemContainer = FrameLayout(context).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                dp(44)
                            )
                            isClickable = true
                            isFocusable = false
                        }
                        val ivEmoji = android.widget.ImageView(context).apply {
                            layoutParams = FrameLayout.LayoutParams(dp(29), dp(29), Gravity.CENTER)
                            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        }
                        val tvFallback = TextView(context).apply {
                            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                            textSize = 24f
                            gravity = Gravity.CENTER
                            visibility = View.GONE
                        }
                        itemContainer.addView(ivEmoji)
                        itemContainer.addView(tvFallback)
                        return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemContainer) {}
                    }

                    override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                        val emo = emojiList[position]
                        val container = holder.itemView as FrameLayout
                        val iv = container.getChildAt(0) as android.widget.ImageView
                        val tv = container.getChildAt(1) as TextView

                        val bmp = AppleEmojiAssetManager.getEmojiBitmap(context, emo)
                        if (bmp != null) {
                            iv.setImageBitmap(bmp)
                            iv.visibility = View.VISIBLE
                            tv.visibility = View.GONE
                        } else {
                            tv.text = emo
                            tv.visibility = View.VISIBLE
                            iv.visibility = View.GONE
                        }

                        val variants = SkinToneManager.skinToneMap[emo]
                        if (variants != null) {
                            container.setOnLongClickListener { v ->
                                try {
                                    v.performHapticFeedback(
                                        android.view.HapticFeedbackConstants.LONG_PRESS,
                                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                    )
                                } catch (_: Throwable) {}
                                showSkinTonePopup(emo, variants, v)
                                true
                            }
                        } else {
                            container.setOnLongClickListener(null)
                        }

                        container.setOnClickListener { v ->
                            v.animate().cancel()
                            v.scaleX = 1.0f
                            v.scaleY = 1.0f
                            v.rotation = 0f

                            try {
                                v.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                )
                            } catch (_: Throwable) {}

                            val wobbleAngle = if (emo.hashCode() % 2 == 0) 16f else -16f

                            v.animate()
                                .scaleX(1.6f)
                                .scaleY(1.6f)
                                .rotation(wobbleAngle)
                                .setDuration(120L)
                                .setInterpolator(OvershootInterpolator(3.5f))
                                .withEndAction {
                                    v.animate()
                                        .scaleX(1.0f)
                                        .scaleY(1.0f)
                                        .rotation(0f)
                                        .setDuration(180L)
                                        .setInterpolator(DecelerateInterpolator())
                                        .start()
                                }
                                .start()

                            onEmojiAction(emo)
                        }
                    }
                }

                recyclerView.adapter = adapter
                contentContainer.addView(recyclerView)
                applySlideTransition(recyclerView, direction)
            }
        }

        fun updateTabStyles() {
            for ((key, btn) in tabViews) {
                if (key == activeCat) {
                    btn.setBackgroundResource(R.drawable.key_bg_blue)
                    btn.setTextColor(Color.WHITE)
                } else {
                    btn.setBackgroundColor(Color.TRANSPARENT)
                    btn.setTextColor(if (isDark) Color.LTGRAY else Color.DKGRAY)
                }
                btn.setTypeface(btn.typeface, if (key == activeCat) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }
        }

        fun switchToCategory(newCat: String, direction: Int = 0) {
            if (newCat == activeCat && direction == 0) return
            activeCat = newCat
            updateTabStyles()
            renderContent(activeCat, direction)

            tabViews[newCat]?.let { tabBtn ->
                tabsScroll.post {
                    val scrollX = tabBtn.left - (tabsScroll.width / 2) + (tabBtn.width / 2)
                    tabsScroll.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
                }
            }
        }
        switchToCategoryRef = { cat, dir -> switchToCategory(cat, dir) }

        // Build Tab Buttons
        for ((catKey, catLabel) in tabCategories) {
            val tabBtn = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(28)
                ).apply { setMargins(dp(3), 0, dp(3), 0) }
                setPadding(dp(10), 0, dp(10), 0)
                gravity = Gravity.CENTER
                text = catLabel
                textSize = 13.5f
                isClickable = true
                isFocusable = false

                setOnClickListener { v ->
                    v.animate().cancel()
                    v.animate()
                        .scaleX(1.18f)
                        .scaleY(1.18f)
                        .setDuration(90L)
                        .setInterpolator(OvershootInterpolator(2.5f))
                        .withEndAction {
                            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100L).start()
                        }
                        .start()

                    if (catKey == "ADD_STICKER") {
                        onAddStickerAction()
                    } else {
                        val oldIdx = contentTabKeys.indexOf(activeCat)
                        val newIdx = contentTabKeys.indexOf(catKey)
                        val dir = if (newIdx > oldIdx) 1 else if (newIdx < oldIdx) -1 else 0
                        switchToCategory(catKey, dir)
                    }
                }
            }
            tabViews[catKey] = tabBtn
            tabsLayout.addView(tabBtn)
        }

        tabsScroll.addView(tabsLayout)
        mainLayout.addView(tabsScroll)

        // Initial content render
        updateTabStyles()
        renderContent(activeCat, 0)

        mainLayout.addView(contentContainer)

        // Bottom Bar (ABC + Backspace)
        val bottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            ).apply { setMargins(dp(6), dp(2), dp(6), dp(4)) }
        }

        val btnAbc = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(70), LinearLayout.LayoutParams.MATCH_PARENT)
            background = ContextCompat.getDrawable(context, if (isDark) R.drawable.key_bg_special_dark else R.drawable.key_bg_special_light)
            text = "ABC"
            setTextColor(if (isDark) Color.WHITE else Color.BLACK)
            textSize = 15f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            setupInstantTouch(this) { onBackAction() }
        }
        bottomBar.addView(btnAbc)

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1.0f)
        }
        bottomBar.addView(spacer)

        val btnBackspace = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(70), LinearLayout.LayoutParams.MATCH_PARENT)
            background = ContextCompat.getDrawable(context, if (isDark) R.drawable.key_bg_special_dark else R.drawable.key_bg_special_light)
            setImageResource(R.drawable.ic_backspace)
            setColorFilter(if (isDark) Color.WHITE else Color.BLACK)
            isClickable = true
            isFocusable = false
            setupRapidBackspaceTouch(this) { onEmojiAction("ACTION_BACKSPACE") }
        }
        bottomBar.addView(btnBackspace)
        mainLayout.addView(bottomBar)

        root.addView(mainLayout)
        return root
    }


    // --- 4. CLIPBOARD HISTORY VIEW (Card & List Mode + Pin/Delete Glassmorphic Action Modal) ---
    fun createClipboardView(
        isDark: Boolean,
        themeMode: String = "auto",
        items: List<com.maoneart.ioskeyboard.clipboard.ClipboardItem>,
        currentViewMode: String = "card",
        onPasteAction: (String) -> Unit,
        onTogglePinAction: (String) -> Unit,
        onDeleteAction: (String) -> Unit,
        onClearUnpinnedAction: () -> Unit,
        onToggleViewMode: (String) -> Unit,
        onCloseAction: () -> Unit
    ): View {
        val root = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }

        val isWoody = (themeMode == "woody")
        val cardBg = when {
            isWoody -> R.drawable.key_bg_woody
            isDark -> R.drawable.key_bg_ios_dark
            else -> R.drawable.key_bg_ios_light
        }
        val textColor = when {
            isWoody -> Color.parseColor("#FFF3E0")
            isDark -> Color.WHITE
            else -> Color.BLACK
        }
        val subtextColor = when {
            isWoody -> Color.parseColor("#C4A587")
            isDark -> Color.parseColor("#98989D")
            else -> Color.parseColor("#8E8E93")
        }

        // Header Row
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(38)
            ).apply { setMargins(0, 0, 0, dp(4)) }
        }

        val title = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            text = "📋 Papan Klip"
            setTextColor(textColor)
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        headerRow.addView(title)

        // View Mode Toggle Button (Card vs List)
        val btnMode = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(30)).apply { setMargins(0, 0, dp(6), 0) }
            background = ContextCompat.getDrawable(context, if (isWoody) R.drawable.key_bg_woody_special else if (isDark) R.drawable.key_bg_special_dark else R.drawable.key_bg_special_light)
            text = if (currentViewMode == "card") "🎴 Card" else "📑 List"
            setTextColor(textColor)
            textSize = 12f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            setupInstantTouch(this) {
                onToggleViewMode(if (currentViewMode == "card") "list" else "card")
            }
        }
        headerRow.addView(btnMode)

        // Modal Action Container (Overlay Glassmorphism)
        var modalOverlay: FrameLayout? = null

        fun hideModal() {
            modalOverlay?.let { root.removeView(it) }
            modalOverlay = null
        }

        fun showClearConfirmModal() {
            hideModal()
            val overlay = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#99000000"))
                isClickable = true
                isFocusable = true
                setOnClickListener { hideModal() }
            }

            val dialogCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                ).apply { setMargins(dp(22), 0, dp(22), 0) }
                background = ContextCompat.getDrawable(context, if (isWoody) R.drawable.key_bg_woody else if (isDark) R.drawable.key_bg_special_dark else R.drawable.card_bg_ios)
                setPadding(dp(18), dp(16), dp(18), dp(16))
                isClickable = true
            }

            val tvTitle = TextView(context).apply {
                text = "Bersihkan Papan Klip?"
                textSize = 15.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(textColor)
                gravity = Gravity.CENTER
            }
            dialogCard.addView(tvTitle)

            val tvDesc = TextView(context).apply {
                text = "Semua riwayat yang tidak disematkan akan dihapus. Item yang di-pin (📌) akan tetap tersimpan aman."
                textSize = 12.5f
                setTextColor(subtextColor)
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(14))
            }
            dialogCard.addView(tvDesc)

            // Symmetrical 2-Column Action Grid (MaoneArt Glassmorphism Rule)
            val btnRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(42)
                )
            }

            val btnCancel = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f).apply {
                    setMargins(0, 0, dp(6), 0)
                }
                background = ContextCompat.getDrawable(context, if (isDark || isWoody) R.drawable.key_bg_special_dark else R.drawable.key_bg_special_light)
                text = "Batal"
                setTextColor(textColor)
                textSize = 13.5f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = false
                setupInstantTouch(this) { hideModal() }
            }
            btnRow.addView(btnCancel)

            val btnConfirm = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f).apply {
                    setMargins(dp(6), 0, 0, 0)
                }
                background = ContextCompat.getDrawable(context, if (isWoody) R.drawable.key_bg_woody_action else R.drawable.key_bg_blue)
                text = "🗑️ Bersihkan"
                setTextColor(Color.WHITE)
                textSize = 13.5f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = false
                setupInstantTouch(this) {
                    hideModal()
                    onClearUnpinnedAction()
                }
            }
            btnRow.addView(btnConfirm)
            dialogCard.addView(btnRow)

            overlay.addView(dialogCard)
            modalOverlay = overlay
            root.addView(overlay)
        }

        // Clear Unpinned Button
        if (items.any { !it.isPinned }) {
            val btnClear = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(72), dp(30)).apply { setMargins(0, 0, dp(6), 0) }
                background = ContextCompat.getDrawable(context, if (isWoody) R.drawable.key_bg_woody_special else if (isDark) R.drawable.key_bg_special_dark else R.drawable.key_bg_special_light)
                text = "🗑️ Bersih"
                setTextColor(subtextColor)
                textSize = 11.5f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = false
                setupInstantTouch(this) { showClearConfirmModal() }
            }
            headerRow.addView(btnClear)
        }

        // Close Button
        val btnClose = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(60), dp(30))
            background = ContextCompat.getDrawable(context, if (isWoody) R.drawable.key_bg_woody_action else R.drawable.key_bg_blue)
            text = "Tutup"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            setupInstantTouch(this) { onCloseAction() }
        }
        headerRow.addView(btnClose)
        mainLayout.addView(headerRow)

        fun showActionModal(item: com.maoneart.ioskeyboard.clipboard.ClipboardItem) {
            hideModal()
            val overlay = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#99000000"))
                isClickable = true
                isFocusable = true
                setOnClickListener { hideModal() }
            }

            val dialogCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                ).apply { setMargins(dp(20), 0, dp(20), 0) }
                background = ContextCompat.getDrawable(context, if (isWoody) R.drawable.key_bg_woody else if (isDark) R.drawable.key_bg_special_dark else R.drawable.card_bg_ios)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                isClickable = true
            }

            // Preview snippet
            val tvPreview = TextView(context).apply {
                text = item.text
                maxLines = 3
                textSize = 13.5f
                setTextColor(textColor)
                setPadding(dp(10), dp(8), dp(10), dp(10))
                background = ContextCompat.getDrawable(context, if (isWoody) R.drawable.key_bg_woody_special else if (isDark) R.drawable.key_bg_ios_dark else R.drawable.key_bg_ios_light)
            }
            dialogCard.addView(tvPreview)

            // Symmetrical 2-Column Action Grid (MaoneArt Rule)
            val btnRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(42)
                ).apply { setMargins(0, dp(12), 0, 0) }
            }

            // Button 1: Pin / Unpin
            val btnPin = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f).apply {
                    setMargins(0, 0, dp(6), 0)
                }
                background = ContextCompat.getDrawable(context, if (isWoody) R.drawable.key_bg_woody_action else R.drawable.key_bg_blue)
                text = if (item.isPinned) "📌 Batal Pin" else "📌 Sematkan"
                setTextColor(Color.WHITE)
                textSize = 13.5f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = false
                setupInstantTouch(this) {
                    hideModal()
                    onTogglePinAction(item.id)
                }
            }
            btnRow.addView(btnPin)

            // Button 2: Delete
            val btnDelete = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f).apply {
                    setMargins(dp(6), 0, 0, 0)
                }
                background = ContextCompat.getDrawable(context, if (isDark || isWoody) R.drawable.key_bg_special_dark else R.drawable.key_bg_special_light)
                text = "🗑️ Hapus"
                setTextColor(Color.parseColor("#FF3B30"))
                textSize = 13.5f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = false
                setupInstantTouch(this) {
                    hideModal()
                    onDeleteAction(item.id)
                }
            }
            btnRow.addView(btnDelete)
            dialogCard.addView(btnRow)

            overlay.addView(dialogCard)
            modalOverlay = overlay
            root.addView(overlay)
        }

        // Scroll Content
        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
            isFillViewport = true
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        if (items.isEmpty()) {
            val empty = TextView(context).apply {
                text = "Belum ada teks yang disalin."
                setTextColor(subtextColor)
                textSize = 13f
                setPadding(dp(16), dp(32), dp(16), dp(32))
                gravity = Gravity.CENTER
            }
            contentLayout.addView(empty)
        } else {
            if (currentViewMode == "card") {
                // 2-Column Grid of Modern Cards
                val chunks = items.chunked(2)
                for (chunk in chunks) {
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(76)
                        ).apply { setMargins(0, dp(3), 0, dp(3)) }
                    }

                    for (item in chunk) {
                        val card = FrameLayout(context).apply {
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f).apply {
                                setMargins(dp(3), 0, dp(3), 0)
                            }
                            background = ContextCompat.getDrawable(context, cardBg)
                            setPadding(dp(10), dp(8), dp(10), dp(8))
                            isClickable = true
                            isFocusable = false

                            setOnClickListener {
                                onPasteAction(item.text)
                            }
                            setOnLongClickListener {
                                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                showActionModal(item)
                                true
                            }
                        }

                        val tvText = TextView(context).apply {
                            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                            text = item.text
                            maxLines = 3
                            textSize = 12.5f
                            setTextColor(textColor)
                        }
                        card.addView(tvText)

                        if (item.isPinned) {
                            val pinBadge = TextView(context).apply {
                                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
                                text = "📌"
                                textSize = 11f
                            }
                            card.addView(pinBadge)
                        }
                        row.addView(card)
                    }

                    // Balance empty cell if odd number of items in row
                    if (chunk.size == 1) {
                        val dummy = View(context).apply {
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f).apply {
                                setMargins(dp(3), 0, dp(3), 0)
                            }
                        }
                        row.addView(dummy)
                    }
                    contentLayout.addView(row)
                }
            } else {
                // List Mode (1-Column full width)
                for (item in items) {
                    val listCard = FrameLayout(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, dp(3), 0, dp(3)) }
                        background = ContextCompat.getDrawable(context, cardBg)
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        isClickable = true
                        isFocusable = false

                        setOnClickListener {
                            onPasteAction(item.text)
                        }
                        setOnLongClickListener {
                            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            showActionModal(item)
                            true
                        }
                    }

                    val rowLayout = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                    }

                    if (item.isPinned) {
                        val pinTv = TextView(context).apply {
                            text = "📌 "
                            textSize = 13f
                        }
                        rowLayout.addView(pinTv)
                    }

                    val snippetView = TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                        text = item.text
                        maxLines = 2
                        textSize = 13.5f
                        setTextColor(textColor)
                    }
                    rowLayout.addView(snippetView)
                    listCard.addView(rowLayout)
                    contentLayout.addView(listCard)
                }
            }
        }

        scroll.addView(contentLayout)
        mainLayout.addView(scroll)
        root.addView(mainLayout)
        return root
    }


    // Helper functions
    private fun createRow(
        keys: List<String>,
        isShifted: Boolean,
        keyBg: Int,
        textColor: Int,
        onKeyAction: (String) -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isMotionEventSplittingEnabled = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            ).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
        }
        for (k in keys) {
            val label = if (isShifted) k.uppercase() else k.lowercase()
            val btn = createKeyButton(label, 1.0f, keyBg, textColor, onKeyAction)
            letterKeyViews.add(Pair(btn, k))
            row.addView(btn)
        }
        return row
    }

    private fun createKeyButton(
        label: String,
        weight: Float,
        keyBg: Int,
        textColor: Int,
        onKeyAction: (String) -> Unit
    ): TextView {
        return TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, keyBg)
            text = label
            setTextColor(textColor)
            textSize = if (isLandscape) 21f else 22f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            isHapticFeedbackEnabled = true
            val isLetter = label.length == 1 && label[0].isLetter()
            if (isLetter) {
                setupLetterKeyTouch(this) { onKeyAction(text.toString()) }
            } else {
                setupSymbolKeyTouch(this) { onKeyAction(text.toString()) }
            }
        }
    }

    // Apple iOS 2D Spacebar Trackpad Cursor Mover (Left, Right, Up, Down & Text Selection)
    private fun setupSpacebarTrackpadTouch(
        view: TextView,
        defaultText: String,
        isDark: Boolean,
        themeMode: String = "auto",
        onSpaceAction: () -> Unit,
        onCursorMove: (Int, Int, Boolean) -> Unit
    ) {
        view.isHapticFeedbackEnabled = true
        val handler = Handler(Looper.getMainLooper())
        var isTrackpadActive = false
        var isSelectingText = false
        var activePointerId = -1
        var startX = 0f
        var startY = 0f
        var lastStepX = 0f
        var lastStepY = 0f
        val stepDistanceX = dp(12).toFloat()
        val stepDistanceY = dp(14).toFloat()
        val isWoody = (themeMode == "woody")
        val normalTextColor = if (isWoody) Color.parseColor("#FFF3E0") else if (isDark) Color.WHITE else Color.BLACK

        fun restoreLetterKeyText() {
            for ((tv, _) in letterKeyViews) {
                tv.alpha = 1f
                tv.setTextColor(normalTextColor)
            }
        }

        fun endTrackpadSession() {
            isTrackpadActive = false
            isSpaceTrackpadActive = false
            isSelectingText = false
            view.text = defaultText
            view.setTextColor(if (isWoody) Color.parseColor("#C4A587") else if (isDark) Color.parseColor("#98989D") else Color.parseColor("#8E8E93"))
            view.setTypeface(view.typeface, android.graphics.Typeface.NORMAL)
            restoreLetterKeyText()
            onTrackpadStateChanged?.invoke(false)
        }

        val activateRunnable = Runnable {
            isTrackpadActive = true
            isSpaceTrackpadActive = true
            isSelectingText = false
            view.text = "│   geser kursor   │"
            view.setTextColor(if (isWoody) Color.parseColor("#FFA726") else ContextCompat.getColor(context, R.color.ios_blue))
            view.setTypeface(view.typeface, android.graphics.Typeface.BOLD)
            view.parent?.requestDisallowInterceptTouchEvent(true)

            // Hide ONLY the letter label text (A-Z), keeping key button shapes & layout completely intact
            for ((tv, _) in letterKeyViews) {
                tv.setTextColor(Color.TRANSPARENT)
            }

            try {
                view.performHapticFeedback(
                    android.view.HapticFeedbackConstants.LONG_PRESS,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                )
            } catch (_: Throwable) {}

            onTrackpadStateChanged?.invoke(true)
        }

        cancelCurrentTrackpad = {
            handler.removeCallbacks(activateRunnable)
            endTrackpadSession()
        }

        onTrackpadSelectionToggle = {
            if (isTrackpadActive) {
                isSelectingText = !isSelectingText
                if (isSelectingText) {
                    view.text = "│   blok teks   │"
                    view.setTextColor(Color.parseColor("#34C759"))
                    try {
                        view.performHapticFeedback(
                            android.view.HapticFeedbackConstants.LONG_PRESS,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                        )
                    } catch (_: Throwable) {}
                } else {
                    view.text = "│   geser kursor   │"
                    view.setTextColor(if (isWoody) Color.parseColor("#FFA726") else ContextCompat.getColor(context, R.color.ios_blue))
                    try {
                        view.performHapticFeedback(
                            android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                        )
                    } catch (_: Throwable) {}
                }
                onCursorMove(0, 0, isSelectingText)
            }
        }

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    activePointerId = event.getPointerId(0)
                    startX = event.x
                    startY = event.y
                    lastStepX = event.x
                    lastStepY = event.y
                    isTrackpadActive = false
                    isSelectingText = false
                    try {
                        v.performHapticFeedback(
                            android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                        )
                    } catch (_: Throwable) {}
                    handler.postDelayed(activateRunnable, 260L)
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (isTrackpadActive) {
                        onTrackpadSelectionToggle?.invoke()
                    } else {
                        handler.removeCallbacks(activateRunnable)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isTrackpadActive) {
                        v.parent?.requestDisallowInterceptTouchEvent(true)

                        val pIdx = if (activePointerId != -1) event.findPointerIndex(activePointerId) else 0
                        val currentX = if (pIdx >= 0 && pIdx < event.pointerCount) event.getX(pIdx) else event.x
                        val currentY = if (pIdx >= 0 && pIdx < event.pointerCount) event.getY(pIdx) else event.y

                        // 1. Sumbu Horisontal (Kiri / Kanan)
                        val deltaX = currentX - lastStepX
                        if (deltaX > stepDistanceX) {
                            val steps = (deltaX / stepDistanceX).toInt()
                            onCursorMove(steps, 0, isSelectingText)
                            lastStepX += steps * stepDistanceX
                            try {
                                v.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                )
                            } catch (_: Throwable) {}
                        } else if (deltaX < -stepDistanceX) {
                            val steps = (kotlin.math.abs(deltaX) / stepDistanceX).toInt()
                            onCursorMove(-steps, 0, isSelectingText)
                            lastStepX -= steps * stepDistanceX
                            try {
                                v.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                )
                            } catch (_: Throwable) {}
                        }

                        // 2. Sumbu Vertikal (Atas / Bawah baris teks)
                        val deltaY = currentY - lastStepY
                        if (deltaY < -stepDistanceY) {
                            val steps = (kotlin.math.abs(deltaY) / stepDistanceY).toInt()
                            onCursorMove(0, -steps, isSelectingText)
                            lastStepY -= steps * stepDistanceY
                            try {
                                v.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                )
                            } catch (_: Throwable) {}
                        } else if (deltaY > stepDistanceY) {
                            val steps = (deltaY / stepDistanceY).toInt()
                            onCursorMove(0, steps, isSelectingText)
                            lastStepY += steps * stepDistanceY
                            try {
                                v.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                )
                            } catch (_: Throwable) {}
                        }
                    } else {
                        val moveDist = kotlin.math.hypot((event.x - startX).toDouble(), (event.y - startY).toDouble()).toFloat()
                        if (moveDist > dp(25)) {
                            handler.removeCallbacks(activateRunnable)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val pIdx = event.actionIndex
                    if (event.getPointerId(pIdx) == activePointerId) {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        handler.removeCallbacks(activateRunnable)
                        v.isPressed = false
                        activePointerId = -1
                        if (isTrackpadActive) {
                            endTrackpadSession()
                        } else {
                            onSpaceAction()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    handler.removeCallbacks(activateRunnable)
                    v.isPressed = false
                    activePointerId = -1
                    if (isTrackpadActive) {
                        endTrackpadSession()
                    } else {
                        onSpaceAction()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    handler.removeCallbacks(activateRunnable)
                    v.isPressed = false
                    activePointerId = -1
                    endTrackpadSession()
                    true
                }
                else -> false
            }
        }
    }

    // iOS Key Pop-up Preview Touch Handler (0ms Latency, Hardware Accelerated with Haptic Feedback & Multi-Touch)
    // Direct view isolation: each key manages ONLY its own pressed state to completely eliminate ghost presses & stuck "mendem" keys.
    private fun setupLetterKeyTouch(view: TextView, onTrigger: () -> Unit) {
        view.isHapticFeedbackEnabled = true
        var watchdogRunnable: Runnable? = null

        view.setOnTouchListener { v, event ->
            if (isSpaceTrackpadActive) {
                if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    onTrackpadSelectionToggle?.invoke()
                }
                return@setOnTouchListener true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    watchdogRunnable?.let { v.removeCallbacks(it) }
                    v.isPressed = true

                    try {
                        v.performHapticFeedback(
                            android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                        )
                    } catch (_: Throwable) {}

                    popupController?.showPopupForKey(v, view.text.toString())
                    onTrigger()

                    // Anti-mendem watchdog: auto-release pressed state after 180ms if Android drops ACTION_UP
                    val r = Runnable {
                        v.isPressed = false
                        popupController?.hidePopup()
                    }
                    watchdogRunnable = r
                    v.postDelayed(r, 180L)

                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    watchdogRunnable?.let { v.removeCallbacks(it) }
                    watchdogRunnable = null
                    v.isPressed = false
                    popupController?.hidePopup()
                    true
                }
                else -> false
            }
        }
    }

    // Symbol / Number Key Touch Handler (Zero predictive redirection: always sends exact symbol typed!)
    private fun setupSymbolKeyTouch(view: TextView, onTrigger: () -> Unit) {
        view.isHapticFeedbackEnabled = true
        var watchdogRunnable: Runnable? = null
        view.setOnTouchListener { v, event ->
            if (isSpaceTrackpadActive) {
                if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    onTrackpadSelectionToggle?.invoke()
                }
                return@setOnTouchListener true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    watchdogRunnable?.let { v.removeCallbacks(it) }
                    v.isPressed = true
                    try {
                        v.performHapticFeedback(
                            android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                        )
                    } catch (_: Throwable) {}
                    popupController?.showPopupForKey(v, view.text.toString())
                    onTrigger()
                    val r = Runnable {
                        v.isPressed = false
                        popupController?.hidePopup()
                    }
                    watchdogRunnable = r
                    v.postDelayed(r, 180L)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    watchdogRunnable?.let { v.removeCallbacks(it) }
                    watchdogRunnable = null
                    v.isPressed = false
                    popupController?.hidePopup()
                    true
                }
                else -> false
            }
        }
    }

    // 0ms Latency Instant Touch Down Listener with Hardware Haptic Trigger & Multi-Touch + Safety Watchdog
    private fun setupInstantTouch(view: View, onTrigger: () -> Unit) {
        view.isHapticFeedbackEnabled = true
        var watchdogRunnable: Runnable? = null
        view.setOnTouchListener { v, event ->
            if (isSpaceTrackpadActive) {
                if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    onTrackpadSelectionToggle?.invoke()
                }
                return@setOnTouchListener true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    watchdogRunnable?.let { v.removeCallbacks(it) }
                    v.isPressed = true
                    try {
                        v.performHapticFeedback(
                            android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                        )
                    } catch (_: Throwable) {}
                    onTrigger()
                    val r = Runnable { v.isPressed = false }
                    watchdogRunnable = r
                    v.postDelayed(r, 180L)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    watchdogRunnable?.let { v.removeCallbacks(it) }
                    watchdogRunnable = null
                    v.isPressed = false
                    true
                }
                else -> false
            }
        }
    }

    // Anti-Accidental Touch Dictation Key Listener:
    // Requires deliberate single-pointer tap-and-release within view bounds.
    // Strictly ignores multi-touch ACTION_POINTER_DOWN during fast two-thumb typing.
    private fun setupDictationKeyTouch(view: View, onTrigger: () -> Unit) {
        view.isHapticFeedbackEnabled = true
        var isEligibleDown = false
        var downTime = 0L
        var watchdogRunnable: Runnable? = null

        view.setOnTouchListener { v, event ->
            if (isSpaceTrackpadActive) {
                if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    onTrackpadSelectionToggle?.invoke()
                }
                return@setOnTouchListener true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    watchdogRunnable?.let { v.removeCallbacks(it) }
                    isEligibleDown = true
                    downTime = System.currentTimeMillis()
                    v.isPressed = true
                    val r = Runnable {
                        v.isPressed = false
                        isEligibleDown = false
                    }
                    watchdogRunnable = r
                    v.postDelayed(r, 1200L)
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // Multi-touch means user is typing letters with two thumbs -> cancel mic touch!
                    watchdogRunnable?.let { v.removeCallbacks(it) }
                    isEligibleDown = false
                    v.isPressed = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isEligibleDown) {
                        val inBounds = event.x in 0f..v.width.toFloat() && event.y in 0f..v.height.toFloat()
                        if (!inBounds) {
                            watchdogRunnable?.let { v.removeCallbacks(it) }
                            isEligibleDown = false
                            v.isPressed = false
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    watchdogRunnable?.let { v.removeCallbacks(it) }
                    v.isPressed = false
                    if (isEligibleDown) {
                        val inBounds = event.x in 0f..v.width.toFloat() && event.y in 0f..v.height.toFloat()
                        val duration = System.currentTimeMillis() - downTime
                        if (inBounds && duration in 40L..1200L) {
                            try {
                                v.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                )
                            } catch (_: Throwable) {}
                            onTrigger()
                        }
                    }
                    isEligibleDown = false
                    true
                }
                MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    watchdogRunnable?.let { v.removeCallbacks(it) }
                    isEligibleDown = false
                    v.isPressed = false
                    true
                }
                else -> false
            }
        }
    }

    // High-Speed Auto Repeat Backspace ("Hapus Ngebut" with Multi-Touch + Safety Release Watchdog)
    private fun setupRapidBackspaceTouch(view: View, onBackspace: () -> Unit) {
        view.isHapticFeedbackEnabled = true
        val handler = Handler(Looper.getMainLooper())
        var repeatRunnable: Runnable? = null
        var deleteCount = 0
        var activePointerId = -1

        fun cancelRepeat(v: View) {
            v.isPressed = false
            repeatRunnable?.let { handler.removeCallbacks(it) }
            repeatRunnable = null
            activePointerId = -1
        }

        view.setOnTouchListener { v, event ->
            if (isSpaceTrackpadActive) {
                if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    onTrackpadSelectionToggle?.invoke()
                }
                return@setOnTouchListener true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelRepeat(v)
                    v.isPressed = true
                    activePointerId = event.getPointerId(0)
                    deleteCount = 0
                    try {
                        v.performHapticFeedback(
                            android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                        )
                    } catch (_: Throwable) {}
                    // 1. Delete character immediately
                    onBackspace()

                    // 2. Start repeating delete job
                    val r = object : Runnable {
                        override fun run() {
                            deleteCount++
                            try {
                                v.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                )
                            } catch (_: Throwable) {}
                            onBackspace()
                            // Accelerate: after 8 deletions switch from 60ms to 35ms (super ngebut!)
                            val nextDelay = if (deleteCount > 8) 35L else 60L
                            handler.postDelayed(this, nextDelay)
                        }
                    }
                    repeatRunnable = r
                    handler.postDelayed(r, 320L)
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // Secondary finger touch shouldn't restart or interfere with repeating backspace
                    true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val pIdx = event.actionIndex
                    if (event.getPointerId(pIdx) == activePointerId) {
                        cancelRepeat(v)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelRepeat(v)
                    true
                }
                else -> false
            }
        }
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
