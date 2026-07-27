package com.example.banktestapp.lib

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.round

/** Форматирование ровно как в прототипе: ru-RU, разряды через узкий пробел. */
object Format {

    private val symbols = DecimalFormatSymbols(Locale.forLanguageTag("ru-RU")).apply {
        groupingSeparator = '\u2009' // тонкий пробел — как в макете
        decimalSeparator = ','
    }

    private val plain = DecimalFormat("#,##0", symbols)
    private val oneDecimal = DecimalFormat("#,##0.#", symbols)

    /** 25 000 000 */
    fun amount(v: Double): String = plain.format(round(v))

    /** 5 млн / 0,5 млн — подписи оси внутри панели. */
    fun axis(v: Double): String = "${oneDecimal.format(v / 1_000_000.0)} млн"

    /** Округляем чувствительность до читаемого разряда, прежде чем показывать. */
    fun sensitivity(v: Double): String {
        val t = when {
            v >= 10_000 -> round(v / 1_000) * 1_000
            v >= 1_000 -> round(v / 100) * 100
            else -> round(v / 10) * 10
        }
        return plain.format(t)
    }
}

