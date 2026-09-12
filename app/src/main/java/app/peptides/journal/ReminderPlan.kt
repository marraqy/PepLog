package app.peptides.journal

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class ReminderOccurrence(val protocol: Protocol, val item: ProtocolItem, val day: LocalDate, val time: String, val at: Long)

object ReminderPlan {
    private fun at(day: LocalDate, time: String, zone: ZoneId) = day.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    fun next(protocols: List<Protocol>, after: Long, zone: ZoneId): Long? {
        val today = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
        return protocols.asSequence().filter { it.status == "active" }.flatMap { p ->
            p.items.asSequence().flatMap { item ->
                val first = maxOf(today, LocalDate.parse(item.from))
                (0L..7L).asSequence().map { first.plusDays(it) }.filter(item::occurs).flatMap { day ->
                    item.times.asSequence().map { at(day, it, zone) }.filter { it > after }
                }
            }
        }.minOrNull()
    }

    // Late alarms are useful for up to an hour; never replay an entire missed day.
    fun due(protocols: List<Protocol>, from: Long, now: Long, zone: ZoneId): List<ReminderOccurrence> {
        if (from > now || now - from > 3_600_000) return emptyList()
        val first = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        val last = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.toList()
        return protocols.filter { it.status == "active" }.flatMap { p ->
            val logged = p.logs.map { it.key }.toSet()
            p.items.flatMap { item -> days.filter(item::occurs).flatMap { day -> item.times.mapNotNull { time ->
                val epoch = at(day, time, zone)
                if (epoch in from..now && "${item.id}/$day/$time" !in logged) ReminderOccurrence(p, item, day, time, epoch) else null
            } } }
        }.sortedBy { it.at }
    }
}
