package com.maoneart.ioskeyboard.emoji

object EmojiSearchEngine {

    // Contextual Emoji Dictionary (Kata percakapan -> Rekomendasi Emoji Otomatis di QuickType bar)
    private val contextualMap: Map<String, List<String>> = mapOf(
        // Terima kasih & Syukur
        "terima kasih" to listOf("🙏", "✨"),
        "makasih" to listOf("🙏", "😊"),
        "trims" to listOf("🙏"),
        "thanks" to listOf("🙏", "✨"),
        "tq" to listOf("🙏"),
        "thx" to listOf("🙏"),
        "matur nuwun" to listOf("🙏"),
        "alhamdulillah" to listOf("🤲", "🙏"),
        "puji tuhan" to listOf("🙏", "✨"),

        // Keren, Mantap, Pujian
        "keren" to listOf("🔥", "😎", "👍"),
        "mantap" to listOf("👍", "🔥", "👌"),
        "mantul" to listOf("👍", "🔥"),
        "cool" to listOf("😎", "🔥"),
        "top" to listOf("🔝", "🔥"),
        "fire" to listOf("🔥"),
        "hebat" to listOf("👏", "💪"),
        "kece" to listOf("😎", "✨"),
        "juara" to listOf("🏆", "🥇"),
        "menang" to listOf("🏆", "🎉"),
        "win" to listOf("🏆", "💪"),
        "sukses" to listOf("🌟", "🚀"),

        // Cinta & Perasaan
        "cinta" to listOf("❤️", "🥰", "💖"),
        "sayang" to listOf("🥰", "❤️", "😘"),
        "love" to listOf("❤️", "🥰", "💕"),
        "suka" to listOf("❤️", "👍"),
        "lope" to listOf("❤️", "🫰"),
        "rindu" to listOf("🥺", "❤️"),
        "kangen" to listOf("🥺", "❤️"),
        "cantik" to listOf("😍", "✨", "🌸"),
        "ganteng" to listOf("😎", "✨"),
        "cakep" to listOf("😍", "😎"),
        "manis" to listOf("🥰", "🍭"),

        // Tawa & Lucu
        "lucu" to listOf("😂", "🤣"),
        "wkwk" to listOf("😂", "🤣"),
        "wkwkwk" to listOf("🤣", "😂"),
        "haha" to listOf("😂", "😆"),
        "hahaha" to listOf("🤣", "😂"),
        "ngakak" to listOf("🤣", "💀"),
        "lol" to listOf("😂", "🤣"),
        "kocak" to listOf("😂", "🤣"),

        // Semangat & Kerja
        "semangat" to listOf("💪", "🔥", "🚀"),
        "gaspoll" to listOf("🚀", "🔥"),
        "strong" to listOf("💪"),
        "kerja" to listOf("💼", "💻", "👨‍💻"),
        "kantor" to listOf("🏢", "💼"),
        "lembur" to listOf("☕", "💻", "😫"),
        "tugas" to listOf("📝", "📚"),
        "belajar" to listOf("📚", "📖", "💡"),
        "kuliah" to listOf("🎓", "📚"),
        "sekolah" to listOf("🎒", "🏫"),

        // Kopi & Minuman
        "kopi" to listOf("☕"),
        "coffee" to listOf("☕"),
        "ngopi" to listOf("☕", "🥐"),
        "teh" to listOf("🫖", "🍵"),
        "tea" to listOf("🍵"),
        "boba" to listOf("🧋"),
        "es" to listOf("🧊", "🧃"),
        "jus" to listOf("🧃", "🍹"),
        "minum" to listOf("🥤", "🥛"),
        "bir" to listOf("🍺", "🍻"),
        "wine" to listOf("🍷", "🥂"),

        // Makanan & Kuliner
        "makan" to listOf("🍜", "🍔", "😋"),
        "lapar" to listOf("🤤", "🍔", "🍕"),
        "laper" to listOf("🤤", "🍜"),
        "food" to listOf("🍔", "🍕", "🍱"),
        "nasi" to listOf("🍚", "🍛"),
        "mie" to listOf("🍜"),
        "bakso" to listOf("🍜"),
        "ayam" to listOf("🍗", "🐔"),
        "burger" to listOf("🍔"),
        "pizza" to listOf("🍕"),
        "roti" to listOf("🍞", "🥐"),
        "kue" to listOf("🍰", "🎂"),
        "cokelat" to listOf("🍫"),
        "es krim" to listOf("🍦", "🍨"),
        "enak" to listOf("😋", "👌"),
        "sedap" to listOf("🤤", "👌"),

        // Uang & Belanja
        "uang" to listOf("💰", "💵", "💸"),
        "duit" to listOf("💰", "💸"),
        "cuan" to listOf("🤑", "💰", "💸"),
        "gaji" to listOf("💰", "🤑"),
        "gajian" to listOf("🎉", "💰", "🤑"),
        "money" to listOf("💵", "💰"),
        "kaya" to listOf("🤑", "💎"),
        "belanja" to listOf("🛍️", "🛒"),
        "borong" to listOf("🛍️", "💸"),
        "diskon" to listOf("🏷️", "🔥"),

        // Tidur & Istirahat
        "tidur" to listOf("😴", "💤", "🛌"),
        "ngantuk" to listOf("🥱", "😴"),
        "sleep" to listOf("😴", "💤"),
        "bobo" to listOf("😴", "🛌"),
        "capek" to listOf("😮‍💨", "🥱"),
        "lelah" to listOf("😮‍💨", "🫠"),

        // Emosi & Keadaan
        "sedih" to listOf("😢", "😭", "🥺"),
        "nangis" to listOf("😭", "😢"),
        "sad" to listOf("😢", "💔"),
        "galau" to listOf("🥺", "🥀"),
        "kecewa" to listOf("😞", "💔"),
        "marah" to listOf("😡", "🤬", "😤"),
        "kesal" to listOf("😤", "😒"),
        "kesel" to listOf("😤", "😠"),
        "bingung" to listOf("🤔", "🧐"),
        "kaget" to listOf("😱", "😳", "🤯"),
        "takut" to listOf("😨", "😱"),
        "panik" to listOf("😱", "😰"),
        "malu" to listOf("🙈", "😳"),
        "bosan" to listOf("🥱", "😑"),

        // Persetujuan & Salam
        "siap" to listOf("🫡", "👌", "✅"),
        "oke" to listOf("👌", "👍", "✅"),
        "ok" to listOf("👌", "👍"),
        "yes" to listOf("✅", "🙌"),
        "deal" to listOf("🤝", "✅"),
        "halo" to listOf("👋", "✨"),
        "hai" to listOf("👋", "😊"),
        "selamat" to listOf("🎉", "🥳"),
        "hbd" to listOf("🎂", "🎉", "🥳"),
        "ultah" to listOf("🎂", "🎉"),
        "congrats" to listOf("🎉", "👏"),

        // Perjalanan & Transportasi
        "jalan" to listOf("🚶‍♂️", "🚗"),
        "otw" to listOf("🛵", "🚗", "💨"),
        "mobil" to listOf("🚗", "🚙"),
        "motor" to listOf("🛵", "🏍️"),
        "sepeda" to listOf("🚲"),
        "pesawat" to listOf("✈️", "🛫"),
        "terbang" to listOf("✈️", "🕊️"),
        "kereta" to listOf("🚆", "🚇"),
        "bus" to listOf("🚌"),
        "liburan" to listOf("🏖️", "🏝️", "✈️"),
        "pantai" to listOf("🏖️", "🌊"),
        "gunung" to listOf("⛰️", "🏕️"),
        "rumah" to listOf("🏠", "🏡"),
        "pulang" to listOf("🏠", "🚗"),

        // Cuaca & Alam
        "hujan" to listOf("🌧️", "☔", "⛈️"),
        "gerimis" to listOf("🌦️", "🌧️"),
        "badai" to listOf("⛈️", "🌪️"),
        "panas" to listOf("☀️", "🥵"),
        "gerah" to listOf("🥵", "☀️"),
        "matahari" to listOf("☀️", "🌞"),
        "dingin" to listOf("🥶", "❄️"),
        "salju" to listOf("❄️", "⛄"),
        "angin" to listOf("🌬️", "💨"),
        "api" to listOf("🔥"),
        "bintang" to listOf("⭐", "✨", "🌟"),
        "bulan" to listOf("🌙", "🌕"),

        // Hewan Peliharaan
        "kucing" to listOf("🐱", "🐈", "😻"),
        "cat" to listOf("🐱", "🐈"),
        "anjing" to listOf("🐶", "🐕"),
        "dog" to listOf("🐶", "🦮"),
        "burung" to listOf("🐦", "🦅"),
        "ikan" to listOf("🐟", "🐠"),

        // Doa & Agama
        "doa" to listOf("🤲", "🙏"),
        "sholat" to listOf("🕌", "🤲"),
        "ibadah" to listOf("🤲", "⛪", "🕌"),
        "amin" to listOf("🤲", "🙏"),
        "berkah" to listOf("🤲", "✨"),

        // Hiburan & Musik
        "musik" to listOf("🎵", "🎶", "🎧"),
        "lagu" to listOf("🎶", "🎵"),
        "nyanyi" to listOf("🎤", "🎶"),
        "gitar" to listOf("🎸"),
        "game" to listOf("🎮", "🕹️"),
        "mabar" to listOf("🎮", "🔥"),
        "nonton" to listOf("🍿", "🎬"),
        "film" to listOf("🎬", "🍿"),

        // Nasional & Simbol
        "indonesia" to listOf("🇮🇩", "🚩"),
        "merdeka" to listOf("🇮🇩", "✊"),
        "garuda" to listOf("🦅", "🇮🇩"),
        "100" to listOf("💯"),
        "top" to listOf("🔝")
    )

