package com.maoneart.ioskeyboard.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.maoneart.ioskeyboard.R
import com.maoneart.ioskeyboard.preferences.KeyboardPreferences

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: KeyboardPreferences

    private lateinit var cardEnableIme: LinearLayout
    private lateinit var ivEnableStatus: ImageView
    private lateinit var tvEnableTitle: TextView
    private lateinit var tvEnableDesc: TextView
    private lateinit var tvEnableBadge: TextView

    private lateinit var cardSelectIme: LinearLayout
    private lateinit var ivSelectStatus: ImageView
    private lateinit var tvSelectTitle: TextView
    private lateinit var tvSelectDesc: TextView
    private lateinit var tvSelectBadge: TextView

    private lateinit var tvCurrentTheme: TextView
    private lateinit var tvSoundVolume: TextView
    private lateinit var seekBarSoundVolume: SeekBar
    private lateinit var tvHapticStrength: TextView
    private lateinit var seekBarHapticStrength: SeekBar
    private lateinit var tvActiveLanguage: TextView
    private lateinit var tvTranslatePair: TextView
    private lateinit var etTestInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = KeyboardPreferences(this)

        initViews()
        bindData()
    }

    override fun onResume() {
        super.onResume()
        updateSetupCards()
    }

    private fun initViews() {
        cardEnableIme = findViewById(R.id.cardEnableIme)
        ivEnableStatus = findViewById(R.id.ivEnableStatus)
        tvEnableTitle = findViewById(R.id.tvEnableTitle)
        tvEnableDesc = findViewById(R.id.tvEnableDesc)
        tvEnableBadge = findViewById(R.id.tvEnableBadge)

        cardSelectIme = findViewById(R.id.cardSelectIme)
        ivSelectStatus = findViewById(R.id.ivSelectStatus)
        tvSelectTitle = findViewById(R.id.tvSelectTitle)
        tvSelectDesc = findViewById(R.id.tvSelectDesc)
        tvSelectBadge = findViewById(R.id.tvSelectBadge)

        val rowTheme = findViewById<LinearLayout>(R.id.rowTheme)
        tvCurrentTheme = findViewById(R.id.tvCurrentTheme)
        val switchKeyPopup = findViewById<SwitchMaterial>(R.id.switchKeyPopup)

        val switchSound = findViewById<SwitchMaterial>(R.id.switchSound)
        tvSoundVolume = findViewById(R.id.tvSoundVolume)
        seekBarSoundVolume = findViewById(R.id.seekBarSoundVolume)

        val switchHaptic = findViewById<SwitchMaterial>(R.id.switchHaptic)
        tvHapticStrength = findViewById(R.id.tvHapticStrength)
        seekBarHapticStrength = findViewById(R.id.seekBarHapticStrength)

        val rowLanguage = findViewById<LinearLayout>(R.id.rowLanguage)
        tvActiveLanguage = findViewById(R.id.tvActiveLanguage)
        val switchAutoCorrect = findViewById<SwitchMaterial>(R.id.switchAutoCorrect)
        val switchDoubleSpace = findViewById<SwitchMaterial>(R.id.switchDoubleSpace)
        val rowDownloadLanguages = findViewById<LinearLayout>(R.id.rowDownloadLanguages)

        val switchLiveTranslate = findViewById<SwitchMaterial>(R.id.switchLiveTranslate)
        val rowTranslatePair = findViewById<LinearLayout>(R.id.rowTranslatePair)
        tvTranslatePair = findViewById(R.id.tvTranslatePair)

        etTestInput = findViewById(R.id.etTestInput)
        val btnClearTest = findViewById<TextView>(R.id.btnClearTest)

        // Setup Card Actions
        cardEnableIme.setOnClickListener {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        }

        cardSelectIme.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
        }

        rowTheme.setOnClickListener {
            showThemeDialog()
        }

        switchKeyPopup.setOnCheckedChangeListener { _, isChecked ->
            prefs.isKeyPopupEnabled = isChecked
        }

        switchSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.isSoundEnabled = isChecked
        }

        seekBarSoundVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    prefs.soundVolume = progress / 100f
                    tvSoundVolume.text = "$progress%"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                try {
                    val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    am?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, prefs.soundVolume)
                } catch (_: Throwable) {}
            }
        })

        switchHaptic.setOnCheckedChangeListener { _, isChecked ->
            prefs.isHapticEnabled = isChecked
        }

        seekBarHapticStrength.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val ms = progress.coerceAtLeast(5)
                    prefs.hapticStrength = ms
                    tvHapticStrength.text = "$ms ms"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                try {
                    val ms = prefs.hapticStrength.toLong()
                    @Suppress("DEPRECATION")
                    val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val amp = ((ms.toFloat() / 100f) * 255).toInt().coerceIn(90, 255)
                        vib?.vibrate(VibrationEffect.createOneShot(ms, amp))
                    } else {
                        vib?.vibrate(ms)
                    }
                } catch (_: Throwable) {}
            }
        })

        rowLanguage.setOnClickListener {
            showLanguageDialog()
        }

        findViewById<SwitchMaterial>(R.id.switchAutoCapitalization)?.setOnCheckedChangeListener { _, isChecked ->
            prefs.isAutoCapitalizationEnabled = isChecked
        }

        switchAutoCorrect.setOnCheckedChangeListener { _, isChecked ->
            prefs.isAutoCorrectEnabled = isChecked
        }

        switchDoubleSpace.setOnCheckedChangeListener { _, isChecked ->
            prefs.isDoubleSpacePeriodEnabled = isChecked
        }

        rowDownloadLanguages.setOnClickListener {
            startActivity(Intent(this, LanguageDownloadActivity::class.java))
        }

        findViewById<SwitchMaterial>(R.id.switchPredictiveText)?.setOnCheckedChangeListener { _, isChecked ->
            prefs.isPredictiveTextEnabled = isChecked
        }

        findViewById<LinearLayout>(R.id.rowTextReplacement)?.setOnClickListener {
            showTextReplacementDialog()
        }

        findViewById<SwitchMaterial>(R.id.switchSwipeTyping)?.setOnCheckedChangeListener { _, isChecked ->
            prefs.isSwipeTypingEnabled = isChecked
        }

        findViewById<SwitchMaterial>(R.id.switchSpaceTrackpad)?.setOnCheckedChangeListener { _, isChecked ->
            prefs.isSpaceTrackpadEnabled = isChecked
        }

        findViewById<SwitchMaterial>(R.id.switchDictation)?.setOnCheckedChangeListener { _, isChecked ->
            prefs.isDictationEnabled = isChecked
        }

        switchLiveTranslate.setOnCheckedChangeListener { _, isChecked ->
            prefs.isLiveTranslateEnabled = isChecked
        }

        rowTranslatePair.setOnClickListener {
            showTranslatePairDialog()
        }

        findViewById<LinearLayout>(R.id.rowAddSticker)?.setOnClickListener {
            startActivity(Intent(this, com.maoneart.ioskeyboard.sticker.StickerUploadActivity::class.java))
        }

        btnClearTest.setOnClickListener {
            etTestInput.setText("")
        }
    }

    private fun bindData() {
        updateThemeLabel()
        updateSoundVolumeLabel()
        updateHapticStrengthLabel()
        updateLanguageLabel()
        updateTranslatePairLabel()
        updateSetupCards()

        findViewById<SwitchMaterial>(R.id.switchKeyPopup).isChecked = prefs.isKeyPopupEnabled
        findViewById<SwitchMaterial>(R.id.switchSound).isChecked = prefs.isSoundEnabled
        findViewById<SwitchMaterial>(R.id.switchHaptic).isChecked = prefs.isHapticEnabled
        findViewById<SwitchMaterial>(R.id.switchAutoCapitalization)?.isChecked = prefs.isAutoCapitalizationEnabled
        findViewById<SwitchMaterial>(R.id.switchAutoCorrect).isChecked = prefs.isAutoCorrectEnabled
        findViewById<SwitchMaterial>(R.id.switchDoubleSpace).isChecked = prefs.isDoubleSpacePeriodEnabled
        findViewById<SwitchMaterial>(R.id.switchPredictiveText)?.isChecked = prefs.isPredictiveTextEnabled
        findViewById<SwitchMaterial>(R.id.switchSwipeTyping)?.isChecked = prefs.isSwipeTypingEnabled
        findViewById<SwitchMaterial>(R.id.switchSpaceTrackpad)?.isChecked = prefs.isSpaceTrackpadEnabled
        findViewById<SwitchMaterial>(R.id.switchDictation)?.isChecked = prefs.isDictationEnabled
        findViewById<SwitchMaterial>(R.id.switchLiveTranslate).isChecked = prefs.isLiveTranslateEnabled
    }

    private fun isImeEnabled(): Boolean {
        return try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
            val enabledMethods = imm.enabledInputMethodList
            val myPackageName = packageName
            for (method in enabledMethods) {
                if (method.packageName == myPackageName) {
                    return true
                }
            }
            false
        } catch (_: Throwable) {
            false
        }
    }

    private fun isImeSelected(): Boolean {
        return try {
            val currentIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            currentIme != null && currentIme.contains(packageName)
        } catch (_: Throwable) {
            false
        }
    }

    private fun updateSetupCards() {
        val enabled = isImeEnabled()
        val selected = isImeSelected()

        if (enabled) {
            cardEnableIme.background = ContextCompat.getDrawable(this, R.drawable.card_bg_setup_active)
            ivEnableStatus.setImageResource(R.drawable.ic_settings_check_circle)
            tvEnableTitle.text = "1. Keyboard Telah Aktif"
            tvEnableDesc.text = "Telah diizinkan di pengaturan input Android"
            tvEnableBadge.text = "Aktif ✓"
            tvEnableBadge.setTextColor(ContextCompat.getColor(this, R.color.ios_green))
        } else {
            cardEnableIme.background = ContextCompat.getDrawable(this, R.drawable.card_bg_ios)
            ivEnableStatus.setImageResource(R.drawable.ic_chevron_right)
            tvEnableTitle.text = "1. Aktifkan di Pengaturan Sistem"
            tvEnableDesc.text = "Ketuk untuk mengaktifkan IME di sistem Android"
            tvEnableBadge.text = "Ketuk"
            tvEnableBadge.setTextColor(ContextCompat.getColor(this, R.color.ios_blue))
        }

        if (selected) {
            cardSelectIme.background = ContextCompat.getDrawable(this, R.drawable.card_bg_setup_active)
            ivSelectStatus.setImageResource(R.drawable.ic_settings_check_circle)
            tvSelectTitle.text = "2. Keyboard Utama Terpilih"
            tvSelectDesc.text = "Sedang aktif sebagai keyboard default saat ini"
            tvSelectBadge.text = "Default ✓"
            tvSelectBadge.setTextColor(ContextCompat.getColor(this, R.color.ios_green))
        } else {
            cardSelectIme.background = ContextCompat.getDrawable(this, R.drawable.card_bg_ios)
            ivSelectStatus.setImageResource(R.drawable.ic_chevron_right)
            tvSelectTitle.text = "2. Pilih sebagai Keyboard Utama"
            tvSelectDesc.text = "Ketuk untuk menjadikan default keyboard Anda"
            tvSelectBadge.text = "Pilih"
            tvSelectBadge.setTextColor(ContextCompat.getColor(this, R.color.ios_blue))
        }
    }

    private fun updateThemeLabel() {
        tvCurrentTheme.text = when (prefs.themeMode) {
            "dark" -> "iOS Dark"
            "light" -> "iOS Light"
            "woody" -> "Woody (Sony Xperia) 🪵"
            else -> "Otomatis"
        }
    }

    private fun updateSoundVolumeLabel() {
        val pct = (prefs.soundVolume * 100).toInt().coerceIn(0, 100)
        tvSoundVolume.text = "$pct%"
        seekBarSoundVolume.progress = pct
    }

    private fun updateHapticStrengthLabel() {
        val ms = prefs.hapticStrength.coerceIn(5, 100)
        tvHapticStrength.text = "$ms ms"
        seekBarHapticStrength.progress = ms
    }

    private fun updateLanguageLabel() {
        tvActiveLanguage.text = when (prefs.activeLanguage) {
            "en" -> "English"
            else -> "Indonesia"
        }
    }

    private fun updateTranslatePairLabel() {
        val src = prefs.translateSourceLang.uppercase()
        val tgt = prefs.translateTargetLang.uppercase()
        tvTranslatePair.text = "$src ➔ $tgt"
    }

    private fun showThemeDialog() {
        val options = arrayOf(
            "Otomatis (Ikuti Sistem)",
            "iOS Light (Terang)",
            "iOS Dark (Gelap)",
            "Woody (Sony Xperia Edition) 🪵"
        )
        val currentSelection = when (prefs.themeMode) {
            "light" -> 1
            "dark" -> 2
            "woody" -> 3
            else -> 0
        }

        AlertDialog.Builder(this)
            .setTitle("Pilih Tema Keyboard")
            .setSingleChoiceItems(options, currentSelection) { dialog, which ->
                prefs.themeMode = when (which) {
                    1 -> "light"
                    2 -> "dark"
                    3 -> "woody"
                    else -> "auto"
                }
                updateThemeLabel()
                dialog.dismiss()
                Toast.makeText(this, "Tema berhasil diperbarui", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showLanguageDialog() {
        val options = arrayOf("Bahasa Indonesia", "English")
        val currentSelection = if (prefs.activeLanguage == "en") 1 else 0

        AlertDialog.Builder(this)
            .setTitle("Pilih Bahasa Utama")
            .setSingleChoiceItems(options, currentSelection) { dialog, which ->
                prefs.activeLanguage = if (which == 1) "en" else "id"
                updateLanguageLabel()
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showTranslatePairDialog() {
        val pairs = arrayOf(
            "Indonesia ➔ English" to Pair("id", "en"),
            "English ➔ Indonesia" to Pair("en", "id"),
            "Indonesia ➔ Arab (العربية)" to Pair("id", "ar"),
            "Indonesia ➔ Jepang (日本語)" to Pair("id", "ja"),
            "Indonesia ➔ Korea (한국어)" to Pair("id", "ko"),
            "Indonesia ➔ Mandarin (中文)" to Pair("id", "zh")
        )
        val names = pairs.map { it.first }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Arah Bahasa Terjemahan")
            .setItems(names) { _, which ->
                val selected = pairs[which].second
                prefs.translateSourceLang = selected.first
                prefs.translateTargetLang = selected.second
                updateTranslatePairLabel()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun isDarkTheme(): Boolean {
        return when (prefs.themeMode) {
            "dark" -> true
            "woody" -> true
            "light" -> false
            else -> {
                val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun showTextReplacementDialog() {
        val manager = com.maoneart.ioskeyboard.shortcut.TextReplacementManager(this)
        val isDark = isDarkTheme()

        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }

        val cardBg = if (isDark) R.drawable.key_bg_special_dark else R.drawable.card_bg_ios
        val textColor = if (isDark) Color.WHITE else Color.BLACK
        val subColor = if (isDark) Color.parseColor("#98989D") else Color.parseColor("#8E8E93")
        val itemBg = if (isDark) R.drawable.key_bg_ios_dark else R.drawable.key_bg_special_light

        val rootCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@SettingsActivity, cardBg)
            val pad = dp(20)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Header Title
        val tvTitle = TextView(this).apply {
            text = "⚡ Penggantian Teks"
            textSize = 17.5f
            setTextColor(textColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        rootCard.addView(tvTitle)

        val tvSubtitle = TextView(this).apply {
            text = "Ketik singkatan untuk otomatis menjadi frasa lengkap"
            textSize = 12f
            setTextColor(subColor)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(4), 0, dp(14))
        }
        rootCard.addView(tvSubtitle)

        // Button: Tambah Pintasan Baru
        val btnAdd = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            ).apply { setMargins(0, 0, 0, dp(12)) }
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.key_bg_blue)
            text = "➕ Tambah Pintasan Baru"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isClickable = true
            setOnClickListener {
                dialog.dismiss()
                showAddShortcutDialog(manager)
            }
        }
        rootCard.addView(btnAdd)

        // List Scroll Container
        val scrollView = android.widget.ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(240)
            )
        }
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val shortcuts = manager.getAllReplacements().toSortedMap()
        if (shortcuts.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Belum ada pintasan teks khusus."
                setTextColor(subColor)
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setPadding(0, dp(30), 0, dp(30))
            }
            listContainer.addView(emptyTv)
        } else {
            for ((shortcut, phrase) in shortcuts) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    background = ContextCompat.getDrawable(this@SettingsActivity, itemBg)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, dp(8)) }
                    isClickable = true
                    setOnClickListener {
                        dialog.dismiss()
                        showManageShortcutDialog(manager, shortcut, phrase)
                    }
                }

                // Shortcut Badge
                val badge = TextView(this).apply {
                    text = shortcut
                    textSize = 13.5f
                    setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.ios_blue))
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setBackgroundColor(Color.TRANSPARENT)
                }
                row.addView(badge)

                val arrow = TextView(this).apply {
                    text = " ➔ "
                    textSize = 12f
                    setTextColor(subColor)
                }
                row.addView(arrow)

                val phraseTv = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                    text = phrase
                    textSize = 13.5f
                    setTextColor(textColor)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                row.addView(phraseTv)

                val deleteBtn = TextView(this).apply {
                    text = "✕"
                    textSize = 14f
                    setTextColor(subColor)
                    setPadding(dp(8), 0, dp(4), 0)
                }
                row.addView(deleteBtn)

                listContainer.addView(row)
            }
        }
        scrollView.addView(listContainer)
        rootCard.addView(scrollView)

        // Bottom Action Button: Tutup
        val btnClose = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            ).apply { setMargins(0, dp(12), 0, 0) }
            background = ContextCompat.getDrawable(this@SettingsActivity, itemBg)
            text = "Tutup"
            setTextColor(textColor)
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            isClickable = true
            setOnClickListener { dialog.dismiss() }
        }
        rootCard.addView(btnClose)

        dialog.setContentView(rootCard)
        dialog.show()
    }

    private fun showAddShortcutDialog(manager: com.maoneart.ioskeyboard.shortcut.TextReplacementManager) {
        val isDark = isDarkTheme()
        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }

        val cardBg = if (isDark) R.drawable.key_bg_special_dark else R.drawable.card_bg_ios
        val textColor = if (isDark) Color.WHITE else Color.BLACK
        val subColor = if (isDark) Color.parseColor("#98989D") else Color.parseColor("#8E8E93")
        val inputBg = if (isDark) R.drawable.key_bg_ios_dark else R.drawable.key_bg_special_light

        val rootCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@SettingsActivity, cardBg)
            val pad = dp(20)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val tvTitle = TextView(this).apply {
            text = "✏️ Tambah Pintasan Baru"
            textSize = 17f
            setTextColor(textColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        rootCard.addView(tvTitle)

        val tvSubtitle = TextView(this).apply {
            text = "Masukkan singkatan dan frasa pengganti"
            textSize = 12f
            setTextColor(subColor)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(4), 0, dp(16))
        }
        rootCard.addView(tvSubtitle)

        // Label 1: SINGKATAN
        val lbl1 = TextView(this).apply {
            text = "SINGKATAN (SHORTCUT)"
            textSize = 11f
            setTextColor(subColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(4), 0, 0, dp(4))
        }
        rootCard.addView(lbl1)

        val etShortcut = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).apply {
                setMargins(0, 0, 0, dp(12))
            }
            background = ContextCompat.getDrawable(this@SettingsActivity, inputBg)
            setPadding(dp(14), 0, dp(14), 0)
            hint = "Contoh: otw, gpp, slm"
            setHintTextColor(subColor)
            setTextColor(textColor)
            textSize = 14f
            setSingleLine(true)
        }
        rootCard.addView(etShortcut)

        // Label 2: FRASA LENGKAP
        val lbl2 = TextView(this).apply {
            text = "FRASA LENGKAP (PHRASE)"
            textSize = 11f
            setTextColor(subColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(4), 0, 0, dp(4))
        }
        rootCard.addView(lbl2)

        val etPhrase = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).apply {
                setMargins(0, 0, 0, dp(18))
            }
            background = ContextCompat.getDrawable(this@SettingsActivity, inputBg)
            setPadding(dp(14), 0, dp(14), 0)
            hint = "Contoh: On the way, Gak apa-apa"
            setHintTextColor(subColor)
            setTextColor(textColor)
            textSize = 14f
            setSingleLine(true)
        }
        rootCard.addView(etPhrase)

        // Symmetrical 2-Column Action Grid (MaoneArt Glassmorphism Standard)
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42))
        }

        // Button 1: Batal
        val btnCancel = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f).apply {
                setMargins(0, 0, dp(6), 0)
            }
            background = ContextCompat.getDrawable(this@SettingsActivity, inputBg)
            text = "Batal"
            setTextColor(textColor)
            textSize = 13.5f
            gravity = android.view.Gravity.CENTER
            isClickable = true
            setOnClickListener {
                dialog.dismiss()
                showTextReplacementDialog()
            }
        }
        btnRow.addView(btnCancel)

        // Button 2: Simpan
        val btnSave = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f).apply {
                setMargins(dp(6), 0, 0, 0)
            }
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.key_bg_blue)
            text = "Simpan"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            isClickable = true
            setOnClickListener {
                val s = etShortcut.text.toString().trim()
                val p = etPhrase.text.toString().trim()
                if (s.isNotEmpty() && p.isNotEmpty()) {
                    manager.addOrUpdate(s, p)
                    Toast.makeText(this@SettingsActivity, "Pintasan '$s' tersimpan", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    showTextReplacementDialog()
                } else {
                    Toast.makeText(this@SettingsActivity, "Harap isi singkatan dan frasa", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnRow.addView(btnSave)
        rootCard.addView(btnRow)

        dialog.setContentView(rootCard)
        dialog.show()
    }

    private fun showManageShortcutDialog(
        manager: com.maoneart.ioskeyboard.shortcut.TextReplacementManager,
        shortcut: String,
        phrase: String
    ) {
        val isDark = isDarkTheme()
        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }

        val cardBg = if (isDark) R.drawable.key_bg_special_dark else R.drawable.card_bg_ios
        val textColor = if (isDark) Color.WHITE else Color.BLACK
        val subColor = if (isDark) Color.parseColor("#98989D") else Color.parseColor("#8E8E93")
        val inputBg = if (isDark) R.drawable.key_bg_ios_dark else R.drawable.key_bg_special_light

        val rootCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@SettingsActivity, cardBg)
            val pad = dp(20)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val tvTitle = TextView(this).apply {
            text = "Pintasan: $shortcut"
            textSize = 17f
            setTextColor(textColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        rootCard.addView(tvTitle)

        // Phrase Display Box
        val boxPhrase = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(12), 0, dp(18))
            }
            background = ContextCompat.getDrawable(this@SettingsActivity, inputBg)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            text = "\"$phrase\""
            setTextColor(textColor)
            textSize = 14f
            gravity = android.view.Gravity.CENTER
        }
        rootCard.addView(boxPhrase)

        // Symmetrical 2-Column Action Grid
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42))
        }

        val btnCancel = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f).apply {
                setMargins(0, 0, dp(6), 0)
            }
            background = ContextCompat.getDrawable(this@SettingsActivity, inputBg)
            text = "Batal"
            setTextColor(textColor)
            textSize = 13.5f
            gravity = android.view.Gravity.CENTER
            isClickable = true
            setOnClickListener {
                dialog.dismiss()
                showTextReplacementDialog()
            }
        }
        btnRow.addView(btnCancel)

        val btnDelete = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f).apply {
                setMargins(dp(6), 0, 0, 0)
            }
            background = ContextCompat.getDrawable(this@SettingsActivity, inputBg)
            text = "🗑️ Hapus"
            setTextColor(Color.parseColor("#FF3B30"))
            textSize = 13.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            isClickable = true
            setOnClickListener {
                manager.remove(shortcut)
                Toast.makeText(this@SettingsActivity, "Pintasan '$shortcut' dihapus", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                showTextReplacementDialog()
            }
        }
        btnRow.addView(btnDelete)
        rootCard.addView(btnRow)

        dialog.setContentView(rootCard)
        dialog.show()
    }
}
