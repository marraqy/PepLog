package app.peptides.journal

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

internal val Ink = Color(0xFF16343B)
internal val Teal = Color(0xFF006B62)
internal val Paper = Color(0xFFF3F7F8)
internal val Water = Color(0xFFD9F0EB)
internal val LocalLanguage = staticCompositionLocalOf { false }
@Composable internal fun t(en: String, pt: String) = if (LocalLanguage.current) pt else en

class MainActivity : ComponentActivity() {
    private var reminderOpened by mutableLongStateOf(0)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Reminders.OPEN_TODAY) reminderOpened = System.nanoTime()
    }
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try { Reminders.refresh(applicationContext) }
            catch (error: Exception) { android.util.Log.e("PepLogReminders", "Could not refresh reminders", error) }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == Reminders.OPEN_TODAY) reminderOpened = System.nanoTime()
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Teal, onPrimary = Color.White,
                primaryContainer = Water, onPrimaryContainer = Ink, background = Paper, surface = Paper,
                onSurface = Ink, onBackground = Ink, secondary = Color(0xFF426A95), secondaryContainer = Water,
                onSecondaryContainer = Ink, surfaceVariant = Color(0xFFE3ECEE), onSurfaceVariant = Color(0xFF486067), outline = Color(0xFF6E858A))) { JournalApp(reminderOpened) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalApp(reminderOpened: Long = 0, refreshReminders: suspend (android.content.Context) -> Unit = { Reminders.refresh(it, clearNotification = true) }) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val prefs = remember { context.getSharedPreferences("settings", 0) }
    var portuguese by rememberSaveable { mutableStateOf(prefs.getBoolean("pt", false)) }
    val db = remember { JournalDb.get(context) }
    val scope = rememberCoroutineScope()
    val peptides by db.dao().peptides().collectAsStateWithLifecycle(initialValue = emptyList())
    val entries by db.dao().entries().collectAsStateWithLifecycle(initialValue = emptyList())
    val protocols by db.dao().protocols().collectAsStateWithLifecycle(initialValue = emptyList())
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var screen by rememberSaveable { mutableStateOf("home") }
    val historyState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    LaunchedEffect(reminderOpened) { if (reminderOpened != 0L) screen = "today" }
    var entryId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var formKey by rememberSaveable { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }
    var pendingBackup by remember { mutableStateOf<BackupData?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf("home") }
    val selected = entries.find { it.id == entryId }

    LaunchedEffect(Unit) { try { db.seed(context); ready = true } catch (_: Exception) { failed = true } }
    AppLanguage(portuguese) {
        val ioError = t("Could not complete the operation. Check your journal before retrying.", "Não foi possível concluir a operação. Confira seu caderno antes de tentar novamente.")
        val reminderError = t("Operation completed, but reminders could not be updated. Check notification settings.",
            "Operação concluída, mas os lembretes não puderam ser atualizados. Confira as configurações de notificação.")
        val saved = t("Record saved", "Registro salvo")
        val backedUp = t("Backup exported", "Backup exportado")
        val restored = t("Backup restored", "Backup restaurado")
        val invalidBackup = t("Invalid or incompatible backup. No data was changed.", "Backup inválido ou incompatível. Nenhum dado foi alterado.")
        fun operation(action: suspend () -> Unit) {
            if (busy) return
            busy = true
            scope.launch(Dispatchers.Main.immediate) {
                try {
                    try { action() } catch (error: Exception) {
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        android.util.Log.e("PeptideJournal", "Operation failed", error)
                        snack.showSnackbar(if (error is DataCapacityException) { if (portuguese) error.limit.pt else error.limit.en } else ioError)
                        return@launch
                    }
                    try { refreshReminders(context) } catch (error: Exception) {
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        android.util.Log.e("PeptideJournal", "Operation completed; reminder refresh failed", error)
                        snack.showSnackbar(reminderError)
                    }
                } finally { busy = false }
            }
        }
        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) operation {
                withContext(Dispatchers.IO) {
                    val text = Backup.export(db)
                    requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(text) }
                }
                snack.showSnackbar(backedUp)
            }
        }
        val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) operation {
                try {
                    pendingBackup = withContext(Dispatchers.IO) {
                        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { stream ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = stream.read(buffer); if (n == -1) break
                                DataLimit.BYTES.check(output.size() + n); output.write(buffer, 0, n)
                            }
                            output.toByteArray()
                        }
                        Backup.decode(bytes.toString(Charsets.UTF_8))
                    }
                } catch (error: Exception) { snack.showSnackbar(if (error is DataCapacityException) { if (portuguese) error.limit.pt else error.limit.en } else invalidBackup) }
            }
        }
        fun navigate(target: String) {
            if (busy) return
            focus.clearFocus(); keyboard?.hide()
            if (screen == "form") { destination = target; discard = true } else screen = target
        }
        fun start(entry: Entry? = null, edit: Boolean = false) {
            focus.clearFocus(); keyboard?.hide()
            entryId = entry?.id; editing = edit; formKey++; screen = "form"
        }
        BackHandler(screen != "home") { navigate("home") }
        Scaffold(
            topBar = { TopAppBar(title = { Text("PepLog", fontWeight = FontWeight.Bold, letterSpacing = (-1).sp) },
                navigationIcon = { if (screen !in listOf("home", "history", "catalog")) IconButton(onClick = { navigate("home") }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, t("Back", "Voltar"))
                } }, actions = { IconButton(onClick = { navigate("settings") }, enabled = !busy) { Icon(Icons.Outlined.Settings, t("Settings", "Configurações")) } }) },
            bottomBar = { NavigationBar(containerColor = Color.White) {
                listOf(Triple("home", t("Home", "Início"), Icons.Outlined.Home), Triple("today", t("Today", "Hoje"), Icons.Outlined.Today), Triple("history", t("History", "Histórico"), Icons.Outlined.History),
                    Triple("protocols", t("Protocols", "Protocolos"), Icons.Outlined.CalendarMonth), Triple("catalog", t("Peptides", "Peptídeos"), Icons.Outlined.Science)).forEach { (route, title, icon) ->
                    NavigationBarItem(selected = screen == route, onClick = { navigate(route) }, enabled = !busy,
                        icon = { Icon(icon, null) }, label = { Text(title) })
                }
            } }, snackbarHost = { SnackbarHost(snack) }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!ready) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (failed) Text(t("Could not open your journal. Close and reopen the app to retry.", "Não foi possível abrir o caderno. Feche e reabra o app para tentar novamente."), Modifier.padding(24.dp))
                        else CircularProgressIndicator()
                    }
                } else when (screen) {
                    "home" -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        item { Heading(t("Your peptide\njournal.", "Seu caderno de\npeptídeos."), t("Calculate, save and find it again.", "Calcule, salve e consulte depois.")) }
                        item { Button(onClick = { start() }, Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text(t("New calculation", "Novo cálculo"))
                        } }
                        item { Text(t("Recent records", "Registros recentes"), style = MaterialTheme.typography.titleLarge) }
                        if (entries.isEmpty()) item { EmptyJournal() }
                        items(entries.take(5), key = { it.id }) { entry -> EntryRow(entry) { entryId = entry.id; screen = "detail" } }
                        item { Text(t("Stored on this device. You control your backup.", "Salvo neste aparelho. Você controla seu backup."), style = MaterialTheme.typography.bodySmall) }
                    }
                    "history" -> historyState.SaveableStateProvider("history") { History(entries) { entryId = it.id; screen = "detail" } }
                    "today" -> key(reminderOpened) { TodayScreen(protocols, busy) { id, log, done -> operation {
                        db.logProtocol(id, log); focus.clearFocus(); keyboard?.hide(); done()
                    } } }
                    "form" -> key(formKey) { EntryForm(peptides, entries, selected, editing, busy) { entry -> operation {
                        db.dao().put(entry); focus.clearFocus(); keyboard?.hide(); entryId = entry.id; screen = "detail"; snack.showSnackbar(saved)
                    } } }
                    "detail" -> if (selected != null) Detail(selected, { start(selected, true) }, { start(selected) }, { deleteId = selected.id })
                    else Text(t("Record not found", "Registro não encontrado"), Modifier.padding(24.dp))
                    "protocols" -> ProtocolScreen(protocols, peptides, entries, busy,
                        save = { p, done -> operation { db.saveProtocol(p); focus.clearFocus(); keyboard?.hide(); done() } },
                        record = { id, log, done -> operation { db.logProtocol(id, log); focus.clearFocus(); keyboard?.hide(); done() } },
                        delete = { id, done -> operation { db.deleteEmptyProtocol(id); done() } },
                        archive = { id, archived, done -> operation { db.setProtocolArchived(id, archived); done() } })
                    "catalog" -> Catalog(peptides, busy) { peptide, done -> operation { db.dao().put(peptide); done() } }
                    "settings" -> Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Heading(t("Make it yours.", "Do seu jeito."), t("Language and backup", "Idioma e backup"))
                        Text(t("Language", "Idioma"), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilterChip(selected = !portuguese, onClick = { portuguese = false; prefs.edit().putBoolean("pt", false).apply() }, label = { Text("English") })
                            FilterChip(selected = portuguese, onClick = { portuguese = true; prefs.edit().putBoolean("pt", true).apply() }, label = { Text("Português (BR)") })
                        }
                        HorizontalDivider()
                        ReminderSettings()
                        HorizontalDivider()
                        Text(t("Keep a copy", "Guarde uma cópia"), style = MaterialTheme.typography.titleLarge)
                        Text(t("Uninstalling the app or losing your phone can erase your journal. Export a backup to a place you trust. The file contains your notes and is not encrypted.",
                            "Desinstalar o app ou perder o celular pode apagar seu caderno. Exporte um backup para um local de confiança. O arquivo contém suas anotações e não é criptografado."))
                        Button(onClick = { export.launch("peptides-backup-${LocalDate.now()}.json") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(t("Export backup", "Exportar backup")) }
                        OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(t("Restore backup", "Restaurar backup")) }
                        HorizontalDivider()
                        Text(t("Calculations use only the values you enter. No dose or treatment recommendations. Catalog inclusion does not indicate suitability for use.",
                            "Os cálculos usam apenas os valores informados. Não há recomendação de dose ou tratamento. A presença no catálogo não indica adequação para uso."), style = MaterialTheme.typography.bodySmall)
                        Text("PepLog " + context.packageManager.getPackageInfo(context.packageName, 0).versionName, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (discard) Confirm(t("Leave this calculation?", "Sair deste cálculo?"), t("Unsaved changes will be discarded.", "As alterações não salvas serão descartadas."),
            t("Discard", "Descartar"), { discard = false }) { discard = false; screen = destination }
        deleteId?.let { id -> Confirm(t("Delete record?", "Excluir registro?"), t("This cannot be undone.", "Esta ação não pode ser desfeita."), t("Delete", "Excluir"), { deleteId = null }) {
            deleteId = null; operation { db.dao().delete(id); screen = "history" }
        } }
        pendingBackup?.let { data -> Confirm(t("Replace your journal?", "Substituir seu caderno?"),
            t("This backup contains ${data.peptides.size} peptides, ${data.entries.size} records and ${data.protocols.size} protocols. It will replace all current data. Export a copy first if needed.",
                "Este backup contém ${data.peptides.size} peptídeos, ${data.entries.size} registros e ${data.protocols.size} protocolos. Ele substituirá todos os dados atuais. Exporte uma cópia antes, se necessário."),
            t("Replace and restore", "Substituir e restaurar"), { pendingBackup = null }) {
            pendingBackup = null; operation { Backup.restore(db, data); entryId = null; screen = "history"; snack.showSnackbar(restored) }
        } }
    }
}

