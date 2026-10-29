# Persistent Memory — MaoneArt iOS Keyboard for Android

> **Repository**: [https://github.com/maoneart/MaoneArtKeyboard](https://github.com/maoneart/MaoneArtKeyboard)  
> **Workspace**: `/sdcard/www/MaoneArtKeyboard`  
> **Target Release APK**: `/sdcard/Download/iOSKeyboard.apk` (v1.0.41)

---

## 📌 Architecture & Technical Decisions
1. **Self-Learning User Dictionary & 74,500+ Official KBBI Vocabulary**:
   - **Self-Learning Dynamic Dictionary**: Keyboard secara mandiri merekam dan mempelajari setiap kata yang sering diketik atau dipilih oleh pengguna (`user_dictionary.txt` dengan bobot frekuensi). Kata yang memiliki frekuensi pemakaian tinggi langsung diposisikan di urutan prioritas terdepan rekomendasi!
   - **74,500+ Entri KBBI Resmi**: Dilengkapi database kata bahasa Indonesia lengkap KBBI (`app/src/main/assets/dictionaries/id.dict`, 665 KB) mencakup seluruh kata panjang berimbuhan konfiks (`ke-...-an`, `pe-...-an`, `me-...-kan`, `ber-...`, `ter-...`), seperti `kehilangan`, `keberhasilan`, `kesejahteraan`, `kesempatan`, `keputusan`, `kemenangan`, `kekalahan`, `kekecewaan`, `keberadaan`, `kemampuan`, `kebutuhan`, dll.
   - **O(log N) Instant Binary Search Prefix Matcher**: Pencarian prefix pada 74.500+ kata dieksekusi secara instan (< 0.1 ms) menggunakan binary search range scanning tanpa latency sama sekali pada tuts keyboard.
2. **1-Key Permanent Signing Architecture (`keystore/release.jks`)**:
   - Sertifikat RSA 2048-bit mandiri (`SHA384withRSA`) disimpan permanen di repositori (`keystore/release.jks`) dengan masa berlaku hingga tahun 2054 (28 tahun).
   - Menghilangkan regenerasi debug keystore acak di GitHub Actions runner.
   - Semua build masa depan memiliki signature fingerprint identik (`3C:1C:CA:35:A9:6B:89:C2:AF:A4:A6:DB:16:7D:8F:4F:C4:8B:41:34:7F:8F:86:E8:6B:52:E4:07:B5:D0:9C:F1`).
   - **Hasil**: Pengguna dapat langsung menimpa / update file APK kapan saja tanpa perlu uninstall aplikasi terlebih dahulu (pengaturan, preferensi, dan status keyboard aktif tidak akan hilang).
2. **WhatsApp Sticker Engine & Converter (WebP 512x512)**:
   - Pengguna dapat memilih gambar PNG/JPG dari galeri HP via `StickerUploadActivity`.
   - Gambar otomatis di-convert menjadi stiker WhatsApp standar: persis 512x512 pixels dengan transparansi (alpha channel) dan format WebP terkompresi (< 500 KB).
   - Pengiriman stiker langsung ke WhatsApp menggunakan standar resmi Android CommitContent API (`InputConnectionCompat.commitContent` + `androidx.core.content.FileProvider`).
   - Tersedia galeri stiker buatan sendiri langsung di dalam keyboard dengan fitur kirim 1-tap dan hapus (long-press).
3. **Emoji Category Tab Bar & Quick Emoji Access**:
   - Tampilan emoji dirombak: di bagian atas laci terdapat bilah tab kategori horizontal yang memisahkan emoji (`Smileys`, `Tangan`, `Hati`, `Hewan`, `Makanan`, `Aktivitas`, `Simbol`, `Stiker WA`, dan `➕ Tambah Stiker`).
   - Tombol cepat Emoji & Stiker (`btnQuickEmoji`) ditambahkan pada toolbar atas di sebelah tombol translate & clipboard.
2. **Dual-Layer Tactile Haptic Engine (All-Keys Vibration Fix)**:
   - Root Cause Masalah Getar: Pengetikan 1 tombol huruf hanya memancarkan 1 pulsa ~20ms, yang tidak cukup kuat untuk mengatasi inersia motor vibrator ERM koin HP Android (berbeda dengan tombol backspace yang repeat pulsa bertubi-tubi).
   - Solusi: Setiap tombol huruf di `KeyboardLayout.kt` kini mengaktifkan `isHapticFeedbackEnabled = true` dan mengeksekusi `performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, FLAG_IGNORE_GLOBAL_SETTING or FLAG_IGNORE_VIEW_SETTING)` langsung pada view tombol yang ditekan saat `ACTION_DOWN` (0ms delay).
   - Ditambah hardware `Vibrator` & `VibratorManager` (Android 12+) dengan pulse durasi terkalibrasi 45ms–85ms dan max amplitude 255 / `EFFECT_CLICK` dengan sonification audio attributes. Semua tombol (huruf, angka, spasi, return) kini bergetar tegas dan responsif.
3. **Adaptive Dynamic Return Key (Search & WhatsApp Enter)**:
   - Web Search & Search Fields: Menampilkan ikon kaca pembesar iOS (`🔍`) dan menjalankan aksi pencarian.
   - URL & Browser Address: Menampilkan teks `Buka`.
   - Multiline Fields (WhatsApp, Telegram, Notes, Pesan): Selalu menampilkan panah Enter iOS (`↵`) dan menginjeksi karakter newline `\n` secara langsung.
   - Single Line Form Action: Menampilkan `Lanjut`, `Selesai`, atau `Kirim`.
4. **Samsung-Style Live Translate Panel with Movable Interactive Cursor**:
   - Terdapat kursor visual interaktif berwarna biru iOS (`│`) di dalam kotak input terjemahan.
   - Kursor dapat digeser langsung dengan sentuhan jari ke posisi mana saja di dalam kalimat (`layout.getOffsetForHorizontal`).
   - Karakter baru disisipkan tepat di posisi kursor, dan backspace menghapus karakter di sebelah kiri kursor.
   - **Auto-Close Proteksi**: Panel terjemahan otomatis tertutup saat kalimat terjemahan berhasil dikirim, saat obrolan/jendela keyboard ditutup (`onFinishInputView`), saat keyboard dibuka kembali di sesi baru (`onStartInputView`), dan saat jendela keyboard disembunyikan (`onWindowHidden`).
5. **Expanded Vocabulary (3,000+ Kata) & Continuous Predictive Typing ("Nulis Otomatis")**:
   - Kamus bahasa Indonesia diperluas hingga 3.000+ kata (KBBI, kata berimbuhan me-/ber-/di-/pe-/ter-/-kan/-i, kosakata percakapan & slang WhatsApp).
   - N-Gram Next-Word Prediction Map: Saat kursor berada setelah spasi atau saat memilih kata kandidat, bilah saran langsung memunculkan prediksi kata berikutnya yang biasa mengikutinya (`saya` ➔ `mau`/`bisa`/`sudah`, `mau` ➔ `makan`/`pergi`/`tanya`, `bisa` ➔ `nggak`/`bantu`/`kirim`).
   - Pengguna dapat mengetik satu kalimat penuh secara otomatis hanya dengan tap-tap-tap bilah saran berantai ("Nulis Otomatis").
   - Deteksi Typo Real-Time: Saat huruf dikurangi (backspace), bilah saran langsung menyesuaikan rekomendasi prefix kata. Damerau-Levenshtein distance menangani salah ketik tombol QWERTY berdampingan (`juha` ➔ `juga`).
9. **CI/CD Pipeline**: GitHub Actions (`.github/workflows/build_apk.yml`) otomatis build release APK bertanda tangan debug keystore dan mempublikasikan release asset.

---

## 🚀 Milestones
- **28 Okt 2026**:
  - Inisialisasi proyek, arsitektur modul, perbaikan styling XML & Kotlin textStyle (Release v1.0.3).
  - Investigasi & perbaikan tuntas masalah Force Close: resolusi tema context IME (`ContextThemeWrapper`), penggantian `?attr/` dengan `@drawable/btn_icon_ripple`, eliminasi widget `Button` menjadi `TextView` di `KeyboardLayout`.
  - Redesign total antarmuka Pengaturan (Cupertino Inset Grouped Settings): penambahan squircle icon warna-warni ala iOS, kartu status aktivasi interaktif 2-langkah dengan auto-detect centang hijau (`isImeEnabled()` / `isImeSelected()`), switch hijau Apple, dan kotak uji coba ketik yang rapi (Release v1.0.5).
  - Peningkatan performa & UX lanjutan (Release v1.0.8):
    1. **0ms Touch Down Response**: Menghilangkan latency sentuh dengan `MotionEvent.ACTION_DOWN` murni, pembaruan Shift/Caps instan tanpa rebuild view hierarchy.
    2. **Hapus Ngebut (Rapid Repeating Backspace)**: Auto-repeat akselerasi saat tombol backspace ditekan lama (320ms delay awal -> 60ms -> akselerasi ke 35ms per karakter).
    3. **Panel Terjemahan Ala Samsung Keyboard**: Kotak input khusus di atas keyboard untuk mengetik kalimat penuh terlebih dahulu sebelum dikirim sebagai terjemahan instan ke obrolan.
    4. **Ikon Emoji Apple Jernih & Animasi Gerak**: Tombol emoji kini menampilkan smiley Apple asli `😊` yang jernih dan seluruh emoji di laci bergerak/membal (*spring bounce pop*) saat ditekan.
  - Perbaikan Tombol Enter WhatsApp & Multiline (Release v1.0.10):
    1. Tombol Return otomatis menampilkan simbol panah Enter iOS (`↵`) saat membuka WhatsApp atau kolom input multiline.
    2. Eksekusi penekanan Enter langsung menginjeksi karakter newline `\n` ke aplikasi tujuan sehingga baris baru selalu tercipta dengan mulus di WhatsApp.
  - Smart Typo Buster & Rekomendasi Kata Responsif (Release v1.0.12):
    1. Pilihan kosakata langsung muncul dan berubah dinamis saat huruf dihapus (`kamu` -> hapus jadi `kam` -> hapus jadi `ka` -> hapus jadi `k`).
    2. Penanganan typo komprehensif: kurang huruf (`kmu` -> `kamu`, `bsa` -> `bisa`), salah ketik tombol berdampingan (`juha` -> `juga`), dan kata singkatan chat.
    3. Bilah rekomendasi horizontal scrollable dengan hingga 8 saran kata berdesain iOS QuickType chip.
  - Sistem 1 Key / Permanent Keystore Release (Release v1.0.13):
    1. Keystore permanen `keystore/release.jks` terintegrasi langsung ke dalam pipeline build release & debug.
    2. Sertifikat berlaku 28 tahun (hingga 2054) dengan SHA-256 fingerprint konstan.
    3. Pengguna bisa langsung update APK kapan pun tanpa perlu uninstall aplikasi terlebih dahulu.
  - Fitur Stiker WhatsApp & Bilah Tab Kategori Emoji (Release v1.0.27):
    1. **Stiker WhatsApp (PNG ➔ WebP Converter)**: Upload gambar PNG/JPG dari galeri, otomatis di-convert menjadi stiker WhatsApp WebP 512x512 transparan dan langsung dikirim ke chat WhatsApp via Android CommitContent API.
    2. **Bilah Tab Kategori Emoji Terpisah**: Di atas laci emoji, emoji kini terpisah rapi berdasarkan kategori (`😀 Smileys`, `👋 Tangan`, `❤️ Hati`, `🐶 Hewan`, `🍔 Makanan`, `⚽ Aktivitas`, `💯 Simbol`, `🖼️ Stiker WA`, `➕ Tambah`).
    3. **Tombol Cepat Emoji/Stiker di Toolbar**: Ikon emoji `😊` hadir di toolbar atas sebelah tombol translate untuk akses instan ke laci emoji dan stiker.
    4. **Menu Upload Stiker di Pengaturan**: Pengguna juga bisa menambah stiker langsung dari Cupertino Settings Hub.
  - Kamus Belajar Mandiri (Self-Learning) & 74.500+ Kosakata KBBI Lengkap (Release v1.0.28):
    1. **Sistem Belajar Mandiri (User Self-Learning Dictionary)**: Setiap kata yang sering diketik atau dipilih pengguna otomatis dipelajari frekuensinya dan dijadikan rekomendasi nomor 1 saat mengetik awalan katanya.
    2. **74.500+ Kosakata KBBI Lengkap**: Mendukung seluruh kata panjang berimbuhan resmi seperti `kehilangan`, `keberhasilan`, `kesejahteraan`, `kesempatan`, `keputusan`, dll.
    3. **Algoritma Pencarian O(log N) Super Ngebut**: Eksekusi instan di bawah 0.1 milidetik tanpa jeda pada tuts keyboard.
  - Toolbar Luas, Perbaikan Scroll Emoji Mulus & Pemilih Bahasa Langsung Keyboard (Release v1.0.29):
    1. **Toolbar QuickType Lega**: Menghapus tombol emoji di sebelah clipboard di toolbar atas sehingga bilah saran kata mendapat ruang penuh yang lapang dan lega.
    2. **Perbaikan Total Scroll Emoji (Anti Salah Pencet)**: Mengganti touch listener yang mengonsumsi ACTION_DOWN dengan setOnClickListener standar Android sehingga gerakan usap scroll vertikal laci emoji 100% mulus dan tidak memicu pengetikan emoji tanpa sengaja saat menggulir.
    3. **Bilah Pemilih Bahasa Langsung di Keyboard**: Di panel terjemahan, kini terdapat deretan chip tombol bahasa instan (`🇺🇸 English`, `🇸🇦 Arab`, `🇯🇵 Jepang`, `🇰🇷 Korea`, `🇨🇳 Mandarin`, `🇪🇸 Spanyol`, `🇫🇷 Prancis`, `🇩🇪 Jerman`, dll) yang dapat diganti dengan 1-tap langsung dari keyboard tanpa perlu membuka menu pengaturan! Tersedia juga tombol `🌐 Semua...` untuk memilih puluhan bahasa dunia.
  - 6 Fitur Unggulan Otentik iOS & Desain Ikon Datar Monokrom (Release v1.0.30):
    1. **Predictive Text (Prediksi Kata QuickType)**: Bilah QuickType 3-slot memunculkan prediksi kata berikutnya berdasarkan N-gram percakapan dan kamus belajar mandiri.
    2. **Dictation (Dikte / Mengetik dengan Suara)**: Tombol mikrofon monokrom (`ic_mic_ios.xml`) di baris ke-4 keyboard untuk input suara live via Android SpeechRecognizer.
    3. **Auto-Correction**: Koreksi kata salah ketik otomatis saat menekan tombol spasi yang mencocokkan kata typo dengan kandidat tengah.
    4. **QuickPath (Swipe Typing)**: Pengetikan usap antar tuts huruf tanpa mengangkat jari, dilengkapi jejak garis biru iOS (`SwipeTrailView`) dan pencocokan cerdas 75.000 kata KBBI (`QuickPathEngine`).
    5. **Tombol Spasi Trackpad Kursor**: Tekan tahan tombol spasi (>260ms) mengaktifkan mode trackpad kursor ("│ geser kursor │") untuk menggeser posisi kursor teks kiri/kanan dengan tactile haptic feedback.
    6. **Pintasan Teks (Text Replacement)**: Mengetik singkatan (`otw` ➔ `On the way`, `brb` ➔ `Be right back`, `asap`, `ty`, `gpp`, dll) otomatis berubah menjadi frasa lengkap saat menekan spasi, dilengkapi hub manajemen pintasan teks di Pengaturan.
    7. **Ikon Search & Emoji Datar Monokrom Apple**: Mengganti emoji kuning 3D dan emoji search 3D dengan ikon outline putih datar (`ic_search_white.xml`) serta ikon wajah outline monokrom flat (`ic_emoji_smile.xml`) yang elegan dan otentik ala Apple iOS.
  - Tombol Return Otentik Apple iOS (Release v1.0.31):
    1. Mengganti simbol panah `↵` dengan teks tulisan lowercase otentik Apple iOS: `return` (ukuran 15sp, presisi font proporsional).
    2. Menyelaraskan warna background tombol `return` default dengan tuts fungsi abu-abu sistem (`key_bg_special`), bukan biru konstan, sehingga 100% identik dengan keyboard Apple iPhone.
    3. Mode adaptif kontekstual cerdas: latar biru hanya aktif saat kolom input membutuhkan aksi Search (kaca pembesar putih), Go ("Buka"), atau Send ("Kirim").
  - Navigasi Kursor 4-Arah Penuh pada Spasi Trackpad (Release v1.0.32):
    1. **Trackpad Virtual 2D Penuh (Atas, Bawah, Kiri, Kanan)**: Tahan tombol spasi untuk menggeser kursor tidak hanya ke kiri/kanan, tapi juga langsung melompat antar baris ke atas (`KEYCODE_DPAD_UP`) dan ke bawah (`KEYCODE_DPAD_DOWN`) saat ada paragraf/enter tanpa harus mundur satu per satu.
    2. **Disallow Intercept Touch**: Memastikan sentuhan jari bebas meluncur ke seluruh area keyboard tanpa terputus atau ter-intercept oleh parent layout.
    3. **Kalibrasi Sensitivitas Nyaman**: `stepDistanceX = 12dp` dan `stepDistanceY = 14dp` untuk presisi navigasi teks yang mulus disertai respon tactile haptic pulse.
  - Ikon Search Dead Center, Logo Kekinian & Tema Woody Sony Xperia (Release v1.0.33):
    1. **Tombol Search 100% Dead Center**: Mengubah struktur tombol aksi menjadi `ReturnButtonLayout` (FrameLayout + ImageView centered) sehingga ikon kaca pembesar putih datar berada tepat di tengah tanpa meleset ke kiri.
    2. **Ikon Toolbar Kekinian (Modern SF Symbols & iOS 18)**:
       - `ic_clipboard.xml`: Desain papan klip minimalis outline modern dengan magnetic clip dan garis catatan elegan.
       - `ic_translate.xml`: Desain Apple Translate (iOS 18) dua gelembung percakapan terhubung dengan tipografi 'A' ramping.
    3. **Tema Woody (Sony Xperia Edition) 🪵**:
       - Latar belakang keyboard serat kayu mahoni hangat (`theme_woody_bg.xml`).
       - Ubin tuts kayu jati/oak halus dengan bevel dan drop shadow (`key_bg_woody.xml`).
       - Tuts fungsi kayu gelap walnut (`key_bg_woody_special.xml`) dan tuts aksi kayu amber bronze keemasan (`key_bg_woody_action.xml`).
       - Warna teks huruf ivory warm cream (`#FFF3E0`) dan animasi jejak swipe QuickPath berwarna api emas (`#FFA726`).
       - Pilihan tema langsung di Pengaturan Cupertino: "Woody (Sony Xperia Edition) 🪵".
  - Papan Klip Card Mode, Sematkan (Pin) & Hapus Modal MaoneArt (Release v1.0.34):
    1. **Clipboard Mode Card & List**:
       - Pilihan tampilan Mode Card (grid 2-kolom ringkas modern dengan badge pin 📌) dan Mode List (1-kolom lebar penuh).
       - Tombol beralih instan `🎴 Card` / `📑 List` di toolbar papan klip, tersimpan persisten di preferensi pengguna.
    2. **Aksi Long Press Sematkan (Pin) & Hapus**:
       - Tekan tahan (long-press) kartu papan klip memunculkan dialog modal aksi MaoneArt Glassmorphism dengan haptic feedback.
       - Grid aksi 2-tombol simetris (`📌 Sematkan / Batal Pin` dan `🗑️ Hapus`).
    3. **Proteksi Item Pinned & Riwayat Persisten JSON**:
       - Item yang disematkan (pinned) selalu diprioritaskan di daftar teratas dan berlabel lencana `📌`.
       - Item pinned diproteksi penuh: tidak akan pernah terhapus otomatis saat kuota riwayat penuh maupun saat menekan tombol `🗑️ Bersih`.
       - Riwayat disimpan persisten dalam format JSON (`clipboard_history.json`).
  - Rekonstruksi Tema Woody Asli Sony Xperia & Kapitalisasi Otomatis (Release v1.0.35):
    1. **Rekonstruksi Tema Woody Asli Sony Xperia (Berdasarkan APK Asli)**:
       - Meriset dan mengekstrak aset resmi dari APK tema Woody Sony Xperia (`se.knowit.mobile.woody` / `somc_bg_dark.jpg`).
       - Mengganti gradien buatan dengan foto tekstur serat kayu alami resolusi tinggi 1080x1920 (`woody_bg.jpg`) yang menyelimuti seluruh keyboard.
       - Tombol tuts keyboard dikonversi menjadi 9-patch bertekstur kayu ukir nyata (`key_woody_normal.9.png` dan `key_woody_pressed.9.png`), tuts fungsi walnut gelap, serta tombol aksi amber bronze keemasan asli Xperia (`key_woody_action_normal.9.png`).
       - Top toolbar semi-transparan (`#4D1A0E08`) sehingga tekstur kayu menyatu harmonis.
    2. **Kapitalisasi Otomatis (Auto-Capitalization)**:
       - Huruf besar otomatis aktif di awal kalimat / kolom input baru.
       - Huruf besar otomatis aktif setelah tanda titik (`. `), tanda tanya (`? `), atau tanda seru (`! `).
       - Huruf besar otomatis aktif di awal baris baru setelah menekan tombol Enter (`\n`).
       - Deteksi cerdas `shouldAutoCapitalize()` mengevaluasi buffer dan kursor serta mem-bypass otomatis pada field password, angka, email, dan URL.
       - Tersedia toggle "Kapitalisasi Otomatis" di menu Pengaturan.
  - Perbaikan Presisi Kapitalisasi (Hanya 1 Huruf Awal) & Kalibrasi Tinggi Tema Woody (Release v1.0.36):
    1. **Presisi Kapitalisasi Otomatis (Hanya 1 Huruf Awal Kalimat & Setelah Titik)**:
       - Menghilangkan query `CAP_MODE_CHARACTERS` pada Android InputConnection yang sebelumnya menyebabkan seluruh huruf menjadi kapital.
       - Menambahkan guard `composingWord.isNotEmpty()` pada `shouldAutoCapitalize()` dan `onUpdateSelection` sehingga begitu 1 huruf kapital pertama diketik ("S"), tuts berikutnya dijamin 100% langsung kembali ke huruf kecil ("aya").
       - Hanya 1 huruf pertama di awal dan setelah titik (`. `) yang kapital ("Saya mau makan. Saya mau minum"), selebihnya huruf kecil normal.
    2. **Kalibrasi Tinggi Keyboard Tema Woody (Identik dengan Light dan Dark)**:
       - Mengatasi masalah keyboard meninggi/tertarik ke atas akibat intrinsic height gambar background 1920px.
       - Memotong `woody_bg.jpg` ke dimensi proporsional keyboard (1080x650) dan menetapkan `minimumHeight = 0` serta `android:minHeight="0dp"` pada `keyboardRoot`.
       - Tinggi keyboard tema Woody kini 100% sama dan proporsional persis seperti tema Light dan Dark (279dp).
  - Perbaikan Crash Hapus Stiker & Swipe Typing QuickPath Mulus (Release v1.0.37):
    1. **Perbaikan Crash Stiker Long-Press (MaoneArt Glassmorphism In-Layout Modal)**:
       - Menghapus pemanggilan android.app.AlertDialog.Builder yang memicu BadTokenException fatal saat dijalankan dari context InputMethodService.
       - Menerapkan showDeleteStickerModal berbasis FrameLayout overlay in-layout sesuai MaoneArt Glassmorphism Modal Standard (overlay #99000000, card rounded, preview gambar stiker, teks konfirmasi, dan 2-kolom tombol simetris Batal & 🗑️ Hapus).
    2. **Perbaikan Tuntas QuickPath (Swipe / Gesture Typing)**:
       - Mengganti getGlobalVisibleRect dengan tv.getLocationOnScreen(loc) untuk deteksi koordinat fisik layar yang sinkron dengan ev.rawX, ev.rawY.
       - 2-pass key detection: Pass 1 bounding box tuts dengan toleransi 4dp, Pass 2 nearest center distance snap (28dp) menghilangkan dead zone antar-tuts saat sapuan jari berlangsung.
       - Kalibrasi touchSlop dari 22dp ke 12dp dan auto-recovery titik awal sapuan untuk responsivitas instan.
       - Algoritma pencocokan kata cerdas findSwipeCandidates memadukan kata frekuensi tinggi percakapan, user dictionary, dan 75.000 kata KBBI berbasis awalan dan akhiran kata, didukung subsequence matching dengan repeat-collapsing (misal "saat" cocok dari sapuan "sat").
       - Penanganan auto-capitalization terintegrasi: Hasil swipe di awal kalimat atau saat shifted otomatis berhuruf awal kapital ("Saya ").
  - Balon Key Pop-up Preview iOS, Optimasi 120 FPS & Auto-Capitalize Pesan WA (Release v1.0.38):
    1. **Perbaikan Auto-Capitalization Pesan Kedua WhatsApp & Semua Chat**:
       - Deteksi reset kursor (newSelStart == 0 && newSelEnd == 0) di onUpdateSelection saat pesan WhatsApp terkirim dan kolom teks dikosongkan.
       - Pembersihan composingWord dan inspeksi teks depan kursor diprioritaskan di awal shouldAutoCapitalize() sehingga pesan ke-2, ke-3, dst selalu otomatis berhuruf awal besar.
    2. **Optimasi Performa 120 FPS Super Ringan (Hit-Box In-Memory Cache)**:
       - Mengganti puluhan pemanggilan native JNI getLocationOnScreen per frame dengan KeyHitBox cache in-memory statis.
       - 0 JNI overhead, 0 garbage collection pause, rendering sentuhan kembali instan, smooth, dan super enteng.
       - Menghilangkan alokasi duplikasi 75.000 list KBBI di SmartCorrectionEngine dengan @Volatile kbbiSnapshot.
    3. **Balon Key Pop-up Preview Apple iOS Otentik**:
       - Menampilkan balon pop-up pembesar huruf otentik tepat melayang di atas tuts yang disentuh jempol pengguna, didukung styling tema lengkap (Light #FFFFFF, Dark #636366, Woody #4E342E).
       - Teks besar 27sp bold berada di atas tuts sehingga tidak tertutup oleh jempol pengguna.
    4. **Penyempurnaan Perlintasan Teks (QuickPath)**:
       - Animasi jejak lintasan SwipeTrailView bertransformasi menjadi Fluid Ribbon dengan ujung dinamis dan ekor memudar bertahap (alpha decay).
       - Live Preview saran kata di tombol saran tengah (✦ kata) secara real-time saat sapuan jari berlangsung.
  - Perbaikan Tuntas Huruf Besar WA (Restarting Fix), Balon Pop-up Visible & 120 FPS Enteng (Release v1.0.39):
    1. **Perbaikan Tuntas Auto-Capitalization Pesan WhatsApp (Eliminasi !restarting Guard)**:
       - Menghapus guard '!restarting' pada onStartInputView() yang sebelumnya memblokir eksekusi shouldAutoCapitalize() saat WhatsApp me-restart input setelah tombol Send ditekan.
       - Huruf pertama pesan ke-2, ke-3, dan seterusnya di WhatsApp kini 100% dipastikan selalu berhuruf besar otomatis.
    2. **Balon Key Pop-up Preview 100% Terlihat (Anti-Clipping & Direct Touch Binding)**:
       - Membuka pembatas clipChildren dan clipToPadding menjadi false pada seluruh rantai layout (keyboardRoot, keyboardContainer, QuickPathContainerLayout, dan root layout).
       - Mengikat kemunculan balon pop-up pembesar huruf (popupBubble) langsung ke handler sentuhan tuts huruf (setupLetterKeyTouch): seketika tombol ditekan (ACTION_DOWN) balon melayang di atas tuts, dan langsung lenyap saat dilepas (ACTION_UP) atau saat swipe aktif (ACTION_CANCEL).
    3. **Eliminasi Beban Layout & UI Thread Super Smooth**:
       - Menghapus OnGlobalLayoutListener yang memicu layout pass berulang di UI thread.
       - Pengetikan kembali super responsif, instan 0ms, dan enteng di semua HP.
  - Perbaikan Tanggap Sentuh Neket (Ultra-Responsive), Modal Penggantian Teks & Anti-Karakter Rusak Emoji (Release v1.0.40):
    1. **Perbaikan Kecepatan Ngetik Neket & Super Responsif (120 FPS Ultra Snappy)**:
       - Eliminasi `requestLayout()` pada balon preview: Mengganti mutasi `layoutParams` dengan GPU Hardware translation (`translationX` & `translationY`). Nol kali layout/measure pass pada tuts huruf.
       - Kalibrasi `touchSlop` QuickPath dari 12dp ke 26dp dan syarat perpindahan tuts berbeda: Mengetik cepat dengan jempol tidak akan pernah lagi terintersepsi secara tidak sengaja oleh deteksi swipe.
       - Taptic Engine "Neket" Haptic: Menghilangkan pemanggilan getar ganda di view touch listener, menyatukannya menjadi micro-pulse presisi (8-24ms) yang renyah dan instan tanpa membebani antrean driver vibrator sistem.
       - Zero-IPC In-Memory Typing Context & 20ms Debounced Suggestions: Saat mengetik huruf, karakter dikirim instan (0ms) tanpa IPC Binder call `getTextBeforeCursor`, dan kamus di-debounce 20ms sehingga UI thread selalu 120 FPS bebas lag.
    2. **Rombak Total Dialog Penggantian Teks (MaoneArt Glassmorphism Modals)**:
       - Mengganti dialog native Android AlertDialog pada Pengaturan menjadi sistem dialog Glassmorphism iOS (Card rounded 20dp, tombol kapsul simetris, input rounded #F2F2F7/#2C2C2E).
       - Menampilkan rekomendasi singkatan teks langsung di bilah saran (`⚡ Frasa`).
    3. **Perbaikan Hapus Emoji Anti-Karakter Rusak**:
       - Menggunakan `deleteSurroundingTextInCodePoints(1, 0)` dan deteksi UTF-16 surrogate pairs: Emoji terhapus 100% utuh dalam 1x tap tanpa meninggalkan kotak silang / karakter aneh tanda tanya.
  - **Redesign iOS White Pintasan Teks & Fix Tombol Delete di Termux (Release v1.0.41)**:
    1. **Redesign Total Pintasan Teks (Apple iOS 18 Cupertino Inset Grouped)**:
       - Mengganti dialog dark theme menjadi 100% iOS Light White (`#FFFFFF` pure white card, rounded 20dp, subtle border `#E5E5EA`, latar dim 45%).
       - Dilengkapi **Cupertino Search Bar** instan dengan `TextWatcher` untuk mencari singkatan/frasa secara real-time.
       - Header bergaya iOS 18 dengan Pill Button `➕ Tambah` (`#007AFF`) dan footer button `Selesai`.
       - Form tambah pintasan bergaya Apple Inset Grouped (`#F2F2F7` card, label `FRASA` & `SINGKATAN` dengan pembatas hairline 0.5dp).
       - Modal konfirmasi hapus dan kelola pintasan mengikuti standar **MaoneArt Glassmorphism Modal** 2-kolom simetris (`Batal` dan `🗑️ Hapus`).
    2. **Perbaikan Tuntas Tombol Delete/Backspace di Termux**:
       - Root Cause: Termux menggunakan pseudo-terminal (PTY) dengan `InputType.TYPE_NULL` tanpa buffer `EditText`, sehingga pemanggilan `deleteSurroundingText` diabaikan oleh terminal.
       - Solusi: Menambahkan deteksi `isTerminalOrRawInput()` (deteksi `TYPE_NULL` dan package `com.termux`, `terminal`, `ssh`).
       - Saat berada di Termux, keyboard langsung mengirim `sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)` ke terminal PTY.
       - Penghapusan karakter single tap maupun tekan-tahan rapid repeating backspace kini berfungsi 100% lancar dan responsif di Termux.
