# Persistent Memory — MaoneArt iOS Keyboard for Android

> **Repository**: [https://github.com/maoneart/MaoneArtKeyboard](https://github.com/maoneart/MaoneArtKeyboard)  
> **Workspace**: `/sdcard/www/MaoneArtKeyboard`  
> **Target Release APK**: `/sdcard/Download/iOSKeyboard.apk` (v1.0.3)

---

## 📌 Architecture & Technical Decisions
1. **Core IME**: Native Android `InputMethodService` (`IosInputMethodService.kt`) dengan latency input 0ms dan konsumsi RAM sangat hemat (~15 MB).
2. **Keyboard Layout & Geometry**: Mengikuti aspect ratio, key margin, inset padding, dan warna tombol iOS 18 (QWERTY, Symbols `#+=` dan `123`, serta laci Apple Emoji).
3. **Live Translate**: In-keyboard translator menggunakan engine Google Translate (URL encode + JSON parse) mendukung pasangan bahasa ID ⇄ EN, Arab, Jepang, Korea, dsb.
4. **Auto-Correction Engine**: Menggunakan algoritma Levenshtein fuzzy distance matching dengan kamus offline bawaan Bahasa Indonesia & English, plus dukungan kamus tambahan di `files/dictionaries/`.
5. **Cupertino Settings Hub**: Tampilan pengaturan berbasis iOS Inset Grouped layout (`activity_settings.xml`) dengan live test pad dan pengaktifan 2-langkah ke pengaturan sistem.
6. **CI/CD Pipeline**: GitHub Actions (`.github/workflows/build_apk.yml`) otomatis build release APK bertanda tangan debug keystore dan mempublikasikan release asset.

---

## 🚀 Milestones
- **28 Okt 2026**: Inisialisasi proyek, arsitektur modul, perbaikan styling XML & Kotlin textStyle, build sukses di GitHub Actions run #40682129420 (Release v1.0.3), dan unduh APK ke `/sdcard/Download/iOSKeyboard.apk` (5.3 MB).
