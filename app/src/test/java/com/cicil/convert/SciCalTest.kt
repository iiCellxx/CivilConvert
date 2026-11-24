package com.cicil.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SciCalTest {

    @Test
    fun testBasicArithmetic() {
        assertEquals(7.0, SciCal.evaluate("3+4", true), 1e-9)
        assertEquals(-1.0, SciCal.evaluate("3-4", true), 1e-9)
        assertEquals(12.0, SciCal.evaluate("3*4", true), 1e-9)
        assertEquals(2.5, SciCal.evaluate("5/2", true), 1e-9)
    }

    @Test
    fun testOrderOfOperations() {
        // Standard precedence: 2 + (3 * 4) = 14
        assertEquals(14.0, SciCal.evaluate("2+3*4", true), 1e-9)
        // Parentheses override: (2 + 3) * 4 = 20
        assertEquals(20.0, SciCal.evaluate("(2+3)*4", true), 1e-9)
    }

    @Test
    fun testImplicitMultiplication() {
        // 2(3+4) = 14
        assertEquals(14.0, SciCal.evaluate("2(3+4)", true), 1e-9)
        // (1+2)(3+4) = 21
        assertEquals(21.0, SciCal.evaluate("(1+2)(3+4)", true), 1e-9)
        // 2pi in radians
        assertEquals(2 * Math.PI, SciCal.evaluate("2pi", false), 1e-9)
    }

    @Test
    fun testExponents() {
        assertEquals(8.0, SciCal.evaluate("2^3", true), 1e-9)
        // Exponentiation right-associativity: 2^(3^2) = 2^9 = 512
        assertEquals(512.0, SciCal.evaluate("2^3^2", true), 1e-9)
    }

    @Test
    fun testFactorial() {
        assertEquals(1.0, SciCal.evaluate("0!", true), 1e-9)
        assertEquals(1.0, SciCal.evaluate("1!", true), 1e-9)
        assertEquals(120.0, SciCal.evaluate("5!", true), 1e-9)
        assertThrows(SciCal.Error::class.java) {
            SciCal.evaluate("171!", true)
        }
    }

    @Test
    fun testTrigonometryDegreesAndRadians() {
        // In degrees: sin(30°) = 0.5, cos(60°) = 0.5, tan(45°) = 1.0
        assertEquals(0.5, SciCal.evaluate("sin(30)", true), 1e-9)
        assertEquals(0.5, SciCal.evaluate("cos(60)", true), 1e-9)
        assertEquals(1.0, SciCal.evaluate("tan(45)", true), 1e-9)

        // In radians: sin(pi/6) = 0.5
        assertEquals(0.5, SciCal.evaluate("sin(pi/6)", false), 1e-9)
    }

    @Test
    fun testFormatSnapping() {
        // IEEE 754 precision snapping
        assertEquals("0", SciCal.format(0.0))
        assertEquals("14", SciCal.format(14.00000000001))
        assertEquals("0.5", SciCal.format(0.5))
    }

    @Test
    fun testDivisionByZero() {
        assertThrows(SciCal.Error::class.java) {
            SciCal.evaluate("10/0", true)
        }
    }
}
