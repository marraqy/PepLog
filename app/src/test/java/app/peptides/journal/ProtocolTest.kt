package app.peptides.journal

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

class ProtocolTest {
    @Test fun archivedProtocolsPreserveRecordsAndReadLegacyDocuments() {
        val log = ProtocolLog(item.id, today.toString(), "08:00", "done", "1")
        val archived = p.copy(status = "completed", archived = true, logs = listOf(log))
        val restored = Backup.decode(Backup.encode(BackupData(listOf(Peptide("p", "Example")), listOf(e), listOf(archived.row()))))
        assertEquals(archived, ProtocolCodec.decode(JSONObject(restored.protocols.single().document)))
        val old = ProtocolCodec.encode(p).apply { remove("archived") }
        assertFalse(ProtocolCodec.decode(old).archived)
        assertThrows(IllegalArgumentException::class.java) { ProtocolCodec.decode(ProtocolCodec.encode(archived.copy(status = "active"))) }
        assertFalse(archived.duplicate(today, "Copy").archived)
    }
    private val today = LocalDate.of(2026, 9, 8)
    private val c = Calculator.calculate("1", "2", "1", "1", "100", "1")
    private val e = Entry(peptideId = "p", peptideName = "Example", dose = "1", mass = "2", water = "1", capacity = "1", scale = "100", tick = "1",
        concentration = c.concentration.toPlainString(), volume = c.volume.toPlainString(), units = c.units.toPlainString(), doses = c.doses.toPlainString(), remainder = c.remainder.toPlainString())
    private val item = ProtocolItem(entry = e, from = today.toString(), days = listOf(2,4), times = listOf("08:00", "20:00"))
    private val p = Protocol(name = "Test", start = today.toString(), status = "active", items = listOf(item))

    @Test fun scheduleRevisionPreservesPastAndSnapshot() {
        val log = ProtocolLog(item.id, today.toString(), "08:00", "done", "0.9", "Actual differs from plan")
        val replacement = item.copy(id = "new", from = today.plusDays(1).toString(), times = listOf("10:00"), entry = e.copy(dose = "0.5"))
        // Recompute the replacement snapshot, just as the editor does.
        val nc = replacement.entry.calculation()
        val updated = replacement.copy(entry = replacement.entry.copy(volume = nc.volume.toPlainString(), units = nc.units.toPlainString(), doses = nc.doses.toPlainString(), remainder = nc.remainder.toPlainString()))
        val revised = p.copy(logs = listOf(log)).revise(updated, item, today)
        assertEquals(listOf(log), revised.logs)
        assertEquals(listOf(item.id), revised.items.filter { it.occurs(today) }.map { it.id })
        assertEquals(listOf("new"), revised.items.filter { it.occurs(today.plusDays(2)) }.map { it.id })
        assertEquals("1", revised.items.first().entry.dose)
        assertEquals(revised, ProtocolCodec.decode(ProtocolCodec.encode(revised)))
        val ended = p.copy(logs = listOf(log)).endItem(item, today)
        assertEquals(listOf(log), ended.logs)
        assertTrue(ended.items.single().occurs(today))
        assertFalse(ended.items.single().occurs(today.plusDays(2)))
        assertThrows(IllegalArgumentException::class.java) { p.revise(updated.copy(from = today.toString()), item, today) }
    }
    @Test fun backupRoundTripAndOldFormatCompatibility() {
        val row = p.row()
        val data = BackupData(listOf(Peptide("p", "Example")), listOf(e), listOf(row))
        val restored = Backup.decode(Backup.encode(data))
        assertEquals(p, ProtocolCodec.decode(JSONObject(restored.protocols.single().document)))
        val legacy = JSONObject(Backup.encode(data)).put("version", 1).apply { remove("protocols") }
        assertTrue(Backup.decode(legacy.toString()).protocols.isEmpty())
        assertEquals(listOf(e), Backup.decode(legacy.toString()).entries)
    }
    @Test fun rejectsInvalidTimesDuplicateCompletionsAndTamperedSnapshot() {
        assertThrows(java.time.DateTimeException::class.java) { ProtocolCodec.time("24:00") }
        val log = ProtocolLog(item.id, today.toString(), "08:00", "done", "1")
        assertThrows(IllegalArgumentException::class.java) { ProtocolCodec.decode(ProtocolCodec.encode(p.copy(logs = listOf(log,log)))) }
        val json = ProtocolCodec.encode(p)
        json.getJSONArray("items").getJSONObject(0).getJSONObject("entry").put("volume", "0.9")
        assertThrows(IllegalArgumentException::class.java) { ProtocolCodec.decode(json) }
        assertThrows(IllegalArgumentException::class.java) {
            val invalid = p.copy(logs = listOf(log.copy(day = today.plusDays(1).toString())))
            ProtocolCodec.decode(ProtocolCodec.encode(invalid))
        }
    }
    @Test fun duplicateHasNewIdentityAndNoCompletions() {
        val copy = p.copy(logs = listOf(ProtocolLog(item.id, today.toString(), "08:00", "skipped"))).duplicate(today.plusDays(7), "Copy")
        assertNotEquals(p.id, copy.id)
        assertNotEquals(item.id, copy.items.single().id)
        assertTrue(copy.logs.isEmpty())
        assertEquals("draft", copy.status)
        assertEquals(today.plusDays(7).toString(), copy.start)
    }
    @Test fun calendarStatusShowsPlannedDoneAndSkippedOccurrences() {
        val threeTimes = item.copy(times = listOf("08:00", "12:00", "20:00"))
        val calendar = p.copy(items = listOf(threeTimes), logs = listOf(
            ProtocolLog(threeTimes.id, today.toString(), "08:00", "done", "1"),
            ProtocolLog(threeTimes.id, today.toString(), "20:00", "skipped")
        ))
        assertEquals(ProtocolDayStatus(planned = true, done = true, skipped = true), calendar.dayStatus(today))
        assertEquals(ProtocolDayStatus(), calendar.dayStatus(today.plusDays(1)))
        val month = calendar.monthStatuses(YearMonth.from(today))
        assertEquals(30, month.size)
        assertEquals(calendar.dayStatus(today), month.getValue(today))
        assertEquals(ProtocolDayStatus(), month.getValue(today.minusDays(1)))
    }
    @Test fun monthlyCalendarRespectsLeapDayAndScheduleEnd() {
        val leapDay = LocalDate.of(2024, 2, 29)
        val calendar = p.copy(start = "2024-02-28", items = listOf(item.copy(
            from = "2024-02-28", to = "2024-02-29", days = (1..7).toList()
        )))
        val february = calendar.monthStatuses(YearMonth.from(leapDay))
        assertEquals(29, february.size)
        assertTrue(february.getValue(leapDay).planned)
        assertFalse(february.getValue(leapDay.minusDays(2)).planned)
        assertTrue(calendar.monthStatuses(YearMonth.of(2024, 3)).values.all { it == ProtocolDayStatus() })
    }
}
