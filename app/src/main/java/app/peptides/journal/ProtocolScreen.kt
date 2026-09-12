package app.peptides.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale

@Composable internal fun statusName(status: String) = when (status) {
    "active" -> t("Active", "Ativo")
    "paused" -> t("Paused", "Pausado")
    "completed" -> t("Completed", "Concluído")
    "done" -> t("Done", "Realizado")
    "skipped" -> t("Skipped", "Ignorado")
    else -> t("Draft", "Rascunho")
}

@Composable internal fun logColor(status: String) = if (status == "skipped") MaterialTheme.colorScheme.error else Teal

@Composable private fun ProtocolDialog(title: String, busy: Boolean, close: () -> Unit,
    actionLabel: String? = null, actionEnabled: Boolean = false, action: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit) {
    var discard by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = { if (!busy) discard = true }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Paper) {
            Column(Modifier.systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    if (actionLabel != null && action != null) {
                        TextButton(enabled = actionEnabled && !busy, onClick = action) { Text(actionLabel) }
                    }
                    TextButton(enabled = !busy, onClick = { discard = true }) { Text(t("Close", "Fechar")) }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                content()
            }
        }
        if (discard) Confirm(t("Discard unsaved changes?", "Descartar alterações não salvas?"),
            t("The saved protocol will remain unchanged.", "O protocolo salvo será mantido."), t("Discard", "Descartar"), { discard = false }) { close() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun DatePickerField(value: String, onDateChange: (String) -> Unit, label: String,
    allowBlank: Boolean = false, blankLabel: String? = null, minDate: LocalDate? = null, maxDate: LocalDate? = null,
    yearRange: IntRange = 2000..2100) {
    var open by rememberSaveable { mutableStateOf(false) }
    val pattern = if (LocalLanguage.current) "dd/MM/yyyy" else "MM/dd/yyyy"
    val displayed = value.takeIf { it.isNotBlank() }?.let {
        LocalDate.parse(it).format(java.time.format.DateTimeFormatter.ofPattern(pattern))
    } ?: blankLabel ?: if (allowBlank) t("No end date", "Sem término") else t("Choose a date", "Escolha uma data")
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(displayed, Modifier.weight(1f))
            Icon(Icons.Outlined.CalendarMonth, t("Open calendar", "Abrir calendário"))
        }
        if (allowBlank && value.isNotBlank()) {
            TextButton(onClick = { onDateChange("") }) { Text(t("Clear date", "Limpar data")) }
        }
    }
    if (open) AppLanguage(LocalLanguage.current) {
        val initial = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
            .coerceIn(minDate ?: LocalDate.of(yearRange.first, 1, 1), maxDate ?: LocalDate.of(yearRange.last, 12, 31))
        val selectable = remember(minDate, maxDate) { object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return (minDate == null || date >= minDate) && (maxDate == null || date <= maxDate)
            }
        } }
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = yearRange, selectableDates = selectable)
        DatePickerDialog(onDismissRequest = { open = false },
            confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                open = false
            }) { Text(t("Choose", "Escolher")) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(t("Cancel", "Cancelar")) } }) {
            AppLanguage(LocalLanguage.current) { DatePicker(state = picker) }
        }
    }
}

