package com.maoneart.ioskeyboard

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
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

    // Samsung-Style Live Translate Input Panel State
    private var isTranslatePanelOpen = false
    private val translatePanelBuffer = StringBuilder()
    private val composingWord = StringBuilder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // View References
    private var rootView: View? = null
    private var keyboardContainer: FrameLayout? = null
    private var candidate1: TextView? = null
    private var candidate2: TextView? = null
    private var candidate3: TextView? = null
    private var badgeLanguage: TextView? = null

    // Samsung-Style Translate Panel Views
    private var translatePanel: LinearLayout? = null
    private var tvTranslateLangPair: TextView? = null
    private var tvTranslateStatus: TextView? = null
    private var tvTranslateInputBox: TextView? = null
    private var btnTranslateSend: TextView? = null
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

            // Setup Translate Panel
            translatePanel = view.findViewById(R.id.translatePanel)
            tvTranslateLangPair = view.findViewById(R.id.tvTranslateLangPair)
            tvTranslateStatus = view.findViewById(R.id.tvTranslateStatus)
            tvTranslateInputBox = view.findViewById(R.id.tvTranslateInputBox)
            btnTranslateSend = view.findViewById(R.id.btnTranslateSend)
            btnCloseTranslate = view.findViewById(R.id.btnCloseTranslate)

            val btnQuickTranslate = view.findViewById<ImageButton>(R.id.btnQuickTranslate)
            val btnQuickClipboard = view.findViewById<ImageButton>(R.id.btnQuickClipboard)
            val btnQuickSettings = view.findViewById<ImageButton>(R.id.btnQuickSettings)

            btnQuickTranslate?.setOnClickListener {
                audioHaptic.playClickSound(it)
                audioHaptic.triggerHapticFeedback(it)
                toggleTranslatePanel()
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


            // Samsung-Style Translate Panel Actions
            tvTranslateLangPair?.setOnClickListener {
                audioHaptic.playClickSound(it)
                audioHaptic.triggerHapticFeedback(it)
                val temp = prefs.translateSourceLang
                prefs.translateSourceLang = prefs.translateTargetLang
                prefs.translateTargetLang = temp
                updateTranslatePanelUI()
            }

            btnTranslateSend?.setOnClickListener {
                audioHaptic.playClickSound(it)
                audioHaptic.triggerHapticFeedback(it)
                sendTranslatedSentence()
            }

            btnCloseTranslate?.setOnClickListener {
                audioHaptic.playClickSound(it)
                audioHaptic.triggerHapticFeedback(it)
                closeTranslatePanel()
            }

            updateLanguageBadge()
            updateTranslatePanelUI()
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
            updateSuggestions()
            updateLanguageBadge()
            updateTranslatePanelUI()
            rootView?.let { applyThemeStyling(it) }
            renderCurrentLayout()
            layoutEngine.updateReturnButton(info)
        } catch (t: Throwable) {
            Log.e("IosInputMethodService", "Error onStartInputView", t)
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateSuggestions()
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

            translatePanel?.setBackgroundColor(ContextCompat.getColor(this, if (isDark) R.color.ios_toolbar_dark else R.color.ios_toolbar_light))
            tvTranslateInputBox?.setBackgroundResource(if (isDark) R.drawable.key_bg_ios_dark else R.drawable.key_bg_ios_light)
            tvTranslateInputBox?.setTextColor(textColor)

            candidate1?.setTextColor(if (isDark) Color.LTGRAY else Color.DKGRAY)
            candidate2?.setTextColor(textColor)
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

    private fun toggleTranslatePanel() {
        isTranslatePanelOpen = !isTranslatePanelOpen
        updateTranslatePanelUI()
    }

    private fun closeTranslatePanel() {
        isTranslatePanelOpen = false
        translatePanelBuffer.clear()
        updateTranslatePanelUI()
    }

    private fun updateTranslatePanelUI() {
        try {
            if (isTranslatePanelOpen) {
                translatePanel?.visibility = View.VISIBLE
                val src = prefs.translateSourceLang.uppercase()
                val tgt = prefs.translateTargetLang.uppercase()
                tvTranslateLangPair?.text = "$src ⇄ $tgt"

                if (translatePanelBuffer.isEmpty()) {
                    tvTranslateInputBox?.text = ""
                    tvTranslateStatus?.text = "Ketik kalimat di kotak, lalu tap Kirim"
                } else {
                    tvTranslateInputBox?.text = translatePanelBuffer.toString()
                    tvTranslateStatus?.text = "Kalimat siap ($src ➔ $tgt)"
                }
            } else {
                translatePanel?.visibility = View.GONE
                translatePanelBuffer.clear()
                tvTranslateInputBox?.text = ""
            }
        } catch (_: Throwable) {}
    }

    private fun sendTranslatedSentence() {
        val sentence = translatePanelBuffer.toString().trim()
        if (sentence.isEmpty()) {
            tvTranslateStatus?.text = "Ketik kalimat terlebih dahulu..."
            return
        }

        tvTranslateStatus?.text = "⏳ Sedang menerjemahkan..."

        serviceScope.launch {
            try {
                val translated = translateManager.translateText(
                    sentence,
                    prefs.translateSourceLang,
                    prefs.translateTargetLang
                )
                // Commit translated sentence directly into the host app (WhatsApp, etc.)
                currentInputConnection?.commitText("$translated ", 1)

                translatePanelBuffer.clear()
                tvTranslateInputBox?.text = ""
                tvTranslateStatus?.text = "✓ Terkirim ke obrolan!"
            } catch (_: Throwable) {
                tvTranslateStatus?.text = "Gagal menerjemahkan (periksa koneksi)"
            }
        }
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
            layoutEngine.updateReturnButton(currentInputEditorInfo)
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
            if (isTranslatePanelOpen) {
                translatePanelBuffer.append(charStr)
                tvTranslateInputBox?.text = translatePanelBuffer.toString()
                tvTranslateStatus?.text = "Kalimat siap (${prefs.translateSourceLang.uppercase()} ➔ ${prefs.translateTargetLang.uppercase()})"
            } else {
                currentInputConnection?.commitText(charStr, 1)
                composingWord.append(charStr)
                updateSuggestions()
            }

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
            if (isTranslatePanelOpen) {
                if (translatePanelBuffer.isNotEmpty()) {
                    translatePanelBuffer.deleteCharAt(translatePanelBuffer.length - 1)
                    tvTranslateInputBox?.text = translatePanelBuffer.toString()
                }
                updateSuggestions()
                return
            }

            if (composingWord.isNotEmpty()) {
                composingWord.deleteCharAt(composingWord.length - 1)
            }
            currentInputConnection?.deleteSurroundingText(1, 0)
            updateSuggestions()
        } catch (_: Throwable) {}
    }

    private fun handleSpace() {
        try {
            if (isTranslatePanelOpen) {
                translatePanelBuffer.append(" ")
                tvTranslateInputBox?.text = translatePanelBuffer.toString()
                return
            }

            val now = System.currentTimeMillis()
            if (prefs.isDoubleSpacePeriodEnabled && (now - lastSpacePressTime < 500) && composingWord.isEmpty()) {
                currentInputConnection?.deleteSurroundingText(1, 0)
                currentInputConnection?.commitText(". ", 1)
                lastSpacePressTime = 0
                return
            }

            lastSpacePressTime = now
            currentInputConnection?.commitText(" ", 1)
            composingWord.clear()
            updateSuggestions()
        } catch (_: Throwable) {}
    }

    // Dynamic Enter / Return Key handling (WhatsApp new line & send support)
    private fun handleReturn() {
        try {
            if (isTranslatePanelOpen && translatePanelBuffer.isNotBlank()) {
                sendTranslatedSentence()
                return
            }

            val ic = currentInputConnection ?: return
            val info = currentInputEditorInfo
            val imeAction = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
            val isMultiLine = info != null && ((info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0)

            // If it's a multiline field (e.g. WhatsApp, Notes, Chat): ALWAYS insert a newline directly!
            if (isMultiLine) {
                ic.commitText("\n", 1)
                composingWord.clear()
                updateSuggestions()
                return
            }

            if (imeAction != EditorInfo.IME_ACTION_NONE && imeAction != EditorInfo.IME_ACTION_UNSPECIFIED) {
                val handled = ic.performEditorAction(imeAction)
                if (!handled) {
                    if (isMultiLine) {
                        ic.commitText("\n", 1)
                    } else {
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                    }
                }
            } else {
                if (isMultiLine) {
                    ic.commitText("\n", 1)
                } else {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                }
            }

            composingWord.clear()
            updateSuggestions()
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
            if (isTranslatePanelOpen) {
                translatePanelBuffer.append(text)
                tvTranslateInputBox?.text = translatePanelBuffer.toString()
            } else {
                currentInputConnection?.commitText(text, 1)
                composingWord.clear()
                updateSuggestions()
            }
        } catch (_: Throwable) {}
    }

    private fun getCurrentWordBeforeCursor(): String {
        try {
            if (isTranslatePanelOpen) {
                val text = translatePanelBuffer.toString()
                if (text.isEmpty()) return ""
                var i = text.length - 1
                while (i >= 0 && !text[i].isWhitespace() && text[i] != '.' && text[i] != ',' && text[i] != '!' && text[i] != '?') {
                    i--
                }
                return text.substring(i + 1)
            }

            val ic = currentInputConnection
            if (ic != null) {
                val chars = ic.getTextBeforeCursor(40, 0)
                if (chars != null && chars.isNotEmpty()) {
                    val text = chars.toString()
                    var i = text.length - 1
                    while (i >= 0 && !text[i].isWhitespace() && text[i] != '.' && text[i] != ',' && text[i] != '!' && text[i] != '?' && text[i] != '\n') {
                        i--
                    }
                    val word = text.substring(i + 1)
                    if (word.isNotEmpty()) {
                        return word
                    }
                }
            }
            return composingWord.toString()
        } catch (_: Throwable) {
            return composingWord.toString()
        }
    }

    private fun updateSuggestions() {
        try {
            if (!prefs.isAutoCorrectEnabled) {
                candidate1?.text = ""
                candidate2?.text = ""
                candidate3?.text = ""
                return
            }

            val currentWord = getCurrentWordBeforeCursor()
            val suggestions = correctionEngine.getSuggestions(currentWord, prefs.activeLanguage)

            if (suggestions.isEmpty()) {
                candidate1?.text = ""
                candidate2?.text = ""
                candidate3?.text = ""
                return
            }

            if (suggestions.size == 1) {
                candidate1?.text = ""
                candidate2?.text = formatCandidateCase(suggestions[0], currentWord)
                candidate3?.text = ""
            } else if (suggestions.size == 2) {
                candidate1?.text = formatCandidateCase(suggestions[1], currentWord)
                candidate2?.text = formatCandidateCase(suggestions[0], currentWord)
                candidate3?.text = ""
            } else {
                candidate1?.text = formatCandidateCase(suggestions[1], currentWord)
                candidate2?.text = formatCandidateCase(suggestions[0], currentWord)
                candidate3?.text = formatCandidateCase(suggestions[2], currentWord)
            }
        } catch (_: Throwable) {}
    }

    private fun formatCandidateCase(candidate: String, original: String): String {
        if (original.isEmpty()) return candidate
        if (original.all { it.isUpperCase() } && original.length > 1) {
            return candidate.uppercase()
        }
        if (original[0].isUpperCase()) {
            return candidate.replaceFirstChar { it.uppercase() }
        }
        return candidate
    }

    private fun selectCandidate(candidate: String?) {
        try {
            if (candidate.isNullOrBlank()) return
            audioHaptic.playClickSound()
            audioHaptic.triggerHapticFeedback()

            val currentWord = getCurrentWordBeforeCursor()
            val lengthToDelete = currentWord.length

            if (isTranslatePanelOpen) {
                if (translatePanelBuffer.length >= lengthToDelete) {
                    translatePanelBuffer.setLength(translatePanelBuffer.length - lengthToDelete)
                }
                translatePanelBuffer.append("$candidate ")
                tvTranslateInputBox?.text = translatePanelBuffer.toString()
            } else {
                val ic = currentInputConnection
                if (ic != null) {
                    if (lengthToDelete > 0) {
                        ic.deleteSurroundingText(lengthToDelete, 0)
                    }
                    ic.commitText("$candidate ", 1)
                }
            }

            composingWord.clear()
            updateSuggestions()
        } catch (_: Throwable) {}
    }
}
