package com.maoneart.ioskeyboard

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.maoneart.ioskeyboard.audio.AudioHapticManager
import com.maoneart.ioskeyboard.clipboard.ClipboardHistoryManager
import com.maoneart.ioskeyboard.correction.SmartCorrectionEngine
import com.maoneart.ioskeyboard.keyboard.KeyboardLayout
import com.maoneart.ioskeyboard.preferences.KeyboardPreferences
import com.maoneart.ioskeyboard.settings.SettingsActivity
import com.maoneart.ioskeyboard.translate.LiveTranslateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class IosInputMethodService : InputMethodService() {

    private enum class Mode {
        QWERTY,
        SYMBOLS_123,
        SYMBOLS_MORE,
        EMOJI,
        CLIPBOARD
    }

    private lateinit var prefs: KeyboardPreferences
    private lateinit var audioHaptic: AudioHapticManager
    private lateinit var correctionEngine: SmartCorrectionEngine
    private lateinit var translateManager: LiveTranslateManager
    private lateinit var clipboardManager: ClipboardHistoryManager
    private lateinit var layoutEngine: KeyboardLayout

    private var currentMode = Mode.QWERTY
    private var isShifted = false
    private var isCapsLock = false
    private var lastShiftPressTime: Long = 0
    private var lastSpacePressTime: Long = 0

    // Sentence-level Live Translate Buffer
    private val composingWord = StringBuilder()
    private val currentSentenceBuffer = StringBuilder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // View References
    private var rootView: View? = null
    private var keyboardContainer: FrameLayout? = null
    private var candidate1: TextView? = null
    private var candidate2: TextView? = null
    private var candidate3: TextView? = null
    private var badgeLanguage: TextView? = null
    private var translateStrip: LinearLayout? = null
    private var tvTranslateStatus: TextView? = null
    private var btnActionTranslate: TextView? = null
    private var btnCloseTranslate: TextView? = null

    override fun onCreate() {
        try {
            setTheme(R.style.Theme_IosKeyboard)
        } catch (_: Throwable) {}
        super.onCreate()

        try {
            prefs = KeyboardPreferences(this)
            audioHaptic = AudioHapticManager(this, prefs)
            correctionEngine = SmartCorrectionEngine(this)
            translateManager = LiveTranslateManager()
            clipboardManager = ClipboardHistoryManager(this)
            layoutEngine = KeyboardLayout(this)
        } catch (t: Throwable) {
            Log.e("IosInputMethodService", "Error in onCreate", t)
        }
    }

    override fun onCreateInputView(): View {
        return try {
            val themedContext = ContextThemeWrapper(this, R.style.Theme_IosKeyboard)
            val inflater = LayoutInflater.from(themedContext)
            val view = inflater.inflate(R.layout.keyboard_view, null)
            rootView = view

            keyboardContainer = view.findViewById(R.id.keyboardContainer)
            candidate1 = view.findViewById(R.id.candidate1)
            candidate2 = view.findViewById(R.id.candidate2)
            candidate3 = view.findViewById(R.id.candidate3)
            badgeLanguage = view.findViewById(R.id.badgeLanguage)
            translateStrip = view.findViewById(R.id.translateStrip)
            tvTranslateStatus = view.findViewById(R.id.tvTranslateStatus)
            btnActionTranslate = view.findViewById(R.id.btnActionTranslate)
            btnCloseTranslate = view.findViewById(R.id.btnCloseTranslate)

            val btnQuickTranslate = view.findViewById<ImageButton>(R.id.btnQuickTranslate)
            val btnQuickClipboard = view.findViewById<ImageButton>(R.id.btnQuickClipboard)
            val btnQuickSettings = view.findViewById<ImageButton>(R.id.btnQuickSettings)

            btnQuickTranslate?.setOnClickListener {
                audioHaptic.playClickSound(it)
                audioHaptic.triggerHapticFeedback(it)
                toggleLiveTranslate()
            }

            btnQuickClipboard?.setOnClickListener {
                audioHaptic.playClickSound(it)
                audioHaptic.triggerHapticFeedback(it)
                if (currentMode == Mode.CLIPBOARD) {
                    switchMode(Mode.QWERTY)
                } else {
                    switchMode(Mode.CLIPBOARD)
                }
            }

            btnQuickSettings?.setOnClickListener {
                audioHaptic.playClickSound(it)
                audioHaptic.triggerHapticFeedback(it)
                try {
                    val intent = Intent(this, SettingsActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (_: Throwable) {}
            }

            badgeLanguage?.setOnClickListener {
                audioHaptic.playClickSound(it)
                audioHaptic.triggerHapticFeedback(it)
                prefs.activeLanguage = if (prefs.activeLanguage == "id") "en" else "id"
                updateLanguageBadge()
                renderCurrentLayout()
                updateSuggestions()
            }

            candidate1?.setOnClickListener { selectCandidate(candidate1?.text?.toString()) }
            candidate2?.setOnClickListener { selectCandidate(candidate2?.text?.toString()) }
            candidate3?.setOnClickListener { selectCandidate(candidate3?.text?.toString()) }

            // Sentence Translate Button Click
            btnActionTranslate?.setOnClickListener {
                audioHaptic.playClickSound(it)
                audioHaptic.triggerHapticFeedback(it)
                translateCurrentSentence()
            }

            btnCloseTranslate?.setOnClickListener {
                prefs.isLiveTranslateEnabled = false
                updateTranslateStripUI()
            }

            updateLanguageBadge()
            updateTranslateStripUI()
            applyThemeStyling(view)
            renderCurrentLayout()

            view
        } catch (t: Throwable) {
            Log.e("IosInputMethodService", "Error in onCreateInputView", t)
            createFallbackKeyboard()
        }
    }

    private fun createFallbackKeyboard(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.LTGRAY)
            val tv = TextView(this@IosInputMethodService).apply {
                text = "MaoneArt iOS Keyboard (Safe Mode)"
                setTextColor(Color.BLACK)
                textSize = 16f
                setPadding(20, 20, 20, 20)
            }
            addView(tv)
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        try {
            composingWord.clear()
            currentSentenceBuffer.clear()
            updateSuggestions()
            updateLanguageBadge()
            updateTranslateStripUI()
            rootView?.let { applyThemeStyling(it) }
            renderCurrentLayout()
        } catch (t: Throwable) {
            Log.e("IosInputMethodService", "Error onStartInputView", t)
        }
    }

    private fun isDarkMode(): Boolean {
        return try {
            when (prefs.themeMode) {
                "dark" -> true
                "light" -> false
                else -> {
                    val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    nightModeFlags == Configuration.UI_MODE_NIGHT_YES
                }
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun applyThemeStyling(view: View) {
        try {
            val isDark = isDarkMode()
            val keyboardRoot = view.findViewById<LinearLayout>(R.id.keyboardRoot)
            val topToolbar = view.findViewById<LinearLayout>(R.id.topToolbar)
            val textColor = if (isDark) Color.WHITE else Color.BLACK

            keyboardRoot?.setBackgroundColor(ContextCompat.getColor(this, if (isDark) R.color.ios_bg_dark else R.color.ios_bg_light))
            topToolbar?.setBackgroundColor(ContextCompat.getColor(this, if (isDark) R.color.ios_toolbar_dark else R.color.ios_toolbar_light))

            candidate1?.setTextColor(textColor)
            candidate2?.setTextColor(if (isDark) Color.LTGRAY else Color.DKGRAY)
            candidate3?.setTextColor(if (isDark) Color.LTGRAY else Color.DKGRAY)

            val btnTranslate = view.findViewById<ImageButton>(R.id.btnQuickTranslate)
            val btnClipboard = view.findViewById<ImageButton>(R.id.btnQuickClipboard)
            val btnSettings = view.findViewById<ImageButton>(R.id.btnQuickSettings)

            btnTranslate?.setColorFilter(textColor)
            btnClipboard?.setColorFilter(textColor)
            btnSettings?.setColorFilter(textColor)
        } catch (_: Throwable) {}
    }

    private fun updateLanguageBadge() {
        try {
            val lang = prefs.activeLanguage.uppercase()
            badgeLanguage?.text = lang
        } catch (_: Throwable) {}
    }

    private fun updateTranslateStripUI() {
        try {
            if (prefs.isLiveTranslateEnabled) {
                translateStrip?.visibility = View.VISIBLE
                val src = prefs.translateSourceLang.uppercase()
                val tgt = prefs.translateTargetLang.uppercase()
                if (currentSentenceBuffer.isEmpty()) {
                    tvTranslateStatus?.text = "🌐 $src ➔ $tgt: Ketik kalimat..."
                } else {
                    val preview = currentSentenceBuffer.toString()
                    val truncated = if (preview.length > 25) preview.take(25) + "…" else preview
                    tvTranslateStatus?.text = "🌐 $src ➔ $tgt: \"$truncated\""
                }
            } else {
                translateStrip?.visibility = View.GONE
            }
        } catch (_: Throwable) {}
    }

    private fun toggleLiveTranslate() {
        try {
            prefs.isLiveTranslateEnabled = !prefs.isLiveTranslateEnabled
            updateTranslateStripUI()
        } catch (_: Throwable) {}
    }

    private fun switchMode(mode: Mode) {
        currentMode = mode
        renderCurrentLayout()
    }

    private fun renderCurrentLayout() {
        try {
            val container = keyboardContainer ?: return
            container.removeAllViews()

            val isDark = isDarkMode()

            val contentView = when (currentMode) {
                Mode.QWERTY -> layoutEngine.createQwertyView(
                    isShifted = isShifted || isCapsLock,
                    isDark = isDark,
                    activeLanguage = prefs.activeLanguage,
                    onKeyAction = { handleKeyAction(it) }
                )
                Mode.SYMBOLS_123 -> layoutEngine.createSymbolsView(
                    isMoreSymbols = false,
                    isDark = isDark,
                    onKeyAction = { handleKeyAction(it) }
                )
                Mode.SYMBOLS_MORE -> layoutEngine.createSymbolsView(
                    isMoreSymbols = true,
                    isDark = isDark,
                    onKeyAction = { handleKeyAction(it) }
                )
                Mode.EMOJI -> layoutEngine.createEmojiView(
                    isDark = isDark,
                    onEmojiAction = { handleEmojiAction(it) },
                    onBackAction = { switchMode(Mode.QWERTY) }
                )
                Mode.CLIPBOARD -> layoutEngine.createClipboardView(
                    isDark = isDark,
                    history = clipboardManager.getHistory(),
                    onPasteAction = { text ->
                        commitDirectText(text)
                        switchMode(Mode.QWERTY)
                    },
                    onCloseAction = { switchMode(Mode.QWERTY) }
                )
            }

            container.addView(contentView)
        } catch (t: Throwable) {
            Log.e("IosInputMethodService", "Error in renderCurrentLayout", t)
        }
    }

    private fun handleKeyAction(action: String) {
        try {
            audioHaptic.playClickSound()
            audioHaptic.triggerHapticFeedback()

            when (action) {
                "ACTION_SHIFT" -> {
                    val now = System.currentTimeMillis()
                    if (now - lastShiftPressTime < 350) {
                        isCapsLock = !isCapsLock
                        isShifted = isCapsLock
                    } else {
                        isCapsLock = false
                        isShifted = !isShifted
                    }
                    lastShiftPressTime = now
                    // Instant Shift update without re-inflating views
                    layoutEngine.updateShiftState(isShifted || isCapsLock)
                    layoutEngine.updateShiftButtonVisual(isShifted, isCapsLock, isDarkMode())
                }
                "ACTION_BACKSPACE" -> {
                    handleBackspace()
                }
                "ACTION_SYMBOLS" -> {
                    switchMode(Mode.SYMBOLS_123)
                }
                "ACTION_MORE_SYMBOLS" -> {
                    switchMode(Mode.SYMBOLS_MORE)
                }
                "ACTION_ABC" -> {
                    switchMode(Mode.QWERTY)
                }
                "ACTION_EMOJI" -> {
                    switchMode(Mode.EMOJI)
                }
                "ACTION_RETURN" -> {
                    handleReturn()
                }
                " " -> {
                    handleSpace()
                }
                else -> {
                    handleCharacterInput(action)
                }
            }
        } catch (_: Throwable) {}
    }

    private fun handleCharacterInput(charStr: String) {
        try {
            currentInputConnection?.commitText(charStr, 1)
            composingWord.append(charStr)
            currentSentenceBuffer.append(charStr)
            updateSuggestions()
            updateTranslateStripUI()

            if (isShifted && !isCapsLock) {
                isShifted = false
                // Instant update without rebuilding view tree
                layoutEngine.updateShiftState(false)
                layoutEngine.updateShiftButtonVisual(false, false, isDarkMode())
            }
        } catch (_: Throwable) {}
    }

    private fun handleBackspace() {
        try {
            if (composingWord.isNotEmpty()) {
                composingWord.deleteCharAt(composingWord.length - 1)
            }
            if (currentSentenceBuffer.isNotEmpty()) {
                currentSentenceBuffer.deleteCharAt(currentSentenceBuffer.length - 1)
            }
            currentInputConnection?.deleteSurroundingText(1, 0)
            updateSuggestions()
            updateTranslateStripUI()
        } catch (_: Throwable) {}
    }

    private fun handleSpace() {
        try {
            val now = System.currentTimeMillis()

            if (prefs.isDoubleSpacePeriodEnabled && (now - lastSpacePressTime < 500) && composingWord.isEmpty()) {
                currentInputConnection?.deleteSurroundingText(1, 0)
                currentInputConnection?.commitText(". ", 1)
                if (currentSentenceBuffer.isNotEmpty()) {
                    currentSentenceBuffer.deleteCharAt(currentSentenceBuffer.length - 1)
                }
                currentSentenceBuffer.append(". ")
                lastSpacePressTime = 0
                updateTranslateStripUI()
                return
            }

            lastSpacePressTime = now
            currentInputConnection?.commitText(" ", 1)
            composingWord.clear()
            currentSentenceBuffer.append(" ")
            updateSuggestions()
            updateTranslateStripUI()
        } catch (_: Throwable) {}
    }

    // Translate full sentence buffer
    private fun translateCurrentSentence() {
        val sentence = currentSentenceBuffer.toString().trim()
        if (sentence.isEmpty()) return

        tvTranslateStatus?.text = "⏳ Menerjemahkan kalimat..."

        serviceScope.launch {
            try {
                val translated = translateManager.translateText(
                    sentence,
                    prefs.translateSourceLang,
                    prefs.translateTargetLang
                )
                // Delete the original sentence from the input connection
                currentInputConnection?.deleteSurroundingText(sentence.length, 0)
                // Insert the full translated sentence
                currentInputConnection?.commitText("$translated ", 1)

                currentSentenceBuffer.clear()
                composingWord.clear()
                tvTranslateStatus?.text = "✓ Selesai diterjemahkan"
            } catch (_: Throwable) {
                tvTranslateStatus?.text = "Gagal menerjemahkan (periksa koneksi)"
            }
        }
    }

    private fun handleReturn() {
        try {
            // If Live Translate is active and has a typed sentence, translate before return
            if (prefs.isLiveTranslateEnabled && currentSentenceBuffer.isNotBlank()) {
                translateCurrentSentence()
            }

            val ic = currentInputConnection ?: return
            val currentEditorInfo = currentInputEditorInfo
            val imeAction = currentEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE

            if (imeAction != EditorInfo.IME_ACTION_NONE && imeAction != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(imeAction)
            } else {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }

            composingWord.clear()
            currentSentenceBuffer.clear()
            updateSuggestions()
            updateTranslateStripUI()
        } catch (_: Throwable) {}
    }

    private fun handleEmojiAction(emoji: String) {
        try {
            audioHaptic.playClickSound()
            audioHaptic.triggerHapticFeedback()

            if (emoji == "ACTION_BACKSPACE") {
                handleBackspace()
            } else {
                commitDirectText(emoji)
            }
        } catch (_: Throwable) {}
    }

    private fun commitDirectText(text: String) {
        try {
            currentInputConnection?.commitText(text, 1)
            composingWord.clear()
            currentSentenceBuffer.append(text)
            updateSuggestions()
            updateTranslateStripUI()
        } catch (_: Throwable) {}
    }

    private fun updateSuggestions() {
        try {
            if (!prefs.isAutoCorrectEnabled || composingWord.isEmpty()) {
                candidate1?.text = ""
                candidate2?.text = ""
                candidate3?.text = ""
                return
            }

            val word = composingWord.toString()
            val suggestions = correctionEngine.getSuggestions(word, prefs.activeLanguage)

            candidate1?.text = suggestions.getOrNull(0) ?: ""
            candidate2?.text = suggestions.getOrNull(1) ?: ""
            candidate3?.text = suggestions.getOrNull(2) ?: ""
        } catch (_: Throwable) {}
    }

    private fun selectCandidate(candidate: String?) {
        try {
            if (candidate.isNullOrBlank()) return
            audioHaptic.playClickSound()
            audioHaptic.triggerHapticFeedback()

            val lengthToDelete = composingWord.length
            currentInputConnection?.deleteSurroundingText(lengthToDelete, 0)
            currentInputConnection?.commitText("$candidate ", 1)

            // Adjust sentence buffer
            if (currentSentenceBuffer.length >= lengthToDelete) {
                currentSentenceBuffer.setLength(currentSentenceBuffer.length - lengthToDelete)
            }
            currentSentenceBuffer.append("$candidate ")

            composingWord.clear()
            updateSuggestions()
            updateTranslateStripUI()
        } catch (_: Throwable) {}
    }
}
