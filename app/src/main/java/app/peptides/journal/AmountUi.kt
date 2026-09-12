package app.peptides.journal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable internal fun AmountField(value: String, unit: String, label: String, change: (String, String) -> Unit) {
    Column {
        Field(value, { change(it.take(25), unit) }, "$label ($unit)", true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("mg", "mcg").forEach { choice ->
                val converted = if (value.isBlank()) "" else runCatching {
                    Mass.fromMg(Mass.toMg(value, unit), choice).stripTrailingZeros().toPlainString()
                }.getOrNull()
                FilterChip(selected = unit == choice, enabled = converted != null,
                    onClick = { change(converted!!, choice) }, label = { Text(choice) },
                    modifier = Modifier.semantics { contentDescription = "$label: $choice" })
            }
        }
    }
}

@Composable internal fun BlendEditor(composition: String, change: (String) -> Unit) {
    val parts = Blend.draft(composition)
    fun update(index: Int, value: Compound) = change(Blend.encode(parts.mapIndexed { n, old -> if (n == index) value else old }))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t("Compounds per vial", "Compostos por frasco"), style = MaterialTheme.typography.titleMedium)
        Text(t("Copy each compound and amount from your vial label. This composition is saved with the calculation.",
            "Informe cada composto e quantidade do rótulo do frasco. Esta composição fica salva com o cálculo."))
        parts.forEachIndexed { index, part ->
            OutlinedCard {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(part.name, { update(index, part.copy(name = it.take(200))) }, t("Compound ${index+1}", "Composto ${index+1}"))
                    AmountField(part.amount, part.unit, t("Amount in vial ${index+1}", "Quantidade no frasco ${index+1}")) { value, unit ->
                        update(index, part.copy(amount = value, unit = unit))
                    }
                    if (parts.size > 2) TextButton(onClick = { change(Blend.encode(parts.filterIndexed { n, _ -> n != index })) }) {
                        Text(t("Remove compound ${index+1}", "Remover composto ${index+1}"))
                    }
                }
            }
        }
        if (parts.size < Blend.MAX_COMPOUNDS) OutlinedButton(onClick = { change(Blend.encode(parts + Compound())) }) {
            Text(t("Add compound", "Adicionar composto"))
        }
        val valid = runCatching { Blend.decode(composition) }.getOrNull()
        if (valid == null) Text(t("Enter 2 to 10 different compounds with positive amounts.",
            "Informe de 2 a 10 compostos distintos com quantidades positivas."), color = MaterialTheme.colorScheme.error)
        else if (valid.isNotEmpty()) Text(t("Total in vial: ", "Total no frasco: ") + Mass.display(Blend.total(valid), "mg", LocalLanguage.current))
    }
}

@Composable internal fun doseBasis(composition: String, reference: Int): String {
    val parts = Blend.decode(composition)
    return if (parts.isEmpty()) "" else if (reference == -1) t("total blend", "blend total") else parts[reference].name
}

@Composable internal fun entryAmount(entry: Entry, amountMg: String = entry.dose): String {
    val basis = doseBasis(entry.composition, entry.reference)
    return Mass.display(amountMg.toBigDecimal(), entry.doseUnit, LocalLanguage.current) + if (basis.isBlank()) "" else " · $basis"
}

@Composable internal fun BlendAmounts(composition: String, reference: Int, amountMg: String, title: String) {
    val parts = Blend.decode(composition)
    if (parts.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Blend.amounts(parts, reference, amountMg).forEach { (part, amount) ->
            Text("${part.name}: " + Mass.display(amount, "mg", LocalLanguage.current) + " · " + Mass.display(amount, "mcg", LocalLanguage.current))
        }
    }
}