@Composable private fun CalendarDot(color: Color) = Box(Modifier.size(6.dp).background(color, CircleShape))

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun MonthlyCalendar(protocol: Protocol, selected: LocalDate, select: (LocalDate) -> Unit) {
    val month = YearMonth.from(selected)
    val statuses = remember(protocol, month) { protocol.monthStatuses(month) }
    val today = LocalDate.now()
    val todayLabel = t("Today", "Hoje")
    val locale = if (LocalLanguage.current) Locale("pt", "BR") else Locale.US
    val title = month.month.getDisplayName(java.time.format.TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) } + " ${month.year}"
    val previous = t("Previous month", "Mês anterior")
    val next = t("Next month", "Próximo mês")
    val monthDescription = t("Calendar month ", "Mês do calendário ") + month
    val calendarDay = t("Calendar day ", "Dia do calendário ")
    val noItems = t("No items", "Sem itens")
    val planned = t("Planned", "Planejado")
    val done = t("Done", "Realizado")
    val skipped = t("Skipped", "Ignorado")
    val plannedColor = MaterialTheme.colorScheme.outline
    val doneColor = logColor("done")
    val skippedColor = logColor("skipped")
    val weekdays = if (LocalLanguage.current) listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom") else listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    fun monthDate(delta: Long): LocalDate {
        val target = month.plusMonths(delta)
        return target.atDay(minOf(selected.dayOfMonth, target.lengthOfMonth()))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("Monthly calendar", "Calendário mensal"), style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = month > YearMonth.of(2000, 1), onClick = { select(monthDate(-1)) }, modifier = Modifier.semantics { contentDescription = previous }) { Text("‹") }
            Text(title, Modifier.weight(1f).semantics { contentDescription = monthDescription }, style = MaterialTheme.typography.titleMedium)
            TextButton(enabled = month < YearMonth.of(2100, 12), onClick = { select(monthDate(1)) }, modifier = Modifier.semantics { contentDescription = next }) { Text("›") }
        }
        TextButton(onClick = { select(today) }) { Text(t("Go to today", "Ir para hoje")) }
        Row(Modifier.fillMaxWidth()) { weekdays.forEach { label -> Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) } }
        val first = month.atDay(1).dayOfWeek.value - 1
        val dates = List(first) { null } + (1..month.lengthOfMonth()).map(month::atDay)
        dates.chunked(7).forEach { week -> Row(Modifier.fillMaxWidth()) {
            (week + List(7 - week.size) { null }).forEach { date ->
                if (date == null) Spacer(Modifier.weight(1f).height(48.dp)) else {
                    val status = statuses.getValue(date)
                    val labels = buildList { if (status.planned) add(planned); if (status.done) add(done); if (status.skipped) add(skipped) }
                    val description = "$calendarDay$date: " + labels.ifEmpty { listOf(noItems) }.joinToString(", ")
                    Surface(shape = CircleShape, color = if (date == selected) Water else Color.Transparent,
                        border = if (date == today) BorderStroke(1.dp, Teal) else null,
                        modifier = Modifier.weight(1f)) {
                        Column(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = date == selected, role = Role.Button, onClick = { select(date) })
                            .semantics { contentDescription = description; if (date == today) stateDescription = todayLabel },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
                                if (status.planned) CalendarDot(plannedColor)
                                if (status.done) CalendarDot(doneColor)
                                if (status.skipped) CalendarDot(skippedColor)
                            }
                        }
                    }
                }
            }
        } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(plannedColor to planned, doneColor to done, skippedColor to skipped).forEach { (color, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { CalendarDot(color); Text(label, style = MaterialTheme.typography.labelSmall) }
            }
        }
        Text(t("Touch a day to open its agenda.", "Toque em um dia para abrir a agenda."), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun ProtocolScreen(rows: List<ProtocolRow>, peptides: List<Peptide>, entries: List<Entry>, busy: Boolean,
    save: (Protocol, () -> Unit) -> Unit, record: (String, ProtocolLog, () -> Unit) -> Unit,
    delete: (String, () -> Unit) -> Unit, archive: (String, Boolean, () -> Unit) -> Unit) {
    val protocols = remember(rows) { rows.map { ProtocolCodec.decode(JSONObject(it.document)) } }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var modal by rememberSaveable { mutableStateOf("") }
    var itemId by rememberSaveable { mutableStateOf<String?>(null) }
    var entryDraft by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var logTime by rememberSaveable { mutableStateOf("") }
    var logStatus by rememberSaveable { mutableStateOf("done") }
    var query by rememberSaveable { mutableStateOf("") }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val p = protocols.find { it.id == selected }
    val previous = p?.items?.find { it.id == itemId }
    val snapshot = remember(entryDraft) { if (entryDraft.isBlank()) null else ProtocolCodec.entry(JSONObject(entryDraft)) }
    BackHandler(selected != null && modal.isEmpty()) { selected = null }
    if (p == null) {
        LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Heading(t("Your protocols.", "Seus protocolos."), t("Plan each item. Keep track of each day.", "Planeje cada item. Acompanhe cada dia.")) }
            item { Button(enabled = !busy, onClick = { selected = null; modal = "protocol" }) { Text(t("New protocol", "Novo protocolo")) } }
            item { Field(query, { query = it }, t("Search protocols", "Buscar protocolos")) }
            item { Row(Modifier.fillMaxWidth().toggleable(value = showArchived, role = Role.Checkbox, onValueChange = { showArchived = it }), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(showArchived, null)
                Text(t("Show archived", "Mostrar arquivados"))
            } }
            if (protocols.isEmpty()) item { Text(t("Create a protocol and add peptides with their own schedules.", "Crie um protocolo e adicione peptídeos com suas próprias agendas.")) }
            val visible = protocols.filter { (showArchived || !it.archived) && it.name.contains(query, true) }
            if (protocols.isNotEmpty() && visible.isEmpty()) item { Text(t("No protocols found. Check the search or show archived protocols.", "Nenhum protocolo encontrado. Confira a busca ou mostre os arquivados.")) }
            items(visible, key = { it.id }) { plan ->
                ListItem(headlineContent = { Text(plan.name) }, supportingContent = { Text((if (plan.archived) t("Archived", "Arquivado") else statusName(plan.status)) + " · " + plan.start) },
                    modifier = Modifier.clickable(enabled = !busy) { selected = plan.id; day = today.toString() })
                HorizontalDivider()
            }
        }
    } else {
        val date = runCatching { ProtocolCodec.date(day) }.getOrNull()
        LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { TextButton(onClick = { selected = null }) { Text(t("All protocols", "Todos os protocolos")) } }
            item { Heading(p.name, (if (p.archived) t("Archived", "Arquivado") else statusName(p.status)) + " · " + p.start + if (p.end.isBlank()) "" else " — " + p.end) }
            if (p.notes.isNotBlank()) item { Text(p.notes) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(enabled = !busy && !p.archived, onClick = { modal = "protocol" }) { Text(t("Edit protocol", "Editar protocolo")) }
                val copyName = p.name.take(180) + t(" (copy)", " (cópia)")
                OutlinedButton(enabled = !busy, onClick = {
                    val copy = p.duplicate(today, copyName)
                    save(copy) { selected = copy.id; day = today.toString() }
                }) { Text(t("Duplicate", "Duplicar")) }
            } }
            item {
                TextButton(enabled = !busy, onClick = { modal = if (p.archived) "unarchive" else if (p.logs.isEmpty()) "delete" else "archive" }) {
                    Text(if (p.archived) t("Unarchive protocol", "Desarquivar protocolo")
                        else if (p.logs.isEmpty()) t("Delete protocol", "Excluir protocolo") else t("Archive protocol", "Arquivar protocolo"))
                }
            }
            item { Text(t("Peptides in this protocol", "Peptídeos deste protocolo"), style = MaterialTheme.typography.titleLarge) }
            items(p.currentItems(today), key = { it.id }) { item ->
                ListItem(headlineContent = { Text(item.entry.peptideName) },
                    supportingContent = { Text(entryAmount(item.entry) + " · ${item.times.joinToString(", ")}\n${item.from}" + if (item.to.isBlank()) "" else " — ${item.to}") },
                    trailingContent = { Text(t("Edit", "Editar")) }, modifier = Modifier.clickable(enabled = !busy && p.status != "completed") {
                        itemId = item.id; entryDraft = ProtocolCodec.entryJson(item.entry).toString(); modal = "item"
                    })
            }
            item { Button(enabled = !busy && p.status != "completed" && (p.end.isBlank() || p.end >= today.toString()), onClick = {
                itemId = null; entryDraft = ""; modal = "item"
            }) { Text(t("Add peptide", "Adicionar peptídeo")) } }
            item { MonthlyCalendar(p, date ?: today) { day = it.toString() } }
            item { HorizontalDivider(); Spacer(Modifier.height(16.dp)); Text(t("Daily agenda", "Agenda do dia"), style = MaterialTheme.typography.titleLarge) }
            item { DatePickerField(day, { day = it }, t("Date", "Data")) }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(enabled = date != null && date > LocalDate.of(2000, 1, 1), onClick = { day = date!!.minusDays(1).toString() }) { Text(t("Previous day", "Dia anterior")) }
                TextButton(onClick = { day = today.toString() }) { Text(t("Today", "Hoje")) }
                TextButton(enabled = date != null && date < LocalDate.of(2100, 12, 31), onClick = { day = date!!.plusDays(1).toString() }) { Text(t("Next day", "Próximo dia")) }
            } }
            if (date == null) item { Text(t("Enter a valid date.", "Informe uma data válida."), color = MaterialTheme.colorScheme.error) }
            else {
                val occurrences = p.items.filter { it.occurs(date) }.flatMap { item -> item.times.map { time -> item to time } }.sortedBy { it.second }
                if (occurrences.isEmpty()) item { Text(t("Nothing planned for this date.", "Nada planejado para esta data.")) }
                items(occurrences, key = { "${it.first.id}/${it.second}" }) { (item, time) ->
                    val log = p.logs.find { it.itemId == item.id && it.day == day && it.time == time }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("$time · ${item.entry.peptideName}", style = MaterialTheme.typography.titleMedium)
                        Text(t("Planned: ", "Planejado: ") + entryAmount(item.entry) + " · " + item.entry.volume.toBigDecimal().let { Calculator.display(it, LocalLanguage.current) } + " mL")
                        BlendAmounts(item.entry.composition, item.entry.reference, item.entry.dose, t("Planned per compound", "Planejado por composto"))
                        if (item.notes.isNotBlank()) Text(item.notes)
                        if (log != null) {
                            Text(statusName(log.status) + if (log.actualMg.isBlank()) "" else " · " + entryAmount(item.entry, log.actualMg), color = logColor(log.status))
                            if (log.actualMg.isNotBlank()) BlendAmounts(item.entry.composition, item.entry.reference, log.actualMg, t("Recorded per compound", "Registrado por composto"))
                            if (log.notes.isNotBlank()) Text(log.notes)
                        } else if (p.status == "active" && date <= today) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(enabled = !busy, onClick = { itemId = item.id; logTime = time; logStatus = "done"; modal = "log" }) { Text(t("Mark done", "Marcar realizado")) }
                                OutlinedButton(enabled = !busy, onClick = { itemId = item.id; logTime = time; logStatus = "skipped"; modal = "log" }) { Text(t("Skip", "Ignorar")) }
                            }
                        } else Text(if (date > today) t("Planned", "Planejado") else t("No completion recorded", "Sem realização registrada"))
                    }
                    HorizontalDivider()
                }
            }
            item { Text(t("Schedules use local dates and times. Enable local reminders in Settings.", "A agenda usa datas e horários locais. Ative os lembretes locais em Configurações."), style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (p != null && modal in listOf("delete", "archive", "unarchive")) {
        val deleting = modal == "delete"
        val archiving = modal == "archive"
        Confirm(if (deleting) t("Delete protocol?", "Excluir protocolo?") else if (archiving) t("Archive protocol?", "Arquivar protocolo?") else t("Unarchive protocol?", "Desarquivar protocolo?"),
            p.name + "\n\n" + if (deleting) t("This protocol has no completion records. Its schedule will be permanently deleted. Saved calculations are kept.", "Este protocolo não tem registros de realização. Sua agenda será excluída permanentemente. Os cálculos salvos serão mantidos.")
                else if (archiving) t("The protocol will leave the main list and stop reminders. Its schedule and all records remain available under Show archived.", "O protocolo sairá da lista principal e deixará de gerar lembretes. A agenda e todos os registros continuarão disponíveis em Mostrar arquivados.")
                else t("The protocol will return as paused. Activate it manually when you want to resume reminders.", "O protocolo voltará como pausado. Ative-o manualmente quando quiser retomar os lembretes."),
            if (deleting) t("Delete", "Excluir") else if (archiving) t("Archive", "Arquivar") else t("Unarchive", "Desarquivar"),
            { modal = "" }) {
                if (!busy) {
                    if (deleting) delete(p.id) { modal = ""; selected = null }
                    else archive(p.id, archiving) { modal = ""; selected = null }
                }
            }
    }
    if (modal == "protocol") {
        key("protocol-editor-${p?.id ?: "new"}") {
            ProtocolEditor(p, busy, { modal = "" }) { edited -> save(edited) { selected = edited.id; modal = "" } }
        }
    }
    if (modal in listOf("item", "entry") && p != null) {
        key("item-editor-${p.id}-${itemId ?: "new"}") {
            ItemEditor(p, previous, snapshot, entries, busy, { modal = "" }, { entryDraft = ProtocolCodec.entryJson(it).toString() },
                { modal = "entry" }, { if (previous != null) save(p.endItem(previous, today)) { modal = "" } }) { item -> save(p.revise(item, previous, today)) { modal = "" } }
        }
    }
    if (modal == "entry") key("entry-editor-${p?.id ?: "new"}-${itemId ?: "new"}") {
        ProtocolDialog(t("Calculation for this item", "Cálculo deste item"), busy, { modal = "item" }) {
            EntryForm(peptides, entries, snapshot, false, busy, t("Use in protocol", "Usar no protocolo")) {
                entryDraft = ProtocolCodec.entryJson(it).toString(); modal = "item"
            }
        }
    }
    if (modal == "log" && p != null && previous != null) {
        var amount by rememberSaveable { mutableStateOf("") }
        var unit by rememberSaveable { mutableStateOf(previous.entry.doseUnit) }
        var note by rememberSaveable { mutableStateOf("") }
        val actualMg = runCatching { Mass.toMg(amount, unit).toPlainString() }.getOrNull()
        val valid = logStatus == "skipped" || actualMg != null
        ProtocolDialog(statusName(logStatus), busy, { modal = "" }) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${previous.entry.peptideName} · $day · $logTime", style = MaterialTheme.typography.titleLarge)
                Text(t("Planned: ", "Planejado: ") + entryAmount(previous.entry))
                if (logStatus == "done") {
                    AmountField(amount, unit, t("Actual amount", "Quantidade realizada")) { value, chosenUnit -> amount = value; unit = chosenUnit }
                    if (actualMg != null) BlendAmounts(previous.entry.composition, previous.entry.reference, actualMg, t("Each compound recorded", "Cada composto registrado"))
                }
                Field(note, { note = it.take(4000) }, t("Observation (optional)", "Observação (opcional)"), multiline = true)
                Text(t("Confirm what happened. Saved completions cannot be changed in this version.", "Confirme o que aconteceu. Realizações salvas não podem ser alteradas nesta versão."), style = MaterialTheme.typography.bodySmall)
                Button(enabled = valid && !busy, onClick = {
                    record(p.id, ProtocolLog(previous.id, day, logTime, logStatus, if (logStatus == "done") actualMg!! else "", note.trim())) { modal = "" }
                }) { Text(t("Confirm record", "Confirmar registro")) }
            }
        }
    }
}

