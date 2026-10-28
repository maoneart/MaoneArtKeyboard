package com.maoneart.ioskeyboard.audio

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import com.maoneart.ioskeyboard.preferences.KeyboardPreferences

class AudioHapticManager(private val context: Context, private val prefs: KeyboardPreferences) {

    private val audioManager = try {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    } catch (_: Throwable) {
        null
    }

    private val vibrator = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Throwable) {
        null
    }

    fun playClickSound(view: View? = null) {
        if (!prefs.isSoundEnabled) return
        try {
            audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, prefs.soundVolume)
        } catch (_: Throwable) {
            try {
                audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
            } catch (_: Throwable) {
                try {
                    view?.playSoundEffect(android.view.SoundEffectConstants.CLICK)
                } catch (_: Throwable) {}
            }
        }
    }

    fun triggerHapticFeedback(view: View? = null) {
        if (!prefs.isHapticEnabled) return

        try {
            if (view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                vibrator?.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(prefs.hapticStrength.toLong().coerceAtLeast(10L), VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(prefs.hapticStrength.toLong().coerceAtLeast(10L))
            }
        } catch (_: Throwable) {
            try {
                view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } catch (_: Throwable) {}
        }
    }
}
