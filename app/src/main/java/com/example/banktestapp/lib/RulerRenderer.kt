package com.example.banktestapp.lib

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Typeface
import com.example.banktestapp.design.Tokens
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Шкала — это не фиксированный набор делений, который «уплотняется в режиме
 * точности». Это одна непрерывная числовая ось, отрисованная на том зуме,
 * который сейчас задаёт палец. Деления существуют на всех величинах сразу;
 * каждая величина просто проявляется, как только её собственный шаг становится
 * читаемым.
 *
 * Именно это делает переход бесшовным: ничего не переключается, ось просто
 * масштабируется. В покое порог читаемости проходят только деления по 5 000 000
 * — что в точности воспроизводит исходный макет. Поднимите палец, и по очереди
 * приходят 1 000 000 → 500 000 → 100 000, каждое уже на своём месте и уже
 * нужного размера.
 */
object RulerRenderer {

    private val MAGNITUDES = doubleArrayOf(
        100_000.0, 500_000.0, 1_000_000.0, 5_000_000.0, 10_000_000.0
    )

    /** Длина деления как доля самого высокого деления, по величинам. */
    private val RANK_HEIGHT = floatArrayOf(0.5f, 0.64f, 0.78f, 1f, 1f)

    /** Шаг в px, на котором величина начинает / заканчивает проявляться. */
    private const val FADE_IN = 12.0
    private const val FADE_FULL = 26.0

    /** Шаг в px, на котором величина начинает / заканчивает показывать подписи. */
    private const val LABEL_IN = 46.0
    private const val LABEL_FULL = 72.0

    private fun smoothstep(a: Double, b: Double, x: Double): Float {
        val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
        return (t * t * (3 - 2 * t)).toFloat()
    }

    private fun isMultiple(v: Double, m: Double): Boolean =
        abs(v / m - Math.round(v / m)) < 1e-6

    /** Параметры отрисовки конкретного экземпляра шкалы. */
    class Style(
        /** Вертикальный центр делений внутри области. */
        var tickCenterY: Float = 0f,
        /** Длина деления высшего ранга. */
        var tickMaxH: Float = 0f,
        var tickW: Float = 2f,
        /** Базовая линия подписей оси; null — не рисовать (у шкалы в покое их нет). */
        var labelBaselineY: Float? = null,
        var labelSize: Float = 10f,
        /** Ширина мягкого края в px. */
        var fade: Float = 0f,
    )

    /** Кадр значений. */
    class Frame(
        var width: Float = 0f,
        var height: Float = 0f,
        /** Значение под неподвижным центральным индикатором. */
        var value: Double = 0.0,
        var unitsPerPx: Double = 0.0,
        var max: Double = ValueModel.MAX_VALUE,
    )

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, 500, false)
    }
    private val eraser = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    private fun bar(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val r = min(w / 2f, h / 2f)
        canvas.drawRoundRect(x, y, x + w, y + h, r, r, fill)
    }

    /**
     * Рисует шкалу в текущей системе координат канвы: (0,0) — левый верхний
     * угол области шкалы.
     */
    fun draw(canvas: Canvas, frame: Frame, style: Style) {
        val w = frame.width
        val h = frame.height
        val value = frame.value
        val upp = frame.unitsPerPx
        val max = frame.max

        // Слой нужен, чтобы мягкие края «съедали» уже нарисованное, а не
        // закрашивали его цветом фона — панель полупрозрачная.
        val layer = if (style.fade > 0f) canvas.saveLayer(0f, 0f, w, h, null) else -1

        val half = w / 2.0
        val vMin = value - half * upp
        val vMax = value + half * upp

        // Самая мелкая величина, прошедшая порог читаемости.
        var finest = MAGNITUDES.size - 1
        for (i in MAGNITUDES.indices) {
            if (MAGNITUDES[i] / upp >= FADE_IN) {
                finest = i
                break
            }
        }
        val finestOpacity = smoothstep(FADE_IN, FADE_FULL, MAGNITUDES[finest] / upp)

        // Самая мелкая величина, чей шаг способен нести подпись.
        var labelled = -1
        if (style.labelBaselineY != null) {
            for (i in finest until MAGNITUDES.size) {
                if (MAGNITUDES[i] / upp >= LABEL_IN) {
                    labelled = i
                    break
                }
            }
        }
        val labelOpacity =
            if (labelled >= 0) smoothstep(LABEL_IN, LABEL_FULL, MAGNITUDES[labelled] / upp) else 0f

        val step = MAGNITUDES[finest]
        val from = ceil(maxOf(vMin, 0.0) / step) * step
        val to = min(vMax, max)

        if (style.labelBaselineY != null) {
            text.textSize = style.labelSize
        }

        var v = from
        while (v <= to + 0.5) {
            val x = (half + (v - value) / upp).toFloat()

            // Ранг = самая крупная величина, кратной которой является значение.
            var rank = finest
            for (i in MAGNITUDES.size - 1 downTo finest + 1) {
                if (isMultiple(v, MAGNITUDES[i])) {
                    rank = i
                    break
                }
            }

            val alpha = if (rank == finest) finestOpacity else 1f
            if (alpha >= 0.01f) {
                val len = style.tickMaxH * RANK_HEIGHT[rank]
                fill.color = Tokens.tick
                fill.alpha = (alpha * 255).toInt().coerceIn(0, 255)
                bar(canvas, x - style.tickW / 2f, style.tickCenterY - len / 2f, style.tickW, len)

                if (labelled >= 0 && rank >= labelled && labelOpacity > 0.01f) {
                    text.color = Tokens.inkFaint
                    text.alpha = (alpha * labelOpacity * 255).toInt().coerceIn(0, 255)
                    canvas.drawText(Format.axis(v), x, style.labelBaselineY!!, text)
                }
            }
            v += step
        }

        // Края диапазона — видны только когда действительно упираешься в 0 или потолок.
        for (edge in doubleArrayOf(0.0, max)) {
            if (edge < vMin - step || edge > vMax + step) continue
            val x = (half + (edge - value) / upp).toFloat()
            fill.color = Tokens.green
            fill.alpha = (0.55f * 255).toInt()
            bar(
                canvas,
                x - (style.tickW + 1f) / 2f,
                style.tickCenterY - style.tickMaxH / 2f,
                style.tickW + 1f,
                style.tickMaxH,
            )
        }
        fill.alpha = 255

        // Мягкие края, чтобы деления растворялись, а не выскакивали при прокрутке.
        if (style.fade > 0f) {
            eraser.shader = LinearGradient(
                0f, 0f, style.fade, 0f,
                0xFF000000.toInt(), 0x00000000,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, style.fade, h, eraser)

            eraser.shader = LinearGradient(
                w - style.fade, 0f, w, 0f,
                0x00000000, 0xFF000000.toInt(),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(w - style.fade, 0f, w, h, eraser)
            eraser.shader = null

            canvas.restoreToCount(layer)
        }
    }

    /** Округление в long без потери на больших значениях — для бакетов гаптики. */
    fun bucket(value: Double, size: Double): Long = (value / size).roundToLong()
}

