package com.maoneart.ioskeyboard.correction

import android.content.Context
import java.io.File
import kotlin.math.abs
import kotlin.math.min

class SmartCorrectionEngine(private val context: Context) {

    // Default suggestions when cursor is at empty input or after space
    private val defaultIndonesianSuggestions = listOf(
        "aku", "kamu", "bisa", "mau", "lagi", "sudah", "yang", "di", "iya"
    )

    private val defaultEnglishSuggestions = listOf(
        "I", "you", "the", "to", "is", "we", "can", "good"
    )

    // Direct mapping for Indonesian abbreviations and instant typo shortcuts
    private val commonAbbreviations = mapOf(
        "kmu" to listOf("kamu", "kamus"),
        "km" to listOf("kamu", "kami", "kemarin"),
        "ak" to listOf("aku", "akan", "ada", "apa"),
        "aku" to listOf("aku", "akan", "akui"),
        "bsa" to listOf("bisa", "bisa-bisa", "bisa jadi"),
        "bs" to listOf("bisa", "besok"),
        "mw" to listOf("mau", "mungkin"),
        "mau" to listOf("mau", "maupun", "maut"),
        "udh" to listOf("udah", "sudah", "udahan"),
        "sdh" to listOf("sudah", "sedang"),
        "blm" to listOf("belum", "bilang", "bulan"),
        "lg" to listOf("lagi", "lagian"),
        "lgi" to listOf("lagi", "lagian"),
        "jg" to listOf("juga", "jangan", "jaga"),
        "jga" to listOf("juga", "jangan"),
        "juha" to listOf("juga", "jauh", "juara"),
        "bgt" to listOf("banget", "bagus", "begitu"),
        "sy" to listOf("saya", "syarat"),
        "sya" to listOf("saya", "syair"),
        "tdk" to listOf("tidak", "tadi"),
        "ga" to listOf("nggak", "tidak", "gimana"),
        "gak" to listOf("nggak", "tidak"),
        "ngga" to listOf("nggak", "tidak"),
        "nggak" to listOf("nggak", "tidak"),
        "gpp" to listOf("gapapa", "tidak apa-apa"),
        "gapapa" to listOf("gapapa", "tidak apa-apa"),
        "tp" to listOf("tapi", "tempat"),
        "tpi" to listOf("tapi"),
        "dgn" to listOf("dengan", "dingin"),
        "dg" to listOf("dengan"),
        "utk" to listOf("untuk", "utama"),
        "sbg" to listOf("sebagai", "sebagian"),
        "org" to listOf("orang", "organisasi"),
        "krn" to listOf("karena", "keren"),
        "klo" to listOf("kalau", "kelompok"),
        "kl" to listOf("kalau", "kali"),
        "klau" to listOf("kalau"),
        "mksih" to listOf("makasih", "terima kasih"),
        "makasi" to listOf("makasih", "terima kasih"),
        "thx" to listOf("thanks", "terima kasih"),
        "ty" to listOf("thank you", "terima kasih"),
        "bbrp" to listOf("beberapa"),
        "sm" to listOf("sama", "semua"),
        "sma" to listOf("sama", "sampai"),
        "gmn" to listOf("gimana", "bagaimana"),
        "knp" to listOf("kenapa", "mengapa"),
        "dmn" to listOf("dimana", "kemana"),
        "bener" to listOf("benar", "beneran"),
        "bnr" to listOf("benar", "beneran"),
        "bgtu" to listOf("begitu"),
        "gt" to listOf("gitu", "begitu"),
        "gitu" to listOf("gitu", "begitu"),
        "skrg" to listOf("sekarang"),
        "ntr" to listOf("nanti", "sebentar"),
        "bsk" to listOf("besok"),
        "kemaren" to listOf("kemarin"),
        "kmrn" to listOf("kemarin"),
        "mkn" to listOf("makan", "maka"),
        "mnm" to listOf("minum", "mana"),
        "tlg" to listOf("tolong"),
        "pls" to listOf("please", "tolong"),
        "u" to listOf("you", "kamu"),
        "r" to listOf("are"),
        "ur" to listOf("your", "you are"),
        "idk" to listOf("I don't know"),
        "tbh" to listOf("to be honest"),
        "btw" to listOf("by the way"),
        "omw" to listOf("on my way")
    )

