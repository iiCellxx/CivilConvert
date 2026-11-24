package com.cicil.convert

import java.util.Locale
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Scientific calculator.
 * Buttons edit a plain-text expression; [evaluate] parses it (no eval).
 * Trig functions use degrees when [evaluate]'s degrees flag is true.
 */
object SciCal {
    class Error(message: String) : Exception(message)

    fun evaluate(expression: String, degrees: Boolean): Double {
        val tokens = insertImplicitMultiply(tokenize(expression))
        if (tokens.isEmpty()) throw Error("Enter an expression.")
        val parser = Parser(tokens, degrees)
        val value = parser.parseExpression()
        if (!parser.atEnd()) throw Error("Invalid expression.")
        if (!value.isFinite()) throw Error("Result is not a finite number.")
        return value
    }

    fun format(value: Double): String {
        if (!value.isFinite()) throw Error("Result is not a finite number.")
        val snapped = snap(value)
        if (snapped == 0.0) return "0"
        val ax = abs(snapped)
        if (ax >= 1e10 || ax < 1e-6) {
            return String.format(Locale.US, "%.8g", snapped)
        }
        val text = String.format(Locale.US, "%.10f", snapped).trimEnd('0').trimEnd('.')
        return if (text == "-0") "0" else text
    }

    /** Expression as shown on the keypad display. */
    fun display(expr: String): String {
        if (expr.isEmpty()) return "0"
        return expr
            .replace("sqrt(", "√(")
            .replace("pi", "π")
            .replace("*", "×")
            .replace("/", "÷")
    }

    fun digit(expr: String, d: Char): String {
        if (endsWithValue(expr) && !expr.last().isDigit() && expr.last() != '.') return expr + "*" + d
        return expr + d
    }

    fun dot(expr: String): String {
        val tail = expr.takeLastWhile { it.isDigit() || it == '.' }
        if ('.' in tail) return expr
        if (tail.isEmpty()) return if (endsWithValue(expr)) expr + "*0." else expr + "0."
        return "$expr."
    }

    fun operator(expr: String, op: Char): String {
        if (expr.isEmpty()) return if (op == '-') "-" else expr
        val last = expr.last()
        if (last in "+-*/^") {
            if (op == '-' && last != '-') return expr + "-"
            return expr.dropLast(1) + op
        }
        if (last == '(') return if (op == '-') "$expr-" else expr
        if (last == '.') return expr + "0" + op
        return expr + op
    }

    fun token(expr: String, t: String): String {
        val multiply = t != ")" && endsWithValue(expr)
        return if (multiply) "$expr*$t" else expr + t
    }

    fun square(expr: String): String {
        if (expr.isEmpty() || !endsWithValue(expr)) return expr
        return "($expr)^2"
    }

    fun factorialMark(expr: String): String {
        if (!endsWithValue(expr) || expr.endsWith("!")) return expr
        return "$expr!"
    }

    fun reciprocal(expr: String): String {
        if (expr.isEmpty() || !endsWithValue(expr)) return expr
        return "1/($expr)"
    }

    fun sign(expr: String): String {
        if (expr.isEmpty()) return "-"
        var i = expr.length - 1
        if (!expr[i].isDigit() && expr[i] != '.') return expr
        while (i >= 0 && (expr[i].isDigit() || expr[i] == '.')) i--
        val numStart = i + 1
        if (i >= 0 && expr[i] == '-') {
            val before = if (i == 0) null else expr[i - 1]
            if (before == null || before in "+-*/^(") return expr.removeRange(i, i + 1)
        }
        val beforeChar = if (numStart == 0) null else expr[numStart - 1]
        return if (beforeChar == null || beforeChar in "+-*/^(") {
            expr.substring(0, numStart) + "-" + expr.substring(numStart)
        } else {
            expr.substring(0, numStart) + "(-" + expr.substring(numStart) + ")"
        }
    }

