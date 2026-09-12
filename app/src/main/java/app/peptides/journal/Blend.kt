package app.peptides.journal

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale

data class Compound(val name: String = "", val amount: String = "", val unit: String = "mg") {
    fun mg() = Mass.toMg(amount, unit)
}

object Blend {
    const val MAX_COMPOUNDS = 10
    fun encode(parts: List<Compound>): String = JSONArray().apply {
        parts.forEach { put(JSONObject().put("name", it.name).put("amount", it.amount).put("unit", it.unit)) }
    }.toString()
    // Drafts may be incomplete. Validate before calculating, saving or restoring.
    fun draft(text: String): List<Compound> {
        val rows = JSONArray(text)
        require(rows.length() <= MAX_COMPOUNDS)
        return (0 until rows.length()).map { rows.getJSONObject(it).run {
            Compound(getString("name"), getString("amount"), getString("unit"))
        } }
    }
    fun decode(text: String): List<Compound> = draft(text).also { parts ->
        require(parts.isEmpty() || parts.size in 2..MAX_COMPOUNDS)
        require(parts.map { it.name.trim().lowercase(Locale.ROOT) }.distinct().size == parts.size)
        parts.forEach { require(it.name.isNotBlank() && it.name.length <= 200); it.mg() }
        if (parts.isNotEmpty()) Mass.mg(total(parts).toPlainString())
    }
    fun total(parts: List<Compound>) = parts.fold(BigDecimal.ZERO) { sum, part -> sum + part.mg() }
    fun basis(parts: List<Compound>, reference: Int): BigDecimal {
        require(parts.isNotEmpty() && reference in -1 until parts.size)
        return if (reference == -1) total(parts) else parts[reference].mg()
    }
    fun amounts(parts: List<Compound>, reference: Int, doseMg: String): List<Pair<Compound, BigDecimal>> {
        val dose = Mass.mg(doseMg)
        val mass = basis(parts, reference)
        return parts.map { it to it.mg().multiply(dose).divide(mass, MathContext.DECIMAL128) }
    }
}
