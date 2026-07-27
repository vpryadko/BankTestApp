package com.example.banktestapp.lib

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sign

object ValueModel {

    const val MIN_VALUE = 0.0
    const val MAX_VALUE = 50_000_000.0

    /** Логическая точность продукта. Любая зафиксированная сумма кратна ей. */
    const val STEP = 100_000.0

    fun clamp(v: Double, lo: Double, hi: Double): Double = max(lo, min(hi, v))

    fun snap(v: Double): Double = round(v / STEP) * STEP

    /** Ниже стольких пикселей на «зубец» фиксация незаметна — остаёмся непрерывными. */
    private const val DETENT_FLOOR = 6.0
    private const val DETENT_FULL = 30.0

    /**
     * Магнитные фиксации, выраженные как функция позиции, а не как анимация.
     *
     * Пружина, догоняющая snap(value), отставала бы от быстрого грубого драга на
     * `tau × velocity` — десятки миллионов сум на скорости прокрутки. Поэтому
     * фиксация — это *функция позиции*: мы смягчаем отклонение от ближайшего
     * кратного 100 000, из-за чего линия «липнет» у зубца и ускоряется между
     * ними. Ни отставания, ни состояния, и это ничего не стоит, когда зубцы
     * субпиксельные.
     *
     * Показатель степени сам интерполируется из того, сколько пикселей сейчас
     * занимает один зубец, поэтому магнетизм *нарастает* по мере подъёма.
     * В покое (1 px на зубец) показатель равен 1 — это чистый проброс,
     * идеально непрерывный.
     */
    fun magnetize(value: Double, unitsPerPx: Double): Double {
        val pxPerDetent = STEP / unitsPerPx
        if (pxPerDetent <= DETENT_FLOOR) return value

        val k = clamp((pxPerDetent - DETENT_FLOOR) / (DETENT_FULL - DETENT_FLOOR), 0.0, 1.0)
        val exponent = 1 + 1.8 * k

        val target = snap(value)
        val halfSpan = STEP / 2
        val norm = (value - target) / halfSpan // −1 … 1
        val eased = sign(norm) * abs(norm).pow(exponent)
        return target + eased * halfSpan
    }

    /** Сопротивление приращениям драга, выталкивающим за 0 или потолок. */
    fun boundaryResistance(value: Double): Double {
        if (value in MIN_VALUE..MAX_VALUE) return 1.0
        val over = if (value < MIN_VALUE) MIN_VALUE - value else value - MAX_VALUE
        return 0.34 / (1 + over / 4_000_000.0)
    }

    /** Экспоненциальное приближение, независимое от частоты кадров. */
    fun approach(current: Double, target: Double, tau: Double, dt: Double): Double =
        current + (target - current) * (1 - exp(-dt / tau))

    /**
     * Гранулярность тактильной отдачи следует тому же правилу, что и видимость
     * делений: вибрируем на самой мелкой величине, чей шаг перевалил за ~9 px.
     * Так щелчки остаются примерно на ширину пальца друг от друга при любой
     * «передаче», вместо строчить очередью в покое и молчать под увеличением.
     */
    private val HAPTIC_MAGNITUDES = doubleArrayOf(STEP, 500_000.0, 1_000_000.0, 5_000_000.0)

    fun hapticStep(unitsPerPx: Double): Double {
        for (m in HAPTIC_MAGNITUDES) if (m / unitsPerPx >= 9) return m
        return HAPTIC_MAGNITUDES[HAPTIC_MAGNITUDES.size - 1]
    }
}

