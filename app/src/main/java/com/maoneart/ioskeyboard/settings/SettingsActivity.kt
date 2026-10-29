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
import android.text.Editable
import android.text.TextWatcher
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun showTextReplacementDialog() {
        val manager = com.maoneart.ioskeyboard.shortcut.TextReplacementManager(this)

        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            window?.setDimAmount(0.45f)
        }

        val rootCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.card_bg_ios_modal)
            val pad = dp(20)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 1. iOS Top Header with Pill Button
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        val tvBadge = TextView(this).apply {
            text = "⚡ PINTASAN TEKS"
            textSize = 10.5f
            setTextColor(Color.parseColor("#FF9500"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
        }
        titleCol.addView(tvBadge)

        val tvTitle = TextView(this).apply {
            text = "Penggantian Teks"
            textSize = 19.5f
            setTextColor(Color.BLACK)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        titleCol.addView(tvTitle)
        headerRow.addView(titleCol)

        // Apple Pill Button: "+ Tambah"
        val btnAdd = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(34)
            )
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_bg_pill_blue)
            setPadding(dp(14), 0, dp(14), 0)
            text = "➕ Tambah"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isClickable = true
            setOnClickListener {
                dialog.dismiss()
                showAddShortcutDialog(manager)
            }
        }
        headerRow.addView(btnAdd)
        rootCard.addView(headerRow)

        val tvSubtitle = TextView(this).apply {
            text = "Ketik singkatan untuk diganti frasa lengkap secara otomatis saat menekan spasi."
            textSize = 12f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, dp(4), 0, dp(12))
        }
        rootCard.addView(tvSubtitle)

        // 2. iOS Cupertino Search Bar
        val searchBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.card_bg_ios_inner)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(38)
            ).apply { setMargins(0, 0, 0, dp(10)) }
            setPadding(dp(12), 0, dp(12), 0)
        }

        val ivSearch = TextView(this).apply {
            text = "🔍 "
            textSize = 13f
        }
        searchBar.addView(ivSearch)

        val etSearch = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)
            background = null
            hint = "Cari singkatan atau frasa..."
            setHintTextColor(Color.parseColor("#8E8E93"))
            setTextColor(Color.BLACK)
            textSize = 13.5f
            setSingleLine(true)
        }
        searchBar.addView(etSearch)
        rootCard.addView(searchBar)

        // 3. Scrollable List Container (Cupertino Inset Group)
        val outerListCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.card_bg_ios_inner)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val scrollView = android.widget.ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(260)
            )
            isFillViewport = true
        }

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        fun populateList(query: String = "") {
            listContainer.removeAllViews()
            val shortcuts = manager.getAllReplacements().toSortedMap()
            val filtered = if (query.isBlank()) {
                shortcuts.entries.toList()
            } else {
                val q = query.trim().lowercase()
                shortcuts.entries.filter { (s, p) ->
                    s.lowercase().contains(q) || p.lowercase().contains(q)
                }
            }

            if (filtered.isEmpty()) {
                val emptyLayout = LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, dp(32), 0, dp(32))
                }
                val emptyIcon = TextView(this@SettingsActivity).apply {
                    text = "⚡"
                    textSize = 30f
                    gravity = android.view.Gravity.CENTER
                }
                emptyLayout.addView(emptyIcon)

                val emptyTitle = TextView(this@SettingsActivity).apply {
                    text = if (query.isBlank()) "Belum Ada Pintasan" else "Pintasan Tidak Ditemukan"
                    textSize = 15f
                    setTextColor(Color.BLACK)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, dp(6), 0, dp(2))
                }
                emptyLayout.addView(emptyTitle)

                val emptyDesc = TextView(this@SettingsActivity).apply {
                    text = if (query.isBlank()) {
                        "Ketik singkatan seperti 'otw' untuk memicu 'On the way'."
                    } else {
                        "Tidak ada singkatan yang cocok dengan kata '$query'."
                    }
                    textSize = 12f
                    setTextColor(Color.parseColor("#8E8E93"))
                    gravity = android.view.Gravity.CENTER
                }
                emptyLayout.addView(emptyDesc)
                listContainer.addView(emptyLayout)
            } else {
                for (entry in filtered) {
                    val sKey = entry.key
                    val sPhrase = entry.value

                    val row = LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.card_bg_ios)
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, 0, 0, dp(4)) }
                        isClickable = true
                        setOnClickListener {
                            dialog.dismiss()
                            showManageShortcutDialog(manager, sKey, sPhrase)
                        }
                    }

                    // Shortcut Badge Chip
                    val badge = TextView(this@SettingsActivity).apply {
                        text = sKey
                        textSize = 13f
                        setTextColor(Color.parseColor("#007AFF"))
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.badge_bg_shortcut)
                        setPadding(dp(8), dp(3), dp(8), dp(3))
                    }
                    row.addView(badge)

                    // Phrase Text
                    val tvPhrase = TextView(this@SettingsActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                            setMargins(dp(10), 0, dp(8), 0)
                        }
                        text = sPhrase
                        textSize = 14f
                        setTextColor(Color.BLACK)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    row.addView(tvPhrase)

                    // Quick Delete Action Button
                    val btnQuickDel = TextView(this@SettingsActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
                        background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_bg_pill_red_subtle)
                        text = "✕"
                        textSize = 12f
                        setTextColor(Color.parseColor("#FF3B30"))
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        gravity = android.view.Gravity.CENTER
                        isClickable = true
                        setOnClickListener {
                            showDeleteConfirmModal(manager, sKey, sPhrase) {
                                populateList(etSearch.text.toString())
                            }
                        }
                    }
                    row.addView(btnQuickDel)
                    listContainer.addView(row)
                }
            }
        }

        populateList()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                populateList(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        scrollView.addView(listContainer)
        outerListCard.addView(scrollView)
        rootCard.addView(outerListCard)

        // 4. iOS Selesai Bottom Action Button
        val btnClose = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply { setMargins(0, dp(14), 0, 0) }
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_bg_pill_gray)
            text = "Selesai"
            setTextColor(Color.parseColor("#007AFF"))
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            isClickable = true
            setOnClickListener { dialog.dismiss() }
        }
        rootCard.addView(btnClose)

        dialog.setContentView(rootCard)
        val dialogWidth = (resources.displayMetrics.widthPixels * 0.92f).toInt()
        dialog.window?.setLayout(dialogWidth, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    private fun showAddShortcutDialog(manager: com.maoneart.ioskeyboard.shortcut.TextReplacementManager) {
        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            window?.setDimAmount(0.45f)
        }

        val rootCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.card_bg_ios_modal)
            val pad = dp(20)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val tvTitle = TextView(this).apply {
            text = "✏️ Tambah Pintasan Baru"
            textSize = 18f
            setTextColor(Color.BLACK)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        rootCard.addView(tvTitle)

        val tvSubtitle = TextView(this).apply {
            text = "Masukkan frasa lengkap dan singkatan pemicu"
            textSize = 12f
            setTextColor(Color.parseColor("#8E8E93"))
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(4), 0, dp(16))
        }
        rootCard.addView(tvSubtitle)

        // Grouped Input Fields (Apple Settings Style)
        val inputGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.card_bg_ios_inner)
            setPadding(dp(14), dp(4), dp(14), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Row 1: FRASA
        val rowPhrase = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
        }
        val lblPhrase = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(85), LinearLayout.LayoutParams.WRAP_CONTENT)
            text = "FRASA"
            textSize = 11.5f
            setTextColor(Color.parseColor("#8E8E93"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        rowPhrase.addView(lblPhrase)

        val etPhrase = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)
            background = null
            hint = "Contoh: On the way"
            setHintTextColor(Color.parseColor("#8E8E93"))
            setTextColor(Color.BLACK)
            textSize = 14f
            setSingleLine(true)
        }
        rowPhrase.addView(etPhrase)
        inputGroup.addView(rowPhrase)

        // Hairline Divider
        val divider = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                height = 1
            }
            setBackgroundColor(Color.parseColor("#E5E5EA"))
        }
        inputGroup.addView(divider)

        // Row 2: SINGKATAN
        val rowShortcut = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
        }
        val lblShortcut = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(85), LinearLayout.LayoutParams.WRAP_CONTENT)
            text = "SINGKATAN"
            textSize = 11.5f
            setTextColor(Color.parseColor("#8E8E93"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        rowShortcut.addView(lblShortcut)

        val etShortcut = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)
            background = null
            hint = "Contoh: otw, gpp, slm"
            setHintTextColor(Color.parseColor("#8E8E93"))
            setTextColor(Color.BLACK)
            textSize = 14f
            setSingleLine(true)
        }
        rowShortcut.addView(etShortcut)
        inputGroup.addView(rowShortcut)
        rootCard.addView(inputGroup)

        val tvTip = TextView(this).apply {
            text = "💡 Tekan spasi setelah mengetik singkatan untuk otomatis menggantikannya dengan frasa."
            textSize = 11.5f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(dp(2), dp(8), dp(2), dp(18))
        }
        rootCard.addView(tvTip)

        // Symmetrical 2-Column Action Grid (MaoneArt Glassmorphism Standard)
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
        }

        val btnCancel = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f).apply {
                setMargins(0, 0, dp(6), 0)
            }
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_bg_pill_gray)
            text = "Batal"
            setTextColor(Color.parseColor("#007AFF"))
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            isClickable = true
            setOnClickListener {
                dialog.dismiss()
                showTextReplacementDialog()
            }
        }
        btnRow.addView(btnCancel)

        val btnSave = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f).apply {
                setMargins(dp(6), 0, 0, 0)
            }
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_bg_pill_blue)
            text = "Simpan"
            setTextColor(Color.WHITE)
            textSize = 14f
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
        val dialogWidth = (resources.displayMetrics.widthPixels * 0.92f).toInt()
        dialog.window?.setLayout(dialogWidth, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    private fun showManageShortcutDialog(
        manager: com.maoneart.ioskeyboard.shortcut.TextReplacementManager,
        shortcut: String,
        phrase: String
    ) {
        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            window?.setDimAmount(0.45f)
        }

        val rootCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.card_bg_ios_modal)
            val pad = dp(20)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val tvTitle = TextView(this).apply {
            text = "Kelola Pintasan Teks"
            textSize = 18f
            setTextColor(Color.BLACK)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        rootCard.addView(tvTitle)

        val tvSubtitle = TextView(this).apply {
            text = "Detail singkatan dan frasa pengganti"
            textSize = 12f
            setTextColor(Color.parseColor("#8E8E93"))
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(4), 0, dp(16))
        }
        rootCard.addView(tvSubtitle)

        // Detail Card Box
        val detailBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.card_bg_ios_inner)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(18)) }
        }

        val shortcutRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val lblS = TextView(this).apply {
            text = "Singkatan: "
            textSize = 12.5f
            setTextColor(Color.parseColor("#8E8E93"))
        }
        shortcutRow.addView(lblS)

        val badgeS = TextView(this).apply {
            text = shortcut
            textSize = 13.5f
            setTextColor(Color.parseColor("#007AFF"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.badge_bg_shortcut)
            setPadding(dp(8), dp(3), dp(8), dp(3))
        }
        shortcutRow.addView(badgeS)
        detailBox.addView(shortcutRow)

        val lblP = TextView(this).apply {
            text = "Frasa Lengkap:"
            textSize = 12f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, dp(10), 0, dp(2))
        }
        detailBox.addView(lblP)

        val tvP = TextView(this).apply {
            text = "\"$phrase\""
            textSize = 15f
            setTextColor(Color.BLACK)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        detailBox.addView(tvP)
        rootCard.addView(detailBox)

        // Symmetrical 2-Column Action Grid (MaoneArt Glassmorphism Standard)
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
        }

        val btnDelete = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f).apply {
                setMargins(0, 0, dp(6), 0)
            }
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_bg_pill_red_subtle)
            text = "🗑️ Hapus"
            setTextColor(Color.parseColor("#FF3B30"))
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            isClickable = true
            setOnClickListener {
                dialog.dismiss()
                showDeleteConfirmModal(manager, shortcut, phrase) {
                    showTextReplacementDialog()
                }
            }
        }
        btnRow.addView(btnDelete)

        val btnClose = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f).apply {
                setMargins(dp(6), 0, 0, 0)
            }
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_bg_pill_gray)
            text = "Tutup"
            setTextColor(Color.parseColor("#007AFF"))
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            isClickable = true
            setOnClickListener {
                dialog.dismiss()
                showTextReplacementDialog()
            }
        }
        btnRow.addView(btnClose)
        rootCard.addView(btnRow)

        dialog.setContentView(rootCard)
        val dialogWidth = (resources.displayMetrics.widthPixels * 0.92f).toInt()
        dialog.window?.setLayout(dialogWidth, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    private fun showDeleteConfirmModal(
        manager: com.maoneart.ioskeyboard.shortcut.TextReplacementManager,
        shortcut: String,
        phrase: String,
        onDeleted: () -> Unit
    ) {
        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            window?.setDimAmount(0.45f)
        }

        val rootCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.card_bg_ios_modal)
            val pad = dp(20)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val iconTv = TextView(this).apply {
            text = "🗑️"
            textSize = 30f
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        rootCard.addView(iconTv)

        val tvTitle = TextView(this).apply {
            text = "Hapus Pintasan Teks?"
            textSize = 17f
            setTextColor(Color.BLACK)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(8), 0, dp(4))
        }
        rootCard.addView(tvTitle)

        val tvMsg = TextView(this).apply {
            text = "Apakah Anda yakin ingin menghapus pintasan '$shortcut' ➔ '$phrase'?"
            textSize = 12.5f
            setTextColor(Color.parseColor("#8E8E93"))
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(18))
        }
        rootCard.addView(tvMsg)

        // Symmetrical 2-Column Action Grid (MaoneArt Glassmorphism Standard)
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
        }

        val btnCancel = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f).apply {
                setMargins(0, 0, dp(6), 0)
            }
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_bg_pill_gray)
            text = "Batal"
            setTextColor(Color.parseColor("#1C1C1E"))
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            isClickable = true
            setOnClickListener { dialog.dismiss() }
        }
        btnRow.addView(btnCancel)

        val btnConfirm = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f).apply {
                setMargins(dp(6), 0, 0, 0)
            }
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_bg_pill_red)
            text = "🗑️ Hapus"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            isClickable = true
            setOnClickListener {
                manager.remove(shortcut)
                Toast.makeText(this@SettingsActivity, "Pintasan '$shortcut' berhasil dihapus", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                onDeleted()
            }
        }
        btnRow.addView(btnConfirm)
        rootCard.addView(btnRow)

        dialog.setContentView(rootCard)
        val dialogWidth = (resources.displayMetrics.widthPixels * 0.90f).toInt()
        dialog.window?.setLayout(dialogWidth, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }
}
