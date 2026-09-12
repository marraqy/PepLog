package app.peptides.journal

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ReminderPlanTest {
    private val utc = ZoneId.of("UTC")
    private fun epoch(text: String) = Instant.parse(text).toEpochMilli()
    private fun plan(from: String = "2026-09-09", to: String = "", times: List<String> = listOf("08:00", "20:00")): Protocol {
        val c = Calculator.calculate("1", "2", "1", "1", "100", "1")
        val e = Entry(peptideId = "p", peptideName = "Example", dose = "1", mass = "2", water = "1", capacity = "1", scale = "100", tick = "1",
            concentration = c.concentration.toString(), volume = c.volume.toString(), units = c.units.toString(), doses = c.doses.toString(), remainder = c.remainder.toString())
        return Protocol(name = "Example", start = from, end = to, status = "active", items = listOf(ProtocolItem(entry = e, from = from, to = to, days = listOf(3), times = times)))
    }
    @Test fun nextUsesWeekdaysAndFutureStartsWithoutAnEndlessSearch() {
        val p = plan()
        assertEquals(epoch("2026-09-09T20:00:00Z"), ReminderPlan.next(listOf(p), epoch("2026-09-09T08:00:00Z"), utc))
        assertEquals(epoch("2026-09-16T08:00:00Z"), ReminderPlan.next(listOf(p), epoch("2026-09-09T20:00:00Z"), utc))
        assertEquals(epoch("2027-09-08T08:00:00Z"), ReminderPlan.next(listOf(plan("2027-09-01")), epoch("2027-09-01T20:00:00Z"), utc))
        assertNull(ReminderPlan.next(listOf(plan(to = "2026-09-09")), epoch("2026-09-09T20:00:00Z"), utc))
    }
    @Test fun onlyActiveUnrecordedItemsNotifyAndLateAlarmsAreBounded() {
        val p = plan(times = listOf("08:00", "08:30"))
        val start = epoch("2026-09-09T08:00:00Z")
        assertEquals(2, ReminderPlan.due(listOf(p), start, start + 1_800_000, utc).size)
        val logged = p.copy(logs = listOf(ProtocolLog(p.items.single().id, p.start, "08:00", "skipped")))
        assertEquals(listOf("08:30"), ReminderPlan.due(listOf(logged), start, start + 1_800_000, utc).map { it.time })
        for (status in listOf("paused", "draft", "completed")) {
            assertNull(ReminderPlan.next(listOf(p.copy(status = status)), start, utc))
            assertTrue(ReminderPlan.due(listOf(p.copy(status = status)), start, start, utc).isEmpty())
        }
        assertTrue(ReminderPlan.due(listOf(p), start, start + 3_600_001, utc).isEmpty())
        assertTrue(ReminderPlan.due(listOf(p), start, start - 1, utc).isEmpty())
    }
    @Test fun localTimezoneAndDaylightSavingHaveOneOccurrence() {
        val zone = ZoneId.of("America/New_York")
        val p = plan("2026-03-08", times = listOf("02:30")).let { it.copy(items = it.items.map { i -> i.copy(days = listOf(7)) }) }
        val trigger = epoch("2026-03-08T07:30:00Z")
        assertEquals(trigger, ReminderPlan.next(listOf(p), epoch("2026-03-08T00:00:00Z"), zone))
        assertEquals("02:30", ReminderPlan.due(listOf(p), trigger, trigger, zone).single().time)
        val overlap = plan("2026-11-01", times = listOf("01:30")).let { it.copy(items = it.items.map { i -> i.copy(days = listOf(7)) }) }
        assertEquals(epoch("2026-11-01T05:30:00Z"), ReminderPlan.next(listOf(overlap), epoch("2026-11-01T00:00:00Z"), zone))
        assertTrue(ReminderPlan.due(listOf(overlap), epoch("2026-11-01T06:30:00Z"), epoch("2026-11-01T06:30:00Z"), zone).isEmpty())
    }
}
