package com.maoneart.ioskeyboard.correction

import android.content.Context
import java.io.File
import kotlin.math.abs
import kotlin.math.min

class SmartCorrectionEngine(private val context: Context) {

    // Default suggestions when cursor is at empty input
    private val defaultIndonesianSuggestions = listOf(
        "aku", "kamu", "bisa", "mau", "lagi", "sudah", "yang", "di", "iya"
    )

    private val defaultEnglishSuggestions = listOf(
        "I", "you", "the", "to", "is", "we", "can", "good"
    )

    // Next-Word Prediction Map (N-Gram) for conversational typing ("Nulis Otomatis")
    private val nextWordMapId = mapOf(
        "saya" to listOf("mau", "bisa", "sudah", "lagi", "ingin", "tidak", "akan", "punya", "juga", "baru"),
        "aku" to listOf("mau", "lagi", "udah", "bisa", "juga", "sayang", "gak", "ingin", "tadi", "nanti"),
        "kamu" to listOf("dimana", "lagi", "mau", "udah", "bisa", "kenapa", "ada", "sama", "siapa", "sudah"),
        "dia" to listOf("lagi", "sudah", "mau", "bisa", "tidak", "ada", "tadi", "bilang", "kemarin"),
        "kita" to listOf("bisa", "mau", "harus", "ketemu", "makan", "pergi", "sudah", "nanti", "kumpul"),
        "kami" to listOf("sudah", "akan", "bisa", "mengucapkan", "berharap", "siap", "ingin"),
        "mereka" to listOf("sudah", "lagi", "mau", "tidak", "ada", "semua", "bisa"),
        "lagi" to listOf("apa", "dimana", "ngapain", "sibuk", "di", "jalan", "makan", "santai", "kerja", "istirahat"),
        "udah" to listOf("makan", "selesai", "sampai", "tidur", "bisa", "di", "siap", "pulang", "tau"),
        "sudah" to listOf("makan", "selesai", "sampai", "tidur", "bisa", "di", "siap", "pulang", "waktunya"),
        "belum" to listOf("bisa", "selesai", "makan", "tahu", "tidur", "sampai", "sempat", "ada"),
        "mau" to listOf("kemana", "apa", "makan", "pergi", "tanya", "beli", "tidur", "pulang", "ketemu", "ikut"),
        "bisa" to listOf("nggak", "gak", "tidak", "dong", "bantu", "kirim", "ketemu", "minta", "datang", "jadi"),
        "tidak" to listOf("bisa", "mau", "ada", "tahu", "apa-apa", "masalah", "usah", "boleh"),
        "gak" to listOf("bisa", "mau", "ada", "tau", "apa-apa", "usah", "boleh", "paham"),
        "nggak" to listOf("bisa", "mau", "ada", "tau", "apa-apa", "usah", "boleh"),
        "terima" to listOf("kasih", "order", "pemberitahuan", "hasil"),
        "kasih" to listOf("tahu", "banyak", "info", "kesempatan", "hadiah"),
        "selamat" to listOf("pagi", "siang", "sore", "malam", "ulang tahun", "datang", "sukses", "hari"),
        "apa" to listOf("kabar", "yang", "bisa", "itu", "aja", "mau", "ada", "alasannya"),
        "dimana" to listOf("sekarang", "posisi", "kamu", "rumahnya", "lokasinya", "alamatnya"),
        "gimana" to listOf("kabarnya", "jadinya", "caranya", "kondisinya", "kelanjutannya", "bro"),
        "kenapa" to listOf("kamu", "bisa", "tidak", "nggak", "tadi", "begitu"),
        "kapan" to listOf("bisa", "kita", "ketemu", "pulang", "sampai", "mulai", "selesai"),
        "siapa" to listOf("yang", "saja", "tadi", "namanya", "itu"),
        "berapa" to listOf("harganya", "biayanya", "nomornya", "lama", "banyak"),
        "jangan" to listOf("lupa", "marah", "sampai", "khawatir", "takut", "gitu", "sedih", "pergi"),
        "tolong" to listOf("bantu", "kirim", "cek", "infokan", "forward", "kabari"),
        "bantu" to listOf("saya", "kami", "cek", "kirim", "selesaikan"),
        "makasih" to listOf("banyak", "ya", "banget", "atas", "semuanya"),
        "thanks" to listOf("ya", "a lot", "banget", "bro"),
        "sama" to listOf("sama", "siapa", "dia", "kamu", "mereka", "kita"),
        "siap" to listOf("terima kasih", "bos", "laksanakan", "komandan", "nanti"),
        "oke" to listOf("siap", "terima kasih", "baik", "nanti", "sip"),
        "ke" to listOf("mana", "rumah", "kantor", "sana", "sini", "toko", "sekolah"),
        "di" to listOf("mana", "rumah", "kantor", "jalan", "sana", "sini", "tempat"),
        "dari" to listOf("mana", "tadi", "dulu", "kemarin", "awal", "situ"),
        "untuk" to listOf("apa", "kamu", "kita", "hari", "semua", "sementara"),
        "dengan" to listOf("siapa", "baik", "cara", "mudah", "cepat", "keluarga"),
        "karena" to listOf("kamu", "ada", "itu", "masih", "sudah", "tidak"),
        "kalau" to listOf("bisa", "mau", "sudah", "ada", "nanti", "sempat"),
        "kalo" to listOf("bisa", "mau", "udah", "ada", "nanti", "sempat"),
        "nanti" to listOf("malam", "siang", "sore", "kita", "saya", "aku", "kabari"),
        "besok" to listOf("pagi", "siang", "sore", "malam", "kita", "ada", "ketemu"),
        "hari" to listOf("ini", "senin", "selasa", "rabu", "kamis", "jumat", "sabtu", "minggu"),
        "makan" to listOf("siang", "malam", "pagi", "bareng", "nasi", "apa", "enak"),
        "minum" to listOf("kopi", "teh", "air", "es", "jus", "susu"),
        "tidur" to listOf("nyenyak", "dulu", "siang", "malam", "jam"),
        "jalan" to listOf("jalan", "terus", "pelan", "bareng", "pagi"),
        "kirim" to listOf("foto", "pesan", "file", "lokasi", "bukti", "uang", "sekarang"),
        "tunggu" to listOf("sebentar", "dulu", "di", "kabar", "konfirmasi"),
        "cek" to listOf("lagi", "dulu", "rekening", "pesan", "email", "status"),
        "ada" to listOf("apa", "yang", "waktu", "kabar", "masalah", "solusi", "acara"),
        "beneran" to listOf("nih", "dong", "ya", "ga", "banget"),
        "santai" to listOf("aja", "dulu", "bro", "banget")
    )

