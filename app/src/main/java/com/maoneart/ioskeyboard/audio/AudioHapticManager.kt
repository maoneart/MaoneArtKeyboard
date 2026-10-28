package com.maoneart.ioskeyboard.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import com.maoneart.ioskeyboard.preferences.KeyboardPreferences

class AudioHapticManager(private val context: Context, private val prefs: KeyboardPreferences) {

    var activeRootView: View? = null

    private val audioManager = try {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    } catch (_: Throwable) {
        null
    }

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
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
                    val target = view ?: activeRootView
                    target?.playSoundEffect(android.view.SoundEffectConstants.CLICK)
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Dual-Layer Tactile Haptic Engine:
     * 1. Direct Hardware View Haptic (forces instant micro-click via WindowManager)
     * 2. Physical Vibrator Motor (calibrated duration & max amplitude to overcome motor inertia)
     */
    fun triggerHapticFeedback(view: View? = null) {
        if (!prefs.isHapticEnabled) return

        // 1. Hardware View Haptic Tap
        try {
            val targetView = view ?: activeRootView
            targetView?.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
        } catch (_: Throwable) {}

        // 2. Physical Vibrator Motor (40ms-60ms duration to overcome hardware ERM spin-up inertia)
        try {
            val vib = vibrator ?: return
            if (!vib.hasVibrator()) return

            val durationMs = prefs.hapticStrength.toLong().coerceIn(35L, 85L)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()

                val effect = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    } else {
                        VibrationEffect.createOneShot(durationMs, 255)
                    }
                } catch (_: Throwable) {
                    VibrationEffect.createOneShot(durationMs, 255)
                }

                try {
                    vib.vibrate(effect, audioAttributes)
                } catch (_: Throwable) {
                    vib.vibrate(effect)
                }
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        } catch (_: Throwable) {}
    }
}
