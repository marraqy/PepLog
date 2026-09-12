package app.peptides.journal

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class InterfaceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(500, 5000)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun fill(label: String, value: String) {
        compose.onNodeWithText(label).performScrollTo().performTextInput(value)
    }
    private fun hideKeyboard() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val manager = compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            manager.hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5000)
    }
    @Test fun saveReopenDuplicateAndSwitchLanguage() {
        compose.waitUntil(15000) { compose.onAllNodesWithText("New calculation").fetchSemanticsNodes().isNotEmpty() }
        capture("home-en.png")
        compose.onNodeWithText("New calculation").performClick()
        compose.onNodeWithText("Select peptide").performClick()
        compose.onNodeWithText("BPC-157").performClick()
        fill("Desired amount (mg)", "1")
        fill("Peptide in vial (mg)", "2")
        fill("Final solution volume (mL)", "1")
        compose.onNodeWithText("Choose").performScrollTo().performClick()
        capture("syringe-picker-en.png")
        compose.onNodeWithText("100 units · 1 mL\nU-100 · Each tick: 1 unit").performScrollTo().performClick()
        compose.onNodeWithText("Volume to draw").performScrollTo()
        capture("calculation-en.png")
        fill("Record name (optional)", "Test record")
        compose.onNodeWithText("Save record").performScrollTo().assertIsEnabled()
        capture("before-save-en.png")
        compose.onNodeWithText("Save record").performClick()
        try {
            compose.waitUntil(10000) { compose.onAllNodesWithText("Use as a starting point").fetchSemanticsNodes().isNotEmpty() }
        } catch (error: Throwable) {
            capture("failure-save.png")
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            File(context.getExternalFilesDir(null), "failure-tree.txt").writeText(compose.onRoot().printToString())
            throw error
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Use as a starting point").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("0.5 mL").assertExists()
        capture("record-en.png")
        compose.onNodeWithText("Use as a starting point").performScrollTo().performClick()
        compose.onNodeWithText("100 units · 1 mL\nU-100 · Each tick: 1 unit").assertExists()
        compose.onNodeWithText("Desired amount (mg)").performScrollTo().performTextReplacement("0.5")
        compose.onNodeWithText("Record name (optional)").performScrollTo().performTextReplacement("Second record")
        compose.onNodeWithText("Save record").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Use as a starting point").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("New calculation").performClick()
        compose.onNodeWithText("100 units · 1 mL\nU-100 · Each tick: 1 unit").assertExists()
        compose.onNodeWithText("Use a saved vial").performClick()
        compose.onNodeWithText("Test record").performClick()
        compose.onNodeWithText("Desired amount (mg)").assertTextContains("")
        fill("Desired amount (mg)", "0.51")
        compose.onNodeWithText("Change").performScrollTo().performClick()
        compose.onNodeWithText("100 units · 1 mL\nU-100 · Each tick: 2 units").performScrollTo().performClick()
        compose.onNodeWithText("The result falls between syringe divisions. It has not been rounded to a marking.").performScrollTo().assertExists()
        compose.onNodeWithText("Desired amount (mg)").performScrollTo().performTextReplacement("2")
        compose.onNodeWithText("Change").performScrollTo().performClick()
        compose.onNodeWithText("30 units · 0.3 mL\nU-100 · Each tick: 1 unit").performScrollTo().performClick()
        compose.onNodeWithText("The calculated volume exceeds syringe capacity.").performScrollTo().assertExists()
        compose.onNodeWithText("Save record").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Change").performScrollTo().performClick()
        compose.onNodeWithText("Custom syringe").performScrollTo().performClick()
        compose.onNodeWithText("Syringe capacity (mL)").performTextReplacement("0")
        compose.onNodeWithText("Use this syringe").assertIsNotEnabled()
        compose.onNodeWithText("Syringe capacity (mL)").performTextReplacement("1")
        compose.onNodeWithText("Smallest division (scale units)").performTextReplacement("0.5")
        compose.onNodeWithText("Use this syringe").assertIsEnabled().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("100 units · 1 mL\nU-100 · Each tick: 0.5 units").assertExists()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Discard").performClick()
        compose.onNodeWithText("Português (BR)").performClick()
        compose.onNodeWithText("Idioma").assertExists()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Idioma").fetchSemanticsNodes().isNotEmpty() }
        capture("settings-pt.png")
        compose.onNodeWithText("Histórico").performClick()
        compose.onNodeWithText("Test record").performScrollTo().assertExists()
        compose.onNodeWithText("Second record").performScrollTo().assertExists()
        capture("history-pt.png")
        compose.onNodeWithText("Peptídeos").performClick()
        compose.onNodeWithText("Adicionar peptídeo").performClick()
        compose.onNodeWithText("Nome").performTextInput("Meu peptídeo")
        compose.onNodeWithText("Salvar").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Nome").fetchSemanticsNodes().isEmpty() }
        hideKeyboard()
        compose.onNodeWithText("Buscar peptídeos").performTextReplacement("Meu peptídeo")
        compose.onNodeWithText("Buscar peptídeos").assertTextContains("Meu peptídeo")
        hideKeyboard()
        val catalogRow = hasText("Meu peptídeo") and !hasSetTextAction()
        try {
            compose.waitUntil(10000) { compose.onAllNodes(catalogRow).fetchSemanticsNodes().isNotEmpty() }
        } catch (error: Throwable) {
            capture("catalog-pt.png")
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            File(context.getExternalFilesDir(null), "catalog-tree.txt").writeText(compose.onRoot().printToString())
            File(context.getExternalFilesDir(null), "catalog-data.txt").writeText(kotlinx.coroutines.runBlocking { JournalDb.get(context).dao().allPeptides().toString() })
            throw error
        }
        compose.onNode(catalogRow).assertIsEnabled().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Nome").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Nome").performTextReplacement("Peptídeo personalizado")
        compose.onNodeWithText("Salvar").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Nome").fetchSemanticsNodes().isEmpty() }
        hideKeyboard()
        compose.onNodeWithText("Buscar peptídeos").performTextReplacement("personalizado")
        compose.onNodeWithText("Buscar peptídeos").assertTextContains("personalizado")
        hideKeyboard()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Peptídeo personalizado").fetchSemanticsNodes().isNotEmpty() }
        capture("catalog-pt.png")
        compose.onNodeWithText("Histórico").performClick()
        compose.onNodeWithText("Second record").performScrollTo().performClick()
        compose.onNodeWithText("Editar registro").performScrollTo().performClick()
        compose.onNodeWithText("Observações (opcional)").performScrollTo().performTextInput("Registro editado")
        compose.onNodeWithText("Salvar alterações").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Usar como base").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Excluir registro").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Excluir registro").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Registro editado").assertExists()
        compose.onNodeWithText("Excluir registro").performScrollTo().performClick()
        compose.onNodeWithText("Excluir", substring = false).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Seus registros.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Second record").assertDoesNotExist()
        compose.onNodeWithText("Test record").assertExists()
    }
}
