package app.peptides.journal

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class BlendInterfaceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun closeSoftKeyboard() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        automation.waitForIdle(200, 5000)
        if (automation.windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD })
            automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitForIdle()
    }
    private fun click(text: String) {
        closeSoftKeyboard()
        if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty())
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        val node = compose.onNode(hasText(text) and hasClickAction())
        runCatching { node.performScrollTo() }
        compose.waitUntil(15000) { !node.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled) }
        node.assertIsEnabled().performClick()
        if (text in listOf("Save", "Save record", "Save changes", "Save protocol", "Save item", "Confirm record"))
            compose.waitUntil(15000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
    }
    private fun fill(label: String, value: String) {
        compose.onNodeWithText(label).performScrollTo().performTextReplacement(value)
        closeSoftKeyboard()
    }
    private fun unit(label: String, unit: String) = compose.onNodeWithContentDescription("$label: $unit").performScrollTo().performClick()
    private fun capture(name: String) {
        closeSoftKeyboard()
        compose.waitForIdle()
        val i = InstrumentationRegistry.getInstrumentation()
        i.uiAutomation.waitForIdle(500, 5000)
        val bitmap = i.uiAutomation.takeScreenshot()
        File(i.targetContext.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun mixedUnitsCompositionSnapshotAndActualAmounts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = JournalDb.get(context)
        val preferences = context.getSharedPreferences("settings", 0)
        val oldLanguage = preferences.getBoolean("pt", false)
        val oldSyringe = listOf("syringe.capacity", "syringe.scale", "syringe.tick").associateWith { preferences.getString(it, null) }
        runBlocking { db.seed(context); db.dao().clearProtocols(); db.dao().clearEntries() }
        try {
            compose.waitUntil(15000) { compose.onAllNodes(hasContentDescription("Settings") or hasContentDescription("Configurações")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasContentDescription("Settings") or hasContentDescription("Configurações")).performClick()
            click("English")
            click("Home")
            click("New calculation")
            click("Select peptide")
            compose.onNodeWithText("Search name or alias").performTextReplacement("KLOW")
            click("KLOW (TB-500 + BPC-157 + GHK-CU + KPV)")
            compose.onNodeWithText("Compound 1").performScrollTo()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("TB-500")))
            compose.onNodeWithText("Amount in vial 1 (mg)").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
            fill("Amount in vial 1 (mg)", "1")
            fill("Amount in vial 2 (mg)", "2")
            fill("Amount in vial 3 (mg)", "3")
            fill("Amount in vial 4 (mg)", "4")
            click("Use this composition")
            compose.onNodeWithText("KPV: 4 mg").performScrollTo().assertExists()
            click("Peptides")
            click("Discard")
            click("Add peptide")
            fill("Name", "Example blend")
            compose.onNodeWithContentDescription("Blend").performScrollTo().performClick()
            fill("Compound 1", "Alpha")
            fill("Amount in vial 1 (mg)", "10")
            fill("Compound 2", "Beta")
            fill("Amount in vial 2 (mg)", "5")
            unit("Amount in vial 2", "mcg")
            compose.onNodeWithText("Amount in vial 2 (mcg)")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("5000")))
            click("Add compound")
            fill("Compound 3", "Gamma")
            fill("Amount in vial 3 (mg)", "2")
            click("Add compound")
            fill("Compound 4", "Delta")
            fill("Amount in vial 4 (mg)", "1")
            click("Save")
            click("Home")
            click("New calculation")
            click("Select peptide")
            click("Example blend")
            click("Beta")
            fill("Final solution volume (mL)", "3")
            unit("Desired amount", "mcg")
            fill("Desired amount (mcg)", "250")
            compose.onNode(hasText("Choose") or hasText("Change")).performScrollTo().performClick()
            click("100 units · 1 mL\nU-100 · Each tick: 1 unit")
            compose.onNodeWithText("0.15 mL").performScrollTo().assertExists()
            compose.onNodeWithText("Alpha: 0.5 mg · 500 mcg").performScrollTo().assertExists()
            capture("blend-calculation-en.png")
            fill("Record name (optional)", "Blend record")
            click("Save record")
            compose.waitUntil(15000) { compose.onAllNodesWithText("Use as a starting point").fetchSemanticsNodes().isNotEmpty() }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15000) { compose.onAllNodesWithText("Beta: 0.25 mg · 250 mcg").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Beta: 0.25 mg · 250 mcg").performScrollTo().assertExists()
            click("Edit record")
            unit("Desired amount", "mg")
            compose.onNodeWithText("Desired amount (mg)").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("0.25")))
            unit("Desired amount", "mcg")
            click("Save changes")
            val saved = runBlocking { db.dao().allEntries().single() }
            assertEquals("mcg", saved.doseUnit)
            assertEquals(1, saved.reference)
            assertEquals(0, saved.dose.toBigDecimal().compareTo("0.25".toBigDecimal()))
            assertEquals(4, Blend.decode(saved.composition).size)
            click("Protocols")
            click("New protocol")
            fill("Protocol name", "Blend protocol")
            click("Save protocol")
            click("Add peptide")
            click("Choose saved calculation")
            click("Blend record")
            click("Every day")
            click("Add time")
            click("Use time")
            click("Save item")
            click("Edit protocol")
            click("Active")
            click("Save protocol")
            click("Today")
            click("Mark done")
            fill("Actual amount (mcg)", "100")
            compose.onNodeWithText("Alpha: 0.2 mg · 200 mcg").performScrollTo().assertExists()
            click("Confirm record")
            val protocol = runBlocking { ProtocolCodec.decode(JSONObject(db.dao().allProtocols().single().document)) }
            assertEquals(0, protocol.logs.single().actualMg.toBigDecimal().compareTo("0.1".toBigDecimal()))
            assertEquals(saved.composition, protocol.items.single().entry.composition)
            runBlocking {
                val backup = Backup.decode(Backup.export(db))
                Backup.restore(db, backup)
                assertEquals(saved, db.dao().allEntries().single())
                assertEquals(protocol, ProtocolCodec.decode(JSONObject(db.dao().allProtocols().single().document)))
            }
            compose.onNodeWithContentDescription("Settings").performClick()
            click("Português (BR)")
            click("Hoje")
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Registrado por composto"))
            compose.onNodeWithText("Alpha: 0,2 mg · 200 mcg").assertExists()
            capture("blend-today-pt.png")
        } finally {
            preferences.edit().putBoolean("pt", oldLanguage).apply {
                oldSyringe.forEach { (key, value) -> if (value == null) remove(key) else putString(key, value) }
            }.commit()
            runBlocking { db.dao().clearProtocols(); db.dao().clearEntries() }
        }
    }
}
