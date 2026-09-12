package app.peptides.journal

import java.util.Locale

object CatalogDefaults {
    private fun key(text: String) = text.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    fun matches(peptide: Peptide, query: String) = key(peptide.name).contains(key(query)) || key(peptide.aliases).contains(key(query))
    fun missing(existing: List<Peptide>, defaults: List<Peptide>): List<Peptide> = defaults.filter { candidate ->
        existing.none { it.id == candidate.id || key(it.name) == key(candidate.name) ||
            candidate.aliases.split(';').any { alias -> alias.isNotBlank() && key(alias) == key(it.name) } }
    }
    // Names only: quantities must come from the user's vial label.
    fun composition(peptide: Peptide): String {
        if (peptide.composition != "[]") return peptide.composition
        val names = when (key(peptide.name)) {
            key("BPC-157 + TB-500") -> listOf("BPC-157", "TB-500")
            key("CJC no DAC + Ipamorelin") -> listOf("CJC-1295 no DAC", "Ipamorelin")
            key("Tesamorelin + Ipamorelin") -> listOf("Tesamorelin", "Ipamorelin")
            key("GLOW (TB-500 + BPC-157 + GHK-CU)"), "glow" -> listOf("TB-500", "BPC-157", "GHK-CU")
            key("KLOW (TB-500 + BPC-157 + GHK-CU + KPV)"), "klow" -> listOf("TB-500", "BPC-157", "GHK-CU", "KPV")
            else -> return "[]"
        }
        return Blend.encode(names.map { Compound(name = it) })
    }
}