    // Emoji Keyword Index for Global Search
    private val keywordIndex: List<Pair<List<String>, List<String>>> = listOf(
        // Smileys
        listOf("senyum", "smile", "happy", "senang", "gembira", "ceria") to listOf("😀", "😃", "😄", "😁", "😊", "🙂", "☺️"),
        listOf("tawa", "laugh", "ngakak", "lucu", "wkwk", "haha", "lol") to listOf("😂", "🤣", "😆", "😹"),
        listOf("cinta", "love", "hati", "sayang", "heart") to listOf("❤️", "🥰", "😍", "😘", "💖", "💕", "💓", "💗", "💘", "💝", "❣️"),
        listOf("cium", "kiss") to listOf("😘", "😗", "😙", "😚", "💋"),
        listOf("kedip", "wink") to listOf("😉", "😜", "😜"),
        listOf("keren", "cool", "kacamata", "sunglasses") to listOf("😎", "🕶️", "🔥"),
        listOf("sedih", "sad", "nangis", "cry", "air mata") to listOf("😢", "😭", "🥺", "😿", "🥲", "😞", "😔"),
        listOf("marah", "angry", "kesal", "mad", "rage") to listOf("😡", "🤬", "😤", "😠", "👿"),
        listOf("kaget", "shock", "terkejut", "wow") to listOf("😱", "🤯", "😳", "😲", "😯", "😮"),
        listOf("tidur", "sleep", "ngantuk", "lelah", "tired") to listOf("😴", "🥱", "😪", "💤"),
        listOf("mikir", "think", "bingung", "tanya") to listOf("🤔", "🧐", "🤨"),
        listOf("diam", "shh", "rahasia", "quiet") to listOf("🤫", "🤐", "😶"),
        listOf("hantu", "ghost", "setan", "spooky") to listOf("👻", "💀", "☠️", "🎃"),
        listOf("kotoran", "poop", "tinja") to listOf("💩"),
        listOf("robot", "bot", "ai") to listOf("🤖"),
        listOf("alien", "ufo") to listOf("👽", "👾", "🛸"),

        // Gestures / People
        listOf("jempol", "thumb", "like", "suka", "bagus", "sip") to listOf("👍", "👎"),
        listOf("tangan", "hand", "dadah", "bye", "halo") to listOf("👋", "✋", "🤚", "🖐️"),
        listOf("tepuk", "clap", "applause") to listOf("👏", "🙌"),
        listOf("doa", "pray", "tolong", "makasih", "terima kasih") to listOf("🙏", "🤲"),
        listOf("otot", "muscle", "kuat", "strong", "semangat") to listOf("💪"),
        listOf("tos", "peace", "damai", "dua") to listOf("✌️", "🤞", "🫰", "🤙", "🤘"),
        listOf("tinju", "punch", "fist") to listOf("👊", "✊", "🤛", "🤜"),
        listOf("tunjuk", "point") to listOf("👉", "👈", "👆", "👇", "☝️"),
        listOf("mata", "eye", "lihat") to listOf("👀", "👁️"),
        listOf("bibir", "lips", "mulut") to listOf("👄", "🫦"),
        listOf("otak", "brain", "cerdas") to listOf("🧠"),

        // Animals
        listOf("kucing", "cat", "meow", "kocheng") to listOf("🐱", "🐈", "🐈‍⬛", "😻", "😹", "😽"),
        listOf("anjing", "dog", "puppy", "guk") to listOf("🐶", "🐕", "🦮", "🐕‍🦺", "🐩"),
        listOf("singa", "lion") to listOf("🦁"),
        listOf("harimau", "tiger") to listOf("🐯", "🐅", "🐆"),
        listOf("kuda", "horse") to listOf("🐴", "🐎", "🦄"),
        listOf("monyet", "monkey", "kera") to listOf("🐵", "🐒", "🦍", "🦧"),
        listOf("burung", "bird", "elang") to listOf("🐦", "🦅", "🦉", "🕊️", "🦜", "🦆"),
        listOf("ayam", "chicken") to listOf("🐔", "🐓", "🐣", "🐤", "🐥"),
        listOf("sapi", "cow") to listOf("🐮", "🐄", "🐂"),
        listOf("babi", "pig") to listOf("🐷", "🐖", "🐽"),
        listOf("ikan", "fish") to listOf("🐟", "🐠", "🐡", "🦈", "🐬", "🐳", "🐋"),
        listOf("kelinci", "rabbit", "bunny") to listOf("🐰", "🐇"),
        listOf("panda", "beruang", "bear") to listOf("🐼", "🐻", "🐻‍❄️", "🐨"),
        listOf("ular", "snake") to listOf("🐍"),
        listOf("kupu", "butterfly") to listOf("🦋"),
        listOf("lebah", "bee") to listOf("🐝"),

        // Food & Drink
        listOf("kopi", "coffee", "cappuccino", "espresso") to listOf("☕"),
        listOf("teh", "tea", "boba") to listOf("🫖", "🍵", "🧋"),
        listOf("bir", "beer", "minum") to listOf("🍺", "🍻", "🥂", "🍷", "🍸", "🍹"),
        listOf("burger", "hamburger") to listOf("🍔"),
        listOf("pizza") to listOf("🍕"),
        listOf("mie", "ramen", "noodle", "bakso") to listOf("🍜", "🍝"),
        listOf("nasi", "rice", "curry") to listOf("🍚", "🍛", "🍙", "🍱"),
        listOf("roti", "bread", "croissant") to listOf("🍞", "🥖", "🥐"),
        listOf("ayam goreng", "daging", "meat") to listOf("🍗", "🥩", "🍖"),
        listOf("buah", "fruit", "apel", "apple") to listOf("🍎", "🍏", "🍉", "🍌", "🍇", "🍓", "🍒", "🍑", "🍍", "🥭"),
        listOf("es krim", "ice cream") to listOf("🍦", "🍨", "🍧"),
        listOf("kue", "cake", "tart", "ultah") to listOf("🎂", "🍰", "🧁"),
        listOf("cokelat", "chocolate", "manis") to listOf("🍫", "🍬", "🍭"),

        // Activity & Sports
        listOf("bola", "soccer", "football", "sepak bola") to listOf("⚽"),
        listOf("basket", "basketball") to listOf("🏀"),
        listOf("tenis", "badminton", "raket") to listOf("🎾", "🏸", "🏓"),
        listOf("game", "gaming", "joystick", "play") to listOf("🎮", "🕹️", "🎲"),
        listOf("musik", "music", "gitar", "nyanyi", "lagu") to listOf("🎵", "🎶", "🎧", "🎤", "🎸", "🎹", "🥁"),
        listOf("piala", "trophy", "medali", "juara", "menang") to listOf("🏆", "🥇", "🥈", "🥉", "🏅"),
        listOf("pesta", "party", "ulang tahun", "rayakan") to listOf("🎉", "🎊", "🥳", "🎈", "🎁"),

        // Travel & Places
        listOf("mobil", "car", "otomotif") to listOf("🚗", "🚙", "🏎️", "🚕", "🛻"),
        listOf("motor", "motorcycle", "scooter") to listOf("🛵", "🏍️"),
        listOf("sepeda", "bicycle", "bike") to listOf("🚲"),
        listOf("pesawat", "plane", "airplane", "terbang") to listOf("✈️", "🛫", "🛬", "🚀"),
        listOf("kereta", "train", "mrt", "krl") to listOf("🚆", "🚇", "🚅", "🚂"),
        listOf("kapal", "boat", "ship", "laut") to listOf("🚢", "🛥️", "⛵"),
        listOf("rumah", "home", "house", "gedung") to listOf("🏠", "🏡", "🏢", "🏬", "🏰"),
        listOf("alam", "pantai", "gunung", "nature") to listOf("🏖️", "⛰️", "🏝️", "🏕️", "🌊"),

        // Nature & Weather
        listOf("api", "fire", "panas", "bakar") to listOf("🔥"),
        listOf("air", "water", "hujan", "rain") to listOf("💧", "🌧️", "🌦️", "⛈️", "☔"),
        listOf("matahari", "sun", "siang") to listOf("☀️", "🌞"),
        listOf("bulan", "moon", "malam") to listOf("🌙", "🌕", "🌚"),
        listOf("bintang", "star") to listOf("⭐", "🌟", "✨", "💫"),
        listOf("salju", "snow", "dingin", "cold") to listOf("❄️", "⛄", "☃️", "🥶"),
        listOf("petir", "kilat", "lightning") to listOf("⚡", "🌩️"),

        // Objects & Tech
        listOf("hp", "phone", "smartphone", "telepon") to listOf("📱", "📲", "📞", "☎️"),
        listOf("laptop", "komputer", "pc", "computer") to listOf("💻", "🖥️", "⌨️"),
        listOf("uang", "money", "duit", "cuan", "rupiah", "dollar") to listOf("💰", "💵", "💸", "🪙", "💳", "🤑"),
        listOf("kamera", "camera", "foto") to listOf("📷", "📸", "📹", "🎥"),
        listOf("buku", "book", "surat", "catatan") to listOf("📚", "📖", "📝", "📄", "✉️", "📩"),
        listOf("jam", "clock", "waktu", "time") to listOf("⏰", "⏱️", "⌚", "⌛"),
        listOf("kunci", "key", "gembok") to listOf("🔑", "🔒", "🔓"),
        listOf("baju", "shirt", "pakaian", "fashion") to listOf("👕", "👗", "👔", "👖", "👟", "👠"),
        listOf("mic", "mikrofon", "microphone", "suara") to listOf("🎤", "🎙️"),

        // Symbols & Flags
        listOf("indonesia", "indo", "merah putih", "garuda") to listOf("🇮🇩", "🚩"),
        listOf("centang", "check", "benar", "yes", "sukses") to listOf("✅", "✔️", "☑️"),
        listOf("silang", "cross", "salah", "no") to listOf("❌", "❎"),
        listOf("tanya", "question") to listOf("❓", "❔"),
        listOf("seru", "exclamation", "penting", "warning") to listOf("❗️", "⚠️", "‼️", "⁉️"),
        listOf("100", "seratus", "sempurna", "perfect") to listOf("💯"),
        listOf("peringatan", "warning", "bahaya") to listOf("⚠️", "⛔️", "🚫")
    )

