package app.peptides.journal

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class BackupTest {
    private val p = Peptide("test", "Test peptide")
    private val c = Calculator.calculate("1", "3", "1", "1", "100", "1")
    private val e = Entry(peptideId = p.id, peptideName = p.name, dose = "1", mass = "3", water = "1", capacity = "1", scale = "100", tick = "1",
        notes = "Português: observação\nEnglish note", concentration = c.concentration.toPlainString(), volume = c.volume.toPlainString(),
        units = c.units.toPlainString(), doses = c.doses.toPlainString(), remainder = c.remainder.toPlainString())
    @Test fun roundTripPreservesEveryFieldAndUnicode() {
        val data = BackupData(listOf(p), listOf(e))
        assertEquals(data, Backup.decode(Backup.export(data)))
    }
    @Test fun exportRejectsImportViolations() {
        listOf(
            BackupData(emptyList(), emptyList()),
            BackupData(listOf(p, p.copy(id = "other", name = " TEST PEPTIDE ")), emptyList()),
            BackupData(listOf(p), listOf(e.copy(peptideId = "missing"))),
            BackupData(listOf(p), listOf(e.copy(volume = "0.5"))),
            BackupData(listOf(p), listOf(e.copy(notes = "x".repeat(4001)))),
            BackupData(listOf(p), listOf(e, e))
        ).forEach { data -> assertThrows(IllegalArgumentException::class.java) { Backup.export(data) } }
    }
    @Test fun catalogBoundaryAndCapacityErrors() {
        val catalog = List(DataLimit.CATALOG.maximum) { p.copy(id = "$it", name = "Peptide $it") }
        val data = BackupData(catalog, emptyList())
        assertEquals(data, Backup.decode(Backup.export(data)))
        val overflow = data.copy(peptides = catalog + p)
        assertEquals(DataLimit.CATALOG, assertThrows(DataCapacityException::class.java) { Backup.export(overflow) }.limit)
        assertEquals(DataLimit.RECORDS, assertThrows(DataCapacityException::class.java) {
            Backup.export(BackupData(listOf(p), List(DataLimit.RECORDS.maximum + 1) { e.copy(id = "$it") }))
        }.limit)
        assertEquals(DataLimit.BYTES, assertThrows(DataCapacityException::class.java) {
            Backup.export(BackupData(listOf(p.copy(source = "é".repeat(Backup.MAX_BYTES / 2))), emptyList()))
        }.limit)
    }
    @Test fun rejectsTamperedResultsAndMissingPeptide() {
        val json = JSONObject(Backup.encode(BackupData(listOf(p), listOf(e))))
        json.getJSONArray("records").getJSONObject(0).put("volume", "0.5")
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(json.toString()) }
        json.getJSONArray("records").getJSONObject(0).put("volume", e.volume).put("peptideId", "missing")
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(json.toString()) }
    }
    @Test fun rejectsDuplicateRecordsAndFutureVersions() {
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(Backup.encode(BackupData(listOf(p), listOf(e,e)))) }
        val json = JSONObject(Backup.encode(BackupData(listOf(p), listOf(e)))).put("version", 99)
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(json.toString()) }
    }
}
