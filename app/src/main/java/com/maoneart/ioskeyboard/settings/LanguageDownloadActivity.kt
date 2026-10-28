package com.maoneart.ioskeyboard.settings

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.maoneart.ioskeyboard.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LanguageDownloadActivity : AppCompatActivity() {

    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

    data class LanguagePack(
        val code: String,
        val name: String,
        val nativeName: String,
        val size: String,
        val isDefault: Boolean,
        val sampleWords: List<String>
    )

    private val languagePacks = listOf(
        LanguagePack("id", "Bahasa Indonesia", "Indonesia", "Bawaan (1.2 MB)", true, emptyList()),
        LanguagePack("en", "English", "United States / UK", "Bawaan (1.8 MB)", true, emptyList()),
        LanguagePack("ar", "Bahasa Arab", "العربية", "1.5 MB", false, listOf("مرحبا", "شكرا", "نعم", "سلام", "الله", "جميل", "كتاب", "طعام")),
        LanguagePack("ja", "Bahasa Jepang", "日本語", "2.1 MB", false, listOf("こんにちは", "ありがとう", "はい", "さようなら", "友達", "日本", "東京")),
        LanguagePack("ko", "Bahasa Korea", "한국어", "1.9 MB", false, listOf("안녕하세요", "감사합니다", "네", "사랑", "친구", "행복", "한국")),
        LanguagePack("es", "Bahasa Spanyol", "Español", "1.4 MB", false, listOf("hola", "gracias", "amigo", "casa", "bueno", "vida", "tiempo")),
        LanguagePack("de", "Bahasa Jerman", "Deutsch", "1.6 MB", false, listOf("hallo", "danke", "bitte", "haus", "freund", "zeit", "gut")),
        LanguagePack("fr", "Bahasa Prancis", "Français", "1.5 MB", false, listOf("bonjour", "merci", "ami", "maison", "amour", "temps", "bien"))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language_download)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        renderLanguageList()
    }

    private fun isPackInstalled(code: String): Boolean {
        if (code == "id" || code == "en") return true
        val dictFile = File(File(filesDir, "dictionaries"), "$code.dict")
        return dictFile.exists() && dictFile.length() > 0
    }

    private fun renderLanguageList() {
        val container = findViewById<LinearLayout>(R.id.cardLanguages)
        container.removeAllViews()

        for ((index, pack) in languagePacks.withIndex()) {
            val itemRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(64)
                )
                gravity = Gravity.CENTER_VERTICAL
            }

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            }

            val tvTitle = TextView(this).apply {
                text = "${pack.name} (${pack.nativeName})"
                textSize = 15.5f
                setTextColor(Color.BLACK)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val tvSubtitle = TextView(this).apply {
                text = "Ukuran Paket: ${pack.size}"
                textSize = 13f
                setTextColor(Color.parseColor("#8E8E93"))
            }
            textCol.addView(tvTitle)
            textCol.addView(tvSubtitle)
            itemRow.addView(textCol)

            val installed = isPackInstalled(pack.code)

            if (installed) {
                val tvInstalled = TextView(this).apply {
                    text = "Terpasang ✓"
                    setTextColor(ContextCompat.getColor(this@LanguageDownloadActivity, R.color.ios_green))
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                }
                itemRow.addView(tvInstalled)
            } else {
                val progress = ProgressBar(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
                    visibility = View.GONE
                }

                val btnDownload = Button(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(84), dp(34))
                    background = ContextCompat.getDrawable(this@LanguageDownloadActivity, R.drawable.btn_apple_action)
                    text = "Unduh"
                    textSize = 13f
                    setTextColor(Color.WHITE)
                    isAllCaps = false
                    setOnClickListener {
                        visibility = View.GONE
                        progress.visibility = View.VISIBLE
                        downloadPack(pack) {
                            progress.visibility = View.GONE
                            renderLanguageList()
                        }
                    }
                }
                itemRow.addView(progress)
                itemRow.addView(btnDownload)
            }

            container.addView(itemRow)

            if (index < languagePacks.size - 1) {
                val sep = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                    setBackgroundColor(Color.parseColor("#E5E5EA"))
                }
                container.addView(sep)
            }
        }
    }

    private fun downloadPack(pack: LanguagePack, onComplete: () -> Unit) {
        activityScope.launch {
            withContext(Dispatchers.IO) {
                delay(1200) // Simulate fast network sync
                val dir = File(filesDir, "dictionaries")
                if (!dir.exists()) dir.mkdirs()

                val dictFile = File(dir, "${pack.code}.dict")
                val content = pack.sampleWords.joinToString("\n")
                dictFile.writeText(content)
            }

            Toast.makeText(this@LanguageDownloadActivity, "Kamus ${pack.name} berhasil diunduh!", Toast.LENGTH_SHORT).show()
            onComplete()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
