package app.peptides.journal

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

enum class Problem { NUMBER, DOSE_EXCEEDS_VIAL, CAPACITY, SCALE }
class InputProblem(val problem: Problem) : IllegalArgumentException(problem.name)

data class Calculation(
    val concentration: BigDecimal, val volume: BigDecimal, val units: BigDecimal,
    val doses: BigDecimal, val remainder: BigDecimal, val aligned: Boolean
)

object Calculator {
    private val precision = MathContext.DECIMAL128
    fun number(raw: String): BigDecimal {
        val text = raw.trim()
        if (!Regex("[0-9]{1,9}([.,][0-9]{1,9})?").matches(text)) throw InputProblem(Problem.NUMBER)
        return text.replace(',', '.').toBigDecimal().also {
            if (it.signum() <= 0) throw InputProblem(Problem.NUMBER)
        }
    }
    fun calculate(dose: String, mass: String, water: String, capacity: String, scale: String, tick: String): Calculation {
        val d = Mass.mg(dose); val m = Mass.mg(mass); val w = number(water)
        val cap = number(capacity); val s = number(scale); val t = number(tick)
        if (d > m) throw InputProblem(Problem.DOSE_EXCEEDS_VIAL)
        if (s > BigDecimal(1000) || cap > BigDecimal(10) || t > s.multiply(cap) || s.multiply(cap).remainder(t).signum() != 0)
            throw InputProblem(Problem.SCALE)
        // Compare exact products, before any display rounding.
        val numerator = d.multiply(w)
        if (numerator > cap.multiply(m)) throw InputProblem(Problem.CAPACITY)
        val unitNumerator = numerator.multiply(s)
        return Calculation(m.divide(w, precision), numerator.divide(m, precision), unitNumerator.divide(m, precision),
            m.divideToIntegralValue(d), m.remainder(d), unitNumerator.remainder(m.multiply(t)).signum() == 0)
    }
    fun display(value: BigDecimal, portuguese: Boolean): String {
        val rounded = value.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
        val approximate = rounded.compareTo(value) != 0
        val text = rounded.toPlainString().let { if (portuguese) it.replace('.', ',') else it }
        return (if (approximate) "≈ " else "") + text
    }
}

/** Amounts are stored in mg; mcg conversion never uses binary floating point. */
object Mass {
    fun mg(raw: String): BigDecimal {
        require(Regex("[0-9]{1,9}([.,][0-9]{1,12})?").matches(raw.trim()))
        return raw.trim().replace(',', '.').toBigDecimal().also { require(it.signum() > 0) }
    }
    fun toMg(raw: String, unit: String): BigDecimal {
        require(unit in listOf("mg", "mcg"))
        val value = if (unit == "mcg") {
            require(Regex("[0-9]{1,12}([.,][0-9]{1,9})?").matches(raw.trim()))
            raw.trim().replace(',', '.').toBigDecimal().also { require(it.signum() > 0) }
        } else mg(raw)
        val converted = if (unit == "mcg") value.movePointLeft(3) else value
        return mg(converted.stripTrailingZeros().toPlainString())
    }
    fun fromMg(value: BigDecimal, unit: String): BigDecimal {
        require(unit in listOf("mg", "mcg"))
        return if (unit == "mcg") value.movePointRight(3) else value
    }
    fun input(rawMg: String, unit: String) = fromMg(rawMg.toBigDecimal(), unit).stripTrailingZeros().toPlainString()
    fun display(value: BigDecimal, unit: String, portuguese: Boolean) = Calculator.display(fromMg(value, unit), portuguese) + " " + unit
}
