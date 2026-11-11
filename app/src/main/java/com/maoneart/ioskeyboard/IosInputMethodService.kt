package com.maoneart.ioskeyboard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class IosInputMethodService : InputMethodService() {

    private enum class Mode {
        QWERTY,
        NUMPAD,
        SYMBOLS_123,
        SYMBOLS_MORE,
        EMOJI,
        CLIPBOARD,
        APP_SHORTCUTS,
        APP_PICKER
    }

    private lateinit var prefs: KeyboardPreferences
    private lateinit var audioHaptic: AudioHapticManager
    private lateinit var correctionEngine: SmartCorrectionEngine
    private lateinit var translateManager: LiveTranslateManager
    private lateinit var clipboardManager: ClipboardHistoryManager
    private lateinit var stickerManager: com.maoneart.ioskeyboard.sticker.StickerManager
    private lateinit var layoutEngine: KeyboardLayout
    private lateinit var textReplacementManager: com.maoneart.ioskeyboard.shortcut.TextReplacementManager
    private lateinit var voiceDictationManager: com.maoneart.ioskeyboard.dictation.VoiceDictationManager
    private lateinit var quickPathEngine: com.maoneart.ioskeyboard.keyboard.swipe.QuickPathEngine

    private var currentMode = Mode.QWERTY
    private var isShifted = false
    private var isCapsLock = false
    private var lastShiftPressTime: Long = 0
    private var lastSpacePressTime: Long = 0
    private var lastKeyTypingTime: Long = 0

    // Live Translate Panel State with Movable Interactive Cursor
    private var isTranslatePanelOpen = false
    private val translatePanelBuffer = StringBuilder()
    private var translateCursorPosition = 0
    private val composingWord = StringBuilder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val suggestionHandler = Handler(Looper.getMainLooper())
    private var suggestionRunnable: Runnable? = null

    // 0.2ms View-Cache for QWERTY Layout to eliminate view inflation overhead when toggling 123 ⇄ ABC
    private var cachedQwertyView: View? = null
    private var cachedQwertyKeySignature: String = ""

    // View References
    private var rootView: View? = null
    private var keyboardContainer: FrameLayout? = null
    private var candidatesRow: LinearLayout? = null
    private var candidate1: TextView? = null
    private var candidate2: TextView? = null
    private var candidate3: TextView? = null
    private var badgeLanguage: TextView? = null
    private var btnQuickApps: ImageButton? = null

    // Translate Panel Views
    private var translatePanel: LinearLayout? = null
    private var layoutTranslateLangs: LinearLayout? = null
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
            clipboardManager = ClipboardHistoryManager(this).apply {
                onNewClipCopied = {
                    suggestionHandler.post { updateSuggestions(immediate = true) }
                }
            }
            stickerManager = com.maoneart.ioskeyboard.sticker.StickerManager(this)
            layoutEngine = KeyboardLayout(this)
            layoutEngine.onTrackpadStateChanged = { isActive ->
                if (isActive) {
                    initTrackpadSession()
                } else {
                    finishTrackpadSession()
                }
            }
            textReplacementManager = com.maoneart.ioskeyboard.shortcut.TextReplacementManager(this)
            quickPathEngine = com.maoneart.ioskeyboard.keyboard.swipe.QuickPathEngine(correctionEngine)
            voiceDictationManager = com.maoneart.ioskeyboard.dictation.VoiceDictationManager(
                this,
                onPartialResult = { partial ->
                    candidate2?.text = "🎙️ $partial..."
                },
                onFinalResult = { text ->
                    currentInputConnection?.commitText("$text ", 1)
                    updateSuggestions()
                },
                onStatusChange = { isListening, statusMsg ->
                    layoutEngine.updateDictationVisual(isListening, isDarkMode(), prefs.themeMode)
                    if (isListening) {
                        candidate2?.text = "🎙️ $statusMsg"
                    } else if (statusMsg.isNotEmpty() && statusMsg != "Selesai") {
                        candidate2?.text = statusMsg
                    } else {
                        updateSuggestions()
                    }
                }
            )
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
            audioHaptic.activeRootView = view

            keyboardContainer = view.findViewById(R.id.keyboardContainer)
            candidatesRow = view.findViewById(R.id.candidatesRow)
            candidate1 = view.findViewById(R.id.candidate1)
            candidate2 = view.findViewById(R.id.candidate2)
            candidate3 = view.findViewById(R.id.candidate3)
            badgeLanguage = view.findViewById(R.id.badgeLanguage)

            // Setup App Shortcuts Button
            btnQuickApps = view.findViewById<ImageButton>(R.id.btnQuickApps)

            // Setup Translate Panel
            translatePanel = view.findViewById(R.id.translatePanel)
            layoutTranslateLangs = view.findViewById(R.id.layoutTranslateLangs)
            tvTranslateLangPair = view.findViewById(R.id.tvTranslateLangPair)
            tvTranslateStatus = view.findViewById(R.id.tvTranslateStatus)
            tvTranslateInputBox = view.findViewById(R.id.tvTranslateInputBox)
            btnTranslateSend = view.findViewById(R.id.btnTranslateSend)
            btnCloseTranslate = view.findViewById(R.id.btnCloseTranslate)

            // Interactive Cursor Touch Listener ("buat di geser-geser")
            tvTranslateInputBox?.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                    val layout = tvTranslateInputBox?.layout
                    if (layout != null && translatePanelBuffer.isNotEmpty()) {
                        val x = event.x - (tvTranslateInputBox?.totalPaddingLeft ?: 0) + (tvTranslateInputBox?.scrollX ?: 0)
                        val y = event.y - (tvTranslateInputBox?.totalPaddingTop ?: 0) + (tvTranslateInputBox?.scrollY ?: 0)
                        val line = layout.getLineForVertical(y.toInt().coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0)))
                        val offset = layout.getOffsetForHorizontal(line, x)
                        translateCursorPosition = offset.coerceIn(0, translatePanelBuffer.length)
                        updateTranslateInputBoxVisual()
                        audioHaptic.triggerHapticFeedback()
                    }
                    true
                } else {
                    false
                }
            }

            val btnQuickTranslate = view.findViewById<ImageButton>(R.id.btnQuickTranslate)
            val btnQuickClipboard = view.findViewById<ImageButton>(R.id.btnQuickClipboard)

            btnQuickApps?.setOnClickListener {
                audioHaptic.playClickSound(it)
                audioHaptic.triggerHapticFeedback(it)
                if (currentMode == Mode.APP_SHORTCUTS) {
                    switchMode(Mode.QWERTY)
                } else {
                    switchMode(Mode.APP_SHORTCUTS)
                }
            }

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

            tvTranslateLangPair?.setOnClickListener {
                audioHaptic.playClickSound(it)
                audioHaptic.triggerHapticFeedback(it)
                val temp = prefs.translateSourceLang
                prefs.translateSourceLang = prefs.translateTargetLang
                prefs.translateTargetLang = temp
                updateTranslatePanelUI()
            }

            tvTranslateLangPair?.setOnLongClickListener {
                audioHaptic.playClickSound(it)
                audioHaptic.triggerHapticFeedback(it)
                showAllLanguagesDialog()
                true
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
            adjustLayoutForOrientation(resources.configuration.orientation)
            renderCurrentLayout()

            view
        } catch (t: Throwable) {
            Log.e("IosInputMethodService", "Error in onCreateInputView", t)
            createFallbackKeyboard()
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        // Never allow full-screen extract mode in landscape so keyboard is always anchored cleanly at the bottom
        return false
    }

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        return true
    }

    override fun updateFullscreenMode() {
        super.updateFullscreenMode()
        setExtractViewShown(false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        try {
            cachedQwertyView = null
            cachedQwertyKeySignature = ""
            layoutEngine.resetTrackpadState()
            layoutEngine.resetAllPressedStates()
            layoutEngine.clearHitBoxCache()
            adjustLayoutForOrientation(newConfig.orientation)
            renderCurrentLayout()
            updateSuggestions()
        } catch (t: Throwable) {
            Log.e("IosInputMethodService", "Error in onConfigurationChanged", t)
        }
    }

    private fun adjustLayoutForOrientation(orientation: Int) {
        try {
            val isLandscape = (orientation == Configuration.ORIENTATION_LANDSCAPE)
            val toolbar = rootView?.findViewById<LinearLayout>(R.id.topToolbar)
            val container = keyboardContainer

            if (toolbar != null) {
                val tbLp = toolbar.layoutParams
                tbLp.height = dp(if (isLandscape) 34 else 44)
                toolbar.layoutParams = tbLp

                val btnSize = dp(if (isLandscape) 30 else 36)
                listOf(
                    R.id.btnQuickTranslate,
                    R.id.btnQuickClipboard,
                    R.id.btnQuickApps
                ).forEach { id ->
                    val btn = toolbar.findViewById<ImageButton>(id)
                    btn?.layoutParams?.let { lp ->
                        lp.width = btnSize
                        lp.height = btnSize
                        btn.layoutParams = lp
                    }
                }
            }

            if (container != null) {
                val cLp = container.layoutParams
                cLp.height = dp(if (isLandscape) 155 else 235)
                container.layoutParams = cLp
                val padH = dp(if (isLandscape) 44 else 3)
                val padV = dp(if (isLandscape) 2 else 3)
                container.setPadding(padH, padV, padH, padV)
            }
        } catch (_: Throwable) {}
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
            audioHaptic.activeRootView = rootView
            composingWord.clear()
            layoutEngine.resetTrackpadState()
            layoutEngine.resetAllPressedStates()

            // Auto-detect Numeric / Phone / Bank Transfer / PIN input fields vs standard Text fields
            currentMode = if (isNumericOrPhoneField(info)) {
                Mode.NUMPAD
            } else {
                Mode.QWERTY
            }

            // Auto-Close translate panel when opening a new chat/input session
            if (isTranslatePanelOpen) {
                closeTranslatePanel()
            }

            // Android 10-15 Clipboard Focus Sync: fetch immediately + after window focus settles (120ms & 350ms)
            if (isTerminalOrRawInput()) {
                candidate1?.text = ""
                candidate2?.text = ""
                candidate3?.text = ""
            } else {
                clipboardManager.fetchCurrentClip()
                suggestionHandler.postDelayed({
                    clipboardManager.fetchCurrentClip()
                    updateSuggestions(immediate = true)
                }, 120L)
                suggestionHandler.postDelayed({
                    clipboardManager.fetchCurrentClip()
                }, 350L)
            }

            adjustLayoutForOrientation(resources.configuration.orientation)
            if (!isTerminalOrRawInput()) {
                updateSuggestions()
            }
            updateLanguageBadge()
            updateTranslatePanelUI()
            rootView?.let { applyThemeStyling(it) }

            if (!isCapsLock) {
                isShifted = shouldAutoCapitalize()
            }

            renderCurrentLayout()
            layoutEngine.updateShiftState(isShifted || isCapsLock)
            layoutEngine.updateReturnButton(info, themeMode = prefs.themeMode, isDark = isDarkMode())
            layoutEngine.updateShiftButtonVisual(isShifted, isCapsLock, isDarkMode(), prefs.themeMode)
        } catch (t: Throwable) {
            Log.e("IosInputMethodService", "Error onStartInputView", t)
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        try {
            // Always reset mode to QWERTY when keyboard is closed
            currentMode = Mode.QWERTY
            layoutEngine.resetTrackpadState()
            layoutEngine.resetAllPressedStates()
            if (isTranslatePanelOpen) {
                closeTranslatePanel()
            }
            if (voiceDictationManager.isListening) {
                voiceDictationManager.stopListening()
                layoutEngine.updateDictationVisual(false, isDarkMode(), prefs.themeMode)
            }
        } catch (_: Throwable) {}
    }

    override fun onWindowShown() {
        super.onWindowShown()
        try {
            // Ensure Android 10+ clipboard is captured the exact moment IME window is shown & focused
            if (!isTerminalOrRawInput()) {
                clipboardManager.fetchCurrentClip()
                suggestionHandler.postDelayed({
                    clipboardManager.fetchCurrentClip()
                    updateSuggestions(immediate = true)
                }, 150L)
            }
        } catch (_: Throwable) {}
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        try {
            // Always reset mode to QWERTY when keyboard window is hidden
            currentMode = Mode.QWERTY
            layoutEngine.resetTrackpadState()
            layoutEngine.resetAllPressedStates()
            if (isTranslatePanelOpen) {
                closeTranslatePanel()
            }
            if (voiceDictationManager.isListening) {
                voiceDictationManager.stopListening()
                layoutEngine.updateDictationVisual(false, isDarkMode(), prefs.themeMode)
            }
        } catch (_: Throwable) {}
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // Reset composingWord and update capitalization only when cursor jumps, selects range, or resets to 0
        if ((newSelStart == 0 && newSelEnd == 0) || newSelStart != newSelEnd || Math.abs(newSelStart - oldSelStart) > 1) {
            composingWord.clear()
            updateSuggestions(immediate = false)
            updateAutoCapitalization()
        }
    }

    private fun isDarkMode(): Boolean {
        return try {
            when (prefs.themeMode) {
                "onepiece" -> true
                "woody" -> true
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
            val isWoody = (prefs.themeMode == "woody")
            val isOnePiece = (prefs.themeMode == "onepiece")
            val keyboardRoot = view.findViewById<LinearLayout>(R.id.keyboardRoot)
            val topToolbar = view.findViewById<LinearLayout>(R.id.topToolbar)
            val textColor = when {
                isOnePiece -> Color.parseColor("#2A1808")
                isWoody -> Color.parseColor("#FFF3E0")
                isDark -> Color.WHITE
                else -> Color.BLACK
            }
            val secondaryText = when {
                isOnePiece -> Color.parseColor("#FFE082")
                isWoody -> Color.parseColor("#C4A587")
                isDark -> Color.LTGRAY
                else -> Color.DKGRAY
            }
            val iconTint = when {
                isOnePiece -> Color.parseColor("#FFE082")
                isWoody -> Color.parseColor("#E8D5B5")
                isDark -> Color.WHITE
                else -> Color.BLACK
            }

            if (isOnePiece) {
                // Background One Piece asli murni dari gambar sampel (Luffy & Sunny)
                keyboardRoot?.setBackgroundResource(R.drawable.theme_onepiece_bg)
                keyboardRoot?.minimumHeight = 0
                topToolbar?.setBackgroundColor(Color.parseColor("#E6261108"))
                translatePanel?.setBackgroundColor(Color.parseColor("#F02A140A"))
                tvTranslateInputBox?.setBackgroundResource(R.drawable.key_bg_onepiece)
                tvTranslateInputBox?.setTextColor(textColor)
            } else if (isWoody) {
                keyboardRoot?.setBackgroundResource(R.drawable.theme_woody_bg)
                keyboardRoot?.minimumHeight = 0
                topToolbar?.setBackgroundColor(Color.parseColor("#4D1A0E08"))
                translatePanel?.setBackgroundColor(Color.parseColor("#661A0E08"))
                tvTranslateInputBox?.setBackgroundResource(R.drawable.key_bg_woody)
                tvTranslateInputBox?.setTextColor(textColor)
            } else {
                keyboardRoot?.setBackgroundColor(ContextCompat.getColor(this, if (isDark) R.color.ios_bg_dark else R.color.ios_bg_light))
                keyboardRoot?.minimumHeight = 0
                topToolbar?.setBackgroundColor(ContextCompat.getColor(this, if (isDark) R.color.ios_toolbar_dark else R.color.ios_toolbar_light))
                translatePanel?.setBackgroundColor(ContextCompat.getColor(this, if (isDark) R.color.ios_toolbar_dark else R.color.ios_toolbar_light))
                tvTranslateInputBox?.setBackgroundResource(if (isDark) R.drawable.key_bg_ios_dark else R.drawable.key_bg_ios_light)
                tvTranslateInputBox?.setTextColor(textColor)
            }

            val toolbarCandidateColor = if (isOnePiece) Color.parseColor("#FFE082") else textColor
            val toolbarSecondaryColor = if (isOnePiece) Color.parseColor("#C4A587") else secondaryText
            candidate1?.setTextColor(toolbarSecondaryColor)
            candidate2?.setTextColor(toolbarCandidateColor)
            candidate3?.setTextColor(toolbarSecondaryColor)

            val btnTranslate = view.findViewById<ImageButton>(R.id.btnQuickTranslate)
            val btnClipboard = view.findViewById<ImageButton>(R.id.btnQuickClipboard)
            val btnApps = view.findViewById<ImageButton>(R.id.btnQuickApps)

            val isAppsActive = (currentMode == Mode.APP_SHORTCUTS || currentMode == Mode.APP_PICKER)
            val isClipboardActive = (currentMode == Mode.CLIPBOARD)
            val isTranslateActive = isTranslatePanelOpen

            btnTranslate?.setColorFilter(if (isTranslateActive) ContextCompat.getColor(this, R.color.ios_blue) else iconTint)
            btnClipboard?.setColorFilter(if (isClipboardActive) ContextCompat.getColor(this, R.color.ios_blue) else iconTint)
            btnApps?.setColorFilter(if (isAppsActive) ContextCompat.getColor(this, R.color.ios_blue) else iconTint)
            badgeLanguage?.setTextColor(iconTint)
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
        if (isTranslatePanelOpen) {
            translateCursorPosition = translatePanelBuffer.length
            if (currentMode != Mode.QWERTY) {
                switchMode(Mode.QWERTY)
            }
        } else {
            if (currentMode != Mode.QWERTY) {
                switchMode(Mode.QWERTY)
            }
        }
        updateTranslatePanelUI()
    }

    private fun closeTranslatePanel() {
        isTranslatePanelOpen = false
        translatePanelBuffer.clear()
        translateCursorPosition = 0
        if (currentMode != Mode.QWERTY) {
            switchMode(Mode.QWERTY)
        }
        updateTranslatePanelUI()
    }

    private fun getInstalledLauncherApps(): List<KeyboardLayout.AppShortcutItem> {
        return try {
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PackageManager.MATCH_ALL
            } else {
                0
            }
            val list = pm.queryIntentActivities(mainIntent, flags)
            list.mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == packageName) return@mapNotNull null // Exclude ourselves
                val label = resolveInfo.loadLabel(pm).toString()
                val icon = resolveInfo.loadIcon(pm)
                KeyboardLayout.AppShortcutItem(pkg, label, icon)
            }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun toggleAppShortcutPackage(pkg: String) {
        val current = prefs.getAppShortcuts().toMutableList()
        if (current.contains(pkg)) {
            current.remove(pkg)
        } else {
            current.add(pkg)
        }
        prefs.saveAppShortcuts(current)
    }

    // Render movable iOS blue cursor ('│') in Translate Input Box
    private fun updateTranslateInputBoxVisual() {
        try {
            val box = tvTranslateInputBox ?: return
            if (!isTranslatePanelOpen) return

            if (translatePanelBuffer.isEmpty()) {
                val ssb = SpannableStringBuilder("│")
                ssb.setSpan(ForegroundColorSpan(Color.parseColor("#007AFF")), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                ssb.setSpan(StyleSpan(Typeface.BOLD), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                box.text = ssb
                return
            }

            val pos = translateCursorPosition.coerceIn(0, translatePanelBuffer.length)
            val ssb = SpannableStringBuilder(translatePanelBuffer)
            ssb.insert(pos, "│")
            ssb.setSpan(ForegroundColorSpan(Color.parseColor("#007AFF")), pos, pos + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(StyleSpan(Typeface.BOLD), pos, pos + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            box.text = ssb
        } catch (_: Throwable) {}
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private val availableTargetLanguages = listOf(
        Pair("🇺🇸 English", "en"),
        Pair("🇸🇦 Arab", "ar"),
        Pair("🇯🇵 Jepang", "ja"),
        Pair("🇰🇷 Korea", "ko"),
        Pair("🇨🇳 Mandarin", "zh"),
        Pair("🇪🇸 Spanyol", "es"),
        Pair("🇫🇷 Prancis", "fr"),
        Pair("🇩🇪 Jerman", "de"),
        Pair("🇷🇺 Rusia", "ru"),
        Pair("🇹🇷 Turki", "tr"),
        Pair("🇮🇩 Indonesia", "id"),
        Pair("🌐 Semua...", "MORE")
    )

    private fun renderQuickLanguageChips() {
        val container = layoutTranslateLangs ?: return
        container.removeAllViews()
        val isDark = isDarkMode()
        val currentTarget = prefs.translateTargetLang.lowercase()

        for ((label, code) in availableTargetLanguages) {
            val isSelected = (code == currentTarget)
            val chip = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(24)
                ).apply { setMargins(dp(2), 0, dp(2), 0) }
                setPadding(dp(8), 0, dp(8), 0)
                gravity = Gravity.CENTER
                text = label
                textSize = 11f

                if (isSelected) {
                    background = ContextCompat.getDrawable(this@IosInputMethodService, R.drawable.btn_apple_translate_send)
                    setTextColor(Color.WHITE)
                    setTypeface(typeface, Typeface.BOLD)
                } else {
                    background = ContextCompat.getDrawable(
                        this@IosInputMethodService,
                        if (isDark) R.drawable.key_bg_special_dark else R.drawable.chip_bg_language_pill
                    )
                    setTextColor(if (isDark) Color.parseColor("#98989D") else Color.parseColor("#48484A"))
                    setTypeface(typeface, Typeface.NORMAL)
                }

                setOnClickListener {
                    audioHaptic.playClickSound(it)
                    audioHaptic.triggerHapticFeedback(it)
                    if (code == "MORE") {
                        showAllLanguagesDialog()
                    } else {
                        prefs.translateTargetLang = code
                        updateTranslatePanelUI()
                    }
                }
            }
            container.addView(chip)
        }
    }

    private fun showAllLanguagesDialog() {
        try {
            val allLanguages = listOf(
                Pair("🇺🇸 Inggris (English)", "en"),
                Pair("🇮🇩 Indonesia", "id"),
                Pair("🇸🇦 Arab (العربية)", "ar"),
                Pair("🇯🇵 Jepang (日本語)", "ja"),
                Pair("🇰🇷 Korea (한국어)", "ko"),
                Pair("🇨🇳 Mandarin (中文)", "zh"),
                Pair("🇪🇸 Spanyol (Español)", "es"),
                Pair("🇫🇷 Prancis (Français)", "fr"),
                Pair("🇩🇪 Jerman (Deutsch)", "de"),
                Pair("🇷🇺 Rusia (Русский)", "ru"),
                Pair("🇹🇷 Turki (Türkçe)", "tr"),
                Pair("🇳🇱 Belanda (Nederlands)", "nl"),
                Pair("🇮🇹 Italia (Italiano)", "it"),
                Pair("🇵🇹 Portugis (Português)", "pt"),
                Pair("🇻🇳 Vietnam (Tiếng Việt)", "vi"),
                Pair("🇹🇭 Thailand (ไทย)", "th"),
                Pair("🇮🇳 Hindi (हिन्दी)", "hi"),
                Pair("🇵🇭 Filipina (Tagalog)", "tl"),
                Pair("🇲🇾 Melayu", "ms")
            )
            val names = allLanguages.map { it.first }.toTypedArray()
            val themeRes = if (isDarkMode()) androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert else androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert
            val builder = androidx.appcompat.app.AlertDialog.Builder(ContextThemeWrapper(this, themeRes))
            builder.setTitle("Pilih Bahasa Tujuan")
            builder.setItems(names) { _, which ->
                prefs.translateTargetLang = allLanguages[which].second
                updateTranslatePanelUI()
            }
            builder.setNegativeButton("Batal", null)
            val token = rootView?.windowToken ?: return
            val dialog = builder.create()
            val window = dialog.window
            if (window != null) {
                val lp = window.attributes
                lp.token = token
                lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
                window.attributes = lp
                dialog.setCanceledOnTouchOutside(true)
                dialog.show()
            }
        } catch (_: Throwable) {}
    }

    private fun updateTranslatePanelUI() {
        try {
            if (isTranslatePanelOpen) {
                translatePanel?.visibility = View.VISIBLE
                val isDark = isDarkMode()
                translatePanel?.background = ContextCompat.getDrawable(
                    this@IosInputMethodService,
                    if (isDark) R.drawable.panel_bg_apple_translate_dark else R.drawable.panel_bg_apple_translate
                )
                tvTranslateInputBox?.background = ContextCompat.getDrawable(
                    this@IosInputMethodService,
                    if (isDark) R.drawable.input_bg_apple_translate_dark else R.drawable.input_bg_apple_translate
                )
                tvTranslateInputBox?.setTextColor(if (isDark) Color.WHITE else Color.BLACK)

                val src = prefs.translateSourceLang.uppercase()
                val tgt = prefs.translateTargetLang.uppercase()
                tvTranslateLangPair?.text = "🌐 $src ⇄ $tgt"

                if (translatePanelBuffer.isEmpty()) {
                    tvTranslateStatus?.text = "✦ Ketik kalimat lalu tap Kirim"
                } else {
                    tvTranslateStatus?.text = "✦ Kalimat siap ($src ➔ $tgt)"
                }
                updateTranslateInputBoxVisual()
                renderQuickLanguageChips()
            } else {
                translatePanel?.visibility = View.GONE
                translatePanelBuffer.clear()
                translateCursorPosition = 0
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

                // Auto-Close translate panel immediately after sending!
                closeTranslatePanel()
            } catch (_: Throwable) {
                tvTranslateStatus?.text = "Gagal menerjemahkan (periksa koneksi)"
            }
        }
    }

    private fun switchMode(mode: Mode) {
        currentMode = mode
        rootView?.let { applyThemeStyling(it) }
        renderCurrentLayout()
    }

    private fun renderCurrentLayout() {
        try {
            val container = keyboardContainer ?: return
            container.removeAllViews()

            val isDark = isDarkMode()

            val contentView = when (currentMode) {
                Mode.QWERTY -> {
                    val orientation = resources.configuration.orientation
                    val sig = "$isDark|${prefs.themeMode}|${prefs.activeLanguage}|${prefs.isSwipeTypingEnabled}|${prefs.isKeyPopupEnabled}|${prefs.isDictationEnabled}|$orientation"
                    val existing = cachedQwertyView
                    if (existing != null && cachedQwertyKeySignature == sig) {
                        (existing.parent as? android.view.ViewGroup)?.removeView(existing)
                        layoutEngine.updateShiftState(isShifted || isCapsLock)
                        existing.post { layoutEngine.rebuildHitBoxCache() }
                        existing
                    } else {
                        val created = layoutEngine.createQwertyView(
                            isShifted = isShifted || isCapsLock,
                            isDark = isDark,
                            themeMode = prefs.themeMode,
                            activeLanguage = prefs.activeLanguage,
                            isSwipeEnabled = prefs.isSwipeTypingEnabled,
                            isKeyPopupEnabled = prefs.isKeyPopupEnabled,
                            isDictationEnabled = prefs.isDictationEnabled,
                            onKeyAction = { handleKeyAction(it) },
                            onCursorMove = { dx, dy, isSelecting -> moveCursor2D(dx, dy, isSelecting) },
                            onSwipeComplete = { handleSwipeComplete(it) },
                            onSwipeHaptic = { audioHaptic.triggerHapticFeedback() },
                            onSwipeLiveUpdate = { handleSwipeLiveUpdate(it) }
                        )
                        cachedQwertyView = created
                        cachedQwertyKeySignature = sig
                        created
                    }
                }
                Mode.NUMPAD -> layoutEngine.createNumpadView(
                    isDark = isDark,
                    themeMode = prefs.themeMode,
                    isPhoneInput = ((currentInputEditorInfo?.inputType ?: 0) and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_PHONE,
                    onKeyAction = { handleKeyAction(it) }
                )
                Mode.SYMBOLS_123 -> layoutEngine.createSymbolsView(
                    isMoreSymbols = false,
                    isDark = isDark,
                    themeMode = prefs.themeMode,
                    isDictationEnabled = prefs.isDictationEnabled,
                    onKeyAction = { handleKeyAction(it) },
                    onCursorMove = { dx, dy, isSelecting -> moveCursor2D(dx, dy, isSelecting) }
                )
                Mode.SYMBOLS_MORE -> layoutEngine.createSymbolsView(
                    isMoreSymbols = true,
                    isDark = isDark,
                    themeMode = prefs.themeMode,
                    isDictationEnabled = prefs.isDictationEnabled,
                    onKeyAction = { handleKeyAction(it) },
                    onCursorMove = { dx, dy, isSelecting -> moveCursor2D(dx, dy, isSelecting) }
                )
                Mode.EMOJI -> layoutEngine.createEmojiView(
                    isDark = isDark,
                    onEmojiAction = { handleEmojiAction(it) },
                    onStickerAction = { stickerFile ->
                        sendStickerToApp(stickerFile)
                    },
                    onAddStickerAction = {
                        openStickerUploadScreen()
                    },
                    onBackAction = { switchMode(Mode.QWERTY) }
                )
                Mode.CLIPBOARD -> layoutEngine.createClipboardView(
                    isDark = isDark,
                    themeMode = prefs.themeMode,
                    items = clipboardManager.getItems(),
                    currentViewMode = prefs.clipboardViewMode,
                    onPasteAction = { text ->
                        commitDirectText(text)
                        switchMode(Mode.QWERTY)
                    },
                    onTogglePinAction = { id ->
                        clipboardManager.togglePin(id)
                        renderCurrentLayout()
                    },
                    onDeleteAction = { id ->
                        clipboardManager.deleteItem(id)
                        renderCurrentLayout()
                    },
                    onClearUnpinnedAction = {
                        clipboardManager.clearAllUnpinned()
                        renderCurrentLayout()
                    },
                    onToggleViewMode = { mode ->
                        prefs.clipboardViewMode = mode
                        renderCurrentLayout()
                    },
                    onCloseAction = { switchMode(Mode.QWERTY) }
                )
                Mode.APP_SHORTCUTS -> {
                    val shortcuts = prefs.getAppShortcuts()
                    val pm = packageManager
                    val appItems = mutableListOf<KeyboardLayout.AppShortcutItem>()
                    for (pkg in shortcuts) {
                        try {
                            val appInfo = pm.getApplicationInfo(pkg, 0)
                            val label = pm.getApplicationLabel(appInfo).toString()
                            val icon = pm.getApplicationIcon(appInfo)
                            appItems.add(KeyboardLayout.AppShortcutItem(pkg, label, icon))
                        } catch (_: Throwable) {}
                    }
                    layoutEngine.createAppShortcutsView(
                        isDark = isDark,
                        themeMode = prefs.themeMode,
                        shortcuts = appItems,
                        onLaunchApp = { pkg ->
                            audioHaptic.playClickSound(null)
                            audioHaptic.triggerHapticFeedback()
                            try {
                                val intent = packageManager.getLaunchIntentForPackage(pkg)?.apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                if (intent != null) {
                                    startActivity(intent)
                                }
                            } catch (t: Throwable) {
                                Log.e("IosInputMethodService", "Failed to launch $pkg", t)
                            }
                        },
                        onOpenAppPicker = {
                            audioHaptic.playClickSound(null)
                            audioHaptic.triggerHapticFeedback()
                            switchMode(Mode.APP_PICKER)
                        },
                        onOpenSettings = {
                            audioHaptic.playClickSound(null)
                            audioHaptic.triggerHapticFeedback()
                            try {
                                val intent = Intent(this, SettingsActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                startActivity(intent)
                            } catch (_: Throwable) {}
                        },
                        onCloseAction = {
                            audioHaptic.playClickSound(null)
                            audioHaptic.triggerHapticFeedback()
                            switchMode(Mode.QWERTY)
                        }
                    )
                }
                Mode.APP_PICKER -> {
                    val installedApps = getInstalledLauncherApps()
                    val currentShortcuts = prefs.getAppShortcuts().toSet()
                    layoutEngine.createAppPickerView(
                        isDark = isDark,
                        themeMode = prefs.themeMode,
                        installedApps = installedApps,
                        selectedPackages = currentShortcuts,
                        onToggleShortcut = { pkg ->
                            toggleAppShortcutPackage(pkg)
                        },
                        onCloseAction = {
                            audioHaptic.playClickSound(null)
                            audioHaptic.triggerHapticFeedback()
                            switchMode(Mode.APP_SHORTCUTS)
                        }
                    )
                }
            }

            container.addView(contentView)
            layoutEngine.updateReturnButton(currentInputEditorInfo, themeMode = prefs.themeMode, isDark = isDarkMode())
        } catch (t: Throwable) {
            Log.e("IosInputMethodService", "Error in renderCurrentLayout", t)
        }
    }

    // Spacebar Trackpad Session State (Zero focus-escape, 100% confined to text box)
    private var trackpadSessionActive = false
    private var trackpadCursorPos = 0
    private var trackpadAnchorPos = -1
    private var trackpadTextLength = 0
    private var trackpadFullText = ""

    private fun initTrackpadSession() {
        val ic = currentInputConnection ?: return
        trackpadSessionActive = true
        trackpadAnchorPos = -1

        var text = ""
        var cursorPos = 0

        try {
            val req = android.view.inputmethod.ExtractedTextRequest().apply {
                token = 0
                flags = 0
                hintMaxLines = 1000
                hintMaxChars = 100000
            }
            val ext = ic.getExtractedText(req, 0)
            if (ext != null && ext.text != null) {
                text = ext.text.toString()
                cursorPos = ext.selectionEnd
            }
        } catch (_: Throwable) {}

        if (text.isEmpty()) {
            val before = ic.getTextBeforeCursor(10000, 0)?.toString() ?: ""
            val after = ic.getTextAfterCursor(10000, 0)?.toString() ?: ""
            text = before + after
            cursorPos = before.length
        }

        trackpadFullText = text
        trackpadTextLength = text.length
        trackpadCursorPos = cursorPos.coerceIn(0, trackpadTextLength)
    }

    private fun finishTrackpadSession() {
        trackpadSessionActive = false
        trackpadAnchorPos = -1
        // Selection remains untouched so user can copy, cut or delete selected block!
    }

    private fun moveCursor2D(dx: Int, dy: Int, isSelecting: Boolean = false) {
        try {
            if (!prefs.isSpaceTrackpadEnabled) return

            if (isTranslatePanelOpen) {
                if (dx > 0) {
                    translateCursorPosition = (translateCursorPosition + dx).coerceAtMost(translatePanelBuffer.length)
                } else if (dx < 0) {
                    translateCursorPosition = (translateCursorPosition + dx).coerceAtLeast(0)
                }
                updateTranslateInputBoxVisual()
                audioHaptic.triggerHapticFeedback()
                return
            }

            val ic = currentInputConnection ?: return

            if (!trackpadSessionActive) {
                initTrackpadSession()
            }

            // 1. Horizontal Cursor Navigation (strictly within text bounds)
            if (dx != 0) {
                trackpadCursorPos = (trackpadCursorPos + dx).coerceIn(0, trackpadTextLength)
            }

            // 2. Vertical Multiline Navigation (within message lines only, NEVER escapes to WhatsApp chat list!)
            if (dy < 0 && trackpadFullText.isNotEmpty()) {
                val currentLineStart = trackpadFullText.lastIndexOf('\n', (trackpadCursorPos - 1).coerceAtLeast(0))
                if (currentLineStart >= 0) {
                    val prevLineStart = trackpadFullText.lastIndexOf('\n', (currentLineStart - 1).coerceAtLeast(0))
                    val lineStart = if (prevLineStart >= 0) prevLineStart + 1 else 0
                    val col = trackpadCursorPos - (currentLineStart + 1)
                    val prevLineLen = currentLineStart - lineStart
                    trackpadCursorPos = (lineStart + col.coerceIn(0, prevLineLen)).coerceIn(0, trackpadTextLength)
                } else {
                    // Top line reached: clamp to index 0, DO NOT ESCAPE VIEW!
                    trackpadCursorPos = 0
                }
            } else if (dy > 0 && trackpadFullText.isNotEmpty()) {
                val nextNewline = trackpadFullText.indexOf('\n', trackpadCursorPos)
                if (nextNewline >= 0) {
                    val currentLineStart = trackpadFullText.lastIndexOf('\n', (trackpadCursorPos - 1).coerceAtLeast(0))
                    val col = trackpadCursorPos - if (currentLineStart >= 0) currentLineStart + 1 else 0
                    val nextLineEnd = trackpadFullText.indexOf('\n', nextNewline + 1).let { if (it >= 0) it else trackpadTextLength }
                    val nextLineLen = nextLineEnd - (nextNewline + 1)
                    trackpadCursorPos = (nextNewline + 1 + col.coerceIn(0, nextLineLen)).coerceIn(0, trackpadTextLength)
                } else {
                    // Bottom line reached: clamp to end, DO NOT ESCAPE VIEW!
                    trackpadCursorPos = trackpadTextLength
                }
            }

            // 3. Selection Anchor or Single Cursor Positioning (Direct Editable selection, zero focus leak)
            if (isSelecting) {
                if (trackpadAnchorPos < 0) {
                    trackpadAnchorPos = (trackpadCursorPos - dx).coerceIn(0, trackpadTextLength)
                }
                val selStart = minOf(trackpadAnchorPos, trackpadCursorPos)
                val selEnd = maxOf(trackpadAnchorPos, trackpadCursorPos)
                ic.setSelection(selStart, selEnd)
            } else {
                trackpadAnchorPos = -1
                ic.setSelection(trackpadCursorPos, trackpadCursorPos)
            }

            audioHaptic.triggerHapticFeedback()
            updateSuggestions()
        } catch (_: Throwable) {}
    }

    private fun handleDictationAction() {
        try {
            if (!prefs.isDictationEnabled) return
            val now = System.currentTimeMillis()
            // Anti-Accidental Touch Guard: Ignore if user was actively typing or word is in-progress
            if (now - lastKeyTypingTime < 280L || composingWord.isNotEmpty()) {
                return
            }
            if (voiceDictationManager.isListening) {
                voiceDictationManager.stopListening()
            } else {
                voiceDictationManager.startListening(prefs.activeLanguage)
            }
        } catch (_: Throwable) {}
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
                    layoutEngine.updateShiftState(isShifted || isCapsLock)
                    layoutEngine.updateShiftButtonVisual(isShifted, isCapsLock, isDarkMode(), prefs.themeMode)
                }
                "ACTION_BACKSPACE" -> {
                    handleBackspace()
                }
                "ACTION_SYMBOLS" -> {
                    switchMode(Mode.SYMBOLS_123)
                }
                "ACTION_NUMPAD" -> {
                    switchMode(Mode.NUMPAD)
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
                "ACTION_DICTATION" -> {
                    handleDictationAction()
                }
                "ACTION_RETURN" -> {
                    handleReturn()
                }
                "ACTION_HIDE_KEYBOARD" -> {
                    requestHideSelf(0)
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

    // --- Auto-Capitalization Engine (Awal Kalimat & Setelah Tanda Titik) ---
    private fun shouldAutoCapitalize(): Boolean {
        if (!prefs.isAutoCapitalizationEnabled) return false
        if (isCapsLock) return false

        val info = currentInputEditorInfo ?: return false
        val inputType = info.inputType

        // Jangan auto-kapitalisasi pada field password, angka, phone, email, URL
        val cls = inputType and InputType.TYPE_MASK_CLASS
        if (cls != InputType.TYPE_CLASS_TEXT) {
            return false
        }
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_URI ||
            variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
            return false
        }

        // 1. Periksa buffer panel terjemahan jika terbuka
        if (isTranslatePanelOpen) {
            if (translateCursorPosition == 0 || translatePanelBuffer.isEmpty()) {
                composingWord.clear()
                return true
            }
            val textBefore = translatePanelBuffer.substring(0, translateCursorPosition.coerceIn(0, translatePanelBuffer.length))
            if (textBefore.trim().isEmpty()) {
                composingWord.clear()
                return true
            }
            if (textBefore.endsWith("\n") || textBefore.endsWith("\r")) {
                composingWord.clear()
                return true
            }
            if (Regex("""[.!?]\s+$""").containsMatchIn(textBefore)) {
                composingWord.clear()
                return true
            }
            return false
        }

        // 2. Inspeksi teks sebelum kursor dari InputConnection
        val ic = currentInputConnection ?: return false
        val textBefore = ic.getTextBeforeCursor(32, 0)?.toString()

        // Jika benar-benar kosong atau hanya spasi awal -> awal kalimat pertama / pesan baru (misal setelah kirim WA)
        if (textBefore.isNullOrEmpty() || textBefore.trim().isEmpty()) {
            composingWord.clear()
            return true
        }

        // Jika berada tepat setelah baris baru (Enter)
        if (textBefore.endsWith("\n") || textBefore.endsWith("\r")) {
            composingWord.clear()
            return true
        }

        // HANYA aktif jika teks berakhiran tanda baca (.!?) diikuti spasi (misal ". ", "? ", "! ")
        if (Regex("""[.!?]\s+$""").containsMatchIn(textBefore)) {
            composingWord.clear()
            return true
        }

        // Jika sedang aktif mengetik huruf dalam suatu kata (misal "Ha..."), huruf berikutnya wajib huruf kecil!
        return false
    }

    private fun updateAutoCapitalization() {
        try {
            if (isCapsLock) return
            val shouldCap = shouldAutoCapitalize()
            if (isShifted != shouldCap) {
                isShifted = shouldCap
                layoutEngine.updateShiftState(isShifted)
                layoutEngine.updateShiftButtonVisual(isShifted, false, isDarkMode(), prefs.themeMode)
            }
        } catch (_: Throwable) {}
    }

    private fun handleCharacterInput(charStr: String) {
        try {
            lastKeyTypingTime = System.currentTimeMillis()
            if (isTranslatePanelOpen) {
                val pos = translateCursorPosition.coerceIn(0, translatePanelBuffer.length)
                translatePanelBuffer.insert(pos, charStr)
                translateCursorPosition = pos + charStr.length
                updateTranslateInputBoxVisual()
                tvTranslateStatus?.text = "Kalimat siap (${prefs.translateSourceLang.uppercase()} ➔ ${prefs.translateTargetLang.uppercase()})"
                updateSuggestions(immediate = false)
            } else {
                currentInputConnection?.commitText(charStr, 1)
                if (charStr.length == 1 && !charStr[0].isLetterOrDigit() && charStr[0] != '\'' && charStr[0] != '-') {
                    composingWord.clear()
                } else {
                    composingWord.append(charStr)
                }
                updateSuggestions(immediate = false)
            }

            if (isShifted && !isCapsLock) {
                isShifted = false
                layoutEngine.updateShiftState(false)
                layoutEngine.updateShiftButtonVisual(false, false, isDarkMode(), prefs.themeMode)
            }
        } catch (_: Throwable) {}
    }

    private fun isNumericOrPhoneField(info: EditorInfo?): Boolean {
        if (info == null) return false
        val inputType = info.inputType
        val cls = inputType and InputType.TYPE_MASK_CLASS
        if (cls == InputType.TYPE_CLASS_NUMBER ||
            cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME
        ) {
            return true
        }
        // Also check common bank/amount/PIN hint or field name when app uses custom text inputType
        val hint = (info.hintText?.toString() ?: "").lowercase()
        val label = (info.label?.toString() ?: "").lowercase()
        val fieldName = (info.fieldName ?: "").lowercase()
        val combined = "$hint $label $fieldName"
        if (combined.contains("nominal") ||
            combined.contains("jumlah transfer") ||
            combined.contains("nomor rekening") ||
            combined.contains("no. rek") ||
            combined.contains("kode otp") ||
            combined.contains("masukkan pin") ||
            combined.contains("nomor hp") ||
            combined.contains("nomor telepon")
        ) {
            return true
        }
        return false
    }

    private fun isTerminalOrRawInput(): Boolean {
        val editor = currentInputEditorInfo ?: return false
        val pkg = (editor.packageName ?: "").lowercase()
        val type = editor.inputType
        return (type and InputType.TYPE_MASK_CLASS) == InputType.TYPE_NULL ||
               type == 0 ||
               pkg.contains("termux") ||
               pkg.contains("terminal") ||
               pkg.contains("connectbot") ||
               pkg.contains("juicessh") ||
               pkg.contains("ssh")
    }

    private fun handleBackspace() {
        try {
            if (isTranslatePanelOpen) {
                if (translatePanelBuffer.isNotEmpty() && translateCursorPosition > 0) {
                    val pos = translateCursorPosition.coerceIn(1, translatePanelBuffer.length)
                    val charBefore = translatePanelBuffer[pos - 1]
                    val charsToDelete = if (Character.isLowSurrogate(charBefore) && pos >= 2 && Character.isHighSurrogate(translatePanelBuffer[pos - 2])) 2 else 1
                    translatePanelBuffer.delete(pos - charsToDelete, pos)
                    translateCursorPosition = pos - charsToDelete
                    updateTranslateInputBoxVisual()
                }
                updateSuggestions()
                updateAutoCapitalization()
                return
            }

            val ic = currentInputConnection
            if (isTerminalOrRawInput()) {
                if (composingWord.isNotEmpty()) {
                    composingWord.deleteCharAt(composingWord.length - 1)
                }
                if (ic != null) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                }
                updateSuggestions()
                updateAutoCapitalization()
                return
            }

            if (composingWord.isNotEmpty()) {
                val lastIdx = composingWord.length - 1
                val lastCh = composingWord[lastIdx]
                val charsToDel = if (Character.isLowSurrogate(lastCh) && composingWord.length >= 2 && Character.isHighSurrogate(composingWord[lastIdx - 1])) 2 else 1
                composingWord.delete(composingWord.length - charsToDel, composingWord.length)
            }

            if (ic != null) {
                val selectedText = ic.getSelectedText(0)
                if (!selectedText.isNullOrEmpty()) {
                    ic.commitText("", 1)
                } else {
                    val deleted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        ic.deleteSurroundingTextInCodePoints(1, 0)
                    } else {
                        false
                    }
                    if (!deleted) {
                        val textBefore = ic.getTextBeforeCursor(4, 0)?.toString()
                        if (!textBefore.isNullOrEmpty()) {
                            val lastChar = textBefore.last()
                            val charsToDelete = if (Character.isLowSurrogate(lastChar) && textBefore.length >= 2 && Character.isHighSurrogate(textBefore[textBefore.length - 2])) 2 else 1
                            val delOk = try { ic.deleteSurroundingText(charsToDelete, 0) } catch (_: Throwable) { false }
                            if (!delOk) {
                                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                            }
                        } else {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                        }
                    }
                }
            }

            updateSuggestions()
            updateAutoCapitalization()
        } catch (_: Throwable) {}
    }

    private fun handleSpace() {
        try {
            if (isTerminalOrRawInput()) {
                currentInputConnection?.commitText(" ", 1)
                composingWord.clear()
                updateSuggestions()
                return
            }

            val (activeWord, _) = getTypingContext()

            // 1. Apple Text Replacement on Space (e.g. "otw" -> "On the way")
            if (activeWord.isNotEmpty()) {
                val replacement = textReplacementManager.getReplacement(activeWord)
                if (replacement != null) {
                    val len = activeWord.length
                    if (isTranslatePanelOpen) {
                        val pos = translateCursorPosition.coerceIn(0, translatePanelBuffer.length)
                        if (pos >= len) {
                            translatePanelBuffer.delete(pos - len, pos)
                            translateCursorPosition = pos - len
                        }
                        translatePanelBuffer.insert(translateCursorPosition, "$replacement ")
                        translateCursorPosition += replacement.length + 1
                        updateTranslateInputBoxVisual()
                    } else {
                        currentInputConnection?.deleteSurroundingText(len, 0)
                        currentInputConnection?.commitText("$replacement ", 1)
                    }
                    correctionEngine.recordWordUsage(replacement, 2)
                    composingWord.clear()
                    updateSuggestions()
                    updateAutoCapitalization()
                    return
                }
            }

            // 2. Apple Auto-Correction on Spacebar (replaces typo with center candidate)
            if (prefs.isAutoCorrectEnabled && activeWord.length >= 3) {
                var centerCandidate = candidate2?.text?.toString()?.trim() ?: ""
                if (centerCandidate.startsWith("⚡ ")) centerCandidate = centerCandidate.removePrefix("⚡ ").trim()
                if (centerCandidate.startsWith("✦ ")) centerCandidate = centerCandidate.removePrefix("✦ ").trim()

                if (centerCandidate.isNotEmpty() && !centerCandidate.equals(activeWord, ignoreCase = true)) {
                    if (correctionEngine.isCorrectionMatch(activeWord, centerCandidate, prefs.activeLanguage)) {
                        val len = activeWord.length
                        if (isTranslatePanelOpen) {
                            val pos = translateCursorPosition.coerceIn(0, translatePanelBuffer.length)
                            if (pos >= len) {
                                translatePanelBuffer.delete(pos - len, pos)
                                translateCursorPosition = pos - len
                            }
                            translatePanelBuffer.insert(translateCursorPosition, "$centerCandidate ")
                            translateCursorPosition += centerCandidate.length + 1
                            updateTranslateInputBoxVisual()
                        } else {
                            currentInputConnection?.deleteSurroundingText(len, 0)
                            currentInputConnection?.commitText("$centerCandidate ", 1)
                        }
                        correctionEngine.recordWordUsage(centerCandidate, 2)
                        composingWord.clear()
                        updateSuggestions()
                        updateAutoCapitalization()
                        return
                    }
                }
            }

            if (activeWord.length >= 2) {
                correctionEngine.recordWordUsage(activeWord, 1)
            }

            if (isTranslatePanelOpen) {
                val pos = translateCursorPosition.coerceIn(0, translatePanelBuffer.length)
                val now = System.currentTimeMillis()
                if (prefs.isDoubleSpacePeriodEnabled && (now - lastSpacePressTime < 500) && composingWord.isEmpty() && pos > 0 && translatePanelBuffer[pos - 1] == ' ' && (pos < 2 || translatePanelBuffer[pos - 2] != '.')) {
                    translatePanelBuffer.delete(pos - 1, pos)
                    translatePanelBuffer.insert(pos - 1, ". ")
                    translateCursorPosition = pos + 1
                    lastSpacePressTime = 0
                    updateTranslateInputBoxVisual()
                    updateSuggestions()
                    updateAutoCapitalization()
                    return
                }
                lastSpacePressTime = now
                translatePanelBuffer.insert(pos, " ")
                translateCursorPosition = pos + 1
                updateTranslateInputBoxVisual()
                updateSuggestions()
                updateAutoCapitalization()
                return
            }

            val now = System.currentTimeMillis()
            val textBefore = currentInputConnection?.getTextBeforeCursor(2, 0)?.toString()
            if (prefs.isDoubleSpacePeriodEnabled && (now - lastSpacePressTime < 500) && composingWord.isEmpty() && textBefore != null && textBefore.endsWith(" ") && !textBefore.endsWith(". ")) {
                currentInputConnection?.deleteSurroundingText(1, 0)
                currentInputConnection?.commitText(". ", 1)
                lastSpacePressTime = 0
                updateSuggestions()
                updateAutoCapitalization()
                return
            }

            lastSpacePressTime = now
            currentInputConnection?.commitText(" ", 1)
            composingWord.clear()
            updateSuggestions()
            updateAutoCapitalization()
        } catch (_: Throwable) {}
    }

    // Dynamic Enter / Return Key handling (Search, WhatsApp new line, Send support)
    private fun handleReturn() {
        try {
            val (activeWord, _) = getTypingContext()
            if (activeWord.length >= 2) {
                correctionEngine.recordWordUsage(activeWord, 1)
            }

            if (isTranslatePanelOpen && translatePanelBuffer.isNotBlank()) {
                sendTranslatedSentence()
                return
            }

            val ic = currentInputConnection ?: return
            val info = currentInputEditorInfo

            // Commit/finish any pending composing text so target field has complete input
            ic.finishComposingText()
            composingWord.clear()

            if (info == null) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                updateSuggestions()
                updateAutoCapitalization()
                return
            }

            if (isTerminalOrRawInput()) {
                // Termux / Terminal PTY: Always send standard hardware enter key
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                updateSuggestions()
                updateAutoCapitalization()
                return
            }

            val imeAction = info.imeOptions and EditorInfo.IME_MASK_ACTION
            val variation = info.inputType and InputType.TYPE_MASK_VARIATION
            val isMultiLine = (info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
            val hasNoEnterAction = (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0

            val isSearchAction = (imeAction == EditorInfo.IME_ACTION_SEARCH) ||
                    (variation == InputType.TYPE_TEXT_VARIATION_FILTER) ||
                    (info.actionId == EditorInfo.IME_ACTION_SEARCH)

            if (isSearchAction) {
                // Play Store search or filter search field: Execute Search Action
                val handled = ic.performEditorAction(EditorInfo.IME_ACTION_SEARCH)
                if (!handled) {
                    if (!sendDefaultEditorAction(false)) {
                        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                    }
                }
            } else if (hasNoEnterAction) {
                // App explicitly requested NO action on enter key (e.g. WhatsApp with Enter is send OFF)
                if (isMultiLine) {
                    ic.commitText("\n", 1)
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                }
            } else if (isMultiLine) {
                // Multiline chat fields (WhatsApp, Telegram, Notes, Messages):
                // If user enabled "Enter is send" in WhatsApp:
                if (imeAction == EditorInfo.IME_ACTION_SEND) {
                    val handled = ic.performEditorAction(EditorInfo.IME_ACTION_SEND)
                    if (!handled) {
                        ic.commitText("\n", 1)
                    }
                } else {
                    // Always insert a clean newline in multiline fields!
                    ic.commitText("\n", 1)
                }
            } else {
                // Single-line inputs: execute editor actions
                val targetAction = when {
                    imeAction == EditorInfo.IME_ACTION_GO || variation == InputType.TYPE_TEXT_VARIATION_URI -> EditorInfo.IME_ACTION_GO
                    imeAction == EditorInfo.IME_ACTION_SEND -> EditorInfo.IME_ACTION_SEND
                    imeAction == EditorInfo.IME_ACTION_NEXT -> EditorInfo.IME_ACTION_NEXT
                    imeAction == EditorInfo.IME_ACTION_DONE -> EditorInfo.IME_ACTION_DONE
                    info.actionId != 0 -> info.actionId
                    imeAction != EditorInfo.IME_ACTION_NONE && imeAction != EditorInfo.IME_ACTION_UNSPECIFIED -> imeAction
                    else -> EditorInfo.IME_ACTION_NONE
                }

                if (targetAction != EditorInfo.IME_ACTION_NONE) {
                    val handled = ic.performEditorAction(targetAction)
                    if (!handled) {
                        if (!sendDefaultEditorAction(false)) {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                        }
                    }
                } else {
                    if (!sendDefaultEditorAction(true)) {
                        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                    }
                }
            }

            updateSuggestions()
            updateAutoCapitalization()
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

    private fun sendStickerToApp(stickerFile: java.io.File) {
        try {
            audioHaptic.playClickSound()
            audioHaptic.triggerHapticFeedback()

            val success = stickerManager.sendSticker(
                currentInputConnection,
                currentInputEditorInfo,
                stickerFile
            )

            if (!success) {
                android.widget.Toast.makeText(
                    this,
                    "Aplikasi ini tidak mendukung stiker langsung",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } catch (_: Throwable) {}
    }

    private fun openStickerUploadScreen() {
        try {
            audioHaptic.playClickSound()
            audioHaptic.triggerHapticFeedback()
            val intent = Intent(this, com.maoneart.ioskeyboard.sticker.StickerUploadActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (_: Throwable) {}
    }

    private fun commitDirectText(text: String) {
        try {
            if (isTranslatePanelOpen) {
                val pos = translateCursorPosition.coerceIn(0, translatePanelBuffer.length)
                translatePanelBuffer.insert(pos, text)
                translateCursorPosition = pos + text.length
                updateTranslateInputBoxVisual()
                updateSuggestions()
            } else {
                currentInputConnection?.commitText(text, 1)
                composingWord.clear()
                updateSuggestions()
            }
        } catch (_: Throwable) {}
    }

    /**
     * Context extraction for Predictive Typing & Typo Correction:
     * Returns Pair(currentWordBeingTyped, lastFinishedWordBeforeSpace)
     */
    private fun getTypingContext(): Pair<String, String> {
        try {
            if (isTranslatePanelOpen) {
                val pos = translateCursorPosition.coerceIn(0, translatePanelBuffer.length)
                val textBefore = translatePanelBuffer.substring(0, pos)
                if (textBefore.isEmpty()) return Pair("", "")
                return extractWordsFromContext(textBefore)
            }

            if (isTerminalOrRawInput()) {
                return Pair(composingWord.toString(), "")
            }

            // Fast-path in-memory: while user is actively typing a word, return immediately without Binder IPC!
            if (composingWord.isNotEmpty()) {
                return Pair(composingWord.toString(), "")
            }

            val ic = currentInputConnection
            val chars = ic?.getTextBeforeCursor(60, 0)
            val textBefore = chars?.toString() ?: ""
            if (textBefore.isEmpty()) return Pair("", "")

            return extractWordsFromContext(textBefore)
        } catch (_: Throwable) {
            return Pair(composingWord.toString(), "")
        }
    }

    private fun isWordChar(c: Char): Boolean {
        return c.isLetterOrDigit() || c == '\'' || c == '-'
    }

    private fun extractWordsFromContext(textBefore: String): Pair<String, String> {
        if (textBefore.isEmpty()) return Pair("", "")

        // If cursor directly follows non-word character (e.g. whitespace or punctuation):
        if (!isWordChar(textBefore.last())) {
            val trimmed = textBefore.trimEnd { !isWordChar(it) }
            if (trimmed.isEmpty()) return Pair("", "")
            var i = trimmed.length - 1
            while (i >= 0 && isWordChar(trimmed[i])) {
                i--
            }
            val lastWord = trimmed.substring(i + 1)
            return Pair("", lastWord)
        }

        // Otherwise, cursor is at the end of a word being typed or backspaced:
        var i = textBefore.length - 1
        while (i >= 0 && isWordChar(textBefore[i])) {
            i--
        }
        val currentWord = textBefore.substring(i + 1)

        // Also find previous word before this current word
        val priorText = textBefore.substring(0, (i + 1).coerceAtLeast(0)).trimEnd { !isWordChar(it) }
        var j = priorText.length - 1
        while (j >= 0 && isWordChar(priorText[j])) {
            j--
        }
        val previousWord = if (priorText.isNotEmpty()) priorText.substring(j + 1) else ""

        return Pair(currentWord, previousWord)
    }

    /**
     * Updates QuickType Candidate Bar:
     * - In-word typing -> Typo correction, Slang expansion, Prefix completions (debounced 20ms)
     * - Space boundary / backspace -> Next-Word Prediction / Instant Update
     */
    private fun updateSuggestions(immediate: Boolean = true) {
        if (!immediate) {
            suggestionRunnable?.let { suggestionHandler.removeCallbacks(it) }
            val r = Runnable { executeSuggestionUpdate() }
            suggestionRunnable = r
            suggestionHandler.postDelayed(r, 20L)
            return
        }
        suggestionRunnable?.let { suggestionHandler.removeCallbacks(it) }
        executeSuggestionUpdate()
    }

    private fun executeSuggestionUpdate() {
        try {
            if (isTerminalOrRawInput()) {
                candidate1?.text = ""
                candidate2?.text = ""
                candidate3?.text = ""
                return
            }

            if (!prefs.isPredictiveTextEnabled) {
                candidate1?.text = ""
                candidate2?.text = ""
                candidate3?.text = ""
                return
            }

            val (currentWord, previousWord) = getTypingContext()

            val nextChars = if (currentWord.isNotEmpty()) {
                correctionEngine.predictNextCharacters(currentWord, prefs.activeLanguage)
            } else emptySet()
            layoutEngine.updatePredictedNextChars(nextChars)

            // 0A. Instant In-Keyboard Math Calculator (e.g. "25000*4" -> "🧮 = 100.000")
            val mathExprCandidate = if (prefs.isCalculatorEnabled) {
                try {
                    val rawContext = if (isTranslatePanelOpen) {
                        val pos = translateCursorPosition.coerceIn(0, translatePanelBuffer.length)
                        translatePanelBuffer.substring(0, pos)
                    } else {
                        currentInputConnection?.getTextBeforeCursor(40, 0)?.toString() ?: currentWord
                    }
                    evaluateInlineMath(rawContext)
                } catch (_: Throwable) { null }
            } else null

            if (mathExprCandidate != null) {
                candidate1?.text = "🧮 = ${mathExprCandidate.second}"
                candidate2?.text = "🧮 = ${mathExprCandidate.first}"
                candidate3?.text = currentWord
                return
            }

            // 0B. iOS 18 Smart Paste / OTP Autofill Pill when input is empty or at word boundary & fresh clip exists
            val freshClip = if (currentWord.isEmpty()) clipboardManager.getRecentFreshClip() else null
            if (!freshClip.isNullOrEmpty()) {
                val cleanSingleLine = freshClip.replace("\n", " ").trim()
                val shortLabel = if (cleanSingleLine.length > 18) cleanSingleLine.take(18) + "…" else cleanSingleLine
                val nextSuggs = if (previousWord.isNotEmpty()) {
                    correctionEngine.getNextWordPredictions(previousWord, prefs.activeLanguage)
                } else {
                    correctionEngine.getSuggestions("", prefs.activeLanguage)
                }
                candidate1?.text = nextSuggs.getOrNull(0) ?: ""
                candidate2?.text = "📋 $shortLabel"
                candidate3?.text = nextSuggs.getOrNull(1) ?: ""
                return
            }

            val suggestions = if (currentWord.isNotEmpty()) {
                // Periksa apakah kata yang sedang diketik cocok dengan singkatan Text Replacement
                val shortcutMatch = textReplacementManager.getReplacement(currentWord)
                val rawSuggs = correctionEngine.getSuggestions(currentWord, prefs.activeLanguage)
                if (shortcutMatch != null) {
                    val list = mutableListOf<String>()
                    list.add("⚡ $shortcutMatch")
                    list.addAll(rawSuggs.filter { !it.equals(shortcutMatch, ignoreCase = true) })
                    list
                } else {
                    rawSuggs
                }
            } else if (previousWord.isNotEmpty()) {
                // User pressed space: continuous predictive typing ("Nulis Otomatis")
                correctionEngine.getNextWordPredictions(previousWord, prefs.activeLanguage)
            } else {
                correctionEngine.getSuggestions("", prefs.activeLanguage)
            }

            if (suggestions.isEmpty()) {
                candidate1?.text = ""
                candidate2?.text = ""
                candidate3?.text = ""
                return
            }

            val refWord = if (currentWord.isNotEmpty()) currentWord else ""
            val shouldCapNext = currentWord.isEmpty() && shouldAutoCapitalize()

            if (suggestions.size == 1) {
                candidate1?.text = ""
                candidate2?.text = formatCandidateCase(suggestions[0], refWord, shouldCapNext)
                candidate3?.text = ""
            } else if (suggestions.size == 2) {
                candidate1?.text = formatCandidateCase(suggestions[1], refWord, shouldCapNext)
                candidate2?.text = formatCandidateCase(suggestions[0], refWord, shouldCapNext)
                candidate3?.text = ""
            } else {
                candidate1?.text = formatCandidateCase(suggestions[1], refWord, shouldCapNext)
                candidate2?.text = formatCandidateCase(suggestions[0], refWord, shouldCapNext)
                candidate3?.text = formatCandidateCase(suggestions[2], refWord, shouldCapNext)
            }
        } catch (_: Throwable) {}
    }

    private fun formatCandidateCase(candidate: String, original: String, forceCapitalizeFirst: Boolean = false): String {
        if (candidate.isEmpty()) return candidate
        if (candidate.startsWith("⚡ ")) {
            val phrase = candidate.removePrefix("⚡ ").trim()
            val formatted = if (forceCapitalizeFirst || (original.isNotEmpty() && original[0].isUpperCase())) {
                phrase.replaceFirstChar { it.uppercase() }
            } else {
                phrase
            }
            return "⚡ $formatted"
        }
        if (candidate.startsWith("✦ ")) return candidate
        if (original.isNotEmpty() && original.all { it.isUpperCase() } && original.length > 1) {
            return candidate.uppercase()
        }
        if (forceCapitalizeFirst || (original.isNotEmpty() && original[0].isUpperCase())) {
            return candidate.replaceFirstChar { it.uppercase() }
        }
        return candidate
    }

    private fun selectCandidate(candidate: String?) {
        try {
            if (candidate.isNullOrBlank()) return
            audioHaptic.playClickSound()
            audioHaptic.triggerHapticFeedback()

            var cleanCandidate = candidate.trim()

            // Handle iOS 18 Smart Paste Pill ("📋 ...")
            if (cleanCandidate.startsWith("📋 ")) {
                val fullClip = clipboardManager.getRecentFreshClip()
                    ?: clipboardManager.getItems().firstOrNull()?.text
                    ?: cleanCandidate.removePrefix("📋 ").trim()
                clipboardManager.consumeFreshClip()
                commitDirectText(fullClip)
                return
            }

            // Handle Instant Math Calculator Result ("🧮 = ...")
            if (cleanCandidate.startsWith("🧮 = ")) {
                val resultValue = cleanCandidate.removePrefix("🧮 = ").trim()
                val ic = currentInputConnection
                if (isTranslatePanelOpen) {
                    val pos = translateCursorPosition.coerceIn(0, translatePanelBuffer.length)
                    val before = translatePanelBuffer.substring(0, pos)
                    val eqSuffix = if (before.trimEnd().endsWith("=")) "" else " = "
                    val insertStr = if (before.trimEnd().endsWith("=")) " $resultValue" else "$eqSuffix$resultValue"
                    translatePanelBuffer.insert(pos, insertStr)
                    translateCursorPosition = pos + insertStr.length
                    updateTranslateInputBoxVisual()
                } else if (ic != null) {
                    val before = ic.getTextBeforeCursor(40, 0)?.toString() ?: ""
                    val insertStr = if (before.trimEnd().endsWith("=")) " $resultValue " else " = $resultValue "
                    ic.commitText(insertStr, 1)
                }
                composingWord.clear()
                updateSuggestions()
                return
            }

            if (cleanCandidate.startsWith("⚡ ")) {
                cleanCandidate = cleanCandidate.removePrefix("⚡ ").trim()
            }
            if (cleanCandidate.startsWith("✦ ")) {
                cleanCandidate = cleanCandidate.removePrefix("✦ ").trim()
            }

            val ic = currentInputConnection

            // Dynamic Self-Learning: Catat kata yang dipilih pengguna dengan prioritas frekuensi tinggi!
            correctionEngine.recordWordUsage(cleanCandidate, 2)

            if (isTranslatePanelOpen) {
                val (currentWord, previousWord) = getTypingContext()
                val pos = translateCursorPosition.coerceIn(0, translatePanelBuffer.length)
                val textBefore = if (pos > 0) translatePanelBuffer.substring(0, pos) else ""

                var lengthToDelete = currentWord.length
                if (lengthToDelete == 0 && textBefore.isNotEmpty() && textBefore.last().isWhitespace() && previousWord.isNotEmpty()) {
                    if (cleanCandidate.startsWith(previousWord, ignoreCase = true) ||
                        correctionEngine.isCorrectionMatch(previousWord, cleanCandidate, prefs.activeLanguage)) {
                        var wsCount = 0
                        var k = textBefore.length - 1
                        while (k >= 0 && textBefore[k].isWhitespace()) {
                            wsCount++
                            k--
                        }
                        lengthToDelete = previousWord.length + wsCount
                    }
                }

                if (lengthToDelete > 0 && pos >= lengthToDelete) {
                    translatePanelBuffer.delete(pos - lengthToDelete, pos)
                    translateCursorPosition = pos - lengthToDelete
                }
                translatePanelBuffer.insert(translateCursorPosition, "$cleanCandidate ")
                translateCursorPosition += cleanCandidate.length + 1
                updateTranslateInputBoxVisual()
            } else if (isTerminalOrRawInput()) {
                // In Termux PTY, deleteSurroundingText() is ignored by the terminal buffer.
                // We MUST use KEYCODE_DEL key events, and rely on composingWord or cursor context.
                val textBefore = ic?.getTextBeforeCursor(64, 0)?.toString() ?: ""
                val (currentWord, previousWord) = extractWordsFromContext(textBefore)
                var lengthToDelete = if (currentWord.isNotEmpty()) currentWord.length else composingWord.length

                if (lengthToDelete == 0 && textBefore.isNotEmpty() && textBefore.last().isWhitespace() && previousWord.isNotEmpty()) {
                    if (cleanCandidate.startsWith(previousWord, ignoreCase = true) ||
                        correctionEngine.isCorrectionMatch(previousWord, cleanCandidate, prefs.activeLanguage)) {
                        var wsCount = 0
                        var k = textBefore.length - 1
                        while (k >= 0 && textBefore[k].isWhitespace()) {
                            wsCount++
                            k--
                        }
                        lengthToDelete = previousWord.length + wsCount
                    }
                }

                if (lengthToDelete > 0 && ic != null) {
                    repeat(lengthToDelete) {
                        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                    }
                }
                ic?.commitText("$cleanCandidate ", 1)
            } else {
                // In normal apps (WhatsApp, Chrome, Notes, Instagram, WebViews, etc.):
                val textBefore = ic?.getTextBeforeCursor(64, 0)?.toString() ?: ""
                val (currentWord, previousWord) = extractWordsFromContext(textBefore)

                var lengthToDelete = currentWord.length
                if (lengthToDelete == 0 && textBefore.isNotEmpty() && textBefore.last().isWhitespace() && previousWord.isNotEmpty()) {
                    if (cleanCandidate.startsWith(previousWord, ignoreCase = true) ||
                        correctionEngine.isCorrectionMatch(previousWord, cleanCandidate, prefs.activeLanguage)) {
                        var wsCount = 0
                        var k = textBefore.length - 1
                        while (k >= 0 && textBefore[k].isWhitespace()) {
                            wsCount++
                            k--
                        }
                        lengthToDelete = previousWord.length + wsCount
                    }
                }

                if (lengthToDelete == 0 && composingWord.isNotEmpty()) {
                    lengthToDelete = composingWord.length
                }

                if (ic != null) {
                    ic.beginBatchEdit()
                    try {
                        if (lengthToDelete > 0) {
                            var deleted = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                try {
                                    deleted = ic.deleteSurroundingTextInCodePoints(lengthToDelete, 0)
                                } catch (_: Throwable) {}
                            }
                            if (!deleted) {
                                try {
                                    deleted = ic.deleteSurroundingText(lengthToDelete, 0)
                                } catch (_: Throwable) {}
                            }
                            // Fallback for apps/custom text views that silently ignore deleteSurroundingText:
                            if (!deleted) {
                                repeat(lengthToDelete) {
                                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                                }
                            }
                        }
                        ic.commitText("$cleanCandidate ", 1)
                    } finally {
                        ic.endBatchEdit()
                    }
                }
            }

            composingWord.clear()
            // Immediately chain to the next word prediction! ("Nulis Otomatis")
            updateSuggestions()
            updateAutoCapitalization()
        } catch (_: Throwable) {}
    }

    private fun handleSwipeLiveUpdate(swipedChars: List<Char>) {
        try {
            if (swipedChars.size < 2) return
            val rawMatched = quickPathEngine.matchSwipe(swipedChars, prefs.activeLanguage) ?: swipedChars.joinToString("")
            val shouldCapitalize = (isCapsLock || (composingWord.isNotEmpty() && composingWord.first().isUpperCase()))
            val displayWord = if (shouldCapitalize) rawMatched.replaceFirstChar { it.uppercase() } else rawMatched
            candidate2?.text = "✦ $displayWord"
            candidate1?.text = ""
            candidate3?.text = ""
        } catch (_: Throwable) {}
    }

    private fun handleSwipeComplete(swipedChars: List<Char>) {
        try {
            if (swipedChars.size < 2) return
            audioHaptic.playClickSound()
            audioHaptic.triggerHapticFeedback()

            val shouldCapitalize = (isCapsLock || (composingWord.isNotEmpty() && composingWord.first().isUpperCase()))

            // Remove the 1 initial char that was committed on ACTION_DOWN
            if (isTranslatePanelOpen) {
                if (translatePanelBuffer.isNotEmpty() && translateCursorPosition > 0) {
                    translatePanelBuffer.deleteCharAt(translateCursorPosition - 1)
                    translateCursorPosition--
                    updateTranslateInputBoxVisual()
                }
            } else if (isTerminalOrRawInput()) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            } else {
                val delOk = try { currentInputConnection?.deleteSurroundingText(1, 0) ?: false } catch (_: Throwable) { false }
                if (!delOk) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                }
            }
            if (composingWord.isNotEmpty()) {
                composingWord.deleteCharAt(composingWord.length - 1)
            }

            val rawMatched = quickPathEngine.matchSwipe(swipedChars, prefs.activeLanguage)
            val matchedWord = if (rawMatched != null && shouldCapitalize) {
                rawMatched.replaceFirstChar { it.uppercase() }
            } else {
                rawMatched
            }

            if (matchedWord != null) {
                if (isTranslatePanelOpen) {
                    val pos = translateCursorPosition.coerceIn(0, translatePanelBuffer.length)
                    translatePanelBuffer.insert(pos, "$matchedWord ")
                    translateCursorPosition = pos + matchedWord.length + 1
                    updateTranslateInputBoxVisual()
                } else {
                    currentInputConnection?.commitText("$matchedWord ", 1)
                }
                correctionEngine.recordWordUsage(rawMatched ?: matchedWord, 2)
                composingWord.clear()
                updateSuggestions()
                updateAutoCapitalization()
            } else {
                val rawFallback = swipedChars.joinToString("")
                val fallback = if (shouldCapitalize) rawFallback.replaceFirstChar { it.uppercase() } else rawFallback
                if (isTranslatePanelOpen) {
                    val pos = translateCursorPosition.coerceIn(0, translatePanelBuffer.length)
                    translatePanelBuffer.insert(pos, "$fallback ")
                    translateCursorPosition = pos + fallback.length + 1
                    updateTranslateInputBoxVisual()
                } else {
                    currentInputConnection?.commitText("$fallback ", 1)
                }
                composingWord.clear()
                updateSuggestions()
                updateAutoCapitalization()
            }
        } catch (_: Throwable) {}
    }

    /**
     * Evaluates simple inline math expressions at the end of the text before cursor
     * (e.g., "25000*4", "150000/3=", "12500+7500") and returns Pair(formattedIdr, rawString).
     */
    private fun evaluateInlineMath(textBefore: String): Pair<String, String>? {
        if (textBefore.isBlank()) return null
        val trimmed = textBefore.trimEnd()
        val match = Regex("""([0-9]+(?:\.[0-9]+)?(?:\s*[\+\-\*\/xX×÷]\s*[0-9]+(?:\.[0-9]+)?)+)\s*=?\s*$""").find(trimmed)
            ?: return null
        val expr = match.groupValues[1]
            .replace(" ", "")
            .replace("x", "*")
            .replace("X", "*")
            .replace("×", "*")
            .replace("÷", "/")

        val tokens = Regex("""([0-9]+(?:\.[0-9]+)?|[\+\-\*\/])""").findAll(expr).map { it.value }.toList()
        if (tokens.size < 3 || tokens.size % 2 == 0) return null

        // First pass: * and /
        val nums = mutableListOf<Double>()
        val ops = mutableListOf<String>()
        var current = tokens[0].toDoubleOrNull() ?: return null
        var i = 1
        while (i < tokens.size) {
            val op = tokens[i]
            val next = tokens[i + 1].toDoubleOrNull() ?: return null
            when (op) {
                "*" -> current *= next
                "/" -> {
                    if (next == 0.0) return null
                    current /= next
                }
                "+", "-" -> {
                    nums.add(current)
                    ops.add(op)
                    current = next
                }
                else -> return null
            }
            i += 2
        }
        nums.add(current)

        // Second pass: + and -
        var total = nums[0]
        for (j in ops.indices) {
            when (ops[j]) {
                "+" -> total += nums[j + 1]
                "-" -> total -= nums[j + 1]
            }
        }
        if (total.isNaN() || total.isInfinite()) return null

        val isWhole = (total % 1.0 == 0.0)
        val rawStr = if (isWhole) total.toLong().toString() else String.format(java.util.Locale.US, "%.2f", total).trimEnd('0').trimEnd('.')
        val formattedStr = if (isWhole) {
            java.text.NumberFormat.getNumberInstance(java.util.Locale("id", "ID")).format(total.toLong())
        } else {
            rawStr
        }
        return Pair(formattedStr, rawStr)
    }

    override fun onDestroy() {
        try {
            suggestionHandler.removeCallbacksAndMessages(null)
            clipboardManager.destroy()
            voiceDictationManager.destroy()
            audioHaptic.destroy()
            correctionEngine.destroy()
            serviceScope.cancel()
        } catch (_: Throwable) {}
        super.onDestroy()
    }
}
