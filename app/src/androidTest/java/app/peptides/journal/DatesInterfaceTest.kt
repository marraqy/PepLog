package app.peptides.journal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.After
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DatesInterfaceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var originalLanguage = false

    @After fun restoreTestState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("settings", 0).edit().putBoolean("pt", originalLanguage).commit()
        runBlocking { JournalDb.get(context).dao().clearEntries() }
    }

    private fun show(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    @Test fun historyDatesUseCalendarAndCanBeCleared() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        originalLanguage = context.getSharedPreferences("settings", 0).getBoolean("pt", false)
        val today = LocalDate.now()
        val c = Calculator.calculate("1", "2", "1", "1", "100", "1")
        val entry = Entry(peptideId = "bpc-157", peptideName = "BPC-157", dose = "1", mass = "2", water = "1",
            capacity = "1", scale = "100", tick = "1", concentration = c.concentration.toPlainString(),
            volume = c.volume.toPlainString(), units = c.units.toPlainString(), doses = c.doses.toPlainString(),
            remainder = c.remainder.toPlainString())
        runBlocking {
            val db = JournalDb.get(context)
            db.seed(context)
            db.dao().clearEntries()
            db.dao().put(entry.copy(id = "date-today", title = "Today sample", created = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()))
            db.dao().put(entry.copy(id = "date-yesterday", title = "Yesterday sample", created = today.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()))
        }
        compose.waitUntil(15000) { compose.onAllNodes(hasContentDescription("Settings") or hasContentDescription("Configurações")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("Settings") or hasContentDescription("Configurações")).performClick()
        compose.onNodeWithText("Português (BR)").performClick()
        compose.onNodeWithText("Histórico").performClick()
        show("2 registros")
        show("Qualquer data inicial")
        compose.onNodeWithText("Qualquer data inicial").performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Qualquer data inicial").assertExists()
        compose.onNodeWithText("Qualquer data inicial").performClick()
        compose.onNodeWithText("Escolher").performClick()
        compose.onNodeWithText(today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).assertExists()
        show("1 registro")
        show("Today sample")
        compose.onNodeWithText("Today sample").performClick()
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithText("Histórico").performClick()
        show("1 registro")
        compose.onNodeWithText("Today sample").assertExists()
        show("Qualquer data final")
        compose.onNodeWithText("Qualquer data final").performClick()
        compose.onNodeWithText("Escolher").performClick()
        show("1 registro")
        show("Limpar período")
        compose.onNodeWithText("Limpar período").performClick()
        show("2 registros")
        show("Buscar registros")
        compose.onNodeWithText("Buscar registros").performTextInput("Yesterday sample")
        show("1 registro")
        compose.onNode(hasText("Yesterday sample") and !hasSetTextAction()).performScrollTo().performClick()
        compose.onNodeWithText("Histórico").performClick()
        show("1 registro")
        compose.onNode(hasSetTextAction() and hasText("Yesterday sample")).assertExists()
    }
}
