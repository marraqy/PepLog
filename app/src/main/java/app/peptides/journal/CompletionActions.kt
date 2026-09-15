package app.peptides.journal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun CompletionActions(item: ProtocolItem, day: String, time: String, busy: Boolean,
    skip: () -> Unit, record: (ProtocolLog, () -> Unit) -> Unit) {
    var adjusting by rememberSaveable { mutableStateOf(false) }
    var amount by rememberSaveable { mutableStateOf(Mass.input(item.entry.dose, item.entry.doseUnit)) }
    var unit by rememberSaveable { mutableStateOf(item.entry.doseUnit) }
    var note by rememberSaveable { mutableStateOf("") }
    val actualMg = runCatching { Mass.toMg(amount, unit).toPlainString() }.getOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (adjusting) {
            AmountField(amount, unit, t("Actual amount", "Quantidade realizada")) { value, chosenUnit -> amount = value; unit = chosenUnit }
            if (actualMg != null) BlendAmounts(item.entry.composition, item.entry.reference, actualMg, t("Each compound recorded", "Cada composto registrado"))
            Field(note, { note = it.take(4000) }, t("Observation (optional)", "Observação (opcional)"), multiline = true)
            Text(t("Check before saving. Saved records cannot be edited.", "Confira antes de salvar. Registros salvos não podem ser editados."), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(enabled = !busy && actualMg != null, onClick = {
                    record(ProtocolLog(item.id, day, time, "done", actualMg!!, note.trim())) { adjusting = false }
                }) { Text(t("Confirm record", "Confirmar registro")) }
                TextButton(enabled = !busy, onClick = { adjusting = false }) { Text(t("Cancel", "Cancelar")) }
            }
        } else {
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                record(ProtocolLog(item.id, day, time, "done", item.entry.dose)) {}
            }) { Text(t("Done as planned · ", "Realizado conforme planejado · ") + entryAmount(item.entry)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(enabled = !busy, onClick = { adjusting = true }) { Text(t("Adjust amount / note", "Ajustar quantidade / observação")) }
                TextButton(enabled = !busy, onClick = skip) { Text(t("Skip", "Ignorar")) }
            }
        }
    }
}
