package com.maoneart.ioskeyboard.keyboard

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.maoneart.ioskeyboard.R
import com.maoneart.ioskeyboard.emoji.EmojiBoardManager

class KeyboardLayout(private val context: Context) {

    private val letterKeyViews = mutableListOf<Pair<TextView, String>>()
    private var shiftButtonRef: ImageButton? = null

    // Update letter cases in 0.1ms without rebuilding views
    fun updateShiftState(isShifted: Boolean) {
        for ((tv, baseChar) in letterKeyViews) {
            tv.text = if (isShifted) baseChar.uppercase() else baseChar.lowercase()
        }
    }

    fun updateShiftButtonVisual(isShifted: Boolean, isCapsLock: Boolean, isDark: Boolean) {
        shiftButtonRef?.let { btn ->
            val activeColor = ContextCompat.getColor(context, R.color.ios_blue)
            val normalColor = if (isDark) Color.WHITE else Color.BLACK
            btn.setColorFilter(if (isShifted || isCapsLock) activeColor else normalColor)
        }
    }

    // --- 1. QWERTY LAYOUT ---
    fun createQwertyView(
        isShifted: Boolean,
        isDark: Boolean,
        activeLanguage: String,
        onKeyAction: (String) -> Unit
    ): View {
        letterKeyViews.clear()

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

        val keyBg = if (isDark) R.drawable.key_bg_ios_dark else R.drawable.key_bg_ios_light
        val specialKeyBg = if (isDark) R.drawable.key_bg_special_dark else R.drawable.key_bg_special_light
        val textColor = if (isDark) Color.WHITE else Color.BLACK

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
            setColorFilter(if (isShifted) ContextCompat.getColor(context, R.color.ios_blue) else textColor)
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

        // Row 4 (123 + Emoji Smiley + Space + Return)
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
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.3f)
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