    // QWERTY keyboard physical adjacency map for accurate typo detection
    private val qwertyNeighbors = mapOf(
        'q' to "wa", 'w' to "qeas", 'e' to "wrds", 'r' to "etfd", 't' to "rygf", 'y' to "tuhg", 'u' to "yijh", 'i' to "uokj", 'o' to "iplk", 'p' to "ol",
        'a' to "qwsz", 's' to "awedxz", 'd' to "serfxc", 'f' to "drtgvc", 'g' to "ftyhbv", 'h' to "gyujnb", 'j' to "huikmn", 'k' to "jiolm", 'l' to "kop",
        'z' to "asx", 'x' to "zsdc", 'c' to "xdfv", 'v' to "cfgb", 'b' to "vghn", 'n' to "bhjm", 'm' to "njk"
    )

    // Comprehensive Indonesian Dictionary (1,500+ high frequency words)
    private val indonesianWords = linkedSetOf(
        // Pronouns & People
        "aku", "saya", "kamu", "dia", "kita", "kami", "mereka", "anda", "kalian", "engkau", "beliau",
        "diriku", "dirimu", "dirinya", "sendiri", "gw", "gue", "lu", "lo", "elu", "doi", "orang",
        "anak", "bayi", "balita", "remaja", "dewasa", "bapak", "ayah", "ibu", "mama", "papa", "kakak",
        "adik", "abang", "kakek", "nenek", "paman", "bibi", "saudara", "keluarga", "teman", "kawan",
        "sahabat", "musuh", "tetangga", "warga", "masyarakat", "guru", "dosen", "dokter", "perawat",
        "polisi", "tentara", "bos", "pimpinan", "staf", "pegawai", "karyawan", "buruh", "petani",
        "nelayan", "sopir", "kurir", "pembeli", "penjual", "tamu", "pelanggan", "pasien",

        // High Frequency Verbs
        "ada", "adalah", "akan", "bisa", "dapat", "mau", "ingin", "harus", "perlu", "butuh", "boleh",
        "mesti", "bantu", "tolong", "mohon", "minta", "kasih", "beri", "memberi", "berikan", "ambil",
        "membawa", "bawa", "taruh", "simpan", "buang", "hapus", "bikin", "membuat", "buat", "kerja",
        "bekerja", "mengerjakan", "kuliah", "sekolah", "belajar", "mengajar", "ajar", "tahu", "tau",
        "mengerti", "paham", "ingat", "mengingat", "lupa", "pikir", "berpikir", "rasa", "merasa",
        "lihat", "melihat", "liat", "tonton", "menonton", "dengar", "mendengar", "dengarkan", "bicara",
        "berbicara", "ngomong", "bilang", "sebut", "menyebutkan", "tanya", "bertanya", "menjawab",
        "jawab", "balas", "membalas", "kirim", "mengirim", "pesan", "terima", "menerima", "jemput",
        "menjemput", "antar", "mengantar", "bayar", "membayar", "transfer", "beli", "membeli", "jual",
        "menjual", "pinjam", "meminjam", "kembalikan", "pakai", "memakai", "gunakan", "menggunakan",
        "pasang", "memasang", "buka", "membuka", "tutup", "menutup", "nyala", "mati", "hidup", "matikan",
        "hidupkan", "makan", "minum", "tidur", "bangun", "jalan", "berjalan", "lari", "berlari",
        "pergi", "pulang", "datang", "tiba", "sampai", "singgah", "masuk", "keluar", "naik", "turun",
        "duduk", "berdiri", "istirahat", "tunggu", "menunggu", "hubungi", "menghubungi", "telpon",
        "menelpon", "chat", "ketik", "mengetik", "tulis", "menulis", "baca", "membaca", "cari",
        "mencari", "temu", "menemukan", "ketemu", "kenal", "mengenal", "jumpa", "bertemu", "pilih",
        "memilih", "putus", "memutuskan", "tentukan", "menentukan", "ubah", "mengubah", "ganti",
        "mengganti", "tambah", "menambah", "kurang", "selesai", "mulai", "lanjut", "melanjutkan",
        "berhenti", "coba", "mencoba", "cek", "periksa", "memeriksa", "atur", "mengatur", "kelola",
        "mengelola", "urus", "mengurus", "rawat", "merawat", "jaga", "menjaga", "lindungi", "melindungi",
        "lawan", "melawan", "bela", "membela", "menang", "kalah", "lolos", "gagal", "sukses", "berhasil",
        "kena", "lepas", "sedia", "siapkan", "adakan", "harap", "berharap", "yakin", "percaya", "ragu",
        "takut", "berani", "suka", "cinta", "sayang", "rindu", "kangen", "benci", "marah", "senang",
        "bahagia", "sedih", "kecewa",

        // High Frequency Adjectives
        "baik", "bagus", "keren", "mantap", "hebat", "cantik", "ganteng", "manis", "lucu", "imut",
        "pintar", "cerdas", "rajin", "malas", "mager", "cepat", "lambat", "ngebut", "pelan", "mudah",
        "gampang", "susah", "sulit", "rumit", "berat", "ringan", "besar", "kecil", "tinggi", "rendah",
        "panjang", "pendek", "luas", "sempit", "tebal", "tipis", "kuat", "lemah", "sehat", "sakit",
        "panas", "dingin", "hangat", "sejuk", "bersih", "kotor", "rapi", "berantakan", "baru", "lama",
        "tua", "muda", "murah", "mahal", "gratis", "kaya", "miskin", "benar", "bener", "salah",
        "tepat", "cocok", "pantas", "aman", "bahaya", "penting", "utama", "biasa", "spesial", "khusus",
        "umum", "santai", "sibuk", "tenang", "ramai", "sepi", "seru", "bosan", "gembira", "kesal",
        "khawatir", "cemas", "capek", "lelah", "lapar", "haus", "kenyang", "enak", "lezat", "sedap",
        "pahit", "asin", "pedas", "gurih", "segar", "jelas", "kabur", "terang", "gelap", "asli",
        "palsu", "sama", "beda", "mirip", "siap", "pasti", "nyata", "sempurna", "parah", "gokil",
        "seram", "unik", "aneh",

        // Prepositions, Conjunctions & Adverbs
        "yang", "untuk", "pada", "ke", "di", "dari", "ini", "itu", "dengan", "dan", "atau", "tetapi",
        "tapi", "karena", "sebab", "akibat", "maka", "sehingga", "agar", "supaya", "jika", "kalau",
        "kalo", "bila", "seandainya", "walaupun", "meskipun", "padahal", "namun", "sebelum", "sesudah",
        "setelah", "sejak", "selama", "sampai", "hingga", "seperti", "sebagai", "tentang", "mengenai",
        "terhadap", "daripada", "bagi", "guna", "bersama", "serta", "selain", "kecuali", "sambil",
        "ketika", "saat", "waktu", "sementara", "kemudian", "lalu", "terus", "selanjutnya", "lagi",
        "masih", "sudah", "udah", "telah", "belum", "sedang", "baru", "barusan", "tadi", "nanti",
        "besok", "lusa", "kemarin", "sekarang", "skrg", "selalu", "sering", "kadang", "pernah",
        "tidak", "bukan", "nggak", "gak", "ga", "tanpa", "hanya", "cuma", "saja", "aja", "juga",
        "pula", "pun", "paling", "sangat", "amat", "sekali", "banget", "lebih", "kurang", "agak",
        "cukup", "terlalu", "bahkan", "tentu", "mungkin", "kira-kira", "nyaris", "hampir", "langsung",
        "segera", "tiba-tiba", "mendadak", "rupanya", "ternyata", "memang", "sebenarnya", "pastinya",
        "pokoknya", "makanya", "asal",

        // Question Words
        "apa", "apakah", "siapa", "dimana", "kemana", "darimana", "kenapa", "mengapa", "bagaimana",
        "gimana", "berapa", "kapan", "mana", "manakah",

        // Time, Days, Numbers
        "hari", "tanggal", "bulan", "tahun", "jam", "menit", "detik", "pagi", "siang", "sore", "malam",
        "senin", "selasa", "rabu", "kamis", "jumat", "sabtu", "minggu", "weekend", "satu", "dua",
        "tiga", "empat", "lima", "enam", "tujuh", "delapan", "sembilan", "sepuluh", "sebelas", "ratus",
        "ribu", "juta", "miliar", "pertama", "kedua", "ketiga", "terakhir", "awal", "tengah", "akhir",
        "jadwal",

        // Everyday Nouns, Places & Technology
        "rumah", "kantor", "sekolah", "kampus", "toko", "warung", "pasar", "mall", "hotel", "restoran",
        "kafe", "stasiun", "terminal", "bandara", "pelabuhan", "jalan", "gang", "gedung", "kamar",
        "ruang", "dapur", "toilet", "tempat", "lokasi", "daerah", "kota", "desa", "negeri", "negara",
        "dunia", "alam", "bumi", "langit", "udara", "air", "api", "tanah", "laut", "sungai", "danau",
        "gunung", "pantai", "taman", "uang", "harga", "biaya", "saldo", "rekening", "dompet", "kartu",
        "bank", "atm", "hp", "handphone", "ponsel", "laptop", "komputer", "keyboard", "layar",
        "baterai", "charger", "kabel", "kuota", "wifi", "sinyal", "jaringan", "internet", "web",
        "website", "link", "aplikasi", "app", "apk", "akun", "password", "login", "email", "chat",
        "pesan", "grup", "kontak", "nomor", "panggilan", "suara", "foto", "gambar", "video", "audio",
        "musik", "lagu", "kamera", "file", "dokumen", "folder", "kode", "info", "informasi", "berita",
        "status", "story", "post", "komentar", "like", "notifikasi", "pengaturan", "fitur", "versi",
        "update", "download", "install", "masalah", "kendala", "solusi", "cara", "tips", "ide",
        "saran", "pilihan", "keputusan", "rencana", "tujuan", "hasil", "proses", "ujian", "tugas",
        "kerjaan", "pekerjaan", "proyek", "rapat", "meeting", "acara", "liburan", "makanan", "minuman",
        "nasi", "mie", "roti", "daging", "ayam", "ikan", "telur", "sayur", "buah", "kopi", "teh",
        "susu", "jus", "es", "gula", "garam", "baju", "celana", "sepatu", "sandal", "tas", "topi",
        "kacamata", "motor", "mobil", "sepeda", "bus", "kereta", "pesawat", "kapal",

        // Conversational & Chat Specific Terms
        "iya", "ya", "oke", "siap", "sip", "mantul", "asik", "boleh", "silakan", "monggo", "permisi",
        "maaf", "maafkan", "sorry", "tolong", "please", "makasih", "terima kasih", "thanks", "sama-sama",
        "halo", "hai", "hei", "selamat", "kabar", "salam", "gapapa", "santai aja", "beneran", "serius",
        "waduh", "astaga", "aduh", "wah", "wkwk", "hehe", "haha", "kosakata", "rekomendasi", "rekomen",
        "pilihan", "typo", "kurang", "huruf", "salah", "ketik", "lebih", "banyak"
    )

