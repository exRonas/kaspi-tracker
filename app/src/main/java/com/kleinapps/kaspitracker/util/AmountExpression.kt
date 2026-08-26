package com.kleinapps.kaspitracker.util

/**
 * Tiny arithmetic evaluator for money input fields, so an amount can be edited
 * the way a spreadsheet cell is: type `1200/2` to halve a shared bill, or
 * `-800` on top of an existing amount to knock a part off it.
 *
 * Supports `+ - * /`, parentheses, and both decimal separators. When the text
 * starts with an operator it is applied to [base] — the amount already in the
 * field — which is what makes `/2` and `-800` mean what the user expects.
 *
 * Returns null for anything it cannot read; callers keep showing the raw text
 * and simply refuse to save.
 */
object AmountExpression {

    fun evaluate(text: String, base: Double? = null): Double? {
        val cleaned = text
            .filterNot { it.isWhitespace() || it == '\u00A0' || it == '\u202F' }
            .replace(',', '.')
            .replace('×', '*')
            .replace('÷', '/')
            .replace('−', '-')
        if (cleaned.isEmpty()) return null

        // "/2", "-800", "*1.5" continue from what is already in the field.
        val expression = when {
            cleaned.first() in "*/+" -> (base ?: return null).toPlainString() + cleaned
            // A leading minus is ambiguous: "-800" on top of an existing amount
            // subtracts, but on an empty field it is just a negative number —
            // and negative amounts are not a thing here, so it needs a base too.
            cleaned.first() == '-' -> (base ?: 0.0).toPlainString() + cleaned
            else -> cleaned
        }

        val result = runCatching { Parser(expression).parse() }.getOrNull() ?: return null
        return result.takeIf { it.isFinite() }
    }

    /** True when the text is more than a plain number — worth showing a preview for. */
    fun isFormula(text: String): Boolean =
        text.any { it in "+-*/()×÷−" } && text.isNotBlank()

    private fun Double.toPlainString(): String =
        if (this == toLong().toDouble()) toLong().toString() else toString()

    /**
     * Recursive descent over `expr := term (('+'|'-') term)*`,
     * `term := factor (('*'|'/') factor)*`, `factor := number | '(' expr ')'`.
     */
    private class Parser(private val input: String) {
        private var position = 0

        fun parse(): Double? {
            val value = parseExpression() ?: return null
            // Trailing junk ("12+") means the user is still typing.
            return if (position == input.length) value else null
        }

        private fun parseExpression(): Double? {
            var left = parseTerm() ?: return null
            while (position < input.length && input[position] in "+-") {
                val operator = input[position++]
                val right = parseTerm() ?: return null
                left = if (operator == '+') left + right else left - right
            }
            return left
        }

        private fun parseTerm(): Double? {
            var left = parseFactor() ?: return null
            while (position < input.length && input[position] in "*/") {
                val operator = input[position++]
                val right = parseFactor() ?: return null
                if (operator == '/' && right == 0.0) return null
                left = if (operator == '*') left * right else left / right
            }
            return left
        }

        private fun parseFactor(): Double? {
            if (position >= input.length) return null

            if (input[position] == '-') {
                position++
                return parseFactor()?.let { -it }
            }
            if (input[position] == '(') {
                position++
                val inner = parseExpression() ?: return null
                if (position >= input.length || input[position] != ')') return null
                position++
                return inner
            }

            val start = position
            while (position < input.length && (input[position].isDigit() || input[position] == '.')) {
                position++
            }
            return input.substring(start, position).toDoubleOrNull()
        }
    }
}
