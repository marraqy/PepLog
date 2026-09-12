package app.peptides.journal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ProtocolRemovalTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun click(text: String) {
        if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty())
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        val node = compose.onNode(hasText(text) and hasClickAction())
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }
    private fun waitText(text: String) = compose.waitUntil(15000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun deletionIsConfirmedAndRecordedProtocolsAreArchived() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("settings", 0)
        val oldLanguage = prefs.getBoolean("pt", false)
        val db = JournalDb.get(context)
        val today = LocalDate.now().toString()
        val c = Calculator.calculate("1", "2", "1", "1", "100", "1")
        val entry = Entry(peptideId = "bpc-157", peptideName = "BPC-157", dose = "1", mass = "2", water = "1",
            capacity = "1", scale = "100", tick = "1", concentration = c.concentration.toPlainString(), volume = c.volume.toPlainString(),
            units = c.units.toPlainString(), doses = c.doses.toPlainString(), remainder = c.remainder.toPlainString())
        val item = ProtocolItem(entry = entry, from = today, days = (1..7).toList(), times = listOf("08:00"))
        val recorded = Protocol(name = "Recorded plan", start = today, status = "active", items = listOf(item),
            logs = listOf(ProtocolLog(item.id, today, "08:00", "skipped", notes = "Keep this note")))
        val copy = recorded.duplicate(LocalDate.now(), "Duplicate plan")
        try {
            runBlocking {
                db.seed(context); db.dao().clearProtocols(); db.dao().put(entry)
                db.saveProtocol(recorded); db.saveProtocol(copy)
                assertTrue(runCatching { db.deleteEmptyProtocol(recorded.id) }.isFailure)
                assertNotNull(db.dao().protocol(recorded.id))
            }
            compose.waitUntil(15000) { compose.onAllNodes(hasContentDescription("Settings") or hasContentDescription("Configurações")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasContentDescription("Settings") or hasContentDescription("Configurações")).performClick()
            click("English"); click("Protocols"); click("Duplicate plan")
            click("Delete protocol"); click("Cancel")
            assertNotNull(runBlocking { db.dao().protocol(copy.id) })
            click("Delete protocol"); click("Delete")
            waitText("Your protocols.")
            assertNull(runBlocking { db.dao().protocol(copy.id) })
            assertTrue(runBlocking { db.dao().allEntries().any { it.id == entry.id } })
            click("Recorded plan")
            compose.onNodeWithText("Delete protocol").assertDoesNotExist()
            click("Archive protocol"); click("Cancel")
            assertFalse(runBlocking { ProtocolCodec.decode(JSONObject(db.dao().protocol(recorded.id)!!.document)).archived })
            click("Archive protocol"); click("Archive")
            waitText("Your protocols.")
            compose.onNodeWithText("Recorded plan").assertDoesNotExist()
            val archived = runBlocking { ProtocolCodec.decode(JSONObject(db.dao().protocol(recorded.id)!!.document)) }
            assertEquals(recorded.copy(status = "completed", archived = true), archived)
            assertNull(ReminderPlan.next(listOf(archived), System.currentTimeMillis(), java.time.ZoneId.systemDefault()))
            val backup = runBlocking { Backup.decode(Backup.export(db)) }
            assertEquals(archived, ProtocolCodec.decode(JSONObject(backup.protocols.single().document)))
            click("Show archived"); click("Recorded plan")
            compose.onNodeWithText("Edit protocol").assertIsNotEnabled()
            click("Unarchive protocol"); click("Unarchive")
            waitText("Your protocols.")
            val unarchived = runBlocking { ProtocolCodec.decode(JSONObject(db.dao().protocol(recorded.id)!!.document)) }
            assertEquals(recorded.copy(status = "paused"), unarchived)
        } finally {
            prefs.edit().putBoolean("pt", oldLanguage).commit()
            runBlocking { db.dao().clearProtocols(); db.dao().delete(entry.id) }
        }
    }
}
