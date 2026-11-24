package com.cicil.convert

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * Drawing-scale converter.
 *
 * Between two drawing scales: D₂ = D₁ × (S₁ / S₂),
 * where S₁ and S₂ are the scale denominators (1:50 → 50).
 * Actual size is 1:1. A measurement of 25 at 1:50 becomes 12.5 at 1:100.
 */
object ScaleCal {
    class Error(message: String) : Exception(message)

    enum class Mode {
        DRAWING_TO_ACTUAL,
        ACTUAL_TO_DRAWING,
        SCALE_TO_SCALE,
    }

    val PRESET_DENOMINATORS: List<Double> = listOf(10.0, 20.0, 25.0, 50.0, 75.0, 100.0, 150.0, 200.0)

    private val units = setOf("mm", "cm", "m", "in")

    data class Result(
        val value: Double,
        val valueText: String,
        val scaleLine: String,
        val steps: List<String>,
        val copyText: String,
        val historyLabel: String,
    )

    fun formatScale(denominator: Double): String {
        if (!denominator.isFinite() || denominator <= 0.0) {
            throw Error("Scale must be greater than zero.")
        }
        return "1:${formatPlain(denominator)}"
    }

    /** Accepts `1:50`, `50`, `2:100`, and `1/50`. The result is the denominator of 1:N. */
    fun parseScale(text: String): Double {
        val compact = text.trim().replace(" ", "").replace("：", ":")
        if (compact.isEmpty()) throw Error("Enter a scale like 1:50 or 50.")
        val ratio = Regex("""^(\d+(?:\.\d+)?)[:/](\d+(?:\.\d+)?)$""")
        val single = Regex("""^(\d+(?:\.\d+)?)$""")
        val denominator = when {
            ratio.matches(compact) -> {
                val match = ratio.matchEntire(compact)!!
                val left = match.groupValues[1].toDouble()
                val right = match.groupValues[2].toDouble()
                if (left == 0.0) throw Error("Scale must be greater than zero.")
                right / left
            }
            single.matches(compact) -> compact.toDouble()
            else -> throw Error("Use a scale like 1:50 or 50.")
        }
        if (denominator <= 0.0) throw Error("Scale must be greater than zero.")
        if (!denominator.isFinite()) throw Error("That scale is too large.")
        return denominator
    }

    fun convert(
        measurementText: String,
        fromDenominator: Double,
        toDenominator: Double,
        unit: String,
        decimals: Int,
        mode: Mode,
        fromTyped: String? = null,
        toTyped: String? = null,
    ): Result {
        val measurement = parseMeasurement(measurementText)
        validateScale(fromDenominator)
        validateScale(toDenominator)
        if (decimals !in 0..6) throw Error("Decimal places must be from 0 to 6.")
        val fromActual = mode == Mode.ACTUAL_TO_DRAWING
        val toActual = mode == Mode.DRAWING_TO_ACTUAL
        if (fromActual && fromDenominator != 1.0) throw Error("Actual size uses scale 1:1.")
        if (toActual && toDenominator != 1.0) throw Error("Actual size uses scale 1:1.")

        val unitKey = normalizeUnit(unit)
        val value = measurement * (fromDenominator / toDenominator)
        if (!value.isFinite() || abs(value) > 1e15) throw Error("That result is too large.")

        val shownNumber = formatNumber(value, decimals)
        val fromName = labelFor(fromDenominator, fromTyped, fromActual)
        val toName = labelFor(toDenominator, toTyped, toActual)
        val modeText = modeLine(mode)
        val arithmetic = "${formatPlain(measurement)} × (${formatPlain(fromDenominator)} / ${formatPlain(toDenominator)}) = $shownNumber $unitKey"
        val steps = mutableListOf(modeText, "D₂ = D₁ × (S₁ / S₂)", arithmetic)
        val shownValue = shownNumber.replace(",", "").toDouble()
        if (abs(value - shownValue) > 1e-8 * kotlin.math.max(1.0, abs(value))) {
            val place = if (decimals == 1) "1 decimal place" else "$decimals decimal places"
            steps += "Shown to $place."
        }

        val valueText = "$shownNumber $unitKey"
        return Result(
            value = value,
            valueText = valueText,
            scaleLine = "From $fromName to $toName · ${unitLabel(unitKey)}",
            steps = steps,
            copyText = "$modeText: ${formatPlain(measurement)} $unitKey on $fromName = $valueText on $toName",
            historyLabel = "$valueText  ·  $fromName → $toName",
        )
    }

    private fun parseMeasurement(text: String): Double {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw Error("Enter a measurement.")
        val value = trimmed.toDoubleOrNull() ?: throw Error("Enter a valid number. Example: 25 or 12.5")
        if (value.isNaN()) throw Error("Enter a valid number. Example: 25 or 12.5")
        if (value < 0.0) throw Error("Measurement cannot be negative.")
        if (!value.isFinite()) throw Error("That measurement is too large.")
        return value
    }

    private fun validateScale(denominator: Double) {
        if (!denominator.isFinite() || denominator <= 0.0) throw Error("Scale must be greater than zero.")
    }

    private fun normalizeUnit(unit: String): String {
        val key = unit.trim().lowercase(Locale.US)
        if (key !in units) throw Error("Choose millimeters, centimeters, meters, or inches.")
        return key
    }

    private fun unitLabel(unit: String) = when (unit) {
        "mm" -> "millimeters (mm)"
        "cm" -> "centimeters (cm)"
        "m" -> "meters (m)"
        else -> "inches (in)"
    }

    private fun modeLine(mode: Mode) = when (mode) {
        Mode.DRAWING_TO_ACTUAL -> "Drawing measurement → actual size"
        Mode.ACTUAL_TO_DRAWING -> "Actual size → drawing measurement"
        Mode.SCALE_TO_SCALE -> "Drawing scale → drawing scale"
    }

    private fun labelFor(denominator: Double, typed: String?, actual: Boolean): String {
        if (actual) return "actual size (1:1)"
        val normalized = formatScale(denominator)
        if (typed.isNullOrBlank()) return normalized
        val pretty = typed.trim().replace(" ", "").replace("：", ":").replace("/", ":")
        if (pretty.isEmpty() || pretty.equals(normalized, ignoreCase = true) || pretty == formatPlain(denominator)) {
            return normalized
        }
        return "$pretty ($normalized)"
    }

    private fun formatPlain(value: Double): String {
        val asLong = value.toLong()
        if (abs(value - asLong.toDouble()) <= 1e-9 && abs(value) < 1e15) return asLong.toString()
        val text = String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
        return if (text.isEmpty() || text == "-0") "0" else text
    }

    private fun formatNumber(value: Double, decimals: Int): String {
        val format = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US))
        format.roundingMode = RoundingMode.HALF_UP
        format.minimumFractionDigits = decimals
        format.maximumFractionDigits = decimals
        format.isGroupingUsed = true
        return format.format(value)
    }
}
