package com.example.banktestapp.lib

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Вертикальное расстояние от шкалы → сколько сумов стоит один горизонтальный пиксель.
 *
 * Пять опорных точек из брифа:
 *
 *     0 px  →  100 000 / px
 *    40 px  →   50 000 / px
 *    80 px  →   20 000 / px
 *   120 px  →    5 000 / px
 *   160 px  →    1 000 / px
 *
 * Линейная интерполяция между ними давала бы видимые изломы в каждой опорной
 * точке — жест «щёлкал» бы при подъёме, то есть ровно то дискретное поведение,
 * которое бриф запрещает. Вместо этого — монотонный кубический сплайн
 * (Fritsch–Carlson) через опорные точки, подогнанный **в логарифмическом
 * пространстве**.
 *
 *  - log-пространство: чувствительность воспринимается мультипликативно,
 *    переход 100 000 → 50 000 ощущается тем же шагом, что 10 000 → 5 000.
 *  - монотонность: обычный Catmull-Rom даёт перелёт, а перелёт здесь означает,
 *    что при подъёме пальца шкала на мгновение становится *менее* точной.
 *  - кубика: C¹-непрерывность, нет скачка производной — «передача» меняется
 *    плавно, а не ступенями.
 *
 * Кривая проходит ровно через все пять заданных значений.
 */
object Sensitivity {

    private val ELEV = doubleArrayOf(0.0, 40.0, 80.0, 120.0, 160.0)
    private val UPP = doubleArrayOf(100_000.0, 50_000.0, 20_000.0, 5_000.0, 1_000.0)
    private val LOG = DoubleArray(UPP.size) { ln(UPP[it]) }

    const val MAX_ELEVATION = 160.0
    const val BASE_UPP = 100_000.0
    const val MIN_UPP = 1_000.0

    /** Касательные Fritsch–Carlson — считаются один раз. */
    private val TANGENTS: DoubleArray = run {
        val n = ELEV.size
        val d = DoubleArray(n - 1)
        val m = DoubleArray(n)
        for (i in 0 until n - 1) d[i] = (LOG[i + 1] - LOG[i]) / (ELEV[i + 1] - ELEV[i])
        m[0] = d[0]
        m[n - 1] = d[n - 2]
        for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0) 0.0 else (d[i - 1] + d[i]) / 2
        for (i in 0 until n - 1) {
            if (d[i] == 0.0) {
                m[i] = 0.0
                m[i + 1] = 0.0
                continue
            }
            val a = m[i] / d[i]
            val b = m[i + 1] / d[i]
            val s = a * a + b * b
            if (s > 9) {
                val t = 3 / sqrt(s)
                m[i] = t * a * d[i]
                m[i + 1] = t * b * d[i]
            }
        }
        m
    }

    /** Сумов на один горизонтальный пиксель при данной высоте над шкалой. */
    fun unitsPerPixel(elevation: Double): Double {
        val e = max(0.0, min(MAX_ELEVATION, elevation))
        var i = 0
        while (i < ELEV.size - 2 && e > ELEV[i + 1]) i++

        val h = ELEV[i + 1] - ELEV[i]
        val t = (e - ELEV[i]) / h
        val t2 = t * t
        val t3 = t2 * t

        // базис Эрмита
        val h00 = 2 * t3 - 3 * t2 + 1
        val h10 = t3 - 2 * t2 + t
        val h01 = -2 * t3 + 3 * t2
        val h11 = t3 - t2

        val logU = h00 * LOG[i] + h10 * h * TANGENTS[i] +
                h01 * LOG[i + 1] + h11 * h * TANGENTS[i + 1]

        return exp(logU)
    }

    /** Во сколько раз мы сейчас точнее шкалы в покое. */
    fun zoomFactor(elevation: Double): Double = BASE_UPP / unitsPerPixel(elevation)
}