@Composable internal fun Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.8).sp)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge)
    }
}
@Composable private fun EmptyJournal() {
    Column(Modifier.fillMaxWidth().background(Water, RoundedCornerShape(20.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.BookmarkBorder, null, tint = Teal)
        Text(t("A clear place for your records.", "Um lugar para seus registros."), style = MaterialTheme.typography.titleMedium)
        Text(t("Save your first calculation to start your journal.", "Salve seu primeiro cálculo para começar seu caderno."))
    }
}
@Composable private fun date(time: Long): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withLocale(if (LocalLanguage.current) Locale.forLanguageTag("pt-BR") else Locale.US).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))
@Composable private fun EntryRow(entry: Entry, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(entry.title.ifBlank { entry.peptideName }, style = MaterialTheme.typography.titleMedium)
        if (entry.title.isNotBlank()) Text(entry.peptideName)
        Text(entryAmount(entry) + "  /  ${Calculator.display(entry.volume.toBigDecimal(), LocalLanguage.current)} mL", color = Teal)
        Text(date(entry.created), style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider(color = Ink.copy(alpha = 0.12f))
}

@Composable private fun History(entries: List<Entry>, open: (Entry) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("") }; var to by rememberSaveable { mutableStateOf("") }
    val fromDate = runCatching { LocalDate.parse(from) }.getOrNull()
    val toDate = runCatching { LocalDate.parse(to) }.getOrNull()
    val invalid = (from.isNotBlank() && fromDate == null) || (to.isNotBlank() && toDate == null) || (fromDate != null && toDate != null && fromDate > toDate)
    val visible = entries.filter {
        val day = Instant.ofEpochMilli(it.created).atZone(ZoneId.systemDefault()).toLocalDate()
        (it.peptideName.contains(query, true) || it.title.contains(query, true)) && (fromDate == null || day >= fromDate) && (toDate == null || day <= toDate)
    }
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Heading(t("Your records.", "Seus registros."), t("Everything you saved, in one place.", "Tudo que você salvou, em um lugar.")) }
        item { Field(query, { query = it }, t("Search records", "Buscar registros")) }
        item { DatePickerField(from, { from = it }, t("From", "De"), allowBlank = true,
            blankLabel = t("Any start date", "Qualquer data inicial"), maxDate = toDate, yearRange = 1900..2100) }
        item { DatePickerField(to, { to = it }, t("To", "Até"), allowBlank = true,
            blankLabel = t("Any end date", "Qualquer data final"), minDate = fromDate, yearRange = 1900..2100) }
        if (from.isNotBlank() || to.isNotBlank()) item {
            TextButton(onClick = { from = ""; to = "" }) { Text(t("Clear period", "Limpar período")) }
        }
        if (invalid) item { Text(t("Enter valid dates with the start before the end.", "Informe datas válidas, com o início antes do fim."), color = MaterialTheme.colorScheme.error) }
        else {
            item { Text("${visible.size} " + if (visible.size == 1) t("record", "registro") else t("records", "registros"), style = MaterialTheme.typography.labelLarge) }
            if (visible.isEmpty()) item { Text(t("No records found.", "Nenhum registro encontrado.")) }
            items(visible, key = { it.id }) { entry -> EntryRow(entry) { open(entry) } }
        }
    }
}

