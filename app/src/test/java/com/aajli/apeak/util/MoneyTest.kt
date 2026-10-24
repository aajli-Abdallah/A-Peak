package com.aajli.apeak.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun parse() {
        assertEquals(1250L, Money.parse("12.5"))
        assertEquals(1250L, Money.parse("12,50"))
        assertEquals(5L, Money.parse("0.05"))
        assertNull(Money.parse("0"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
    }
    @Test fun toInput() { assertEquals("12.50", Money.toInput(1250L)) }
}
