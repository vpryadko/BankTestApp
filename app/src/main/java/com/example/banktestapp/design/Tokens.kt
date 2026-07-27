package com.example.banktestapp.design

import android.graphics.Color

/**
 * Каждое значение здесь снято с оригинального макета (screenshot 418 x 935).
 * Ничего не выдумано — экран должен читаться как тот же самый экран.
 *
 * Все координаты — в «дизайнерских пикселях». Вью масштабирует эту коробку
 * под реальный экран одним общим множителем, поэтому расстояния — буквально
 * расстояния макета, а не адаптивное приближение.
 */
object Tokens {

    // ── цвета ────────────────────────────────────────────────────────────────
    val canvas = Color.parseColor("#EFF0F2")
    val headerTop = Color.parseColor("#D2E8E9")
    val ink = Color.parseColor("#11191F")
    val inkMuted = Color.parseColor("#6A7176")
    val inkFaint = Color.parseColor("#98A0A5")
    val tick = Color.parseColor("#BFC6CC")
    val green = Color.parseColor("#2ABA8C")
    val greenDeep = Color.parseColor("#1E9B74")
    val chipBg = Color.parseColor("#D3EBF1")
    val chipInk = Color.parseColor("#183B44")
    val white = Color.WHITE
    val dim = Color.parseColor("#08211C")
    val navGlyph = Color.parseColor("#868F96")

    // ── логическая коробка экрана ────────────────────────────────────────────
    const val SCREEN_W = 418f
    const val SCREEN_H = 935f

    // ── абсолютные позиции, снятые с макета ──────────────────────────────────
    const val TITLE_TOP = 243f
    const val AMOUNT_TOP = 288f
    const val CAPTION_TOP = 331f
    const val RULER_TOP = 382f
    const val RULER_H = 106f
    const val HINT_TOP = 506f
    const val CHIP_TOP = 545f
    const val CHIP_H = 48f
    const val CHIP_W = 298f
    const val CTA_TOP = 813f
    const val CTA_H = 56f
    const val CTA_INSET = 20f
    const val NAV_TOP = 889f

    const val HEADER_GRADIENT_H = 135f

    /** Осевая линия шкалы в координатах экрана — начало отсчёта «высоты». */
    const val RULER_CENTER_Y = RULER_TOP + RULER_H / 2f // 435

    // ── геометрия панели точной настройки ────────────────────────────────────
    const val PANEL_W = SCREEN_W - 32f      // 386
    const val PANEL_H = 180f
    const val PANEL_X = 16f
    const val PANEL_RADIUS = 30f

    /** Нижняя кромка панели в покое. */
    const val PANEL_REST_BOTTOM = 358f

    /** Какую долю подъёма пальца панель повторяет (чтобы не лезть под палец). */
    const val PANEL_FOLLOW = 0.75f

    /** Панель никогда не заходит в статус-бар. */
    const val PANEL_MIN_TOP = 56f

    const val PANEL_PAD = 18f
    const val PANEL_CANVAS_W = PANEL_W - PANEL_PAD * 2   // 350
    const val PANEL_CANVAS_H = 66f
    const val PANEL_CANVAS_TOP = 94f
    const val PANEL_TICK_CENTER = 26f
    const val PANEL_TICK_MAX = 40f

    // ── геометрия шкалы в покое ──────────────────────────────────────────────
    const val BASE_TICK_CENTER = RULER_H / 2f
    const val BASE_TICK_MAX = 30f
}

