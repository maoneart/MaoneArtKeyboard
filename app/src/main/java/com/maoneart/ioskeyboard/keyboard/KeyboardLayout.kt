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

    private val letterKeyViews = mutableListOf<Pair<TextView, String>>()
    private var shiftButtonRef: ImageButton? = null
    private var returnButtonRef: TextView? = null

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

    // Dynamic Context-Adaptive Enter / Return / Search Button (iOS Smart Action)
    fun updateReturnButton(editorInfo: EditorInfo?, isDark: Boolean = false) {
        val btn = returnButtonRef ?: return
        val specialKeyBg = if (isDark) R.drawable.key_bg_special_dark else R.drawable.key_bg_special_light
        val textColor = if (isDark) Color.WHITE else Color.BLACK

        if (editorInfo == null) {
            btn.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            btn.background = ContextCompat.getDrawable(context, specialKeyBg)
            btn.setTextColor(textColor)
            btn.text = "return"
            btn.textSize = 15f
            return
        }

        val imeAction = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
        val variation = editorInfo.inputType and InputType.TYPE_MASK_VARIATION
        val isMultiLine = (editorInfo.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0

        // 1. Web Search or Filter: Show Flat White Search Icon with Blue BG
        if (imeAction == EditorInfo.IME_ACTION_SEARCH ||
            variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
            btn.text = ""
            btn.background = ContextCompat.getDrawable(context, R.drawable.key_bg_blue)
            btn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search_white, 0, 0, 0)
            return
        }

        btn.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)

        // 2. Browser URL or Web Address: Show "Buka" (Blue BG)
        if (imeAction == EditorInfo.IME_ACTION_GO || variation == InputType.TYPE_TEXT_VARIATION_URI) {
            btn.text = "Buka"
            btn.textSize = 14f
            btn.setTextColor(Color.WHITE)
            btn.background = ContextCompat.getDrawable(context, R.drawable.key_bg_blue)
            return
        }

        // 3. Form Send Action: Show "Kirim" (Blue BG)
        if (imeAction == EditorInfo.IME_ACTION_SEND) {
            btn.text = "Kirim"
            btn.textSize = 14f
            btn.setTextColor(Color.WHITE)
            btn.background = ContextCompat.getDrawable(context, R.drawable.key_bg_blue)
            return
        }

        // 4. WhatsApp, Telegram, Notes & all multiline / standard newline:
        // Authentic Apple iOS "return" key with functional gray background
        btn.text = "return"
        btn.textSize = 15f
        btn.background = ContextCompat.getDrawable(context, specialKeyBg)
        btn.setTextColor(textColor)
    }

    // --- 1. QWERTY LAYOUT ---
    fun createQwertyView(
        isShifted: Boolean,
        isDark: Boolean,
        activeLanguage: String,
        isSwipeEnabled: Boolean = true,
        onKeyAction: (String) -> Unit,
        onCursorMove: (Int) -> Unit = {},
        onSwipeComplete: (List<Char>) -> Unit = {},
        onSwipeHaptic: () -> Unit = {}
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
            setTextColor(if (isDark) Color.parseColor("#98989D") else Color.parseColor("#8E8E93"))
            textSize = 14f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            setupSpacebarTrackpadTouch(this, langLabel, isDark, { onKeyAction(" ") }, onCursorMove)
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

        // Return / Enter Action Button (return)
        val btnReturn = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2.2f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            text = "return"
            setTextColor(textColor)
            textSize = 15f
            gravity = Gravity.CENTER
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
            findLetterAtCoords = { rx, ry -> findLetterAt(rx, ry) },
            onSwipeCompleted = onSwipeComplete,
            onSwipeCharDetected = onSwipeHaptic
        )
        container.setKeyboardContentView(root)
        return container
    }

    private fun findLetterAt(rawX: Float, rawY: Float): String? {
        val hitRect = android.graphics.Rect()
        for ((tv, charStr) in letterKeyViews) {
            if (!tv.isShown) continue
            tv.getGlobalVisibleRect(hitRect)
            if (hitRect.contains(rawX.toInt(), rawY.toInt())) {
                return charStr
            }
        }
        return null
    }

    // --- 2. SYMBOLS LAYOUT (123 & #+=) ---
    fun createSymbolsView(
        isMoreSymbols: Boolean,
        isDark: Boolean,
        onKeyAction: (String) -> Unit,
        onCursorMove: (Int) -> Unit = {}
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
            setTextColor(if (isDark) Color.parseColor("#98989D") else Color.parseColor("#8E8E93"))
            textSize = 14f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            setupSpacebarTrackpadTouch(this, "spasi", isDark, { onKeyAction(" ") }, onCursorMove)
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

        // Return / Enter Action Button (return)
        val btnReturn = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2.2f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            text = "return"
            setTextColor(textColor)
            textSize = 15f
            gravity = Gravity.CENTER
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
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
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
                                android.app.AlertDialog.Builder(context)
                                    .setTitle("Hapus Stiker")
                                    .setMessage("Hapus stiker ini dari keyboard?")
                                    .setPositiveButton("Hapus") { _, _ ->
                                        stickerManager.deleteSticker(stickerFile)
                                        renderContent("STICKERS")
                                    }
                                    .setNegativeButton("Batal", null)
                                    .show()
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
        root.addView(tabsScroll)

        // Initial content render
        updateTabStyles()
        renderContent(activeCat)

        root.addView(contentContainer)

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
            isHapticFeedbackEnabled = true
            setupInstantTouch(this) { onKeyAction(text.toString()) }
        }
    }

    // Apple iOS Spacebar Trackpad Cursor Mover
    private fun setupSpacebarTrackpadTouch(
        view: TextView,
        defaultText: String,
        isDark: Boolean,
        onSpaceAction: () -> Unit,
        onCursorMove: (Int) -> Unit
    ) {
        view.isHapticFeedbackEnabled = true
        val handler = Handler(Looper.getMainLooper())
        var isTrackpadActive = false
        var startX = 0f
        var lastStepX = 0f
        val stepDistance = dp(14).toFloat()

        val activateRunnable = Runnable {
            isTrackpadActive = true
            view.text = "│   geser kursor   │"
            view.setTextColor(ContextCompat.getColor(context, R.color.ios_blue))
            view.setTypeface(view.typeface, android.graphics.Typeface.BOLD)
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
                    lastStepX = event.x
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
                        val deltaX = event.x - lastStepX
                        if (deltaX > stepDistance) {
                            val steps = (deltaX / stepDistance).toInt()
                            for (i in 0 until steps) {
                                onCursorMove(1)
                            }
                            lastStepX += steps * stepDistance
                            try {
                                v.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                )
                            } catch (_: Throwable) {}
                        } else if (deltaX < -stepDistance) {
                            val steps = (kotlin.math.abs(deltaX) / stepDistance).toInt()
                            for (i in 0 until steps) {
                                onCursorMove(-1)
                            }
                            lastStepX -= steps * stepDistance
                            try {
                                v.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                )
                            } catch (_: Throwable) {}
                        }
                    } else {
                        if (kotlin.math.abs(event.x - startX) > dp(20)) {
                            handler.removeCallbacks(activateRunnable)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(activateRunnable)
                    v.isPressed = false
                    if (isTrackpadActive) {
                        isTrackpadActive = false
                        view.text = defaultText
                        view.setTextColor(if (isDark) Color.parseColor("#98989D") else Color.parseColor("#8E8E93"))
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
