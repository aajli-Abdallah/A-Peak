package com.aajli.apeak.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency

val CURRENCIES = listOf("USD", "EUR", "GBP", "MAD", "CAD", "AUD", "CHF", "AED", "SAR", "DZD", "TND", "EGP", "TRY", "INR")

object Money {
    /** Text to minor units (cents). Returns null if empty, invalid or not > 0. */
    fun parse(input: String, allowZero: Boolean = false): Long? {
        val s = input.trim().replace(',', '.')
        if (s.isEmpty()) return null
        return try {
            val bd = BigDecimal(s)
            if (bd.signum() < 0 || (!allowZero && bd.signum() == 0)) null
            else bd.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        } catch (e: Exception) { null }
    }

    fun format(minor: Long, currency: String): String {
        val nf = NumberFormat.getCurrencyInstance()
        try { nf.currency = Currency.getInstance(currency) } catch (e: Exception) { }
        nf.minimumFractionDigits = 2
        nf.maximumFractionDigits = 2
        return nf.format(BigDecimal(minor).movePointLeft(2))
    }

    fun toInput(minor: Long): String = BigDecimal(minor).movePointLeft(2).toPlainString()
    fun parseOpt(input: String): Long? = if (input.isBlank()) 0L else parse(input, true)
    fun parseQty(input: String): Double? = input.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it < 1e9 }
    fun parseRate(input: String): Double? = input.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
    fun qtyInput(q: Double): String = BigDecimal(q).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