        // Emoji Button (Apple Emoji Smiley with Tactile Bounce)
        val btnEmoji = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.1f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            text = "😊"
            textSize = 21f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(1.35f).scaleY(1.35f).setDuration(100).setInterpolator(OvershootInterpolator(2.5f)).start()
                        onKeyAction("ACTION_EMOJI")
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                        true
                    }
                    else -> false
                }
            }
        }
        r4Layout.addView(btnEmoji)

        // Space Button (with active language indicator)
        val langLabel = if (activeLanguage == "en") "English" else "Indonesia"
        val btnSpace = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 4.4f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, keyBg)
            text = langLabel
            setTextColor(if (isDark) Color.parseColor("#98989D") else Color.parseColor("#8E8E93"))
            textSize = 14f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            setupInstantTouch(this) { onKeyAction(" ") }
        }
        r4Layout.addView(btnSpace)

        // Return Action Button (iOS Blue)
        val btnReturn = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2.0f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, R.drawable.key_bg_blue)
            text = "return"
            setTextColor(Color.WHITE)
            textSize = 14.5f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            setupInstantTouch(this) { onKeyAction("ACTION_RETURN") }
        }
        r4Layout.addView(btnReturn)
        root.addView(r4Layout)

        return root
    }

    // --- 2. SYMBOLS LAYOUT (123 & #+=) ---
    fun createSymbolsView(
        isMoreSymbols: Boolean,
        isDark: Boolean,
        onKeyAction: (String) -> Unit
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

        val keyBg = if (isDark) R.drawable.key_bg_ios_dark else R.drawable.key_bg_ios_light
        val specialKeyBg = if (isDark) R.drawable.key_bg_special_dark else R.drawable.key_bg_special_light
        val textColor = if (isDark) Color.WHITE else Color.BLACK

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
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.3f)
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

        val btnEmoji = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.1f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            text = "😊"
            textSize = 21f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(1.35f).scaleY(1.35f).setDuration(100).setInterpolator(OvershootInterpolator(2.5f)).start()
                        onKeyAction("ACTION_EMOJI")
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                        true
                    }
                    else -> false
                }
            }
        }
        r4.addView(btnEmoji)

        val btnSpace = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 4.4f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, keyBg)
            text = "space"
            setTextColor(if (isDark) Color.parseColor("#98989D") else Color.parseColor("#8E8E93"))
            textSize = 14f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            setupInstantTouch(this) { onKeyAction(" ") }
        }
        r4.addView(btnSpace)

        val btnReturn = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2.0f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, R.drawable.key_bg_blue)
            text = "return"
            setTextColor(Color.WHITE)
            textSize = 14.5f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            setupInstantTouch(this) { onKeyAction("ACTION_RETURN") }
        }
        r4.addView(btnReturn)
        root.addView(r4)

        return root
    }

    // --- 3. APPLE EMOJI BOARD LAYOUT (with Animated Bounce on Touch) ---
    fun createEmojiView(
        isDark: Boolean,
        onEmojiAction: (String) -> Unit,
        onBackAction: () -> Unit
    ): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val scrollContainer = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        }
        val emojiContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        for (cat in EmojiBoardManager.categories) {
            val list = EmojiBoardManager.emojis[cat] ?: continue
            val header = TextView(context).apply {
                text = cat.uppercase()
                setTextColor(if (isDark) Color.LTGRAY else Color.GRAY)
                textSize = 11.5f
                setPadding(dp(4), dp(8), dp(4), dp(4))
            }
            emojiContent.addView(header)

            var row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
            }
            var count = 0
            for (emo in list) {
                val btn = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)
                    text = emo
                    textSize = 25f
                    gravity = Gravity.CENTER
                    isClickable = true
                    isFocusable = false

                    // Apple Animated Emoji Spring Bounce on Touch ("Animasi Emoji Gerak")
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                v.animate()
                                    .scaleX(1.45f)
                                    .scaleY(1.45f)
                                    .setDuration(120)
                                    .setInterpolator(OvershootInterpolator(3.0f))
                                    .start()
                                onEmojiAction(emo)
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                v.animate()
                                    .scaleX(1.0f)
                                    .scaleY(1.0f)
                                    .setDuration(150)
                                    .start()
                                true
                            }
                            else -> false
                        }
                    }
                }
                row.addView(btn)
                count++
                if (count == 8) {
                    emojiContent.addView(row)
                    row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
                    }
                    count = 0
                }
            }
            if (count > 0) emojiContent.addView(row)
        }
        scrollContainer.addView(emojiContent)
        root.addView(scrollContainer)

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
        root.addView(bottomBar)

        return root
    }

    // --- 4. CLIPBOARD HISTORY VIEW ---
    fun createClipboardView(
        isDark: Boolean,
        history: List<String>,
        onPasteAction: (String) -> Unit,
        onCloseAction: () -> Unit
    ): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(36)
            )
        }

        val title = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            text = "📋 Riwayat Salinan (Clipboard)"
            setTextColor(if (isDark) Color.WHITE else Color.BLACK)
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        headerRow.addView(title)

        val btnClose = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(70), dp(32))
            background = ContextCompat.getDrawable(context, R.drawable.key_bg_blue)
            text = "Tutup"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            setupInstantTouch(this) { onCloseAction() }
        }
        headerRow.addView(btnClose)
        root.addView(headerRow)

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        }
        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        if (history.isEmpty()) {
            val empty = TextView(context).apply {
                text = "Belum ada teks yang disalin."
                setTextColor(Color.GRAY)
                textSize = 13f
                setPadding(dp(16), dp(24), dp(16), dp(24))
                gravity = Gravity.CENTER
            }
            listLayout.addView(empty)
        } else {
            for (textSnippet in history) {
                val itemCard = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, dp(4), 0, dp(4)) }
                    background = ContextCompat.getDrawable(
                        context,
                        if (isDark) R.drawable.key_bg_ios_dark else R.drawable.key_bg_ios_light
                    )
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    isClickable = true
                    isFocusable = false
                    setupInstantTouch(this) { onPasteAction(textSnippet) }
                }

                val snippetView = TextView(context).apply {
                    text = textSnippet
                    maxLines = 2
                    textSize = 13.5f
                    setTextColor(if (isDark) Color.WHITE else Color.BLACK)
                }
                itemCard.addView(snippetView)
                listLayout.addView(itemCard)
            }
        }

        scroll.addView(listLayout)
        root.addView(scroll)
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
            setupInstantTouch(this) { onKeyAction(text.toString()) }
        }
    }

    // 0ms Latency Instant Touch Down Listener
    private fun setupInstantTouch(view: View, onTrigger: () -> Unit) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
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
        val handler = Handler(Looper.getMainLooper())
        var repeatRunnable: Runnable? = null
        var deleteCount = 0

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    deleteCount = 0
                    // 1. Delete character immediately
                    onBackspace()

                    // 2. Start repeating delete job
                    repeatRunnable = object : Runnable {
                        override fun run() {
                            deleteCount++
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
