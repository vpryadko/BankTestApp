package com.example.banktestapp.lib

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * navigator.vibrate из прототипа. Длительности сохранены один в один:
 * tick 6 мс, engage [0, 12], release 4 мс, bound [0, 9, 40, 9].
 */
@SuppressLint("MissingPermission")
class Haptics(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val enabled = vibrator?.hasVibrator() == true
    private val hasAmplitude = enabled && vibrator?.hasAmplitudeControl() == true

    private fun oneShot(ms: Long, amplitude: Int) {
        if (!enabled) return
        val a = if (hasAmplitude) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
        runCatching {
            vibrator?.vibrate(VibrationEffect.createOneShot(ms, a))
        }
    }

    private fun pattern(timings: LongArray, amplitudes: IntArray) {
        if (!enabled) return
        runCatching {
            if (hasAmplitude) {
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, -1))
            }
        }
    }

    /** Проход зубца. */
    fun tick() = oneShot(6, 90)

    /** Вход в режим точной настройки. */
    fun engage() = oneShot(12, 160)

    /** Выход из режима точной настройки. */
    fun release() = oneShot(4, 70)

    /** Упор в 0 или в потолок. */
    fun bound() = pattern(longArrayOf(0, 9, 40, 9), intArrayOf(0, 130, 0, 130))
}