@Composable internal fun Field(value: String, change: (String) -> Unit, label: String, numeric: Boolean = false, multiline: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
        singleLine = !multiline, minLines = if (multiline) 3 else 1, shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text))
}

@Composable internal fun EntryForm(peptides: List<Peptide>, entries: List<Entry>, original: Entry?, editing: Boolean, busy: Boolean, saveLabel: String? = null, save: (Entry) -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var peptideId by rememberSaveable { mutableStateOf(original?.peptideId ?: "") }
    // A retry after activity recreation must not create a second record.
    val draftId = rememberSaveable { if (editing) original?.id ?: UUID.randomUUID().toString() else UUID.randomUUID().toString() }
    var peptideName by rememberSaveable { mutableStateOf(original?.peptideName ?: "") }
    var picker by rememberSaveable { mutableStateOf(false) }
    var doseUnit by rememberSaveable { mutableStateOf(original?.doseUnit ?: "mg") }
    var massUnit by rememberSaveable { mutableStateOf(original?.massUnit ?: "mg") }
    var dose by rememberSaveable { mutableStateOf(original?.let { Mass.input(it.dose, it.doseUnit) } ?: "") }
    var mass by rememberSaveable { mutableStateOf(original?.let { Mass.input(it.mass, it.massUnit) } ?: "") }
    var composition by rememberSaveable { mutableStateOf(original?.composition ?: "[]") }
    var reference by rememberSaveable { mutableIntStateOf(original?.reference ?: -1) }
    var editComposition by rememberSaveable { mutableStateOf(false) }
    var water by rememberSaveable { mutableStateOf(original?.water ?: "") }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", 0) }
    var syringePicker by rememberSaveable { mutableStateOf(false) }
    var vialPicker by rememberSaveable { mutableStateOf(false) }
    var cap by rememberSaveable { mutableStateOf(original?.capacity ?: prefs.getString("syringe.capacity", "")!!) }
    var scale by rememberSaveable { mutableStateOf(original?.scale ?: prefs.getString("syringe.scale", "")!!) }
    var tick by rememberSaveable { mutableStateOf(original?.tick ?: prefs.getString("syringe.tick", "")!!) }
    var title by rememberSaveable { mutableStateOf(original?.title ?: "") }
    var notes by rememberSaveable { mutableStateOf(original?.notes ?: "") }
    val parts = runCatching { Blend.decode(composition) }.getOrNull()
    val isBlend = composition != "[]"
    val doseMg = runCatching { Mass.toMg(dose, doseUnit).toPlainString() }.getOrNull()
    val massMg = runCatching {
        if (isBlend) Blend.basis(requireNotNull(parts), reference).toPlainString() else Mass.toMg(mass, massUnit).toPlainString()
    }.getOrNull()
    val inputs = listOf(doseMg ?: dose, massMg ?: mass, water, cap, scale, tick)
    val attempt = if (dose.isNotBlank() && water.isNotBlank() && cap.isNotBlank() && scale.isNotBlank() && tick.isNotBlank()) runCatching {
        require(parts != null)
        Calculator.calculate(requireNotNull(doseMg), requireNotNull(massMg), water, cap, scale, tick)
    } else null
    val calc = attempt?.getOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Heading(if (editing) t("Edit record.", "Editar registro.") else t("A new calculation.", "Um novo cálculo."), t("Your inputs. A record you can return to.", "Seus dados. Um registro para consultar depois."))
        OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(12.dp)) {
            Text(peptideName.ifBlank { t("Select peptide", "Selecionar peptídeo") }); Spacer(Modifier.weight(1f)); Icon(Icons.Outlined.ExpandMore, null)
        }
        if (!editing && entries.isNotEmpty()) TextButton(onClick = { focus.clearFocus(); keyboard?.hide(); vialPicker = true }) {
            Icon(Icons.Outlined.History, null); Spacer(Modifier.width(8.dp))
            Text(t("Use a saved vial", "Usar frasco de um registro"))
        }
        if (isBlend) {
            if (editComposition) {
                BlendEditor(composition) { composition = it; reference = -1; dose = "" }
                TextButton(enabled = parts != null, onClick = { editComposition = false }) { Text(t("Use this composition", "Usar esta composição")) }
            } else {
                Text(t("Composition per vial", "Composição por frasco"), style = MaterialTheme.typography.titleMedium)
                parts?.forEach { Text(it.name + ": " + Mass.display(it.mg(), it.unit, LocalLanguage.current)) }
                OutlinedButton(onClick = { editComposition = true }) { Text(t("Edit vial composition", "Editar composição do frasco")) }
            }
            if (parts != null) {
                Text(t("Calculate desired amount for", "Calcular quantidade desejada de"), style = MaterialTheme.typography.titleMedium)
                (listOf(-1 to t("Total blend", "Blend total")) + parts.mapIndexed { n, part -> n to part.name }).forEach { (n, label) ->
                    FilterChip(selected = reference == n, onClick = { reference = n; dose = "" }, label = { Text(label) })
                }
            }
            Text(t("All compounds share the same volume. Their proportions follow the vial composition.",
                "Todos os compostos compartilham o mesmo volume. As proporções seguem a composição do frasco."))
        } else AmountField(mass, massUnit, t("Peptide in vial", "Peptídeo no frasco")) { value, unit -> mass = value; massUnit = unit }
        Field(water, { water = it.take(19) }, t("Final solution volume (mL)", "Volume final da solução (mL)"), true)
        Text(t("Use the final solution volume, which may differ from the diluent added.", "Use o volume final da solução, que pode diferir do diluente adicionado."), style = MaterialTheme.typography.bodySmall)
        if (isBlend && parts != null) Text(t("Amount refers to: ", "Quantidade referente a: ") + doseBasis(composition, reference))
        AmountField(dose, doseUnit, t("Desired amount", "Quantidade desejada")) { value, unit -> dose = value; doseUnit = unit }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t("My syringe", "Minha seringa"), style = MaterialTheme.typography.titleMedium)
                    if (cap.isNotBlank() && scale.isNotBlank() && tick.isNotBlank())
                        Text(syringeLabel(cap, scale, tick), style = MaterialTheme.typography.bodyMedium)
                    else Text(t("Choose once to calculate", "Escolha uma vez para calcular"))
                }
                TextButton(onClick = { focus.clearFocus(); keyboard?.hide(); syringePicker = true }) {
                    Text(if (cap.isBlank()) t("Choose", "Escolher") else t("Change", "Trocar"))
                }
            }
        }
        if (attempt?.isFailure == true) {
            val problem = (attempt.exceptionOrNull() as? InputProblem)?.problem
            Text(when (problem) {
                Problem.DOSE_EXCEEDS_VIAL -> t("The entered dose exceeds the contents of the vial.", "A dose informada excede o conteúdo do frasco.")
                Problem.CAPACITY -> t("The calculated volume exceeds syringe capacity.", "O volume calculado excede a capacidade da seringa.")
                Problem.SCALE -> t("Check the scale: capacity up to 10 mL, up to 1000 units/mL, and divisions that fit exactly within capacity.", "Confira a escala: capacidade até 10 mL, até 1000 unidades/mL e divisões que caibam exatamente na capacidade.")
                else -> t("Check the positive amounts, selected units and vial composition. Use a decimal point or comma, without thousands separators.", "Confira as quantidades positivas, unidades selecionadas e composição do frasco. Use ponto ou vírgula decimal, sem separadores de milhar.")
            }, color = MaterialTheme.colorScheme.error)
        }
        if (calc != null) {
            ResultBlock(calc, cap, scale, tick, doseUnit, doseBasis(composition, reference))
            BlendAmounts(composition, reference, doseMg!!, t("Each compound in this application", "Cada composto nesta aplicação"))
        }
        Field(title, { title = it.take(200) }, t("Record name (optional)", "Nome do registro (opcional)"))
        Field(notes, { notes = it.take(4000) }, t("Notes (optional)", "Observações (opcional)"), multiline = true)
        Button(enabled = calc != null && peptideId.isNotBlank() && !busy && (!editing || original != null), onClick = {
            val c = calc ?: return@Button
            val normalized = inputs.mapIndexed { n, value -> (if (n < 2) Mass.mg(value) else Calculator.number(value)).toPlainString() }
            save(Entry(id = if (editing) original!!.id else draftId, peptideId = peptideId, peptideName = peptideName,
                title = title.trim(), notes = notes.trim(), dose = normalized[0], mass = normalized[1], water = normalized[2], capacity = normalized[3], scale = normalized[4], tick = normalized[5],
                created = if (editing) original!!.created else System.currentTimeMillis(), concentration = c.concentration.toPlainString(), volume = c.volume.toPlainString(),
                units = c.units.toPlainString(), doses = c.doses.toPlainString(), remainder = c.remainder.toPlainString(),
                doseUnit = doseUnit, massUnit = massUnit, composition = composition, reference = reference))
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(saveLabel ?: if (editing) t("Save changes", "Salvar alterações") else t("Save record", "Salvar registro")) }
        Text(t("Arithmetic from your inputs. No dose recommendation.", "Cálculo a partir dos seus dados. Sem recomendação de dose."), style = MaterialTheme.typography.bodySmall)
    }
    if (syringePicker) SyringePicker(cap, scale, tick, { syringePicker = false }) { capacity, units, division ->
        cap = capacity; scale = units; tick = division
        prefs.edit().putString("syringe.capacity", cap).putString("syringe.scale", scale).putString("syringe.tick", tick).apply()
        syringePicker = false
    }
    if (vialPicker) {
        var query by rememberSaveable { mutableStateOf("") }
        AlertDialog(onDismissRequest = { vialPicker = false }, title = { Text(t("Use a saved vial", "Usar frasco de um registro")) },
            text = { Column {
                Text(t("Copies the peptide and solution. Enter a new desired amount.", "Copia o peptídeo e a solução. Informe uma nova quantidade desejada."))
                Field(query, { query = it }, t("Search records", "Buscar registros"))
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(entries.filter { it.peptideName.contains(query, true) || it.title.contains(query, true) }, key = { it.id }) { e ->
                        ListItem(headlineContent = { Text(e.title.ifBlank { e.peptideName }) },
                            supportingContent = { Text("${e.peptideName} · " + entryAmount(e) + " / ${e.volume} mL") },
                            modifier = Modifier.clickable {
                                peptideId = e.peptideId; peptideName = e.peptideName; mass = Mass.input(e.mass, e.massUnit); water = e.water
                                massUnit = e.massUnit; doseUnit = e.doseUnit; composition = e.composition; reference = e.reference; editComposition = false
                                dose = ""; vialPicker = false
                            })
                    }
                }
            } }, confirmButton = { TextButton(onClick = { vialPicker = false }) { Text(t("Close", "Fechar")) } })
    }
    if (picker) PeptidePicker(peptides.filter { it.active }, { picker = false }) {
        peptideId = it.id; peptideName = it.name; composition = CatalogDefaults.composition(it); reference = -1; dose = ""
        editComposition = runCatching { Blend.decode(composition) }.isFailure; picker = false
    }
}

