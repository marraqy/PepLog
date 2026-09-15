package app.peptides.journal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private data class TodayItem(val protocol: Protocol, val item: ProtocolItem, val time: String, val log: ProtocolLog?)

@Composable
internal fun TodayScreen(rows: List<ProtocolRow>, busy: Boolean, now: () -> ZonedDateTime = { ZonedDateTime.now() }, record: (String, ProtocolLog, () -> Unit) -> Unit) {
    val today = currentDay(now)
    val protocols = remember(rows) { rows.map { ProtocolCodec.decode(JSONObject(it.document)) } }
    val agenda = remember(protocols, today) { protocols.filter { it.status == "active" }.flatMap { protocol ->
        protocol.items.filter { it.occurs(today) }.flatMap { item -> item.times.map { time ->
            TodayItem(protocol, item, time, protocol.logs.find { log -> log.itemId == item.id && log.day == today.toString() && log.time == time })
        } }
    }.sortedWith(compareBy<TodayItem> { it.time }.thenBy { it.protocol.name }.thenBy { it.item.entry.peptideName }) }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var chosenDay by rememberSaveable { mutableStateOf("") }
    val selected = protocols.flatMap { protocol -> protocol.items.flatMap { item -> item.times.map { time ->
        TodayItem(protocol, item, time, null)
    } } }.find { "${it.protocol.id}/${it.item.id}/${it.time}" == chosen }
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            val date = today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(if (LocalLanguage.current) Locale.forLanguageTag("pt-BR") else Locale.US))
            Heading(t("Today.", "Hoje."), date)
        }
        if (agenda.isEmpty()) item { Text(t("No items planned in active protocols for today.", "Nenhum item planejado em protocolos ativos para hoje.")) }
        items(agenda, key = { "${it.protocol.id}/${it.item.id}/${it.time}" }) { row ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${row.time} · ${row.item.entry.peptideName}", style = MaterialTheme.typography.titleLarge)
                Text(row.protocol.name, style = MaterialTheme.typography.bodyMedium, color = Teal)
                Text(t("Planned: ", "Planejado: ") + entryAmount(row.item.entry) + " · " +
                    Calculator.display(row.item.entry.volume.toBigDecimal(), LocalLanguage.current) + " mL")
                BlendAmounts(row.item.entry.composition, row.item.entry.reference, row.item.entry.dose, t("Planned per compound", "Planejado por composto"))
                if (row.item.notes.isNotBlank()) Text(row.item.notes)
                if (row.log != null) {
                    Text(statusName(row.log.status) + if (row.log.actualMg.isBlank()) "" else " · " + entryAmount(row.item.entry, row.log.actualMg), color = logColor(row.log.status))
                    if (row.log.actualMg.isNotBlank()) BlendAmounts(row.item.entry.composition, row.item.entry.reference, row.log.actualMg, t("Recorded per compound", "Registrado por composto"))
                    if (row.log.notes.isNotBlank()) Text(row.log.notes)
                } else key(today) {
                    CompletionActions(row.item, today.toString(), row.time, busy,
                        skip = { chosen = "${row.protocol.id}/${row.item.id}/${row.time}"; chosenDay = today.toString() },
                        record = { log, done -> record(row.protocol.id, log, done) })
                }
                HorizontalDivider()
            }
        }
        item { Text(t("Only active protocols appear here. Open Protocols to plan future days or change a schedule.", "Somente protocolos ativos aparecem aqui. Abra Protocolos para planejar os próximos dias ou alterar uma agenda."), style = MaterialTheme.typography.bodySmall) }
    }
    if (selected != null) SkipLogDialog(selected.item, selected.protocol.name, selected.time, chosenDay, busy, { chosen = null }) { log -> record(selected.protocol.id, log) { chosen = null } }
}

@Composable internal fun SkipLogDialog(item: ProtocolItem, protocolName: String, time: String, day: String, busy: Boolean, dismiss: () -> Unit, save: (ProtocolLog) -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text(t("Skip this occurrence?", "Ignorar esta ocorrência?")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("$time · ${item.entry.peptideName}", style = MaterialTheme.typography.titleMedium)
            Text(protocolName)
            Text(LocalDate.parse(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(if (LocalLanguage.current) Locale.forLanguageTag("pt-BR") else Locale.US)))
            Text(t("Planned: ", "Planejado: ") + entryAmount(item.entry))
            Field(note, { note = it.take(4000) }, t("Observation (optional)", "Observação (opcional)"), multiline = true)
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = {
        save(ProtocolLog(item.id, day, time, "skipped", "", note.trim()))
    }) { Text(t("Confirm record", "Confirmar registro")) } }, dismissButton = { TextButton(enabled = !busy, onClick = dismiss) { Text(t("Cancel", "Cancelar")) } })
}
