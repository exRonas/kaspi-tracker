package com.kleinapps.kaspitracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountExpressionTest {

    private fun eval(text: String, base: Double? = null) =
        AmountExpression.evaluate(text, base)

    @Test
    fun `plain numbers still work`() {
        assertEquals(1500.0, eval("1500")!!, 0.001)
        assertEquals(1500.5, eval("1500,5")!!, 0.001)
        assertEquals(1500.0, eval("1 500")!!, 0.001)
    }

    @Test
    fun `a shared bill can be split right in the field`() {
        assertEquals(1800.0, eval("3600/2")!!, 0.001)
        assertEquals(1200.0, eval("3600/3")!!, 0.001)
        assertEquals(900.0, eval("3600*0,25")!!, 0.001)
    }

    @Test
    fun `an operator alone continues from the amount already there`() {
        assertEquals(450.0, eval("/2", base = 900.0)!!, 0.001)
        assertEquals(100.0, eval("-800", base = 900.0)!!, 0.001)
        assertEquals(1350.0, eval("+450", base = 900.0)!!, 0.001)
        assertEquals(1800.0, eval("*2", base = 900.0)!!, 0.001)
    }

    @Test
    fun `operator precedence and parentheses`() {
        assertEquals(1100.0, eval("500+1200/2")!!, 0.001)
        assertEquals(850.0, eval("(500+1200)/2")!!, 0.001)
    }

    @Test
    fun `half-typed input is refused instead of guessed`() {
        assertNull(eval("1200+"))
        assertNull(eval("(1200"))
        assertNull(eval("abc"))
        assertNull(eval(""))
        assertNull(eval("12//3"))
    }

    @Test
    fun `division by zero is not a number`() {
        assertNull(eval("1200/0"))
    }

    @Test
    fun `a leading operator without a base cannot be resolved`() {
        assertNull(eval("/2"))
        assertNull(eval("*3"))
    }

    @Test
    fun `a lone minus on an empty field is a negative number, not a subtraction`() {
        // Callers reject non-positive amounts, so this stays unsaveable.
        assertEquals(-800.0, eval("-800")!!, 0.001)
    }

    @Test
    fun `formula detection drives the live preview`() {
        assertTrue(AmountExpression.isFormula("3600/2"))
        assertTrue(AmountExpression.isFormula("-800"))
        assertFalse(AmountExpression.isFormula("1500"))
        assertFalse(AmountExpression.isFormula(""))
    }

    @Test
    fun `typed multiplication signs from the keyboard are understood`() {
        assertEquals(3600.0, eval("1800×2")!!, 0.001)
        assertEquals(900.0, eval("1800÷2")!!, 0.001)
    }
}
