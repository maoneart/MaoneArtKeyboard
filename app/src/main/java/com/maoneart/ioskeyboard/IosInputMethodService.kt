package com.maoneart.ioskeyboard

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
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

    private val composingWord = StringBuilder()
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
    private var btnCloseTranslate: Button? = null

    override fun onCreate() {
        super.onCreate()
        prefs = KeyboardPreferences(this)
        audioHaptic = AudioHapticManager(this, prefs)
        correctionEngine = SmartCorrectionEngine(this)
        translateManager = LiveTranslateManager()
        clipboardManager = ClipboardHistoryManager(this)
        layoutEngine = KeyboardLayout(this)
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        rootView = view

        keyboardContainer = view.findViewById(R.id.keyboardContainer)
        candidate1 = view.findViewById(R.id.candidate1)
        candidate2 = view.findViewById(R.id.candidate2)
        candidate3 = view.findViewById(R.id.candidate3)
        badgeLanguage = view.findViewById(R.id.badgeLanguage)
        translateStrip = view.findViewById(R.id.translateStrip)
        tvTranslateStatus = view.findViewById(R.id.tvTranslateStatus)
        btnCloseTranslate = view.findViewById(R.id.btnCloseTranslate)

        val btnQuickTranslate = view.findViewById<ImageButton>(R.id.btnQuickTranslate)
        val btnQuickClipboard = view.findViewById<ImageButton>(R.id.btnQuickClipboard)
        val btnQuickSettings = view.findViewById<ImageButton>(R.id.btnQuickSettings)

        // Setup Quick Toolbar Buttons
        btnQuickTranslate.setOnClickListener {
            audioHaptic.playClickSound(it)
            audioHaptic.triggerHapticFeedback(it)
            toggleLiveTranslate()
        }

        btnQuickClipboard.setOnClickListener {
            audioHaptic.playClickSound(it)
            audioHaptic.triggerHapticFeedback(it)
            if (currentMode == Mode.CLIPBOARD) {
                switchMode(Mode.QWERTY)
            } else {
                switchMode(Mode.CLIPBOARD)
            }
        }

        btnQuickSettings.setOnClickListener {
            audioHaptic.playClickSound(it)
            audioHaptic.triggerHapticFeedback(it)
            val intent = Intent(this, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }

        badgeLanguage?.setOnClickListener {
            audioHaptic.playClickSound(it)
            audioHaptic.triggerHapticFeedback(it)
            // Toggle language between ID and EN
            prefs.activeLanguage = if (prefs.activeLanguage == "id") "en" else "id"
            updateLanguageBadge()
            renderCurrentLayout()
            updateSuggestions()
        }

        candidate1?.setOnClickListener { selectCandidate(candidate1?.text?.toString()) }
        candidate2?.setOnClickListener { selectCandidate(candidate2?.text?.toString()) }
        candidate3?.setOnClickListener { selectCandidate(candidate3?.text?.toString()) }

        btnCloseTranslate?.setOnClickListener {
            prefs.isLiveTranslateEnabled = false
            updateTranslateStripUI()
        }

        updateLanguageBadge()
        updateTranslateStripUI()
        applyThemeStyling(view)
        renderCurrentLayout()

        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composingWord.clear()
        updateSuggestions()
        updateLanguageBadge()
        updateTranslateStripUI()
        rootView?.let { applyThemeStyling(it) }
        renderCurrentLayout()
    }

    private fun isDarkMode(): Boolean {
        return when (prefs.themeMode) {
            "dark" -> true
            "light" -> false
            else -> {
                val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun applyThemeStyling(view: View) {
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
    }

    private fun updateLanguageBadge() {
        val lang = prefs.activeLanguage.uppercase()
        badgeLanguage?.text = lang
    }

    private fun updateTranslateStripUI() {
        if (prefs.isLiveTranslateEnabled) {
            translateStrip?.visibility = View.VISIBLE
            val src = prefs.translateSourceLang.uppercase()
            val tgt = prefs.translateTargetLang.uppercase()
            tvTranslateStatus?.text = "Live Translate: $src ➔ $tgt"
        } else {
            translateStrip?.visibility = View.GONE
        }
    }

    private fun toggleLiveTranslate() {
        prefs.isLiveTranslateEnabled = !prefs.isLiveTranslateEnabled
        updateTranslateStripUI()
    }

    private fun switchMode(mode: Mode) {
        currentMode = mode
        renderCurrentLayout()
    }

    private fun renderCurrentLayout() {
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
    }

    private fun handleKeyAction(action: String) {
        audioHaptic.playClickSound()
        audioHaptic.triggerHapticFeedback()

        when (action) {
            "ACTION_SHIFT" -> {
                val now = System.currentTimeMillis()
                if (now - lastShiftPressTime < 350) {
                    // Double tap shift = Caps Lock
                    isCapsLock = !isCapsLock
                    isShifted = isCapsLock
                } else {
                    isCapsLock = false
                    isShifted = !isShifted
                }
                lastShiftPressTime = now
                renderCurrentLayout()
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
    }

    private fun handleCharacterInput(charStr: String) {
        currentInputConnection?.commitText(charStr, 1)
        composingWord.append(charStr)
        updateSuggestions()

        if (isShifted && !isCapsLock) {
            isShifted = false
            renderCurrentLayout()
        }
    }

    private fun handleBackspace() {
        if (composingWord.isNotEmpty()) {
            composingWord.deleteCharAt(composingWord.length - 1)
        }
        currentInputConnection?.deleteSurroundingText(1, 0)
        updateSuggestions()
    }

    private fun handleSpace() {
        val now = System.currentTimeMillis()

        // Double space to period "."
        if (prefs.isDoubleSpacePeriodEnabled && (now - lastSpacePressTime < 500) && composingWord.isEmpty()) {
            currentInputConnection?.deleteSurroundingText(1, 0)
            currentInputConnection?.commitText(". ", 1)
            lastSpacePressTime = 0
            return
        }

        lastSpacePressTime = now

        // Check if Live Translate is active for completed word
        if (prefs.isLiveTranslateEnabled && composingWord.isNotEmpty()) {
            val wordToTranslate = composingWord.toString()
            composingWord.clear()
            serviceScope.launch {
                val translated = translateManager.translateText(
                    wordToTranslate,
                    prefs.translateSourceLang,
                    prefs.translateTargetLang
                )
                currentInputConnection?.deleteSurroundingText(wordToTranslate.length, 0)
                currentInputConnection?.commitText("$translated ", 1)
            }
        } else {
            currentInputConnection?.commitText(" ", 1)
            composingWord.clear()
        }

        updateSuggestions()
    }

    private fun handleReturn() {
        val ic = currentInputConnection ?: return
        val currentEditorInfo = currentInputEditorInfo
        val imeAction = currentEditorInfo.imeOptions and EditorInfo.IME_MASK_ACTION

        if (imeAction != EditorInfo.IME_ACTION_NONE && imeAction != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(imeAction)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }

        composingWord.clear()
        updateSuggestions()
    }

    private fun handleEmojiAction(emoji: String) {
        audioHaptic.playClickSound()
        audioHaptic.triggerHapticFeedback()

        if (emoji == "ACTION_BACKSPACE") {
            handleBackspace()
        } else {
            commitDirectText(emoji)
        }
    }

    private fun commitDirectText(text: String) {
        currentInputConnection?.commitText(text, 1)
        composingWord.clear()
        updateSuggestions()
    }

    private fun updateSuggestions() {
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
    }

    private fun selectCandidate(candidate: String?) {
        if (candidate.isNullOrBlank()) return
        audioHaptic.playClickSound()
        audioHaptic.triggerHapticFeedback()

        val lengthToDelete = composingWord.length
        currentInputConnection?.deleteSurroundingText(lengthToDelete, 0)
        currentInputConnection?.commitText("$candidate ", 1)

        composingWord.clear()
        updateSuggestions()
    }
}