@Composable private fun ProtocolEditor(original: Protocol?, busy: Boolean, close: () -> Unit, save: (Protocol) -> Unit) {
    val draftId = rememberSaveable { original?.id ?: java.util.UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(original?.name ?: "") }
    var note by rememberSaveable { mutableStateOf(original?.notes ?: "") }
    var start by rememberSaveable { mutableStateOf(original?.start ?: LocalDate.now().toString()) }
    var end by rememberSaveable { mutableStateOf(original?.end ?: "") }
    var status by rememberSaveable { mutableStateOf(original?.status ?: "draft") }
    val valid = name.isNotBlank() && runCatching { ProtocolCodec.date(start); require(end.isBlank() || ProtocolCodec.date(end) >= ProtocolCodec.date(start)) }.isSuccess
    val saveDraft = {
        save((original ?: Protocol(id = draftId, name = name.trim(), start = start, end = end)).copy(name = name.trim(), notes = note.trim(), status = status))
    }
    ProtocolDialog(if (original == null) t("New protocol", "Novo protocolo") else t("Edit protocol", "Editar protocolo"), busy, close,
        actionLabel = t("Save protocol", "Salvar protocolo"), actionEnabled = valid, action = saveDraft) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Field(name, { name = it.take(200) }, t("Protocol name", "Nome do protocolo"))
            if (original == null) {
                DatePickerField(start, { start = it }, t("Start date", "Início"))
                DatePickerField(end, { end = it }, t("End date (optional)", "Término (opcional)"), allowBlank = true)
                Text(t("Starts as a draft. Add peptides, then activate it.", "Começa como rascunho. Adicione peptídeos e depois ative."))
            } else {
                Text("$start — " + end.ifBlank { t("No end date", "Sem término") })
                Text(t("The period is preserved after creation. Duplicate to start a new period.", "O período é preservado após a criação. Duplique para iniciar um novo período."), style = MaterialTheme.typography.bodySmall)
                listOf(listOf("draft", "active"), listOf("paused", "completed")).forEach { group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { group.forEach { s ->
                        FilterChip(selected = status == s, enabled = s != "active" || original.items.isNotEmpty(), onClick = { status = s }, label = { Text(statusName(s)) })
                    } }
                }
            }
            Field(note, { note = it.take(4000) }, t("Description and notes", "Descrição e observações"), multiline = true)
            if (!valid) Text(t("Enter a name and valid dates; the end cannot precede the start.", "Informe um nome e datas válidas; o término não pode ser anterior ao início."), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun ItemEditor(p: Protocol, previous: ProtocolItem?, entry: Entry?, entries: List<Entry>, busy: Boolean,
    close: () -> Unit, choose: (Entry) -> Unit, calculate: () -> Unit, endItem: () -> Unit, save: (ProtocolItem) -> Unit) {
    val today = LocalDate.now()
    val minimum = maxOf(ProtocolCodec.date(p.start), if (previous == null) today else today.plusDays(1))
    var from by rememberSaveable { mutableStateOf(maxOf(previous?.from ?: "", minimum.toString())) }
    var to by rememberSaveable { mutableStateOf(previous?.to ?: p.end) }
    var days by rememberSaveable { mutableStateOf(previous?.days?.joinToString(",") ?: "") }
    var times by rememberSaveable { mutableStateOf(previous?.times?.joinToString(", ") ?: "") }
    var notes by rememberSaveable { mutableStateOf(previous?.notes ?: "") }
    var picker by rememberSaveable { mutableStateOf(false) }
    var ending by rememberSaveable { mutableStateOf(false) }
    val itemDraftId = rememberSaveable { java.util.UUID.randomUUID().toString() }
    val selectedDays = days.split(',').mapNotNull { it.toIntOrNull() }
    val parsedTimes = times.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    val valid = entry != null && selectedDays.isNotEmpty() && parsedTimes.size in 1..12 && runCatching {
        require(ProtocolCodec.date(from) >= minimum)
        require(to.isBlank() || ProtocolCodec.date(to) >= ProtocolCodec.date(from))
        require(p.end.isBlank() || to.isNotBlank() && to <= p.end && from <= p.end)
        parsedTimes.forEach { ProtocolCodec.time(it) }
    }.isSuccess
    ProtocolDialog(if (previous == null) t("Add peptide", "Adicionar peptídeo") else t("Edit scheduled item", "Editar item da agenda"), busy, close,
        actionLabel = t("Save item", "Salvar item"), actionEnabled = valid, action = {
            save(ProtocolItem(itemDraftId, previous?.group ?: itemDraftId, entry!!, from, to, selectedDays, parsedTimes, notes.trim()))
        }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (entry != null) {
                Text(entry.peptideName, style = MaterialTheme.typography.titleLarge)
                Text(entryAmount(entry) + " · ${entry.volume} mL")
                BlendAmounts(entry.composition, entry.reference, entry.dose, t("Each compound in this application", "Cada composto nesta aplicação"))
                Text(syringeLabel(entry.capacity, entry.scale, entry.tick))
            }
            if (entries.isNotEmpty()) {
                OutlinedButton(onClick = { picker = true }) { Text(t("Choose saved calculation", "Escolher cálculo salvo")) }
            }
            OutlinedButton(onClick = calculate) {
                Text(if (entries.isEmpty()) t("Select peptide and enter calculation", "Selecionar peptídeo e informar cálculo")
                else t("Enter calculation", "Informar cálculo"))
            }
            Text(if (previous == null) t("Choose the dates and weekdays for this item.", "Escolha as datas e os dias da semana deste item.")
                else t("Changes apply tomorrow or later. Earlier dates and completions retain the previous plan.", "Alterações valem a partir de amanhã. Datas e realizações anteriores mantêm o planejamento original."))
            DatePickerField(from, { from = it }, t("Item start", "Início do item"))
            DatePickerField(to, { to = it }, t("Item end (optional)", "Término do item (opcional)"), allowBlank = true)
            val labels = if (LocalLanguage.current) listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom") else listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            listOf(0..3, 4..6).forEach { range -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                range.forEach { n -> FilterChip(selected = n+1 in selectedDays, onClick = {
                    days = (if (n+1 in selectedDays) selectedDays - (n+1) else selectedDays + (n+1)).sorted().joinToString(",")
                }, label = { Text(labels[n]) }) }
            } }
            TextButton(onClick = { days = "1,2,3,4,5,6,7" }) { Text(t("Every day", "Todos os dias")) }
            ScheduleTimes(parsedTimes) { times = it.joinToString(", ") }
            Field(notes, { notes = it.take(4000) }, t("Item notes", "Observações do item"), multiline = true)
            if (!valid) Text(t("Select a calculation, weekdays and valid times. Dates must fit the protocol period and start on or after ", "Selecione um cálculo, dias da semana e horários válidos. As datas devem estar no período do protocolo e começar em ou após ") + minimum,
                style = MaterialTheme.typography.bodySmall)
            if (previous != null) TextButton(enabled = !busy, onClick = { ending = true }) { Text(t("End this item after today", "Encerrar este item após hoje")) }
        }
        if (picker) {
            var query by rememberSaveable { mutableStateOf("") }
            AlertDialog(onDismissRequest = { picker = false }, title = { Text(t("Choose saved calculation", "Escolher cálculo salvo")) }, text = { Column {
                Field(query, { query = it }, t("Search records", "Buscar registros"))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(entries.filter { it.title.contains(query, true) || it.peptideName.contains(query, true) }, key = { it.id }) { e ->
                        ListItem(headlineContent = { Text(e.title.ifBlank { e.peptideName }) }, supportingContent = { Text(e.peptideName + " · " + entryAmount(e)) },
                            modifier = Modifier.clickable { choose(e); picker = false })
                    }
                }
            } }, confirmButton = { TextButton(onClick = { picker = false }) { Text(t("Close", "Fechar")) } })
        }
        if (ending) Confirm(t("End this scheduled item?", "Encerrar este item da agenda?"),
            t("Future dates will be removed. Earlier dates and saved completions are preserved.", "As datas futuras serão removidas. Datas anteriores e realizações salvas serão preservadas."),
            t("End item", "Encerrar item"), { ending = false }, endItem)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ScheduleTimes(times: List<String>, change: (List<String>) -> Unit) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    Text(t("Times", "Horários"), style = MaterialTheme.typography.titleMedium)
    if (times.isEmpty()) Text(t("Add a time using the clock.", "Adicione um horário pelo relógio."))
    times.forEach { time ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { editing = time }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Schedule, null); Spacer(Modifier.width(8.dp)); Text(time)
            }
            IconButton(onClick = { change(times - time) }) {
                Icon(Icons.Outlined.Close, t("Remove time ", "Remover horário ") + time)
            }
        }
    }
    OutlinedButton(enabled = times.size < 12, onClick = { editing = "" }) {
        Icon(Icons.Outlined.Schedule, null); Spacer(Modifier.width(8.dp)); Text(t("Add time", "Adicionar horário"))
    }
    editing?.let { original ->
        val initial = if (original.isEmpty()) LocalTime.of(8, 0) else LocalTime.parse(original)
        val picker = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
        val selected = LocalTime.of(picker.hour, picker.minute).toString()
        val duplicate = selected in times && selected != original
        Dialog(onDismissRequest = { editing = null }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = Paper) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(t("Choose time", "Escolher horário"), style = MaterialTheme.typography.titleLarge)
                    AppLanguage(LocalLanguage.current) { TimePicker(state = picker) }
                    if (duplicate) Text(t("This time is already added.", "Este horário já foi adicionado."), color = MaterialTheme.colorScheme.error)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editing = null }) { Text(t("Cancel", "Cancelar")) }
                        TextButton(enabled = !duplicate, onClick = {
                            change(((times - original) + selected).sorted()); editing = null
                        }) { Text(t("Use time", "Usar horário")) }
                    }
                }
            }
        }
    }
}