@Composable internal fun syringeLabel(cap: String, scale: String, tick: String): String {
    val pt = LocalLanguage.current
    fun f(raw: String) = Calculator.display(Calculator.number(raw), pt)
    val total = Calculator.number(cap).multiply(Calculator.number(scale))
    val unit = if (Calculator.number(tick).compareTo(BigDecimal.ONE) == 0) t("unit", "unidade") else t("units", "unidades")
    val scaleName = if (Calculator.number(scale).compareTo(BigDecimal(100)) == 0) "U-100" else f(scale) + t(" units/mL", " unidades/mL")
    return Calculator.display(total, pt) + t(" units", " unidades") + " · ${f(cap)} mL\n$scaleName · " +
        t("Each tick: ", "Cada traço: ") + f(tick) + " " + unit
}

@Composable private fun SyringePicker(cap: String, scale: String, tick: String, dismiss: () -> Unit, select: (String, String, String) -> Unit) {
    var custom by rememberSaveable { mutableStateOf(false) }
    var capacity by rememberSaveable { mutableStateOf(cap) }
    var units by rememberSaveable { mutableStateOf(scale) }
    var division by rememberSaveable { mutableStateOf(tick) }
    val valid = runCatching { Calculator.calculate("0.000000001", "1", "0.000000001", capacity, units, division) }.isSuccess
    AlertDialog(onDismissRequest = dismiss, title = { Text(t("Choose your syringe", "Escolha sua seringa")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("Match the capacity and smallest marking on your syringe. Your choice is saved for new calculations.", "Confira a capacidade e a menor marcação da sua seringa. Sua escolha fica salva para novos cálculos."))
            if (!custom) {
                listOf("0.3" to "0.5", "0.3" to "1", "0.5" to "1", "1" to "1", "1" to "2").forEach { (c, d) ->
                    OutlinedButton(onClick = { select(c, "100", d) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Outlined.Straighten, null); Spacer(Modifier.width(12.dp))
                        Text(syringeLabel(c, "100", d), Modifier.weight(1f))
                    }
                }
                TextButton(onClick = { custom = true }) { Text(t("Custom syringe", "Seringa personalizada")) }
            } else {
                Field(capacity, { capacity = it.take(19) }, t("Syringe capacity (mL)", "Capacidade da seringa (mL)"), true)
                Field(units, { units = it.take(19) }, t("Scale units per mL", "Unidades da escala por mL"), true)
                Field(division, { division = it.take(19) }, t("Smallest division (scale units)", "Menor divisão (unidades da escala)"), true)
                Text(t("Positive values: capacity up to 10 mL, scale up to 1000 units/mL. Divisions must fit exactly within capacity.", "Valores positivos: capacidade até 10 mL, escala até 1000 unidades/mL. As divisões devem caber exatamente na capacidade."), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { custom = false }) { Text(t("Show models", "Ver modelos")) }
            }
        }
    }, confirmButton = { if (custom) TextButton(enabled = valid, onClick = {
        select(Calculator.number(capacity).toPlainString(), Calculator.number(units).toPlainString(), Calculator.number(division).toPlainString())
    }) { Text(t("Use this syringe", "Usar esta seringa")) } }, dismissButton = { TextButton(onClick = dismiss) { Text(t("Cancel", "Cancelar")) } })
}

