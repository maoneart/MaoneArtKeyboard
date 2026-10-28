package com.maoneart.ioskeyboard.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.maoneart.ioskeyboard.R
import com.maoneart.ioskeyboard.preferences.KeyboardPreferences

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: KeyboardPreferences

    private lateinit var tvCurrentTheme: TextView
    private lateinit var tvActiveLanguage: TextView
    private lateinit var tvTranslatePair: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = KeyboardPreferences(this)

        initViews()
        bindData()
    }

    private fun initViews() {
        val btnEnableIme = findViewById<Button>(R.id.btnEnableIme)
        val btnSelectIme = findViewById<Button>(R.id.btnSelectIme)

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

        // Setup Actions
        btnEnableIme.setOnClickListener {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        }

        btnSelectIme.setOnClickListener {
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
    }

    private fun bindData() {
        updateThemeLabel()
        updateLanguageLabel()
        updateTranslatePairLabel()

        findViewById<SwitchMaterial>(R.id.switchKeyPopup).isChecked = prefs.isKeyPopupEnabled
        findViewById<SwitchMaterial>(R.id.switchSound).isChecked = prefs.isSoundEnabled
        findViewById<SwitchMaterial>(R.id.switchHaptic).isChecked = prefs.isHapticEnabled
        findViewById<SwitchMaterial>(R.id.switchAutoCorrect).isChecked = prefs.isAutoCorrectEnabled
        findViewById<SwitchMaterial>(R.id.switchDoubleSpace).isChecked = prefs.isDoubleSpacePeriodEnabled
        findViewById<SwitchMaterial>(R.id.switchLiveTranslate).isChecked = prefs.isLiveTranslateEnabled
    }

    private fun updateThemeLabel() {
        tvCurrentTheme.text = when (prefs.themeMode) {
            "dark" -> "iOS Dark"
            "light" -> "iOS Light"
            else -> "Otomatis (Sistem)"
        }
    }

    private fun updateLanguageLabel() {
        tvActiveLanguage.text = when (prefs.activeLanguage) {
            "en" -> "English (US/UK)"
            else -> "Bahasa Indonesia"
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
