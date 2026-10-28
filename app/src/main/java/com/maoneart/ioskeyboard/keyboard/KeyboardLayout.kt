package com.maoneart.ioskeyboard.keyboard

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.maoneart.ioskeyboard.R
import com.maoneart.ioskeyboard.emoji.EmojiBoardManager

class KeyboardLayout(private val context: Context) {

    // --- 1. QWERTY LAYOUT ---
    fun createQwertyView(
        isShifted: Boolean,
        isDark: Boolean,
        activeLanguage: String,
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
            setOnClickListener { onKeyAction("ACTION_SHIFT") }
        }
        r3Layout.addView(btnShift)

        for (ch in row3) {
            val label = if (isShifted) ch.uppercase() else ch
            r3Layout.addView(createKeyButton(label, 1.0f, keyBg, textColor, onKeyAction))
        }

        // Backspace Button
        val btnBackspace = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.4f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            setImageResource(R.drawable.ic_backspace)
            setColorFilter(textColor)
            setOnClickListener { onKeyAction("ACTION_BACKSPACE") }
        }
        r3Layout.addView(btnBackspace)
        root.addView(r3Layout)

        // Row 4 (123 + Emoji/Globe + Space + Return)
        val r4Layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            ).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
        }

        // 123 Button
        val btn123 = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.3f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            text = "123"
            setTextColor(textColor)
            textSize = 14.5f
            isAllCaps = false
            setOnClickListener { onKeyAction("ACTION_SYMBOLS") }
        }
        r4Layout.addView(btn123)

        // Emoji / Globe Button
        val btnGlobe = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.1f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            setImageResource(R.drawable.ic_globe)
            setColorFilter(textColor)
            setOnClickListener { onKeyAction("ACTION_EMOJI") }
        }
        r4Layout.addView(btnGlobe)

        // Space Button (with active language indicator)
        val langLabel = if (activeLanguage == "en") "English" else "Indonesia"
        val btnSpace = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 4.4f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, keyBg)
            text = langLabel
            setTextColor(if (isDark) Color.GRAY else Color.DKGRAY)
            textSize = 13.5f
            isAllCaps = false
            setOnClickListener { onKeyAction(" ") }
        }
        r4Layout.addView(btnSpace)

        // Return Action Button (iOS Blue)
        val btnReturn = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2.0f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, R.drawable.key_bg_blue)
            text = "return"
            setTextColor(Color.WHITE)
            textSize = 14.0f
            isAllCaps = false
            setOnClickListener { onKeyAction("ACTION_RETURN") }
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

        val btnToggleMore = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.4f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            text = if (!isMoreSymbols) "#+=" else "123"
            setTextColor(textColor)
            isAllCaps = false
            textSize = 13.5f
            setOnClickListener { onKeyAction(if (!isMoreSymbols) "ACTION_MORE_SYMBOLS" else "ACTION_SYMBOLS") }
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
            setOnClickListener { onKeyAction("ACTION_BACKSPACE") }
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

        val btnAbc = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.3f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            text = "ABC"
            setTextColor(textColor)
            textSize = 14.5f
            isAllCaps = false
            setOnClickListener { onKeyAction("ACTION_ABC") }
        }
        r4.addView(btnAbc)

        val btnGlobe = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.1f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, specialKeyBg)
            setImageResource(R.drawable.ic_globe)
            setColorFilter(textColor)
            setOnClickListener { onKeyAction("ACTION_EMOJI") }
        }
        r4.addView(btnGlobe)

        val btnSpace = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 4.4f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, keyBg)
            text = "space"
            setTextColor(if (isDark) Color.GRAY else Color.DKGRAY)
            isAllCaps = false
            textSize = 13.5f
            setOnClickListener { onKeyAction(" ") }
        }
        r4.addView(btnSpace)

        val btnReturn = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2.0f)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, R.drawable.key_bg_blue)
            text = "return"
            setTextColor(Color.WHITE)
            textSize = 14.0f
            isAllCaps = false
            setOnClickListener { onKeyAction("ACTION_RETURN") }
        }
        r4.addView(btnReturn)
        root.addView(r4)

        return root
    }

    // --- 3. APPLE EMOJI BOARD LAYOUT ---
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

        // Category Tab Row
        val categoryTabs = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(36)
            )
            isHorizontalScrollBarEnabled = false
        }
        val tabLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
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
                    textSize = 24f
                    gravity = Gravity.CENTER
                    setOnClickListener { onEmojiAction(emo) }
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

        val btnAbc = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(70), LinearLayout.LayoutParams.MATCH_PARENT)
            background = ContextCompat.getDrawable(context, if (isDark) R.drawable.key_bg_special_dark else R.drawable.key_bg_special_light)
            text = "ABC"
            setTextColor(if (isDark) Color.WHITE else Color.BLACK)
            textSize = 14f
            isAllCaps = false
            setOnClickListener { onBackAction() }
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
            setOnClickListener { onEmojiAction("ACTION_BACKSPACE") }
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
            textStyle = android.graphics.Typeface.BOLD
        }
        headerRow.addView(title)

        val btnClose = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(70), dp(32))
            background = ContextCompat.getDrawable(context, R.drawable.key_bg_blue)
            text = "Tutup"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            setOnClickListener { onCloseAction() }
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
                    setOnClickListener { onPasteAction(textSnippet) }
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
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            ).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
            for (k in keys) {
                val label = if (isShifted) k.uppercase() else k
                addView(createKeyButton(label, 1.0f, keyBg, textColor, onKeyAction))
            }
        }
    }

    private fun createKeyButton(
        label: String,
        weight: Float,
        keyBg: Int,
        textColor: Int,
        onKeyAction: (String) -> Unit
    ): View {
        return Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
                .apply { setMargins(dp(2), 0, dp(2), 0) }
            background = ContextCompat.getDrawable(context, keyBg)
            text = label
            setTextColor(textColor)
            textSize = 21f
            isAllCaps = false
            setOnClickListener { onKeyAction(label) }
        }
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
