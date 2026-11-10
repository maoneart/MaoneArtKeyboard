package com.maoneart.ioskeyboard.preferences

import android.content.Context
import android.content.SharedPreferences

class KeyboardPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ios_keyboard_prefs", Context.MODE_PRIVATE)

    var themeMode: String
        get() = prefs.getString("theme_mode", "auto") ?: "auto"
        set(value) = prefs.edit().putString("theme_mode", value).apply()

    var isSoundEnabled: Boolean
        get() = prefs.getBoolean("sound_enabled", true)
        set(value) = prefs.edit().putBoolean("sound_enabled", value).apply()

    var soundVolume: Float
        get() = prefs.getFloat("sound_volume", 0.7f)
        set(value) = prefs.edit().putFloat("sound_volume", value).apply()

    var isHapticEnabled: Boolean
        get() = prefs.getBoolean("haptic_enabled", true)
        set(value) = prefs.edit().putBoolean("haptic_enabled", value).apply()

    var hapticStrength: Int
        get() = prefs.getInt("haptic_strength", 45) // ms calibrated for Android tactile feedback
        set(value) = prefs.edit().putInt("haptic_strength", value).apply()

    var isKeyPopupEnabled: Boolean
        get() = prefs.getBoolean("key_popup_enabled", true)
        set(value) = prefs.edit().putBoolean("key_popup_enabled", value).apply()

    var activeLanguage: String
        get() = prefs.getString("active_language", "id") ?: "id"
        set(value) = prefs.edit().putString("active_language", value).apply()

    var isAutoCorrectEnabled: Boolean
        get() = prefs.getBoolean("auto_correct_enabled", true)
        set(value) = prefs.edit().putBoolean("auto_correct_enabled", value).apply()

    var isDoubleSpacePeriodEnabled: Boolean
        get() = prefs.getBoolean("double_space_period", true)
        set(value) = prefs.edit().putBoolean("double_space_period", value).apply()

    var isLiveTranslateEnabled: Boolean
        get() = prefs.getBoolean("live_translate_enabled", false)
        set(value) = prefs.edit().putBoolean("live_translate_enabled", value).apply()

    var translateSourceLang: String
        get() = prefs.getString("translate_source_lang", "id") ?: "id"
        set(value) = prefs.edit().putString("translate_source_lang", value).apply()

    var translateTargetLang: String
        get() = prefs.getString("translate_target_lang", "en") ?: "en"
        set(value) = prefs.edit().putString("translate_target_lang", value).apply()

    var isPredictiveTextEnabled: Boolean
        get() = prefs.getBoolean("predictive_text_enabled", true)
        set(value) = prefs.edit().putBoolean("predictive_text_enabled", value).apply()

    var isSwipeTypingEnabled: Boolean
        get() = prefs.getBoolean("swipe_typing_enabled", true)
        set(value) = prefs.edit().putBoolean("swipe_typing_enabled", value).apply()

    var isSpaceTrackpadEnabled: Boolean
        get() = prefs.getBoolean("space_trackpad_enabled", true)
        set(value) = prefs.edit().putBoolean("space_trackpad_enabled", value).apply()

    var isDictationEnabled: Boolean
        get() = prefs.getBoolean("dictation_enabled", true)
        set(value) = prefs.edit().putBoolean("dictation_enabled", value).apply()

    var clipboardViewMode: String
        get() = prefs.getString("clipboard_view_mode", "card") ?: "card"
        set(value) = prefs.edit().putString("clipboard_view_mode", value).apply()

    var isAutoCapitalizationEnabled: Boolean
        get() = prefs.getBoolean("auto_capitalization_enabled", true)
        set(value) = prefs.edit().putBoolean("auto_capitalization_enabled", value).apply()
}
