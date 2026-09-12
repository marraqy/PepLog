package app.peptides.journal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.ZonedDateTime
import java.util.Locale

class ReviewInterfaceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun content(block: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.setContent(content = block) }
    }

    private fun entry(): Entry {
        val c = Calculator.calculate("1", "2", "1", "1", "100", "1")
        return Entry(peptideId = "p", peptideName = "Sample", dose = "1", mass = "2", water = "1", capacity = "1", scale = "100", tick = "1",
            concentration = c.concentration.toPlainString(), volume = c.volume.toPlainString(), units = c.units.toPlainString(),
            doses = c.doses.toPlainString(), remainder = c.remainder.toPlainString())
    }

    @Test fun midnightKeepsChosenDayAndTimezoneAndResumeRefreshToday() {
        var now = ZonedDateTime.parse("2026-09-10T23:59:59Z")
        var log: ProtocolLog? = null
        var receiver: BroadcastReceiver? = null
        val context = object : ContextWrapper(compose.activity) {
            override fun registerReceiver(value: BroadcastReceiver?, filter: IntentFilter?): Intent? { receiver = value; return null }
            override fun unregisterReceiver(value: BroadcastReceiver?) { receiver = null }
        }
        val item = ProtocolItem(entry = entry(), from = "2026-09-01", days = (1..7).toList(), times = listOf("08:00"))
        val rows = listOf(Protocol(name = "Daily", start = item.from, status = "active", items = listOf(item)).row())
        compose.mainClock.autoAdvance = false
        content { MaterialTheme { CompositionLocalProvider(LocalContext provides context, androidx.lifecycle.compose.LocalLifecycleOwner provides compose.activity) {
            TodayScreen(rows, false, now = { now }) { _, value, done -> log = value; done() }
        } } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Skip").performClick()
        compose.mainClock.advanceTimeByFrame()
        now = now.plusSeconds(2)
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithText("Confirm record").performClick()
        compose.mainClock.advanceTimeByFrame()
        assertEquals("2026-09-10", log?.day)
        compose.onNodeWithText("Sep 11, 2026").assertExists()
        now = now.withZoneSameInstant(java.time.ZoneId.of("America/Los_Angeles"))
        compose.runOnIdle { receiver!!.onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED)) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Sep 10, 2026").assertExists()
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        now = now.plusDays(2)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.mainClock.advanceTimeBy(100)
        assertTrue("Clock: $now; lifecycle: ${compose.activity.lifecycle.currentState}\n" + compose.onAllNodes(isRoot()).onLast().printToString(),
            compose.onAllNodesWithText("Sep 12, 2026").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun appLocaleOverridesOppositeAndroidLocaleInMaterialPicker() {
        var pt by mutableStateOf(true)
        var observed: Locale? = null
        content {
            val base = LocalContext.current
            val config = Configuration(LocalConfiguration.current).apply { setLocale(if (pt) Locale.US else Locale.forLanguageTag("pt-BR")) }
            CompositionLocalProvider(LocalContext provides base.createConfigurationContext(config), LocalConfiguration provides config) {
                AppLanguage(pt) { MaterialTheme {
                    observed = LocalContext.current.resources.configuration.locales[0]
                    DatePickerField("2026-09-10", {}, "Date")
                } }
            }
        }
        compose.onNodeWithContentDescription("Abrir calendário").performClick()
        assertTrue("Resource locale: $observed\n" + compose.onAllNodes(isRoot()).onLast().printToString(),
            compose.onAllNodesWithText("Selecionar data").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("setembro", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals("pt", observed?.language)
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { pt = false }
        compose.onNodeWithContentDescription("Open calendar").performClick()
        compose.onNodeWithText("Select date").assertExists()
        assertTrue(compose.onAllNodesWithText("September", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals("en", observed?.language)
    }

    @Test fun catalogRequestsDiscardAndKeepsDraftWhenSaveFails() {
        content { MaterialTheme { Catalog(emptyList(), false) { _, _ -> } } }
        compose.onNodeWithText("Add peptide").performClick()
        compose.onNodeWithText("Name").performTextInput("Draft sample")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Discard unsaved changes?").assertExists()
        compose.onAllNodesWithText("Cancel").onLast().performClick()
        compose.onNodeWithText("Draft sample").assertExists()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Draft sample").assertExists()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(200, 5000)
        if (automation.windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }) {
            automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            automation.waitForIdle(200, 5000)
        }
        automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitUntil(5000) { compose.onAllNodesWithText("Discard unsaved changes?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Discard unsaved changes?").assertExists()
        compose.onNodeWithText("Discard").performClick()
        compose.onNodeWithText("Draft sample").assertDoesNotExist()
    }

    @Test fun reminderFailureReportsCompletedSave() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("settings", 0)
        val language = prefs.getBoolean("pt", false)
        prefs.edit().putBoolean("pt", false).commit()
        val name = "Reminder failure ${System.nanoTime()}"
        try {
            content { MaterialTheme { JournalApp(refreshReminders = { throw IllegalStateException("Test scheduling failure") }) } }
            compose.waitUntil(15000) { compose.onAllNodesWithText("New calculation").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Peptides").performClick()
            compose.onNodeWithText("Add peptide").performClick()
            compose.onNodeWithText("Name").performTextInput(name)
            compose.onNodeWithText("Save").performClick()
            compose.onNodeWithText("Operation completed, but reminders could not be updated. Check notification settings.").assertExists()
            assertTrue(runBlocking { JournalDb.get(context).dao().allPeptides().any { it.name == name } })
        } finally {
            prefs.edit().putBoolean("pt", language).commit()
            JournalDb.get(context).openHelper.writableDatabase.execSQL("DELETE FROM peptides WHERE name = ?", arrayOf(name))
        }
    }
}
