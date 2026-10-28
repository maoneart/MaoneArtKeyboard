# Persistent Memory — MaoneArt iOS Keyboard for Android

> **Repository**: [https://github.com/maoneart/MaoneArtKeyboard](https://github.com/maoneart/MaoneArtKeyboard)  
> **Workspace**: `/sdcard/www/MaoneArtKeyboard`  
> **Target Release APK**: `/sdcard/Download/iOSKeyboard.apk` (v1.0.25)

---

## 📌 Architecture & Technical Decisions
1. **1-Key Permanent Signing Architecture (`keystore/release.jks`)**:
   - Sertifikat RSA 2048-bit mandiri (`SHA384withRSA`) disimpan permanen di repositori (`keystore/release.jks`) dengan masa berlaku hingga tahun 2054 (28 tahun).
   - Menghilangkan regenerasi debug keystore acak di GitHub Actions runner.
   - Semua build masa depan memiliki signature fingerprint identik (`3C:1C:CA:35:A9:6B:89:C2:AF:A4:A6:DB:16:7D:8F:4F:C4:8B:41:34:7F:8F:86:E8:6B:52:E4:07:B5:D0:9C:F1`).
   - **Hasil**: Pengguna dapat langsung menimpa / update file APK kapan saja tanpa perlu uninstall aplikasi terlebih dahulu (pengaturan, preferensi, dan status keyboard aktif tidak akan hilang).
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
  - Haptic Dual-Layer, Cursor Translate, Auto-Close & Nulis Otomatis (Release v1.0.22/v1.0.23/v1.0.25):
    1. **Tampilan Slider (SeekBar) Suara & Getar di Menu Setting**: Mengganti dialog pop-up dengan slider SeekBar horizontal langsung di kartu pengaturan. Pengguna bisa menggeser volume suara dari 0% (paling rendah/senyap) hingga 100%, serta kekuatan getar dari 5 ms (paling lembut) hingga 100 ms, dengan real-time live preview saat digeser.
    2. **Perbaikan Getaran Tuts Huruf Total (Direct OneShot Hardware Pulse)**: Menghapus `EFFECT_CLICK` yang di-mute oleh kernel vendor ROM Android. Menggunakan `VibrationEffect.createOneShot(durationMs, amplitude)` + `AudioAttributes(USAGE_ASSISTANCE_SONIFICATION)` sehingga satu ketukan huruf mana pun di keyboard dijamin bergetar mantap dan responsif.
    3. **Kursor Interaktif Mode Terjemahan**: Kursor biru khas iOS (`│`) dapat digeser langsung dengan sentuhan jari untuk edit di tengah kalimat.
    4. **Auto-Close Mode Terjemahan**: Panel otomatis tertutup setelah kirim atau saat pindah chat.
    5. **Nulis Otomatis Berantai (Next-Word Prediction)**: 3.000+ kosakata dengan N-Gram prediction memungkinkan user mengetik kalimat lengkap hanya dengan tap bilah saran beruntun.
    6. **Flexible Return Key**: Ikon `🔍` untuk pencarian web, `Buka` untuk URL, dan `↵` untuk WhatsApp & multiline fields.
