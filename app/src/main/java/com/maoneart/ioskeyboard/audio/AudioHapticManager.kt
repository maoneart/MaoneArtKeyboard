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
    private val asyncExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun destroy() {
        try {
            asyncExecutor.shutdownNow()
        } catch (_: Throwable) {}
    }

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

    fun playClickSound(view: View? = null, effectType: Int = AudioManager.FX_KEYPRESS_STANDARD) {
        if (!prefs.isSoundEnabled) return

        val am = audioManager ?: return
        try {
            // Respect ringer mode: if silent or vibrate, do not play click sound
            val ringerMode = am.ringerMode
            if (ringerMode == AudioManager.RINGER_MODE_SILENT || ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                return
            }

            // Sync 100% with Android System Volume (STREAM_SYSTEM)
            val sysVol = am.getStreamVolume(AudioManager.STREAM_SYSTEM)
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_SYSTEM)
            if (sysVol <= 0) return // muted in system

            // Ratio of current system volume (0.0 .. 1.0) scaled dynamically
            val systemRatio = if (maxVol > 0) sysVol.toFloat() / maxVol.toFloat() else 1.0f
            val finalVolume = (systemRatio * prefs.soundVolume.coerceIn(0.05f, 1.0f)).coerceIn(0.01f, 1.0f)

            if (!asyncExecutor.isShutdown && !asyncExecutor.isTerminated) {
                asyncExecutor.execute {
                    try {
                        am.playSoundEffect(effectType, finalVolume)
                    } catch (_: Throwable) {
                        try {
                            am.playSoundEffect(effectType)
                        } catch (_: Throwable) {
                            try {
                                val target = view ?: activeRootView
                                target?.playSoundEffect(android.view.SoundEffectConstants.CLICK)
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * Direct Tactile Haptic Engine:
     * Combines WindowManager view haptic and physical hardware vibrator (calibrated durationMs)
     * so that every single character keystroke produces a definite, crisp physical vibration
     * overcoming ERM and LRA motor inertia across all Android ROMs without blocking the UI thread.
     */
    fun triggerHapticFeedback(view: View? = null) {
        if (!prefs.isHapticEnabled) return

        val durationMs = prefs.hapticStrength.toLong().coerceIn(20L, 120L)
        if (durationMs <= 0L) return

        // 1. Hardware View Haptic (WindowManager instant tap, 0ms kernel latency on UI thread)
        try {
            val targetView = view ?: activeRootView
            targetView?.isHapticFeedbackEnabled = true
            targetView?.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
        } catch (_: Throwable) {}

        // 2. Direct Physical Hardware Vibration: Async background offload to prevent IPC frame drops
        try {
            if (!asyncExecutor.isShutdown && !asyncExecutor.isTerminated) {
                asyncExecutor.execute {
                    try {
                        val vib = vibrator ?: return@execute
                        if (!vib.hasVibrator()) return@execute

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val audioAttributes = AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                .build()

                            val amplitude = ((durationMs.toFloat() / 100f) * 255).toInt().coerceIn(120, 255)
                            val effect = VibrationEffect.createOneShot(durationMs, amplitude)

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
        } catch (_: Throwable) {}
    }
}
