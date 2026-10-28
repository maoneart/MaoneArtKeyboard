# Persistent Memory — MaoneArt iOS Keyboard for Android

> **Repository**: [https://github.com/maoneart/MaoneArtKeyboard](https://github.com/maoneart/MaoneArtKeyboard)  
> **Workspace**: `/sdcard/www/MaoneArtKeyboard`  
> **Target Release APK**: `/sdcard/Download/iOSKeyboard.apk` (v1.0.17)

---

## 📌 Architecture & Technical Decisions
1. **1-Key Permanent Signing Architecture (`keystore/release.jks`)**:
   - Sertifikat RSA 2048-bit mandiri (`SHA384withRSA`) disimpan permanen di repositori (`keystore/release.jks`) dengan masa berlaku hingga tahun 2054 (28 tahun).
   - Menghilangkan regenerasi debug keystore acak di GitHub Actions runner.
   - Semua build masa depan memiliki signature fingerprint identik (`3C:1C:CA:35:A9:6B:89:C2:AF:A4:A6:DB:16:7D:8F:4F:C4:8B:41:34:7F:8F:86:E8:6B:52:E4:07:B5:D0:9C:F1`).
   - **Hasil**: Pengguna dapat langsung menimpa / update file APK kapan saja tanpa perlu uninstall aplikasi terlebih dahulu (pengaturan, preferensi, dan status keyboard aktif tidak akan hilang).
2. **Core IME**: Native Android `InputMethodService` (`IosInputMethodService.kt`) dengan latency input 0ms dan konsumsi RAM sangat hemat (~15 MB).
2. **Keyboard Layout & Geometry**: Mengikuti aspect ratio, key margin, inset padding, dan warna tombol iOS 18 (QWERTY, Symbols `#+=` dan `123`, serta laci Apple Emoji). Tombol keyboard di-render menggunakan `TextView` murni (bukan `Button`) untuk eliminasi delay rendering, konflik MaterialButton, dan shadow artefak.
3. **Safe Inflation & Ripple**: Toolbar dan icon menggunakan circular ripple drawable mandiri (`btn_icon_ripple.xml`), eliminasi referensi `?attr/selectableItemBackgroundBorderless` yang memicu InflateException pada Android IME service.
4. **Cupertino Settings Hub**: Tampilan pengaturan berbasis iOS Inset Grouped layout (`activity_settings.xml`) dengan squircle colored icons iOS, live status badge deteksi IME aktif/default, switch dengan track hijau Apple (`#34C759`), dan live test pad.
5. **Samsung-Style Live Translate Panel**: Panel input kalimat terisolasi di atas keyboard. Pengguna mengetik kalimat lengkap di dalam kotak terjemahan keyboard, lalu menekan tombol `[Kirim ➔]` untuk menerjemahkan sekaligus ke aplikasi tujuan (misal WhatsApp), meniadakan penerjemahan patah perkata ("tidak 1-1").
6. **Animated Bouncing Emojis**: Laci emoji Apple dilengkapi animasi pegas dinamis (*tactile spring bounce pop*) menggunakan `OvershootInterpolator(3.0f)` saat disentuh (`scale 1.45x`), menghidupkan animasi emoji khas iOS.
7. **WhatsApp & Multiline Return / Enter Key**: Deteksi tipe input dinamis pada `updateReturnButton(editorInfo)`. Menampilkan simbol Enter iOS (`↵`) pada chat dan form multiline (WhatsApp, Telegram, Notes). Penekanan tombol Enter pada multiline field langsung mengirimkan `\n` via `commitText("\n", 1)` untuk menjamin baris baru terbuat tanpa hilang/diabaikan oleh app.
8. **Smart Typo Buster & Multi-Candidate Strip (v1.0.11)**:
   - **Kamus Kaya & Slang Chat (1.500+ kata)**: Meliputi kosa kata sehari-hari, kata ganti (`aku`, `kamu`, `dia`, `kita`, `kami`), kata kerja, kata tanya, singkatan chat (`kmu`, `bsa`, `ak`, `mw`, `udh`, `blm`, `lgi`, `jg`, `juha`, `bgt`, `sy`, `tdk`, `ga`, `gpp`, `mksih`, dll.).
   - **Sinkronisasi Kata Real-Time saat Hapus (Backspace)**: Membaca token kata sebelum kursor via `getCurrentWordBeforeCursor()` sehingga saat pengguna menghapus karakter satu per satu, bilah rekomendasi langsung mengupdate pilihan kata yang sesuai dengan sisa huruf. Saat input kosong atau setelah spasi, otomatis menyajikan kata-kata umum awal (`aku`, `kamu`, `bisa`, `mau`, `lagi`, `sudah`).
   - **Deteksi Typo Cerdas (Kurang Huruf, Salah Ketik, Kedekatan QWERTY)**: Algoritma Damerau-Levenshtein yang diperkaya deteksi subsequences untuk typo huruf hilang (`kmu` -> `kamu`, `bsa` -> `bisa`), transposisi huruf tertukar (`akmu` -> `kamu`), dan bonus kedekatan tuts keyboard QWERTY (`juha` -> `juga`).
   - **Scrollable Candidate Strip (Lebih Banyak Rekomendasi)**: Bilah saran kini berupa `HorizontalScrollView` dengan desain chip iOS (teks tebal pada saran pertama, teks reguler pada saran lanjutan, dipisahkan garis vertikal tipis, mendukung 6-8 rekomendasi yang dapat digeser).
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
