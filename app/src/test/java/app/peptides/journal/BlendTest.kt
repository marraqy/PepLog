package app.peptides.journal

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class BlendTest {
    private val parts = listOf(Compound("A", "10"), Compound("B", "5000", "mcg"), Compound("C", "2"), Compound("D", "1"))
    private val composition = Blend.encode(parts)
    private fun entry(): Entry {
        val dose = Mass.toMg("250", "mcg").toPlainString()
        val mass = Blend.basis(parts, 1).toPlainString()
        val c = Calculator.calculate(dose, mass, "3", "1", "100", "1")
        return Entry(peptideId = "blend", peptideName = "Example blend", dose = dose, mass = mass, water = "3", capacity = "1", scale = "100", tick = "1",
            concentration = c.concentration.toPlainString(), volume = c.volume.toPlainString(), units = c.units.toPlainString(),
            doses = c.doses.toPlainString(), remainder = c.remainder.toPlainString(), doseUnit = "mcg", composition = composition, reference = 1)
    }
    @Test fun exactConversionsAndSyringeLimits() {
        assertEquals(0, Mass.toMg("1", "mg").compareTo(Mass.toMg("1000", "mcg")))
        assertEquals(0, Mass.toMg("0,001", "mg").compareTo(Mass.toMg("1", "mcg")))
        assertEquals("0.000000000001", Mass.toMg("0.000000001", "mcg").toPlainString())
        assertEquals("999999999000", Mass.input("999999999", "mcg"))
        assertEquals(0, Mass.toMg("999999999000", "mcg").compareTo(BigDecimal("999999999")))
        val a = Calculator.calculate("0.25", "5", "3", "1", "100", "1")
        assertEquals(a, entry().calculation())
        assertTrue(a.aligned)
        assertThrows(InputProblem::class.java) { Calculator.calculate(Mass.toMg("250.000001", "mcg").toPlainString(), "5", "3", "0.15", "100", "1") }
        assertFalse(Calculator.calculate("0.000000000001", "1", "1", "1", "100", "1").aligned)
    }
    @Test fun unequalCompoundsUseTheSameVialFraction() {
        val e = entry()
        assertEquals(0, e.volume.toBigDecimal().compareTo(BigDecimal("0.15")))
        val expected = listOf("0.5", "0.25", "0.1", "0.05").map(::BigDecimal)
        Blend.amounts(parts, 1, e.dose).map { it.second }.zip(expected).forEach { (a, b) -> assertEquals(0, a.compareTo(b)) }
        Blend.amounts(parts, -1, "0.9").map { it.second }.zip(expected).forEach { (a, b) -> assertEquals(0, a.compareTo(b)) }
        val actual = Blend.amounts(parts, 1, Mass.toMg("100", "mcg").toPlainString())
        assertEquals(0, actual.first().second.compareTo(BigDecimal("0.2")))
    }
    @Test fun invalidCompositionAndReferenceAreRejected() {
        listOf(listOf(Compound("A", "1")), listOf(Compound("A", "1"), Compound(" a ", "2")),
            listOf(Compound("A", "0"), Compound("B", "2")), listOf(Compound("A", "1", "g"), Compound("B", "2")),
            List(11) { Compound("Part $it", "1") }).forEach {
            assertThrows(IllegalArgumentException::class.java) { Blend.decode(Blend.encode(it)) }
        }
        assertThrows(IllegalArgumentException::class.java) { entry().copy(reference = 4).calculation() }
        assertThrows(IllegalArgumentException::class.java) { entry().copy(mass = "18").calculation() }
        listOf("0", "-1", "1e3", "1,000.5").forEach { assertThrows(IllegalArgumentException::class.java) { Mass.toMg(it, "mcg") } }
    }
    @Test fun backupPreservesSnapshotsUnitsAndRecordedCompoundAmounts() {
        val e = entry()
        val item = ProtocolItem(entry = e, from = "2026-09-08", days = listOf(2), times = listOf("08:00"))
        val p = Protocol(name = "Blend plan", start = item.from, status = "active", items = listOf(item),
            logs = listOf(ProtocolLog(item.id, item.from, "08:00", "done", "0.1")))
        // Editing the catalog must not rewrite saved vial compositions.
        val catalog = Peptide("blend", "Renamed", composition = Blend.encode(listOf(Compound("A", "20"), Compound("B", "10"))))
        val data = BackupData(listOf(catalog), listOf(e), listOf(p.row()))
        assertEquals(data, Backup.decode(Backup.encode(data)))
        val restored = ProtocolCodec.decode(JSONObject(Backup.decode(Backup.encode(data)).protocols.single().document))
        assertEquals(composition, restored.items.single().entry.composition)
        assertEquals("0.1", restored.logs.single().actualMg)
        val tampered = JSONObject(Backup.encode(data))
        tampered.getJSONArray("records").getJSONObject(0).getJSONArray("composition").getJSONObject(1).put("amount", "6000")
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(tampered.toString()) }
    }
    @Test fun legacyBackupsDefaultToMgWithoutComposition() {
        val e = entry().copy(composition = "[]", reference = -1, doseUnit = "mg")
        for (version in 1..2) {
            val json = JSONObject(Backup.encode(BackupData(listOf(Peptide("blend", "Plain")), listOf(e)))).put("version", version)
            json.getJSONArray("peptides").getJSONObject(0).remove("composition")
            val row = json.getJSONArray("records").getJSONObject(0)
            listOf("composition", "reference", "doseUnit", "massUnit").forEach(row::remove)
            assertEquals(e, Backup.decode(json.toString()).entries.single())
        }
    }
}