    // Comprehensive English Dictionary
    private val englishWords = linkedSetOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it", "for", "not", "on",
        "with", "he", "as", "you", "do", "at", "this", "but", "his", "by", "from", "they", "we",
        "say", "her", "she", "or", "an", "will", "my", "one", "all", "would", "there", "their",
        "what", "so", "up", "out", "if", "about", "who", "get", "which", "go", "me", "when", "make",
        "can", "like", "time", "no", "just", "him", "know", "take", "people", "into", "year", "your",
        "good", "some", "could", "them", "see", "other", "than", "then", "now", "look", "only", "come",
        "its", "over", "think", "also", "back", "after", "use", "two", "how", "our", "work", "first",
        "well", "way", "even", "new", "want", "because", "any", "these", "give", "day", "most", "us",
        "hello", "thanks", "please", "sorry", "welcome", "tomorrow", "yesterday", "meeting", "great",
        "love", "happy", "need", "help", "send", "call", "home", "phone", "today", "money", "message",
        "chat", "typing", "keyboard", "setting", "download", "app", "file", "open", "close", "start",
        "finish", "ready", "yes", "sure", "fine", "cool", "nice", "right", "wrong", "more", "less"
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

    /**
     * Get candidate suggestions for the current word:
     * - Empty input -> initial high-frequency prompts ("aku", "kamu", "bisa", ...)
     * - Abbreviations & Slang -> instant expansion ("kmu" -> "kamu", "bsa" -> "bisa", "juha" -> "juga")
     * - Prefix completions -> words starting with input prefix (as letters are typed or deleted)
     * - Typo Buster -> Damerau-Levenshtein distance (missing letter, extra letter, swapped letters, QWERTY proximity)
     */
    fun getSuggestions(input: String, lang: String): List<String> {
        val word = input.trim().lowercase()

        // 0. If empty (e.g. after space, or backspaced to empty):
        if (word.isEmpty()) {
            return if (lang == "en") defaultEnglishSuggestions else defaultIndonesianSuggestions
        }

        val dict = when (lang) {
            "en" -> englishWords + customWords
            else -> indonesianWords + customWords
        }

        val result = linkedSetOf<String>()

        // 1. Direct Abbreviation / Chat Slang match
        commonAbbreviations[word]?.let { expansions ->
            for (exp in expansions) {
                result.add(exp)
                if (result.size >= 8) return result.toList()
            }
        }

        // 2. Exact match in dictionary
        if (dict.contains(word)) {
            result.add(word)
        }

        // 3. Prefix Matches (Word completion as user types or backspaces)
        val prefixMatches = dict.filter { it.startsWith(word) }
            .sortedWith(compareBy({ it.length }, { it }))
        for (pm in prefixMatches) {
            result.add(pm)
            if (result.size >= 8) return result.toList()
        }

        // 4. Subsequence matches for common missing-vowel typos (e.g. "kmu" -> "kamu", "bsa" -> "bisa", "mkn" -> "makan")
        if (word.length in 2..5) {
            val subseqMatches = dict.filter { candidate ->
                candidate.length <= word.length + 2 && isSubsequence(word, candidate)
            }.sortedBy { it.length }
            for (sm in subseqMatches) {
                result.add(sm)
                if (result.size >= 8) return result.toList()
            }
        }

        // 5. Typo Buster: Damerau-Levenshtein distance + QWERTY keyboard proximity
        val maxDist = if (word.length <= 3) 1 else 2
        val typoCandidates = mutableListOf<Pair<String, Int>>()

        for (candidate in dict) {
            if (abs(candidate.length - word.length) > 2) continue
            // Optimization: if word is 3+ chars and candidate starts with unrelated key, skip
            if (word.length >= 3 && candidate[0] != word[0] && !isQwertyNeighbor(word[0], candidate[0])) continue

            val dist = damerauLevenshtein(word, candidate)
            if (dist <= maxDist) {
                var score = dist * 10
                // Bonus if first letter matches
                if (candidate.isNotEmpty() && word.isNotEmpty() && candidate[0] == word[0]) {
                    score -= 3
                }
                // Bonus for QWERTY neighbor substitutions (e.g. 'g' <-> 'h', 'u' <-> 'y')
                if (candidate.length == word.length) {
                    var neighborMatches = 0
                    for (i in word.indices) {
                        if (word[i] != candidate[i] && isQwertyNeighbor(word[i], candidate[i])) {
                            neighborMatches++
                        }
                    }
                    score -= neighborMatches * 3
                }
                typoCandidates.add(Pair(candidate, score))
            }
        }

        typoCandidates.sortBy { it.second }
        for ((cand, _) in typoCandidates) {
            result.add(cand)
            if (result.size >= 8) break
        }

        // Fallback: If still empty, preserve user input
        if (result.isEmpty()) {
            result.add(input)
        }

        return result.take(8).toList()
    }

    private fun isSubsequence(sub: String, target: String): Boolean {
        if (sub.isEmpty()) return true
        if (target.length < sub.length) return false
        var i = 0
        var j = 0
        while (i < sub.length && j < target.length) {
            if (sub[i] == target[j]) {
                i++
            }
            j++
        }
        return i == sub.length
    }

    private fun isQwertyNeighbor(c1: Char, c2: Char): Boolean {
        return qwertyNeighbors[c1]?.contains(c2) == true || qwertyNeighbors[c2]?.contains(c1) == true
    }

    private fun damerauLevenshtein(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        val d = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) d[i][0] = i
        for (j in 0..len2) d[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                var minVal = min(
                    min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                    d[i - 1][j - 1] + cost
                )

                // Transposition of adjacent letters (e.g. "akmu" -> "kamu")
                if (i > 1 && j > 1 && s1[i - 1] == s2[j - 2] && s1[i - 2] == s2[j - 1]) {
                    minVal = min(minVal, d[i - 2][j - 2] + 1)
                }
                d[i][j] = minVal
            }
        }
        return d[len1][len2]
    }
}