@Composable private fun SyringeDrawing(c: Calculation, cap: String, scale: String, tick: String) {
    val pt = LocalLanguage.current
    val capacity = Calculator.number(cap)
    val total = capacity.multiply(Calculator.number(scale))
    val division = Calculator.number(tick)
    val fraction = c.volume.divide(capacity, java.math.MathContext.DECIMAL64).toFloat().coerceIn(0f, 1f)
    val marks = total.divideToIntegralValue(division)
    val shown = marks.min(BigDecimal.TEN).toInt()
    val first = c.units.divideToIntegralValue(division).subtract(BigDecimal(shown / 2)).max(BigDecimal.ZERO).min(marks.subtract(BigDecimal(shown)))
    val start = first.multiply(division)
    val end = start.add(division.multiply(BigDecimal(shown)))
    val position = c.units.subtract(start).divide(end.subtract(start), java.math.MathContext.DECIMAL64).toFloat().coerceIn(0f, 1f)
    val description = t("Syringe volume preview", "Prévia do volume na seringa")
    Canvas(Modifier.fillMaxWidth().height(58.dp).semantics { contentDescription = description }) {
        val left = 14.dp.toPx(); val width = size.width - 48.dp.toPx(); val top = 14.dp.toPx(); val height = 28.dp.toPx()
        drawRoundRect(Color.White, Offset(left, top), Size(width, height), androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
        drawRect(Teal.copy(alpha = .4f), Offset(left, top), Size(width * fraction, height))
        drawLine(Ink, Offset(left, top - 6.dp.toPx()), Offset(left, top + height + 6.dp.toPx()), 3.dp.toPx())
        drawLine(Ink, Offset(left + width, top + height / 2), Offset(size.width - 4.dp.toPx(), top + height / 2), 2.dp.toPx())
        drawLine(Teal, Offset(left + width * fraction, top), Offset(left + width * fraction, top + height), 3.dp.toPx())
    }
    Text(t("Markings near your result", "Marcações próximas do resultado"), style = MaterialTheme.typography.labelLarge)
    val zoomDescription = "${Calculator.display(start, pt)} – ${Calculator.display(end, pt)}; " +
        t("step ", "intervalo ") + Calculator.display(division, pt)
    Canvas(Modifier.fillMaxWidth().height(54.dp).semantics { contentDescription = zoomDescription }) {
        val left = 8.dp.toPx(); val width = size.width - 2 * left; val baseline = 44.dp.toPx()
        drawLine(Ink, Offset(left, baseline), Offset(left + width, baseline), 1.dp.toPx())
        for (i in 0..shown) {
            val x = left + width * i / shown
            drawLine(Ink, Offset(x, baseline), Offset(x, baseline - 18.dp.toPx()), 1.5.dp.toPx())
        }
        val x = left + width * position
        drawLine(Teal, Offset(x, 5.dp.toPx()), Offset(x, baseline), 3.dp.toPx())
        drawCircle(Teal, 4.dp.toPx(), Offset(x, 5.dp.toPx()))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(Calculator.display(start, pt)); Text(t("scale units", "unidades da escala")); Text(Calculator.display(end, pt))
    }
    Text(t("Each tick = ", "Cada traço = ") + Calculator.display(division, pt) + t(" scale units. Enlarged preview; check your syringe.", " unidades da escala. Prévia ampliada; confira sua seringa."), style = MaterialTheme.typography.bodySmall)
}

@Composable private fun ResultBlock(c: Calculation, cap: String, scale: String, tick: String, unit: String = "mg", basis: String = "") {
    val pt = LocalLanguage.current
    fun f(v: BigDecimal) = Calculator.display(v, pt)
    Column(Modifier.fillMaxWidth().background(Water, RoundedCornerShape(24.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t("Volume to draw", "Volume a aspirar"), style = MaterialTheme.typography.titleMedium)
        Text("${f(c.volume)} mL", fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold, color = Teal)
        Text("${f(c.units)} " + t("scale units", "unidades da escala"), style = MaterialTheme.typography.titleLarge)
        SyringeDrawing(c, cap, scale, tick)
        HorizontalDivider(color = Teal.copy(alpha = 0.2f))
        if (basis.isNotBlank()) Text(t("Reference: ", "Referência: ") + basis)
        Text(t("Concentration", "Concentração") + ": " + Mass.display(c.concentration, unit, pt) + "/mL")
        Text(t("Theoretical complete doses", "Doses completas teóricas") + ": ${f(c.doses)}")
        Text(t("Theoretical remainder", "Sobra teórica") + ": " + Mass.display(c.remainder, unit, pt))
        Text(t("Scale", "Escala") + ": ${f(Calculator.number(scale))} " + t("units/mL", "unidades/mL"))
        Text(t("Yield excludes losses. ≈ indicates a rounded display value.", "O rendimento não considera perdas. ≈ indica arredondamento na exibição."), style = MaterialTheme.typography.bodySmall)
        if (!c.aligned) Text(t("The result falls between syringe divisions. It has not been rounded to a marking.", "O resultado fica entre divisões da seringa. Ele não foi arredondado para uma marcação."), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
    }
}

@Composable private fun Detail(e: Entry, edit: () -> Unit, duplicate: () -> Unit, delete: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Heading(e.title.ifBlank { e.peptideName }, date(e.created))
        if (e.title.isNotBlank()) Text(e.peptideName, style = MaterialTheme.typography.titleLarge)
        ResultBlock(e.calculation(), e.capacity, e.scale, e.tick, e.doseUnit, doseBasis(e.composition, e.reference))
        BlendAmounts(e.composition, e.reference, e.dose, t("Each compound in this application", "Cada composto nesta aplicação"))
        val components = Blend.decode(e.composition)
        if (components.isNotEmpty()) {
            Text(t("Composition per vial", "Composição por frasco"), style = MaterialTheme.typography.titleMedium)
            components.forEach { Text(it.name + ": " + Mass.display(it.mg(), it.unit, LocalLanguage.current)) }
        }
        val pt = LocalLanguage.current
        listOf(t("Entered dose", "Dose informada") to entryAmount(e),
            (if (components.isEmpty()) t("Peptide in vial", "Peptídeo no frasco") else t("Reference amount in vial", "Quantidade de referência no frasco")) to Mass.display(e.mass.toBigDecimal(), e.massUnit, pt),
            t("Final volume", "Volume final") to "${Calculator.display(e.water.toBigDecimal(), pt)} mL",
            t("Smallest division", "Menor divisão") to "${Calculator.display(e.tick.toBigDecimal(), pt)} ${t("scale units", "unidades da escala")}")
            .forEach { (label, value) -> Text("$label: $value") }
        if (e.notes.isNotBlank()) { Text(t("Notes", "Observações"), style = MaterialTheme.typography.titleMedium); Text(e.notes) }
        if (e.updated > e.created + 1000) Text(t("Updated: ", "Atualizado: ") + date(e.updated), style = MaterialTheme.typography.bodySmall)
        Button(onClick = duplicate, modifier = Modifier.fillMaxWidth()) { Text(t("Use as a starting point", "Usar como base")) }
        OutlinedButton(onClick = edit, modifier = Modifier.fillMaxWidth()) { Text(t("Edit record", "Editar registro")) }
        TextButton(onClick = delete, modifier = Modifier.fillMaxWidth()) { Text(t("Delete record", "Excluir registro"), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable private fun PeptidePicker(peptides: List<Peptide>, dismiss: () -> Unit, select: (Peptide) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text(t("Select peptide", "Selecionar peptídeo")) }, text = {
        Column { Field(query, { query = it }, t("Search name or alias", "Buscar nome ou sinônimo"))
            LazyColumn(Modifier.heightIn(max = 350.dp)) {
                val found = peptides.filter { CatalogDefaults.matches(it, query) }
                if (found.isEmpty()) item { Text(t("No match. Add a peptide in the catalog.", "Nenhum resultado. Adicione um peptídeo no catálogo."), Modifier.padding(vertical = 16.dp)) }
                items(found, key = { it.id }) { p -> ListItem(headlineContent = { Text(p.name) }, supportingContent = { if (p.aliases.isNotBlank()) Text(p.aliases) }, modifier = Modifier.clickable { select(p) }) }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text(t("Close", "Fechar")) } })
}

@Composable internal fun Catalog(peptides: List<Peptide>, busy: Boolean, save: (Peptide, () -> Unit) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var showInactive by rememberSaveable { mutableStateOf(false) }
    var editor by rememberSaveable { mutableStateOf(false) }
    var id by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var aliases by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(true) }
    var source by rememberSaveable { mutableStateOf("") }
    var composition by rememberSaveable { mutableStateOf("[]") }
    var discard by rememberSaveable { mutableStateOf(false) }
    val original = peptides.find { it.id == id }
    val changed = name != (original?.name ?: "") || aliases != (original?.aliases ?: "") ||
        active != (original?.active ?: true) || composition != (original?.let { CatalogDefaults.composition(it) } ?: "[]")
    fun dismissEditor() { if (!busy) { if (changed) discard = true else editor = false } }
    val duplicateName = peptides.any { it.id != id && it.name.trim().equals(name.trim(), true) }
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Heading(t("Your peptide index.", "Seu índice de peptídeos."), t("Names for your records. Always editable.", "Nomes para seus registros. Sempre editáveis.")) }
        item { Button(onClick = { id = null; name = ""; aliases = ""; active = true; source = ""; composition = "[]"; editor = true }, enabled = !busy) { Icon(Icons.Outlined.Add, null); Text(t("Add peptide", "Adicionar peptídeo")) } }
        item { Text(t("You can also register a blend and each compound per vial.", "Você também pode cadastrar um blend e cada composto por frasco.")) }
        item { Field(query, { query = it }, t("Search peptides", "Buscar peptídeos")) }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(showInactive, { showInactive = it }); Text(t("Show inactive", "Mostrar inativos")) } }
        val found = peptides.filter { (showInactive || it.active) && CatalogDefaults.matches(it, query) }
        if (found.isEmpty()) item { Text(t("No peptides found.", "Nenhum peptídeo encontrado.")) }
        items(found, key = { it.id }) { p ->
            ListItem(headlineContent = { Text(p.name) }, supportingContent = { Text(if (!p.active) t("Inactive", "Inativo") else p.aliases) },
                trailingContent = { Icon(Icons.Outlined.Edit, t("Edit", "Editar")) }, modifier = Modifier.clickable(enabled = !busy) {
                    id = p.id; name = p.name; aliases = p.aliases; active = p.active; source = p.source; composition = CatalogDefaults.composition(p); editor = true
                })
            HorizontalDivider()
        }
    }
    if (editor) AlertDialog(onDismissRequest = ::dismissEditor, title = { Text(if (id == null) t("Add peptide", "Adicionar peptídeo") else t("Edit peptide", "Editar peptídeo")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(name, { name = it.take(200) }, t("Name", "Nome"))
            Field(aliases, { aliases = it.take(200) }, t("Aliases (optional)", "Sinônimos (opcional)"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(composition != "[]", { composition = if (it) Blend.encode(listOf(Compound(), Compound())) else "[]" },
                    modifier = Modifier.semantics { contentDescription = "Blend" })
                Spacer(Modifier.width(12.dp))
                Text(t("Blend (multiple compounds)", "Blend (vários compostos)"))
            }
            if (composition != "[]") BlendEditor(composition) { composition = it }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(active, { active = it }); Spacer(Modifier.width(12.dp)); Text(t("Active", "Ativo")) }
            if (source.isNotBlank()) Text(t("Identification source: ", "Fonte de identificação: ") + source, style = MaterialTheme.typography.bodySmall)
            if (duplicateName) Text(t("This name already exists.", "Este nome já existe."), color = MaterialTheme.colorScheme.error)
            Text(t("Existing records keep their original names.", "Registros existentes mantêm os nomes originais."), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank() && !duplicateName && !busy && runCatching { Blend.decode(composition) }.isSuccess, onClick = {
        save(Peptide(id ?: UUID.randomUUID().toString(), name.trim(), aliases.trim(), source, active, composition)) { editor = false }
    }) { Text(t("Save", "Salvar")) } }, dismissButton = { TextButton(enabled = !busy, onClick = ::dismissEditor) { Text(t("Cancel", "Cancelar")) } })
    if (discard) Confirm(t("Discard unsaved changes?", "Descartar alterações não salvas?"),
        t("The changes to this peptide will be lost.", "As alterações deste peptídeo serão perdidas."),
        t("Discard", "Descartar"), { discard = false }) { discard = false; editor = false }
}

@Composable internal fun Confirm(title: String, message: String, action: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = confirm) { Text(action) } }, dismissButton = { TextButton(onClick = dismiss) { Text(t("Cancel", "Cancelar")) } })
}
