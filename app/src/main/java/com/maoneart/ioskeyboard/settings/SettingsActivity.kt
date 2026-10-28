package com.maoneart.ioskeyboard.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
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
        val switchHaptic = findViewById<SwitchMaterial>(R.id.switchHaptic)

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

        switchHaptic.setOnCheckedChangeListener { _, isChecked ->
            prefs.isHapticEnabled = isChecked
        }

        rowLanguage.setOnClickListener {
            showLanguageDialog()
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

        switchLiveTranslate.setOnCheckedChangeListener { _, isChecked ->
            prefs.isLiveTranslateEnabled = isChecked
        }

        rowTranslatePair.setOnClickListener {
            showTranslatePairDialog()
        }

        btnClearTest.setOnClickListener {
            etTestInput.setText("")
        }
    }

    private fun bindData() {
        updateThemeLabel()
        updateLanguageLabel()
        updateTranslatePairLabel()
        updateSetupCards()

        findViewById<SwitchMaterial>(R.id.switchKeyPopup).isChecked = prefs.isKeyPopupEnabled
        findViewById<SwitchMaterial>(R.id.switchSound).isChecked = prefs.isSoundEnabled
        findViewById<SwitchMaterial>(R.id.switchHaptic).isChecked = prefs.isHapticEnabled
        findViewById<SwitchMaterial>(R.id.switchAutoCorrect).isChecked = prefs.isAutoCorrectEnabled
        findViewById<SwitchMaterial>(R.id.switchDoubleSpace).isChecked = prefs.isDoubleSpacePeriodEnabled
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
            else -> "Otomatis"
        }
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
        val options = arrayOf("Otomatis (Ikuti Sistem)", "iOS Light (Terang)", "iOS Dark (Gelap)")
        val currentSelection = when (prefs.themeMode) {
            "light" -> 1
            "dark" -> 2
            else -> 0
        }

        AlertDialog.Builder(this)
            .setTitle("Pilih Tema Keyboard")
            .setSingleChoiceItems(options, currentSelection) { dialog, which ->
                prefs.themeMode = when (which) {
                    1 -> "light"
                    2 -> "dark"
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
}
