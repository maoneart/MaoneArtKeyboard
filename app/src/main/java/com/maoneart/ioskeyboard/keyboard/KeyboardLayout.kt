package com.maoneart.ioskeyboard.keyboard

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.maoneart.ioskeyboard.R
import com.maoneart.ioskeyboard.emoji.EmojiBoardManager

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

    private val letterKeyViews = mutableListOf<Pair<TextView, String>>()
    private var shiftButtonRef: ImageButton? = null
    private var returnButtonRef: ReturnButtonLayout? = null
    private var popupController: com.maoneart.ioskeyboard.keyboard.swipe.QuickPathContainerLayout? = null

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

        // 1. Web Search or Filter: Show Flat White Search Icon with Action BG (100% Dead Center)
        if (imeAction == EditorInfo.IME_ACTION_SEARCH ||
            variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
            btn.showIcon(R.drawable.ic_search_white, actionKeyBg)
            return
        }

        // 2. Browser URL or Web Address: Show "Buka" (Action BG)
        if (imeAction == EditorInfo.IME_ACTION_GO || variation == InputType.TYPE_TEXT_VARIATION_URI) {
            btn.showText("Buka", 14f, Color.WHITE, actionKeyBg)
            return
        }

        // 3. Form Send Action: Show "Kirim" (Action BG)
        if (imeAction == EditorInfo.IME_ACTION_SEND) {
            btn.showText("Kirim", 14f, Color.WHITE, actionKeyBg)
            return
        }

        // 4. WhatsApp, Telegram, Notes & all multiline / standard newline:
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
        onKeyAction: (String) -> Unit,
        onCursorMove: (Int, Int) -> Unit = { _, _ -> },
        onSwipeComplete: (List<Char>) -> Unit = {},
        onSwipeHaptic: () -> Unit = {},
        onSwipeLiveUpdate: (List<Char>) -> Unit = {}
    ): View {
        letterKeyViews.clear()
        cachedHitBoxes = emptyList()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER
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
        root.addView(createRow(row1, isShifted, keyBg, textColor, onKeyAction))

        // Row 2 (Padded for iOS stagger)
        val r2Layout = createRow(row2, isShifted, keyBg, textColor, onKeyAction)
        r2Layout.setPadding(dp(18), 0, dp(18), 0)
        root.addView(r2Layout)

        // Row 3 (Shift + Letters + Backspace)
        val r3Layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
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
        val btnSpace = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 4.3f)
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

        // Dictation Microphone Button (Apple iOS Monochrome Mic)
        val btnMic = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.1f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            setImageResource(R.drawable.ic_mic_ios)
            setColorFilter(textColor)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            isClickable = true
            isFocusable = false
            setupInstantTouch(this) { onKeyAction("ACTION_DICTATION") }
        }
        r4Layout.addView(btnMic)

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

        val rx = rawX.toInt()
        val ry = rawY.toInt()
        val tol = dp(4)

        // 1. Direct hit on key bounding box (In-Memory, Zero JNI)
        for (box in boxes) {
            val r = box.hitRect
            if (rx in (r.left - tol)..(r.right + tol) &&
                ry in (r.top - tol)..(r.bottom + tol)) {
                return Pair(box.tv.text.toString(), r)
            }
        }

        // 2. Nearest key center within snap radius (28dp) for fluid gesture sweeps
        var closest: KeyHitBox? = null
        var closestDistSq = Float.MAX_VALUE
        val maxSnapRadius = dp(28).toFloat()
        val maxSnapRadiusSq = maxSnapRadius * maxSnapRadius

        for (box in boxes) {
            val dx = rawX - box.centerX
            val dy = rawY - box.centerY
            val distSq = dx * dx + dy * dy
            if (distSq < closestDistSq && distSq <= maxSnapRadiusSq) {
                closestDistSq = distSq
                closest = box
            }
        }

        return closest?.let { Pair(it.tv.text.toString(), it.hitRect) }
    }

    // --- 2. SYMBOLS LAYOUT (123 & #+=) ---
    fun createSymbolsView(
        isMoreSymbols: Boolean,
        isDark: Boolean,
        themeMode: String = "auto",
        onKeyAction: (String) -> Unit,
        onCursorMove: (Int, Int) -> Unit = { _, _ -> }
    ): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER
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

        root.addView(createRow(row1, false, keyBg, textColor, onKeyAction))
        root.addView(createRow(row2, false, keyBg, textColor, onKeyAction))

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

        val btnSpace = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 4.3f)
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

        // Dictation Microphone Button (Apple iOS Monochrome Mic)
        val btnMic = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.1f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            setImageResource(R.drawable.ic_mic_ios)
            setColorFilter(textColor)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            isClickable = true
            isFocusable = false
            setupInstantTouch(this) { onKeyAction("ACTION_DICTATION") }
        }
        r4.addView(btnMic)

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

        // Top Category Bar (Smileys, Hands, Hearts, Animals, Food, Activities, Symbols, Stickers, + Add)
        val tabCategories = listOf(
            Pair("Smileys", "😀"),
            Pair("Hands", "👋"),
            Pair("Hearts", "❤️"),
            Pair("Animals", "🐶"),
            Pair("Food", "🍔"),
            Pair("Activities", "⚽"),
            Pair("Symbols", "💯"),
            Pair("STICKERS", "🖼️ Stiker"),
            Pair("ADD_STICKER", "➕")
        )

        var activeCat = "Smileys"
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

        val contentContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        }

        fun renderContent(selectedCategory: String) {
            contentContainer.removeAllViews()

            if (selectedCategory == "STICKERS") {
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

                            setOnClickListener {
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
            } else {
                // Render Emojis for selected category
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
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                }

                val emojiList = EmojiBoardManager.emojis[selectedCategory] ?: emptyList()

                var row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
                }
                var count = 0

                for (emo in emojiList) {
                    val btn = TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)
                        text = emo
                        textSize = 25f
                        gravity = Gravity.CENTER
                        isClickable = true
                        isFocusable = false

                        // Use setOnClickListener so user can freely scroll without accidental emoji taps!
                        setOnClickListener { v ->
                            v.animate()
                                .scaleX(1.35f)
                                .scaleY(1.35f)
                                .setDuration(90)
                                .withEndAction {
                                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(90).start()
                                }
                                .start()
                            onEmojiAction(emo)
                        }
                    }
                    row.addView(btn)
                    count++

                    if (count == 8) {
                        layout.addView(row)
                        row = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
                        }
                        count = 0
                    }
                }

                if (count > 0) {
                    while (count < 8) {
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
            }
        }

        fun updateTabStyles() {
            for ((key, btn) in tabViews) {
                if (key == activeCat) {
                    btn.setBackgroundResource(R.drawable.key_bg_blue)
                    btn.setTextColor(Color.WHITE)
                    btn.setTypeface(btn.typeface, android.graphics.Typeface.BOLD)
                } else {
                    btn.setBackgroundColor(Color.TRANSPARENT)
                    btn.setTextColor(if (isDark) Color.LTGRAY else Color.DKGRAY)
                    btn.setTypeface(btn.typeface, android.graphics.Typeface.NORMAL)
                }
            }
        }

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

                setOnClickListener {
                    if (catKey == "ADD_STICKER") {
                        onAddStickerAction()
                    } else {
                        activeCat = catKey
                        updateTabStyles()
                        renderContent(activeCat)
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
        renderContent(activeCat)

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
                setupInstantTouch(this) { onClearUnpinnedAction() }
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

        // Modal Action Container (Overlay Glassmorphism)
        var modalOverlay: FrameLayout? = null

        fun hideModal() {
            modalOverlay?.let { root.removeView(it) }
            modalOverlay = null
        }

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
            textSize = 22f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            isHapticFeedbackEnabled = true
            setupLetterKeyTouch(this) { onKeyAction(text.toString()) }
        }
    }

    // Apple iOS 2D Spacebar Trackpad Cursor Mover (Left, Right, Up, Down)
    private fun setupSpacebarTrackpadTouch(
        view: TextView,
        defaultText: String,
        isDark: Boolean,
        themeMode: String = "auto",
        onSpaceAction: () -> Unit,
        onCursorMove: (Int, Int) -> Unit
    ) {
        view.isHapticFeedbackEnabled = true
        val handler = Handler(Looper.getMainLooper())
        var isTrackpadActive = false
        var startX = 0f
        var startY = 0f
        var lastStepX = 0f
        var lastStepY = 0f
        val stepDistanceX = dp(12).toFloat()
        val stepDistanceY = dp(14).toFloat()
        val isWoody = (themeMode == "woody")

        val activateRunnable = Runnable {
            isTrackpadActive = true
            view.text = "│   geser kursor   │"
            view.setTextColor(if (isWoody) Color.parseColor("#FFA726") else ContextCompat.getColor(context, R.color.ios_blue))
            view.setTypeface(view.typeface, android.graphics.Typeface.BOLD)
            view.parent?.requestDisallowInterceptTouchEvent(true)
            try {
                view.performHapticFeedback(
                    android.view.HapticFeedbackConstants.LONG_PRESS,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                )
            } catch (_: Throwable) {}
        }

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    startX = event.x
                    startY = event.y
                    lastStepX = event.x
                    lastStepY = event.y
                    isTrackpadActive = false
                    try {
                        v.performHapticFeedback(
                            android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                        )
                    } catch (_: Throwable) {}
                    handler.postDelayed(activateRunnable, 260L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isTrackpadActive) {
                        v.parent?.requestDisallowInterceptTouchEvent(true)

                        // 1. Sumbu Horisontal (Kiri / Kanan)
                        val deltaX = event.x - lastStepX
                        if (deltaX > stepDistanceX) {
                            val steps = (deltaX / stepDistanceX).toInt()
                            onCursorMove(steps, 0)
                            lastStepX += steps * stepDistanceX
                            try {
                                v.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                )
                            } catch (_: Throwable) {}
                        } else if (deltaX < -stepDistanceX) {
                            val steps = (kotlin.math.abs(deltaX) / stepDistanceX).toInt()
                            onCursorMove(-steps, 0)
                            lastStepX -= steps * stepDistanceX
                            try {
                                v.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                )
                            } catch (_: Throwable) {}
                        }

                        // 2. Sumbu Vertikal (Atas / Bawah baris teks)
                        val deltaY = event.y - lastStepY
                        if (deltaY < -stepDistanceY) {
                            // Jari digeser ke ATAS
                            val steps = (kotlin.math.abs(deltaY) / stepDistanceY).toInt()
                            onCursorMove(0, -steps)
                            lastStepY -= steps * stepDistanceY
                            try {
                                v.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                )
                            } catch (_: Throwable) {}
                        } else if (deltaY > stepDistanceY) {
                            // Jari digeser ke BAWAH
                            val steps = (deltaY / stepDistanceY).toInt()
                            onCursorMove(0, steps)
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
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    handler.removeCallbacks(activateRunnable)
                    v.isPressed = false
                    if (isTrackpadActive) {
                        isTrackpadActive = false
                        view.text = defaultText
                        view.setTextColor(if (isWoody) Color.parseColor("#C4A587") else if (isDark) Color.parseColor("#98989D") else Color.parseColor("#8E8E93"))
                        view.setTypeface(view.typeface, android.graphics.Typeface.NORMAL)
                    } else {
                        onSpaceAction()
                    }
                    true
                }
                else -> false
            }
        }
    }

    // iOS Key Pop-up Preview Touch Handler with Hardware Haptic Feedback
    private fun setupLetterKeyTouch(view: TextView, onTrigger: () -> Unit) {
        view.isHapticFeedbackEnabled = true
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    popupController?.showPopupForKey(v, view.text.toString())
                    try {
                        v.performHapticFeedback(
                            android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                        )
                    } catch (_: Throwable) {}
                    onTrigger()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    popupController?.hidePopup()
                    true
                }
                else -> false
            }
        }
    }

    // 0ms Latency Instant Touch Down Listener with Hardware Haptic Trigger
    private fun setupInstantTouch(view: View, onTrigger: () -> Unit) {
        view.isHapticFeedbackEnabled = true
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    try {
                        v.performHapticFeedback(
                            android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                        )
                    } catch (_: Throwable) {}
                    onTrigger()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    true
                }
                else -> false
            }
        }
    }

    // High-Speed Auto Repeat Backspace ("Hapus Ngebut")
    private fun setupRapidBackspaceTouch(view: View, onBackspace: () -> Unit) {
        view.isHapticFeedbackEnabled = true
        val handler = Handler(Looper.getMainLooper())
        var repeatRunnable: Runnable? = null
        var deleteCount = 0

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
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
                    repeatRunnable = object : Runnable {
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
                    handler.postDelayed(repeatRunnable!!, 320L)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    repeatRunnable?.let { handler.removeCallbacks(it) }
                    repeatRunnable = null
                    true
                }
                else -> false
            }
        }
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
