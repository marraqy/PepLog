package app.peptides.journal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class DeviceTest {
    @Test fun expandedCatalogPreservesEditsAndDoesNotDuplicateNames() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, JournalDb::class.java).build()
        try {
            val edited = Peptide(id = "semaglutide", name = "My semaglutide", active = false)
            val custom = Peptide(id = "custom-klow", name = "KLOW", active = false)
            db.dao().put(edited); db.dao().put(custom)
            db.seed(context)
            val first = db.dao().allPeptides()
            assertEquals(35, first.size)
            assertEquals(edited, first.single { it.id == edited.id })
            assertEquals(custom, first.single { it.id == custom.id })
            assertEquals(1, first.count { it.name.contains("KLOW") })
            assertTrue(CatalogDefaults.matches(first.single { it.id == "bremelanotide" }, "PT141"))
            val draft = CatalogDefaults.composition(first.single { it.name.startsWith("GLOW") })
            assertEquals(listOf("TB-500", "BPC-157", "GHK-CU"), Blend.draft(draft).map { it.name })
            assertTrue(Blend.draft(draft).all { it.amount.isEmpty() })
            assertTrue(runCatching { Blend.decode(draft) }.isFailure)
            db.seed(context)
            assertEquals(first, db.dao().allPeptides())
            Backup.restore(db, Backup.decode(Backup.export(db)))
            assertEquals(first, db.dao().allPeptides())
        } finally { db.close() }
    }
    @Test fun migrationFromV2PreservesRecordsAndProtocolDocuments() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "migration-${System.nanoTime()}.db"
        val schema = JSONObject(instrumentation.context.assets.open("schema-v2.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val c = Calculator.calculate("1", "2", "1", "1", "100", "1")
        val entry = Entry(peptideId = "legacy", peptideName = "Legacy peptide", dose = "1", mass = "2", water = "1", capacity = "1", scale = "100", tick = "1",
            concentration = c.concentration.toPlainString(), volume = c.volume.toPlainString(), units = c.units.toPlainString(), doses = c.doses.toPlainString(), remainder = c.remainder.toPlainString())
        val item = ProtocolItem(entry = entry, from = "2026-09-08", days = listOf(2), times = listOf("08:00"))
        val protocol = Protocol(name = "Legacy protocol", start = item.from, status = "active", items = listOf(item),
            logs = listOf(ProtocolLog(item.id, item.from, "08:00", "done", "0.9")))
        val document = ProtocolCodec.encode(protocol)
        val snapshot = document.getJSONArray("items").getJSONObject(0).getJSONObject("entry")
        listOf("doseUnit", "massUnit", "composition", "reference").forEach { snapshot.remove(it) }
        val legacy = context.openOrCreateDatabase(name, 0, null)
        try {
            val tables = schema.getJSONArray("entities")
            for (n in 0 until tables.length()) {
                val table = tables.getJSONObject(n)
                legacy.execSQL(table.getString("createSql").replace("\${TABLE_NAME}", table.getString("tableName")))
            }
            legacy.execSQL("INSERT INTO peptides(id,name,aliases,source,active) VALUES ('legacy','Legacy peptide','','',1)")
            val fields = tables.getJSONObject(1).getJSONArray("fields")
            val columns = (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }
            val json = ProtocolCodec.entryJson(entry)
            legacy.execSQL("INSERT INTO records (${columns.joinToString(",")}) VALUES (${columns.joinToString(",") { "?" }})", columns.map { json.get(it) }.toTypedArray())
            legacy.execSQL("INSERT INTO protocols(id,document) VALUES (?,?)", arrayOf(protocol.id, document.toString()))
            legacy.version = 2
        } finally { legacy.close() }
        val db = Room.databaseBuilder(context, JournalDb::class.java, name).addMigrations(JournalDb.MIGRATION_2_3).build()
        try {
            assertEquals(entry, db.dao().allEntries().single())
            assertEquals("[]", db.dao().allPeptides().single().composition)
            assertEquals(document.toString(), db.dao().allProtocols().single().document)
            assertEquals(protocol, ProtocolCodec.decode(JSONObject(db.dao().allProtocols().single().document)))
            val restored = Backup.decode(Backup.export(db))
            Backup.restore(db, restored)
            assertEquals(entry, db.dao().allEntries().single())
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun persistenceBackupAndCatalogSnapshot() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-${System.nanoTime()}.db"
        var db = Room.databaseBuilder(context, JournalDb::class.java, name).build()
        try {
            db.seed(context)
            val peptide = db.dao().allPeptides().first()
            val c = Calculator.calculate("1", "2", "1", "1", "100", "1")
            val entry = Entry(peptideId = peptide.id, peptideName = peptide.name, dose = "1", mass = "2", water = "1", capacity = "1", scale = "100", tick = "1",
                concentration = c.concentration.toPlainString(), volume = c.volume.toPlainString(), units = c.units.toPlainString(), doses = c.doses.toPlainString(), remainder = c.remainder.toPlainString())
            db.dao().put(entry)
            db.close()
            db = Room.databaseBuilder(context, JournalDb::class.java, name).build()
            assertEquals(entry, db.dao().allEntries().single())
            db.dao().put(peptide.copy(name = "Renamed", active = false))
            assertEquals(peptide.name, db.dao().allEntries().single().peptideName)
            val backup = Backup.decode(Backup.export(db))
            db.dao().delete(entry.id)
            assertTrue(db.dao().allEntries().isEmpty())
            Backup.restore(db, backup)
            assertEquals(entry, db.dao().allEntries().single())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
