# Persistent Memory — MaoneArt iOS Keyboard for Android

> **Repository**: [https://github.com/maoneart/MaoneArtKeyboard](https://github.com/maoneart/MaoneArtKeyboard)  
> **Workspace**: `/sdcard/www/MaoneArtKeyboard`  
> **Target Release APK**: `/sdcard/Download/iOSKeyboard.apk` (v1.0.8)

---

## 📌 Architecture & Technical Decisions
1. **Core IME**: Native Android `InputMethodService` (`IosInputMethodService.kt`) dengan latency input 0ms dan konsumsi RAM sangat hemat (~15 MB).
2. **Keyboard Layout & Geometry**: Mengikuti aspect ratio, key margin, inset padding, dan warna tombol iOS 18 (QWERTY, Symbols `#+=` dan `123`, serta laci Apple Emoji). Tombol keyboard di-render menggunakan `TextView` murni (bukan `Button`) untuk eliminasi delay rendering, konflik MaterialButton, dan shadow artefak.
3. **Safe Inflation & Ripple**: Toolbar dan icon menggunakan circular ripple drawable mandiri (`btn_icon_ripple.xml`), eliminasi referensi `?attr/selectableItemBackgroundBorderless` yang memicu InflateException pada Android IME service.
4. **Cupertino Settings Hub**: Tampilan pengaturan berbasis iOS Inset Grouped layout (`activity_settings.xml`) dengan squircle colored icons iOS, live status badge deteksi IME aktif/default, switch dengan track hijau Apple (`#34C759`), dan live test pad.
5. **Samsung-Style Live Translate Panel**: Panel input kalimat terisolasi di atas keyboard. Pengguna mengetik kalimat lengkap di dalam kotak terjemahan keyboard, lalu menekan tombol `[Kirim ➔]` untuk menerjemahkan sekaligus ke aplikasi tujuan (misal WhatsApp), meniadakan penerjemahan patah perkata ("tidak 1-1").
6. **Animated Bouncing Emojis**: Laci emoji Apple dilengkapi animasi pegas dinamis (*tactile spring bounce pop*) menggunakan `OvershootInterpolator(3.0f)` saat disentuh (`scale 1.45x`), menghidupkan animasi emoji khas iOS.
7. **Auto-Correction Engine**: Menggunakan algoritma Levenshtein fuzzy distance matching dengan kamus offline bawaan Bahasa Indonesia & English, plus dukungan kamus tambahan di `files/dictionaries/`.
8. **CI/CD Pipeline**: GitHub Actions (`.github/workflows/build_apk.yml`) otomatis build release APK bertanda tangan debug keystore dan mempublikasikan release asset.

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
  - CI/CD build sukses di GitHub Actions run #40699159019 (Release v1.0.8), dan berkas APK terbaru berhasil diunduh ke `/sdcard/Download/iOSKeyboard.apk` (5.3 MB).