    fun backspace(expr: String): String {
        if (expr.isEmpty()) return ""
        val chunks = listOf("asin(", "acos(", "atan(", "sqrt(", "sin(", "cos(", "tan(", "log(", "ln(", "pi")
        for (chunk in chunks) {
            if (expr.endsWith(chunk)) return expr.dropLast(chunk.length)
        }
        return expr.dropLast(1)
    }

    private fun endsWithValue(expr: String): Boolean {
        if (expr.isEmpty()) return false
        val c = expr.last()
        if (c.isDigit() || c == ')' || c == '!') return true
        if (expr.endsWith("pi")) return true
        if (expr.endsWith("e")) {
            val prev = if (expr.length == 1) null else expr[expr.length - 2]
            if (prev == null || (!prev.isLetterOrDigit() && prev != '.')) return true
        }
        return false
    }

    private fun snap(value: Double): Double {
        val nearest = round(value)
        val tol = 1e-9 * max(1.0, abs(nearest))
        return if (abs(value - nearest) <= tol) nearest else value
    }

    private sealed interface Tok {
        data class Num(val v: Double) : Tok
        data class Id(val name: String) : Tok
        data class Op(val c: Char) : Tok
    }

    private val functions = setOf("sin", "cos", "tan", "asin", "acos", "atan", "log", "ln", "sqrt")

