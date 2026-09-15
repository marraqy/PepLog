package app.peptides.journal

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

class ProtocolInterfaceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun click(text: String) {
        if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty())
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        val node = compose.onNode(hasText(text) and hasClickAction())
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed().performClick()
        if (text in listOf("Save protocol", "Save item", "Confirm record"))
            compose.waitUntil(15000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
    }
    private fun addTime(hour: Int, minute: Int = 0) {
        click("Add time")
        if (hour == 20) capture("time-picker-en.png")
        if (hour != 8) compose.onNodeWithContentDescription("$hour hours").performClick()
        if (minute != 0) {
            compose.onNodeWithContentDescription("Select minutes").performClick()
            compose.onNodeWithContentDescription("$minute minutes").performClick()
        }
        compose.onNodeWithText("Use time").performClick()
        compose.onNodeWithText("${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}").performScrollTo().assertExists()
    }
    private fun fill(label: String, text: String) = compose.onNodeWithText(label).performScrollTo().performTextReplacement(text)
    private fun waitText(text: String) = compose.waitUntil(15000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun assertFullyVisible(text: String) {
        val node = compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertTrue("All text lines must fit in the visible form", bounds.height + 1 >= layouts.single().size.height)
    }
    private fun agenda(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        waitText(text)
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val i = InstrumentationRegistry.getInstrumentation()
        i.uiAutomation.waitForIdle(500,5000)
        val bitmap = i.uiAutomation.takeScreenshot()
        File(i.targetContext.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    @Test fun multiPeptideAgendaCompletionRevisionAndBackup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = JournalDb.get(context)
        runBlocking {
            db.seed(context)
            db.dao().clearProtocols()
            db.dao().clearEntries()
        }
        compose.waitUntil(15000) { compose.onAllNodes(hasContentDescription("Settings") or hasContentDescription("Configurações")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("Settings") or hasContentDescription("Configurações")).performClick()
        compose.onNodeWithText("English").performClick()
        compose.onNodeWithText("Protocols").performClick()
        click("New protocol")
        fill("Protocol name", "Test protocol")
        compose.onAllNodesWithContentDescription("Open calendar")[0].performClick()
        waitText("Choose")
        compose.onNodeWithText("Cancel").performClick()
        click("Save protocol")
        agenda("Daily agenda")
        for (n in 1..2) {
            click("Add peptide")
            compose.onNodeWithText("Add a time using the clock.").performScrollTo().assertExists()
            compose.onNodeWithText("Save item").assertIsNotEnabled()
            if (n == 1) {
                compose.onNodeWithText("Choose saved calculation").assertDoesNotExist()
                compose.onNodeWithText("Select peptide and enter calculation").performScrollTo()
                capture("protocol-empty-en.png")
                click("Select peptide and enter calculation")
                compose.onNodeWithText("Select peptide").performClick()
                compose.onNode(hasText("BPC-157") and hasText("BPC 157")).performClick()
                fill("Peptide in vial (mg)", "2")
                fill("Final solution volume (mL)", "1")
                fill("Desired amount (mg)", "1")
                compose.onNode(hasText("Choose") or hasText("Change")).performScrollTo().performClick()
                click("100 units · 1 mL\nU-100 · Each tick: 1 unit")
                click("Use in protocol")
                compose.onNodeWithText("BPC-157").assertExists()
                assertTrue(runBlocking { db.dao().allEntries().isEmpty() })
            } else {
                click("Choose saved calculation")
                click("Protocol sample 2")
            }
            click("Every day")
            if (n == 1) {
                addTime(8)
                click("Add time")
                compose.onNodeWithText("This time is already added.").assertExists()
                compose.onNodeWithText("Use time").assertIsNotEnabled()
                compose.onNodeWithText("Cancel").performClick()
                addTime(9, 30)
                compose.onNodeWithContentDescription("Remove time 09:30").performScrollTo().performClick()
                addTime(20)
            } else addTime(9)
            if (n == 1) capture("protocol-item-en.png")
            compose.onNodeWithText("Save item").assertIsDisplayed()
            click("Save item")
            agenda("Daily agenda")
            if (n == 1) runBlocking {
                val saved = ProtocolCodec.decode(JSONObject(db.dao().allProtocols().single().document)).items.single()
                assertEquals("BPC-157", saved.entry.peptideName)
                assertEquals(listOf("08:00", "20:00"), saved.times)
                val p = db.dao().allPeptides().first { it.id == "bremelanotide" }
                db.dao().put(saved.entry.copy(id = java.util.UUID.randomUUID().toString(), peptideId = p.id,
                    peptideName = p.name, title = "Protocol sample 2"))
            }
        }
        agenda("Monthly calendar")
        val calendarMonth = YearMonth.now()
        compose.onNodeWithContentDescription("Calendar day ${LocalDate.now()}: Planned").assertExists()
        compose.onNodeWithContentDescription("Calendar day ${LocalDate.now()}: Planned").assertIsSelected()
        capture("protocol-calendar-en.png")
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onNodeWithContentDescription("Calendar month ${calendarMonth.plusMonths(1)}").assertExists()
        compose.onNodeWithContentDescription("Previous month").performClick()
        compose.onNodeWithContentDescription("Calendar month $calendarMonth").assertExists()
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onNodeWithText("Go to today").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Calendar day ${LocalDate.now()}: Planned").assertIsSelected()
        click("Edit protocol")
        click("Active")
        click("Save protocol")
        compose.onNodeWithText("Today").performClick()
        waitText("Today.")
        agenda("Adjust amount / note")
        compose.onAllNodesWithText("Adjust amount / note")[0].performScrollTo().performClick()
        fill("Actual amount (mg)", "0.8")
        fill("Observation (optional)", "Recorded from today")
        compose.onNodeWithText("Confirm record").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Confirm record").fetchSemanticsNodes().isEmpty() }
        agenda("Done · 0.8 mg")
        capture("today-en.png")
        compose.onNodeWithText("Protocols").performClick()
        click("Test protocol")
        agenda("Adjust amount / note")
        compose.onAllNodesWithText("Adjust amount / note")[0].performScrollTo().performClick()
        fill("Actual amount (mg)", "0.9")
        fill("Observation (optional)", "Completion note")
        click("Confirm record")
        agenda("Done · 0.9 mg")
        agenda("Skip")
        compose.onAllNodesWithText("Skip")[0].performScrollTo().performClick()
        click("Confirm record")
        agenda("Skipped")
        agenda("Monthly calendar")
        compose.onNodeWithContentDescription("Calendar day ${LocalDate.now()}: Done, Skipped").assertExists()
        capture("protocol-calendar-states-en.png")
        compose.activityRule.scenario.recreate()
        agenda("Done · 0.9 mg")
        capture("protocol-agenda-en.png")
        val before = runBlocking { ProtocolCodec.decode(JSONObject(db.dao().allProtocols().single().document)) }
        assertEquals(2, before.items.size)
        assertEquals(3, before.logs.size)
        val editRow = hasText(before.items.first().entry.peptideName) and hasText("Edit")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(editRow)
        compose.onNode(editRow).performClick()
        compose.onNodeWithText("End item after today").assertIsDisplayed()
        compose.onNodeWithText("Save item").assertIsDisplayed()
        capture("protocol-edit-actions-en.png")
        compose.onNodeWithText("End item after today").performClick()
        compose.onNodeWithText("End this scheduled item?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Remove time 20:00").performScrollTo().performClick()
        compose.onNodeWithText("End item after today").assertIsDisplayed()
        click("08:00")
        compose.onNodeWithContentDescription("10 hours").performClick()
        compose.onNodeWithText("Use time").performClick()
        click("Save item")
        agenda("Daily agenda")
        val revised = runBlocking { ProtocolCodec.decode(JSONObject(db.dao().allProtocols().single().document)) }
        assertEquals(before.logs, revised.logs)
        assertEquals(3, revised.items.size)
        assertEquals("10:00", revised.currentItems(LocalDate.now()).first { it.group == before.items.first().group }.times.single())
        agenda("Duplicate")
        click("Duplicate")
        waitText("Test protocol (copy)")
        agenda("Add peptide")
        click("Add peptide")
        click("Every day")
        addTime(12)
        click("Enter calculation")
        compose.onNodeWithText("Select peptide").performClick()
        compose.onNode(hasText("BPC-157") and hasText("BPC 157")).performClick()
        fill("Peptide in vial (mg)", "2")
        fill("Final solution volume (mL)", "1")
        fill("Desired amount (mg)", "1")
        click("Use in protocol")
        compose.onNodeWithText("12:00").performScrollTo().assertExists()
        click("Save item")
        val all = runBlocking { db.dao().allProtocols().map { ProtocolCodec.decode(JSONObject(it.document)) } }
        assertEquals(2, all.size)
        assertTrue(all.single { it.name.endsWith("(copy)") }.logs.isEmpty())
        assertEquals(3, all.single { it.name.endsWith("(copy)") }.items.size)
        runBlocking {
            val text = Backup.export(db)
            val backup = Backup.decode(text)
            db.dao().clearProtocols()
            Backup.restore(db, backup)
            assertEquals(all.toSet(), db.dao().allProtocols().map { ProtocolCodec.decode(JSONObject(it.document)) }.toSet())
            File(context.getExternalFilesDir(null), "protocol-backup.json").writeText(text)
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Português (BR)").performClick()
        compose.onNodeWithText("Protocolos").performClick()
        waitText("Seus protocolos.")
        click("Test protocol")
        agenda("Agenda do dia")
        capture("protocol-agenda-pt.png")
        click("Adicionar peptídeo")
        assertFullyVisible("Selecione um cálculo, dias da semana e horários válidos. As datas devem estar no período do protocolo e começar em ou após ${LocalDate.now()}")
    }
}
