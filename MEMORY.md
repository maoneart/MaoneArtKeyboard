# Persistent Memory — MaoneArt iOS Keyboard for Android

> **Repository**: [https://github.com/maoneart/MaoneArtKeyboard](https://github.com/maoneart/MaoneArtKeyboard)  
> **Workspace**: `/sdcard/www/MaoneArtKeyboard`  
> **Target Release APK**: `/sdcard/Download/iOSKeyboard.apk` (v1.0.3)

---

## 📌 Architecture & Technical Decisions
1. **Core IME**: Native Android `InputMethodService` (`IosInputMethodService.kt`) dengan latency input 0ms dan konsumsi RAM sangat hemat (~15 MB).
2. **Keyboard Layout & Geometry**: Mengikuti aspect ratio, key margin, inset padding, dan warna tombol iOS 18 (QWERTY, Symbols `#+=` dan `123`, serta laci Apple Emoji). Tombol keyboard di-render menggunakan `TextView` murni (bukan `Button`) untuk eliminasi delay rendering, konflik MaterialButton, dan shadow artefak.
3. **Safe Inflation & Ripple**: Toolbar dan icon menggunakan circular ripple drawable mandiri (`btn_icon_ripple.xml`), eliminasi referensi `?attr/selectableItemBackgroundBorderless` yang memicu InflateException pada Android IME service.
4. **Cupertino Settings Hub**: Tampilan pengaturan berbasis iOS Inset Grouped layout (`activity_settings.xml`) dengan squircle colored icons iOS, live status badge deteksi IME aktif/default, switch dengan track hijau Apple (`#34C759`), dan live test pad.
5. **Live Translate**: In-keyboard translator menggunakan engine Google Translate (URL encode + JSON parse) mendukung pasangan bahasa ID ⇄ EN, Arab, Jepang, Korea, dsb.
6. **Auto-Correction Engine**: Menggunakan algoritma Levenshtein fuzzy distance matching dengan kamus offline bawaan Bahasa Indonesia & English, plus dukungan kamus tambahan di `files/dictionaries/`.
7. **CI/CD Pipeline**: GitHub Actions (`.github/workflows/build_apk.yml`) otomatis build release APK bertanda tangan debug keystore dan mempublikasikan release asset.

---

## 🚀 Milestones
- **28 Okt 2026**:
  - Inisialisasi proyek, arsitektur modul, perbaikan styling XML & Kotlin textStyle (Release v1.0.3).
  - Investigasi & perbaikan tuntas masalah Force Close: resolusi tema context IME (`ContextThemeWrapper`), penggantian `?attr/` dengan `@drawable/btn_icon_ripple`, eliminasi widget `Button` menjadi `TextView` di `KeyboardLayout`.
  - Redesign total antarmuka Pengaturan (Cupertino Inset Grouped Settings): penambahan squircle icon warna-warni ala iOS, kartu status aktivasi interaktif 2-langkah dengan auto-detect centang hijau (`isImeEnabled()` / `isImeSelected()`), switch hijau Apple, dan kotak uji coba ketik yang rapi.
  - CI/CD build sukses di GitHub Actions run #40694429935 (Release v1.0.5), dan berkas APK terbaru berhasil diunduh ke `/sdcard/Download/iOSKeyboard.apk` (5.3 MB).