    private fun tokenize(raw: String): List<Tok> {
        val s = raw.trim()
        val out = ArrayList<Tok>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c.isWhitespace()) {
                i++
                continue
            }
            if (c.isDigit() || (c == '.' && i + 1 < s.length && s[i + 1].isDigit())) {
                val start = i
                i++
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                    var j = i + 1
                    if (j < s.length && (s[j] == '+' || s[j] == '-')) j++
                    if (j < s.length && s[j].isDigit()) {
                        j++
                        while (j < s.length && s[j].isDigit()) j++
                        i = j
                    }
                }
                val number = s.substring(start, i).toDoubleOrNull() ?: throw Error("Invalid number.")
                out.add(Tok.Num(number))
                continue
            }
            if (c.isLetter()) {
                val start = i
                i++
                while (i < s.length && s[i].isLetter()) i++
                val name = s.substring(start, i).lowercase(Locale.US)
                when (name) {
                    "pi" -> out.add(Tok.Id("pi"))
                    "e" -> out.add(Tok.Id("e"))
                    in functions -> out.add(Tok.Id(name))
                    else -> throw Error("Unknown function.")
                }
                continue
            }
            if (c in "+-*/^()!") {
                out.add(Tok.Op(c))
                i++
                continue
            }
            throw Error("Cannot use '$c' here.")
        }
        return out
    }

    private fun insertImplicitMultiply(tokens: List<Tok>): List<Tok> {
        if (tokens.size < 2) return tokens
        val out = ArrayList<Tok>(tokens.size + 4)
        out.add(tokens[0])
        for (index in 1 until tokens.size) {
            val prev = out.last()
            val cur = tokens[index]
            val prevEnds = prev is Tok.Num ||
                (prev is Tok.Id && (prev.name == "pi" || prev.name == "e")) ||
                (prev is Tok.Op && prev.c in ")!")
            val curStarts = cur is Tok.Num || cur is Tok.Id || (cur is Tok.Op && cur.c == '(')
            if (prevEnds && curStarts) out.add(Tok.Op('*'))
            out.add(cur)
        }
        return out
    }

    private class Parser(private val tokens: List<Tok>, private val degrees: Boolean) {
        private var index = 0

        fun atEnd() = index >= tokens.size

        fun parseExpression(): Double {
            var left = parseTerm()
            while (true) {
                val op = tokens.getOrNull(index) as? Tok.Op ?: break
                if (op.c != '+' && op.c != '-') break
                index++
                val right = parseTerm()
                left = if (op.c == '+') left + right else left - right
            }
            return left
        }

        private fun parseTerm(): Double {
            var left = parseUnary()
            while (true) {
                val op = tokens.getOrNull(index) as? Tok.Op ?: break
                if (op.c != '*' && op.c != '/') break
                index++
                val right = parseUnary()
                if (op.c == '*') {
                    left *= right
                } else {
                    if (right == 0.0) throw Error("Division by zero.")
                    left /= right
                }
            }
            return left
        }

        private fun parseUnary(): Double {
            val op = tokens.getOrNull(index) as? Tok.Op
            if (op != null && (op.c == '+' || op.c == '-')) {
                index++
                val value = parseUnary()
                return if (op.c == '-') -value else value
            }
            return parsePower()
        }

        private fun parsePower(): Double {
            val base = parsePostfix()
            val op = tokens.getOrNull(index) as? Tok.Op ?: return base
            if (op.c != '^') return base
            index++
            val exponent = parseUnary()
            val value = base.pow(exponent)
            if (!value.isFinite()) throw Error("That power is not a real number.")
            return value
        }

        private fun parsePostfix(): Double {
            var value = parsePrimary()
            while ((tokens.getOrNull(index) as? Tok.Op)?.c == '!') {
                index++
                value = factorial(value)
            }
            return value
        }

        private fun parsePrimary(): Double {
            val token = tokens.getOrNull(index) ?: throw Error("Expression is incomplete.")
            when (token) {
                is Tok.Num -> {
                    index++
                    return token.v
                }
                is Tok.Id -> {
                    index++
                    if (token.name == "pi") return PI
                    if (token.name == "e") return E
                    val open = tokens.getOrNull(index)
                    if (open !is Tok.Op || open.c != '(') throw Error("${token.name} needs parentheses.")
                    index++
                    val arg = parseExpression()
                    val close = tokens.getOrNull(index)
                    if (close !is Tok.Op || close.c != ')') throw Error("Missing ).")
                    index++
                    return applyFn(token.name, arg)
                }
                is Tok.Op -> {
                    if (token.c == '(') {
                        index++
                        val value = parseExpression()
                        val close = tokens.getOrNull(index)
                        if (close !is Tok.Op || close.c != ')') throw Error("Missing ).")
                        index++
                        return value
                    }
                }
            }
            throw Error("Invalid expression.")
        }

        private fun applyFn(name: String, x: Double): Double {
            fun toRad(v: Double) = if (degrees) Math.toRadians(v) else v
            fun fromRad(v: Double) = if (degrees) Math.toDegrees(v) else v
            val result = when (name) {
                "sin" -> sin(toRad(x))
                "cos" -> cos(toRad(x))
                "tan" -> {
                    if (degrees) {
                        val wrapped = abs(x % 180.0)
                        if (abs(wrapped - 90.0) < 1e-9) throw Error("Tangent is undefined there.")
                    }
                    tan(toRad(x))
                }
                "asin" -> {
                    if (x < -1.0 || x > 1.0) throw Error("asin only accepts values from -1 to 1.")
                    fromRad(asin(x))
                }
                "acos" -> {
                    if (x < -1.0 || x > 1.0) throw Error("acos only accepts values from -1 to 1.")
                    fromRad(acos(x))
                }
                "atan" -> fromRad(atan(x))
                "log" -> {
                    if (x <= 0.0) throw Error("log only accepts positive numbers.")
                    log10(x)
                }
                "ln" -> {
                    if (x <= 0.0) throw Error("ln only accepts positive numbers.")
                    ln(x)
                }
                "sqrt" -> {
                    if (x < 0.0) throw Error("Square root of a negative number is not real.")
                    sqrt(x)
                }
                else -> throw Error("Unknown function.")
            }
            if (!result.isFinite()) throw Error("Result is not a finite number.")
            return result
        }

        private fun factorial(n: Double): Double {
            if (n < 0.0 || abs(n - round(n)) > 1e-9 || n > 170.0) {
                throw Error("Factorial is only defined for whole numbers from 0 to 170.")
            }
            var result = 1.0
            for (i in 2..n.toInt()) result *= i
            return result
        }
    }
}