    // Quick Search Filters for Instant One-Tap Discovery on the Emoji Board
    val quickFilterTags: List<Pair<String, String>> = listOf(
        Pair("🔥 Populer", "populer"),
        Pair("❤️ Cinta", "cinta"),
        Pair("😂 Tertawa", "tawa"),
        Pair("👍 Jempol", "jempol"),
        Pair("🙏 Doa", "doa"),
        Pair("☕ Kopi", "kopi"),
        Pair("🍜 Makan", "makan"),
        Pair("🐱 Hewan", "kucing"),
        Pair("🚗 Jalan", "mobil"),
        Pair("🎉 Pesta", "pesta"),
        Pair("🇮🇩 Indonesia", "indonesia")
    )

    /**
     * Recommends contextual emojis based on typed word (for suggestion candidate strip).
     */
    fun getContextualEmojis(word: String): List<String> {
        val clean = word.trim().lowercase()
        if (clean.isEmpty()) return emptyList()

        contextualMap[clean]?.let { return it }

        // Also check sub-matches if word is long enough
        if (clean.length >= 3) {
            for ((key, emojis) in contextualMap) {
                if (clean.startsWith(key) || key.startsWith(clean)) {
                    return emojis
                }
            }
        }
        return emptyList()
    }

    /**
     * Searches the emoji library using keyword matching (for Search Bar in Emoji Board).
     */
    fun searchEmojis(query: String, maxResults: Int = 48): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()

        val results = linkedSetOf<String>()

        // 1. Direct Contextual Match
        contextualMap[q]?.let { results.addAll(it) }

        // 2. Keyword Index Match
        for ((tags, emojis) in keywordIndex) {
            if (tags.any { it.startsWith(q) || q.startsWith(it) || it.contains(q) }) {
                results.addAll(emojis)
                if (results.size >= maxResults) break
            }
        }

        // 3. Fallback to broad substring search in all tag categories
        if (results.size < maxResults) {
            for ((tags, emojis) in keywordIndex) {
                if (tags.any { it.contains(q) }) {
                    results.addAll(emojis)
                    if (results.size >= maxResults) break
                }
            }
        }

        return results.take(maxResults)
    }
}