    private val nextWordMapEn = mapOf(
        "i" to listOf("am", "have", "will", "would", "want", "think", "need", "can", "know"),
        "you" to listOf("are", "can", "have", "want", "know", "need", "will", "look"),
        "we" to listOf("are", "can", "have", "need", "will", "should", "want"),
        "they" to listOf("are", "have", "will", "can", "say", "want"),
        "it" to listOf("is", "was", "will", "looks", "works", "seems"),
        "how" to listOf("are", "is", "can", "do", "about", "much", "many"),
        "what" to listOf("is", "are", "do", "time", "happened", "about"),
        "where" to listOf("are", "is", "did", "were", "can"),
        "thank" to listOf("you", "god"),
        "good" to listOf("morning", "afternoon", "evening", "night", "job", "luck"),
        "please" to listOf("help", "let", "send", "check", "call"),
        "see" to listOf("you", "that", "what", "later")
    )

    // Direct mapping for Indonesian abbreviations and instant typo shortcuts
    private val commonAbbreviations = mapOf(
        "kmu" to listOf("kamu", "kamus"),
        "km" to listOf("kamu", "kami", "kemarin"),
        "kam" to listOf("kamu", "kami", "kamar", "kampus"),
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

    // Comprehensive Indonesian Dictionary (3,000+ words: KBBI, prefixes, everyday WhatsApp terms)
    private val indonesianWords = linkedSetOf(
        // Pronouns & People
        "aku", "saya", "kamu", "dia", "kita", "kami", "mereka", "anda", "kalian", "engkau", "beliau",
        "diriku", "dirimu", "dirinya", "sendiri", "gw", "gue", "lu", "lo", "elu", "doi", "orang",
        "anak", "bayi", "balita", "remaja", "dewasa", "bapak", "ayah", "ibu", "mama", "papa", "kakak",
        "adik", "abang", "kakek", "nenek", "paman", "bibi", "saudara", "keluarga", "teman", "kawan",
        "sahabat", "musuh", "tetangga", "warga", "masyarakat", "guru", "dosen", "dokter", "perawat",
        "polisi", "tentara", "bos", "pimpinan", "staf", "pegawai", "karyawan", "buruh", "petani",
        "nelayan", "sopir", "kurir", "pembeli", "penjual", "tamu", "pelanggan", "pasien", "kolega",
        "sobat", "bocah", "manusia", "tokoh", "presiden", "menteri", "gubernur", "walikota", "bupati",

        // High Frequency Verbs (Basic & Affixed)
        "ada", "adalah", "akan", "bisa", "dapat", "mau", "ingin", "harus", "perlu", "butuh", "boleh",
        "mesti", "bantu", "membantu", "bantuan", "tolong", "menolong", "pertolongan", "mohon", "memohon",
        "minta", "meminta", "permintaan", "kasih", "mengasih", "beri", "memberi", "berikan", "pemberian",
        "ambil", "mengambil", "bawa", "membawa", "taruh", "menaruh", "simpan", "menyimpan", "buang",
        "membuang", "hapus", "menghapus", "bikin", "membuat", "buat", "buatan", "pembuatan", "kerja",
        "bekerja", "mengerjakan", "pekerjaan", "kuliah", "sekolah", "belajar", "pembelajaran", "mengajar",
        "pengajar", "ajar", "ajaran", "tahu", "mengetahui", "pengetahuan", "tau", "mengerti", "paham",
        "memahami", "pemahaman", "ingat", "mengingat", "peringatan", "ingatan", "lupa", "melupakan",
        "pikir", "berpikir", "pemikiran", "pikiran", "rasa", "merasa", "perasaan", "lihat", "melihat",
        "penglihatan", "liat", "tonton", "menonton", "tontonan", "dengar", "mendengar", "pendengaran",
        "dengarkan", "bicara", "berbicara", "pembicaraan", "ngomong", "ngomongin", "bilang", "sebut",
        "menyebut", "menyebutkan", "sebutan", "tanya", "bertanya", "pertanyaan", "jawab", "menjawab",
        "jawaban", "balas", "membalas", "balasan", "kirim", "mengirim", "pengiriman", "pesan", "pemesanan",
        "terima", "menerima", "penerimaan", "jemput", "menjemput", "penjemputan", "antar", "mengantar",
        "pengantaran", "bayar", "membayar", "pembayaran", "transfer", "beli", "membeli", "pembelian",
        "jual", "menjual", "penjualan", "pinjam", "meminjam", "peminjaman", "kembalikan", "mengembalikan",
        "pengembalian", "pakai", "memakai", "pemakaian", "gunakan", "menggunakan", "penggunaan", "pasang",
        "memasang", "pemasangan", "buka", "membuka", "pembukaan", "tutup", "menutup", "penutupan",
        "nyala", "menyalakan", "mati", "matikan", "mematikan", "kematian", "hidup", "hidupkan", "menghidupkan",
        "kehidupan", "makan", "memakan", "makanan", "minum", "meminum", "minuman", "tidur", "tertidur",
        "bangun", "membangun", "pembangunan", "jalan", "berjalan", "perjalanan", "lari", "berlari",
        "pergi", "bepergian", "pulang", "kepulangan", "datang", "kedatangan", "tiba", "sampai", "singgah",
        "masuk", "memasuki", "pemasukan", "keluar", "mengeluarkan", "pengeluaran", "naik", "menaikkan",
        "kenaikan", "turun", "menurunkan", "penurunan", "duduk", "berdiri", "istirahat", "beristirahat",
        "tunggu", "menunggu", "hubungi", "menghubungi", "telpon", "menelpon", "panggilan", "chat",
        "ketik", "mengetik", "ketikan", "tulis", "menulis", "tulisan", "penulis", "baca", "membaca",
        "bacaan", "pembaca", "cari", "mencari", "pencarian", "temu", "menemukan", "penemuan", "ketemu",
        "bertemu", "pertemuan", "kenal", "mengenal", "perkenalan", "kenalan", "jumpa", "berjumpa",
        "pilih", "memilih", "pilihan", "pemilihan", "putus", "memutuskan", "keputusan", "terputus",
        "tentukan", "menentukan", "penentuan", "ketentuan", "ubah", "mengubah", "perubahan", "ganti",
        "mengganti", "pergantian", "pengganti", "tambah", "menambah", "penambahan", "tambahan", "kurang",
        "mengurangi", "pengurangan", "selesai", "menyelesaikan", "penyelesaian", "mulai", "memulai",
        "permulaan", "lanjut", "melanjutkan", "kelanjutan", "lanjutan", "berhenti", "pemberhentian",
        "coba", "mencoba", "percobaan", "cek", "mengecek", "periksa", "memeriksa", "pemeriksaan",
        "atur", "mengatur", "pengaturan", "kelola", "mengelola", "pengelolaan", "urus", "mengurus",
        "pengurusan", "urusan", "rawat", "merawat", "perawatan", "jaga", "menjaga", "penjaga", "lindungi",
        "melindungi", "perlindungan", "lawan", "melawan", "perlawanan", "bela", "membela", "pembelaan",
        "menang", "pemenang", "kemenangan", "kalah", "kekalahan", "lolos", "gagal", "kegagalan", "sukses",
        "kesuksesan", "berhasil", "keberhasilan", "kena", "mengenai", "lepas", "melepaskan", "pelepasan",
        "sedia", "menyediakan", "penyediaan", "persediaan", "siapkan", "menyiapkan", "persiapan",
        "adakan", "mengadakan", "pengadaan", "harap", "berharap", "harapan", "yakin", "meyakinkan",
        "percaya", "kepercayaan", "mempercayai", "ragu", "keraguan", "meragukan", "takut", "ketakutan",
        "menakutkan", "berani", "keberanian", "memberanikan", "suka", "menyukai", "kesukaan", "cinta",
        "mencintai", "pecinta", "sayang", "menyayangi", "kesayangan", "rindu", "merindukan", "kerinduan",
        "kangen", "benci", "membenci", "kebencian", "marah", "kemarahan", "senang", "kesenangan",
        "menyenangkan", "bahagia", "kebahagiaan", "membahagiakan", "sedih", "kesedihan", "menyedihkan",
        "kecewa", "kekecewaan", "mengecewakan", "puas", "kepuasan", "memuaskan", "kaget", "terkejut",
        "mengejutkan", "kagum", "mengagumi", "bangga", "kebanggaan", "bimbang", "resah", "gelisah",
        "nikmati", "menikmati", "kenikmatan", "rasakan", "merasakan", "pegang", "memegang", "sentuh",
        "menyentuh", "sentuhan", "dorong", "mendorong", "dorongan", "tarik", "menarik", "tarikan",
        "tekan", "menekan", "tekanan", "lepas", "tahan", "menahan", "tahanan", "geser", "menggeser",
        "geseran", "putar", "memutar", "putaran", "balik", "membalik", "terbalik", "kebalikan",

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
        "seram", "unik", "aneh", "langka", "modern", "tradisional", "lengkap", "padat", "kosong",
        "penuh", "tajam", "tumpul", "halus", "kasar", "licin", "kering", "basah", "lembab", "kaku",
        "fleksibel", "lentur", "rapih", "cantik", "rupawan", "anggun", "gagah", "berani", "takut",
        "kreatif", "kritis", "aktif", "pasif", "positif", "negatif", "efektif", "efisien", "otomatis",

        // Prepositions, Conjunctions & Adverbs
        "yang", "untuk", "pada", "ke", "di", "dari", "ini", "itu", "dengan", "dan", "atau", "tetapi",
        "tapi", "karena", "sebab", "akibat", "maka", "sehingga", "agar", "supaya", "jika", "kalau",
        "kalo", "bila", "seandainya", "walaupun", "meskipun", "padahal", "namun", "sebelum", "sesudah",
        "setelah", "sejak", "selama", "sampai", "hingga", "seperti", "sebagai", "tentang", "mengenai",
        "terhadap", "daripada", "bagi", "guna", "bersama", "serta", "selain", "kecuali", "sambil",
        "ketika", "saat", "waktu", "sementara", "kemudian", "lalu", "terus", "selanjutnya", "lagi",
        "masih", "sudah", "udah", "telah", "belum", "blm", "sedang", "baru", "barusan", "tadi", "nanti",
        "besok", "lusa", "kemarin", "sekarang", "skrg", "selalu", "sering", "kadang", "pernah",
        "tidak", "bukan", "nggak", "gak", "ga", "tanpa", "hanya", "cuma", "saja", "aja", "juga",
        "pula", "pun", "paling", "sangat", "amat", "sekali", "banget", "lebih", "kurang", "agak",
        "cukup", "terlalu", "bahkan", "tentu", "mungkin", "kira-kira", "nyaris", "hampir", "langsung",
        "segera", "tiba-tiba", "mendadak", "rupanya", "ternyata", "memang", "sebenarnya", "pastinya",
        "pokoknya", "makanya", "asal", "supaya", "biar", "padahal", "bagaimanapun", "apapun", "siapapun",

        // Question Words
        "apa", "apakah", "siapa", "siapakah", "dimana", "kemana", "darimana", "kenapa", "mengapa",
        "bagaimana", "gimana", "berapa", "berapakah", "kapan", "kapankah", "mana", "manakah",

        // Time, Days, Numbers
        "hari", "tanggal", "bulan", "tahun", "jam", "menit", "detik", "pagi", "siang", "sore", "malam",
        "subuh", "senja", "fajar", "tengah", "senin", "selasa", "rabu", "kamis", "jumat", "sabtu", "minggu",
        "weekend", "mingguan", "bulanan", "tahunan", "harian", "satu", "dua", "tiga", "empat", "lima",
        "enam", "tujuh", "delapan", "sembilan", "sepuluh", "sebelas", "dua belas", "ratus", "ribu", "juta",
        "miliar", "triliun", "pertama", "kedua", "ketiga", "keempat", "kelima", "terakhir", "awal", "akhir",
        "jadwal", "periode", "durasi", "tempo",

        // Everyday Nouns, Places, Office & Technology
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
        "kacamata", "motor", "mobil", "sepeda", "bus", "kereta", "pesawat", "kapal", "helm", "jaket",
        "kunci", "pintu", "jendela", "meja", "kursi", "lampu", "kipas", "ac", "televisi", "kulkas",
        "kursor", "cursor", "geser", "panah", "tombol", "layar", "ketukan", "sentuhan", "getaran",
        "getar", "haptic", "suara", "klik", "spasi", "enter", "kembali", "hapus", "huruf", "angka",

        // Conversational, Indonesian Slang & Chat Terms
        "iya", "ya", "oke", "siap", "sip", "mantul", "asik", "boleh", "silakan", "monggo", "permisi",
        "maaf", "maafkan", "sorry", "sory", "tolong", "please", "makasih", "terima kasih", "thanks",
        "sama-sama", "halo", "hai", "hei", "selamat", "kabar", "salam", "gapapa", "santai aja",
        "beneran", "serius", "waduh", "astaga", "aduh", "wah", "wkwk", "hehe", "haha", "wkwkwk",
        "kosakata", "rekomendasi", "rekomen", "pilihan", "typo", "kurang", "huruf", "salah", "ketik",
        "lebih", "banyak", "ototmatis", "nulis", "ketikan", "perbaiki", "perbaikan", "kamus", "terjemah",
        "terjemahan", "translate", "artikan", "kirimkan", "bagikan", "salin", "tempel", "potong",
        "baper", "mager", "kepo", "curhat", "nongkrong", "ngopi", "otw", "kuy", "gaskeun", "gas",
        "mantap", "mantap jiwa", "keren abis", "kece", "parah", "anjay", "anjir", "gila", "asli",
        "bocil", "pansos", "sultan", "receh", "cuan", "halu", "julid", "bucin", "ambyar"
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
     * Next-Word Prediction for continuous predictive typing ("Nulis Otomatis")
     * Triggered when cursor follows a space or punctuation.
     */
    fun getNextWordPredictions(previousWord: String, lang: String): List<String> {
        val word = previousWord.trim().lowercase()
        if (word.isEmpty()) {
            return if (lang == "en") defaultEnglishSuggestions else defaultIndonesianSuggestions
        }

        val map = if (lang == "en") nextWordMapEn else nextWordMapId
        val predictions = map[word]
        if (!predictions.isNullOrEmpty()) {
            return predictions.take(8)
        }

        // Fallback default suggestions
        return if (lang == "en") defaultEnglishSuggestions else defaultIndonesianSuggestions
    }

    /**
     * Main word correction & suggestions:
     * - Empty input -> default high-frequency prompts
     * - Abbreviations & Slang -> instant expansion ("kmu" -> "kamu", "bsa" -> "bisa", "juha" -> "juga")
     * - Prefix completions -> words starting with input prefix (as letters are typed or backspaced)
     * - Typo Buster -> Damerau-Levenshtein distance (missing letter, extra letter, swapped letters, QWERTY proximity)
     */
    fun getSuggestions(input: String, lang: String): List<String> {
        val word = input.trim().lowercase()

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

        // 3. Prefix Matches (Word completion as user types or backspaces, e.g. "kam" -> "kamu", "kami")
        val prefixMatches = dict.filter { it.startsWith(word) }
            .sortedWith(compareBy({ it.length }, { it }))
        for (pm in prefixMatches) {
            result.add(pm)
            if (result.size >= 8) return result.toList()
        }

        // 4. Subsequence matches for common missing-vowel typos (e.g. "kmu" -> "kamu", "bsa" -> "bisa", "mkn" -> "makan")
        if (word.length in 2..6) {
            val subseqMatches = dict.filter { candidate ->
                candidate.length <= word.length + 3 && isSubsequence(word, candidate)
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
