package com.maoneart.ioskeyboard.correction

import android.content.Context
import java.io.File
import kotlin.math.min

class SmartCorrectionEngine(private val context: Context) {

    private val indonesianWords = setOf(
        "yang", "untuk", "pada", "ke", "di", "dari", "ini", "itu", "dengan", "dan", "atau",
        "adalah", "sebagai", "akan", "bisa", "dapat", "sudah", "telah", "belum", "tidak", "bukan",
        "saya", "kamu", "dia", "mereka", "kita", "kami", "anda", "kalian", "bapak", "ibu",
        "halo", "selamat", "pagi", "siang", "sore", "malam", "terima", "kasih", "sama-sama",
        "bagaimana", "mengapa", "kenapa", "siapa", "dimana", "kapan", "apa", "kabar", "baik",
        "tolong", "bantu", "mohon", "maaf", "permisi", "silakan", "iya", "ya", "oke", "siap",
        "kerja", "rapat", "kantor", "rumah", "sekolah", "kuliah", "teman", "sahabat", "keluarga",
        "makan", "minum", "tidur", "jalan", "pergi", "pulang", "datang", "sampai", "bertemu",
        "besok", "lusa", "kemarin", "hari", "minggu", "bulan", "tahun", "jam", "menit", "waktu",
        "uang", "harga", "beli", "jual", "bayar", "transfer", "bank", "pesan", "kirim", "chat",
        "bagus", "keren", "mantap", "hebat", "cantik", "ganteng", "pintar", "cepat", "lambat",
        "senang", "bahagia", "sedih", "marah", "capek", "lelah", "semangat", "sukses", "selalu"
    )

    private val englishWords = setOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it", "for", "not", "on",
        "with", "he", "as", "you", "do", "at", "this", "but", "his", "by", "from", "they", "we",
        "say", "her", "she", "or", "an", "will", "my", "one", "all", "would", "there", "their",
        "what", "so", "up", "out", "if", "about", "who", "get", "which", "go", "me", "when", "make",
        "can", "like", "time", "no", "just", "him", "know", "take", "people", "into", "year", "your",
        "good", "some", "could", "them", "see", "other", "than", "then", "now", "look", "only", "come",
        "its", "over", "think", "also", "back", "after", "use", "two", "how", "our", "work", "first",
        "well", "way", "even", "new", "want", "because", "any", "these", "give", "day", "most", "us",
        "hello", "thanks", "please", "sorry", "welcome", "tomorrow", "yesterday", "meeting", "great"
    )

    private val customWords = mutableSetOf<String>()

    init {
        loadCustomDictionary()
    }

    private fun loadCustomDictionary() {
        try {
            val dictDir = File(context.filesDir, "dictionaries")
            if (dictDir.exists()) {
                dictDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.extension == "dict") {
                        file.readLines().forEach { line ->
                            val word = line.trim().lowercase()
                            if (word.isNotEmpty()) customWords.add(word)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun getSuggestions(input: String, lang: String): List<String> {
        val word = input.trim().lowercase()
        if (word.isEmpty()) return emptyList()

        return try {
            val dict = when (lang) {
                "en" -> englishWords + customWords
                else -> indonesianWords + customWords
            }

            // 1. Exact match or prefix matches
            val exactOrPrefix = dict.filter { it.startsWith(word) }.sortedBy { it.length }

            // 2. Levenshtein fuzzy match if no direct prefix matches
            val candidates = if (exactOrPrefix.isNotEmpty()) {
                exactOrPrefix.take(3)
            } else {
                dict.map { Pair(it, levenshtein(word, it)) }
                    .filter { it.second <= 2 }
                    .sortedBy { it.second }
                    .map { it.first }
                    .take(3)
            }

            val result = mutableListOf<String>()
            if (candidates.isNotEmpty()) {
                result.addAll(candidates)
            } else {
                result.add(input)
            }

            result
        } catch (_: Throwable) {
            listOf(input)
        }
    }

    private fun levenshtein(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
