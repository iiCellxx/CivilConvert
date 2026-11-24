package com.cicil.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaleCalTest {

    @Test
    fun testScaleToScaleExample() {
        val result = ScaleCal.convert(
            "25", 50.0, 100.0, "mm", 2, ScaleCal.Mode.SCALE_TO_SCALE,
        )
        assertEquals(12.5, result.value, 0.0)
        assertEquals("12.50 mm", result.valueText)
        assertEquals("From 1:50 to 1:100 · millimeters (mm)", result.scaleLine)
        assertEquals("Drawing scale → drawing scale", result.steps[0])
        assertEquals("D₂ = D₁ × (S₁ / S₂)", result.steps[1])
        assertEquals("25 × (50 / 100) = 12.50 mm", result.steps[2])
        assertEquals(
            "Drawing scale → drawing scale: 25 mm on 1:50 = 12.50 mm on 1:100",
            result.copyText,
        )
    }

    @Test
    fun testDrawingToActualAndBack() {
        val actual = ScaleCal.convert(
            "25", 50.0, 1.0, "mm", 2, ScaleCal.Mode.DRAWING_TO_ACTUAL,
        )
        assertEquals(1250.0, actual.value, 0.0)
        assertEquals("1,250.00 mm", actual.valueText)
        assertEquals("From 1:50 to actual size (1:1) · millimeters (mm)", actual.scaleLine)
        assertEquals("25 × (50 / 1) = 1,250.00 mm", actual.steps[2])

        val drawing = ScaleCal.convert(
            "1250", 1.0, 50.0, "mm", 2, ScaleCal.Mode.ACTUAL_TO_DRAWING,
        )
        assertEquals(25.0, drawing.value, 0.0)
        assertEquals("25.00 mm", drawing.valueText)
        assertEquals("From actual size (1:1) to 1:50 · millimeters (mm)", drawing.scaleLine)
        assertTrue(drawing.copyText.contains("actual size (1:1)"))
    }

    @Test
    fun testRoundTripRestoresTheMeasurement() {
        val forward = ScaleCal.convert("25", 50.0, 100.0, "mm", 6, ScaleCal.Mode.SCALE_TO_SCALE)
        val back = ScaleCal.convert(
            forward.value.toString(), 100.0, 50.0, "mm", 6, ScaleCal.Mode.SCALE_TO_SCALE,
        )
        assertEquals(25.0, back.value, 1e-9)

        val actual = ScaleCal.convert("25", 50.0, 1.0, "m", 6, ScaleCal.Mode.DRAWING_TO_ACTUAL)
        val drawing = ScaleCal.convert(
            actual.value.toString(), 1.0, 50.0, "m", 6, ScaleCal.Mode.ACTUAL_TO_DRAWING,
        )
        assertEquals(25.0, drawing.value, 1e-9)
    }

    @Test
    fun testPresetCombinationsAndWholeNumbers() {
        for (from in ScaleCal.PRESET_DENOMINATORS) {
            for (to in ScaleCal.PRESET_DENOMINATORS) {
                val result = ScaleCal.convert("100", from, to, "mm", 4, ScaleCal.Mode.SCALE_TO_SCALE)
                assertEquals(100.0 * from / to, result.value, 1e-6)
                assertTrue(result.valueText.endsWith(" mm"))
            }
        }
        val whole = ScaleCal.convert("100", 10.0, 200.0, "in", 2, ScaleCal.Mode.SCALE_TO_SCALE)
        assertEquals(5.0, whole.value, 1e-9)
        assertEquals("5.00 in", whole.valueText)
    }

    @Test
    fun testDecimalInputAndUnitsDoNotChangeTheRatio() {
        val centimeters = ScaleCal.convert("12.5", 20.0, 50.0, "cm", 2, ScaleCal.Mode.SCALE_TO_SCALE)
        assertEquals(5.0, centimeters.value, 1e-9)
        assertEquals("5.00 cm", centimeters.valueText)

        for (unit in listOf("mm", "cm", "m", "in")) {
            val result = ScaleCal.convert("25", 50.0, 100.0, unit, 2, ScaleCal.Mode.SCALE_TO_SCALE)
            assertEquals(12.5, result.value, 0.0)
            assertEquals("12.50 $unit", result.valueText)
        }
        val upper = ScaleCal.convert("25", 50.0, 100.0, "MM", 2, ScaleCal.Mode.SCALE_TO_SCALE)
        assertEquals("12.50 mm", upper.valueText)
    }

    @Test
    fun testCustomScales() {
        assertEquals(50.0, ScaleCal.parseScale("1:50"), 0.0)
        assertEquals(50.0, ScaleCal.parseScale("50"), 0.0)
        assertEquals(50.0, ScaleCal.parseScale("2:100"), 0.0)
        assertEquals(50.0, ScaleCal.parseScale("1 / 50"), 0.0)
        assertEquals(50.0, ScaleCal.parseScale("  1 : 50 "), 0.0)
        assertEquals("1:50", ScaleCal.formatScale(50.0))
        assertEquals("1:1", ScaleCal.formatScale(1.0))
        assertEquals("1:0.2", ScaleCal.formatScale(0.2))

        val reduced = ScaleCal.convert(
            "10",
            100.0,
            ScaleCal.parseScale("1:125"),
            "cm",
            2,
            ScaleCal.Mode.SCALE_TO_SCALE,
            toTyped = "1:125",
        )
        assertEquals(8.0, reduced.value, 1e-9)
        assertEquals("8.00 cm", reduced.valueText)
        assertTrue(reduced.scaleLine.contains("1:125"))

        val enlargement = ScaleCal.convert(
            "10",
            ScaleCal.parseScale("5:1"),
            1.0,
            "mm",
            2,
            ScaleCal.Mode.DRAWING_TO_ACTUAL,
            fromTyped = "5:1",
        )
        assertEquals(2.0, enlargement.value, 1e-9)
        assertEquals("From 5:1 (1:0.2) to actual size (1:1) · millimeters (mm)", enlargement.scaleLine)
        assertEquals("10 × (0.2 / 1) = 2.00 mm", enlargement.steps[2])
    }

    @Test
    fun testDecimalPlacesRoundHalfUp() {
        val twoPlaces = ScaleCal.convert("10", 20.0, 75.0, "mm", 2, ScaleCal.Mode.SCALE_TO_SCALE)
        assertEquals(10.0 * 20.0 / 75.0, twoPlaces.value, 1e-12)
        assertEquals("2.67 mm", twoPlaces.valueText)
        assertTrue(twoPlaces.steps.last().contains("2 decimal places"))

        val zeroPlaces = ScaleCal.convert("25", 50.0, 100.0, "mm", 0, ScaleCal.Mode.SCALE_TO_SCALE)
        assertEquals("13 mm", zeroPlaces.valueText)

        val onePlace = ScaleCal.convert("1.25", 1.0, 1.0, "mm", 1, ScaleCal.Mode.SCALE_TO_SCALE)
        assertEquals("1.3 mm", onePlace.valueText)

        val exact = ScaleCal.convert("25", 50.0, 100.0, "mm", 2, ScaleCal.Mode.SCALE_TO_SCALE)
        assertEquals(3, exact.steps.size)
    }

    @Test
    fun testZeroAndWhitespace() {
        val zero = ScaleCal.convert("0", 50.0, 100.0, "mm", 2, ScaleCal.Mode.SCALE_TO_SCALE)
        assertEquals(0.0, zero.value, 0.0)
        assertEquals("0.00 mm", zero.valueText)

        val padded = ScaleCal.convert("  25.  ", 50.0, 100.0, "mm", 2, ScaleCal.Mode.SCALE_TO_SCALE)
        assertEquals(12.5, padded.value, 1e-9)
    }

    @Test
    fun testInvalidMeasurementScaleUnitAndPrecision() {
        assertEquals(
            "Measurement cannot be negative.",
            assertThrows(ScaleCal.Error::class.java) {
                ScaleCal.convert("-5", 50.0, 100.0, "mm", 2, ScaleCal.Mode.SCALE_TO_SCALE)
            }.message,
        )
        assertThrows(ScaleCal.Error::class.java) {
            ScaleCal.convert("abc", 50.0, 100.0, "mm", 2, ScaleCal.Mode.SCALE_TO_SCALE)
        }
        assertThrows(ScaleCal.Error::class.java) {
            ScaleCal.convert("", 50.0, 100.0, "mm", 2, ScaleCal.Mode.SCALE_TO_SCALE)
        }
        assertThrows(ScaleCal.Error::class.java) {
            ScaleCal.convert("25", 0.0, 100.0, "mm", 2, ScaleCal.Mode.SCALE_TO_SCALE)
        }
        assertThrows(ScaleCal.Error::class.java) {
            ScaleCal.convert("25", 50.0, -5.0, "mm", 2, ScaleCal.Mode.SCALE_TO_SCALE)
        }
        assertThrows(ScaleCal.Error::class.java) {
            ScaleCal.convert("25", 50.0, 100.0, "km", 2, ScaleCal.Mode.SCALE_TO_SCALE)
        }
        assertThrows(ScaleCal.Error::class.java) {
            ScaleCal.convert("25", 50.0, 100.0, "mm", 7, ScaleCal.Mode.SCALE_TO_SCALE)
        }
        assertThrows(ScaleCal.Error::class.java) {
            ScaleCal.convert("25", 50.0, 100.0, "mm", 2, ScaleCal.Mode.DRAWING_TO_ACTUAL)
        }
        assertThrows(ScaleCal.Error::class.java) { ScaleCal.parseScale("1:0") }
        assertThrows(ScaleCal.Error::class.java) { ScaleCal.parseScale("0") }
        assertThrows(ScaleCal.Error::class.java) { ScaleCal.parseScale("nope") }
        assertThrows(ScaleCal.Error::class.java) { ScaleCal.parseScale("") }
    }
}
