package app.peptides.journal

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID

@Entity(tableName = "protocols")
data class ProtocolRow(@PrimaryKey val id: String, val document: String)

data class ProtocolItem(
    val id: String = UUID.randomUUID().toString(), val group: String = id,
    val entry: Entry, val from: String, val to: String = "",
    val days: List<Int>, val times: List<String>, val notes: String = ""
) {
    fun occurs(date: LocalDate) = date >= LocalDate.parse(from) &&
        (to.isBlank() || date <= LocalDate.parse(to)) && date.dayOfWeek.value in days
}

data class ProtocolLog(
    val itemId: String, val day: String, val time: String, val status: String,
    val actualMg: String = "", val notes: String = "", val recorded: Long = System.currentTimeMillis()
) { val key get() = "$itemId/$day/$time" }

data class ProtocolDayStatus(val planned: Boolean = false, val done: Boolean = false, val skipped: Boolean = false)

data class Protocol(
    val id: String = UUID.randomUUID().toString(), val name: String, val notes: String = "",
    val start: String, val end: String = "", val status: String = "draft",
    val items: List<ProtocolItem> = emptyList(), val logs: List<ProtocolLog> = emptyList(),
    val archived: Boolean = false
) {
    fun row() = ProtocolRow(id, ProtocolCodec.encode(this).toString())
    fun currentItems(today: LocalDate) = items.filter { it.to.isBlank() || LocalDate.parse(it.to) >= today }
        .groupBy { it.group }.values.map { versions -> versions.maxBy { it.from } }

    fun endItem(item: ProtocolItem, today: LocalDate) = copy(items = items.flatMap {
        if (it.id != item.id) listOf(it)
        else if (LocalDate.parse(it.from) > today) emptyList()
        else listOf(it.copy(to = minOf(it.to.ifBlank { today.toString() }, today.toString())))
    })

    // Keep prior schedule versions and their calculation snapshots for historical dates.
    fun revise(item: ProtocolItem, previous: ProtocolItem?, today: LocalDate): Protocol {
        val kept = if (previous == null) items else items.flatMap {
            if (it.id != previous.id) listOf(it)
            else if (LocalDate.parse(it.from) > today) emptyList()
            else listOf(it.copy(to = minOf(it.to.ifBlank { today.toString() }, today.toString())))
        }
        require(previous == null || LocalDate.parse(item.from) > today)
        return copy(items = kept + item)
    }

    fun duplicate(today: LocalDate, newName: String): Protocol {
        val active = items.groupBy { it.group }.values.map { versions -> versions.maxBy { it.from } }
        val newEnd = if (end.isBlank()) "" else today.plusDays(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(start), LocalDate.parse(end))).toString()
        return Protocol(name = newName, notes = notes, start = today.toString(), end = newEnd, items = active.map {
            ProtocolItem(entry = it.entry, from = today.toString(), to = newEnd, days = it.days, times = it.times, notes = it.notes)
        })
    }
}

fun Protocol.monthStatuses(month: YearMonth): Map<LocalDate, ProtocolDayStatus> {
    val logsByKey = logs.associateBy { it.key }
    return (1..month.lengthOfMonth()).associate { day ->
        val date = month.atDay(day)
        date to dayStatus(date, logsByKey)
    }
}

fun Protocol.dayStatus(date: LocalDate): ProtocolDayStatus = dayStatus(date, logs.associateBy { it.key })

private fun Protocol.dayStatus(date: LocalDate, logsByKey: Map<String, ProtocolLog>): ProtocolDayStatus {
    var planned = false; var done = false; var skipped = false
    items.filter { it.occurs(date) }.forEach { item -> item.times.forEach { time ->
        when (logsByKey["${item.id}/$date/$time"]?.status) {
            "done" -> done = true
            "skipped" -> skipped = true
            else -> planned = true
        }
    } }
    return ProtocolDayStatus(planned, done, skipped)
}

object ProtocolCodec {
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
    private fun JSONObject.text(key: String, max: Int = 200) = getString(key).also { require(it.length <= max) }
    fun date(value: String): LocalDate {
        require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value))
        return LocalDate.parse(value).also { require(it.year in 2000..2100) }
    }
    fun time(value: String): String {
        require(Regex("[0-9]{2}:[0-9]{2}").matches(value))
        LocalTime.parse(value)
        return value
    }
    fun entryJson(e: Entry): JSONObject = JSONObject(Backup.encode(BackupData(listOf(Peptide(e.peptideId, e.peptideName)), listOf(e))))
        .getJSONArray("records").getJSONObject(0)
    fun entry(json: JSONObject): Entry {
        // Decode only a record-shaped snapshot, using the same arithmetic validation as the journal.
        val wrapper = JSONObject().put("app", "peptides-journal").put("version", 1)
            .put("peptides", JSONArray().put(JSONObject().put("id", json.getString("peptideId")).put("name", json.getString("peptideName"))
                .put("aliases", "").put("source", "").put("active", true)))
            .put("records", JSONArray().put(json))
        return Backup.decode(wrapper.toString()).entries.single()
    }
    fun encode(p: Protocol): JSONObject = JSONObject().put("id", p.id).put("name", p.name).put("notes", p.notes)
        .put("start", p.start).put("end", p.end).put("status", p.status).put("archived", p.archived)
        .put("items", JSONArray().apply { p.items.forEach { i -> put(JSONObject().put("id", i.id).put("group", i.group)
            .put("entry", entryJson(i.entry)).put("from", i.from).put("to", i.to).put("days", JSONArray(i.days))
            .put("times", JSONArray(i.times)).put("notes", i.notes)) } })
        .put("logs", JSONArray().apply { p.logs.forEach { l -> put(JSONObject().put("itemId", l.itemId).put("day", l.day)
            .put("time", l.time).put("status", l.status).put("actualMg", l.actualMg).put("notes", l.notes).put("recorded", l.recorded)) } })

    fun decode(json: JSONObject): Protocol {
        val rows = json.getJSONArray("items"); val events = json.getJSONArray("logs")
        require(rows.length() <= 500 && events.length() <= 20000)
        val p = Protocol(json.text("id"), json.text("name"), json.text("notes", 4000), json.text("start"), json.text("end"), json.text("status"),
            (0 until rows.length()).map { rows.getJSONObject(it).run {
                val ds = getJSONArray("days"); require(ds.length() in 1..7)
                val ts = getJSONArray("times"); require(ts.length() in 1..12)
                ProtocolItem(text("id"), text("group"), entry(getJSONObject("entry")), text("from"), text("to"),
                    (0 until ds.length()).map { n -> ds.getInt(n) }, ts.strings(), text("notes", 4000))
            } }, (0 until events.length()).map { events.getJSONObject(it).run {
                ProtocolLog(text("itemId"), text("day"), text("time"), text("status"), text("actualMg"), text("notes", 4000), getLong("recorded"))
            } }, if (json.has("archived")) json.getBoolean("archived") else false)
        require(p.id.isNotBlank() && p.name.isNotBlank())
        require(p.status in listOf("draft", "active", "paused", "completed"))
        require(!p.archived || p.status == "completed")
        val start = date(p.start); val end = p.end.takeIf { it.isNotBlank() }?.let(::date)
        require(end == null || end >= start)
        require(p.items.map { it.id }.distinct().size == p.items.size)
        p.items.forEach { i ->
            require(i.id.isNotBlank() && i.group.isNotBlank())
            require(date(i.from) >= start && (end == null || date(i.from) <= end))
            require(i.to.isBlank() && end == null || i.to.isNotBlank() && date(i.to) >= date(i.from) && (end == null || date(i.to) <= end))
            require(i.days.all { it in 1..7 } && i.days.distinct().size == i.days.size)
            require(i.times.distinct().size == i.times.size); i.times.forEach(::time)
        }
        p.items.groupBy { it.group }.values.forEach { versions ->
            versions.sortedBy { it.from }.zipWithNext().forEach { (a,b) -> require(a.to.isNotBlank() && date(a.to) < date(b.from)) }
        }
        require(p.logs.map { it.key }.distinct().size == p.logs.size)
        p.logs.forEach { l ->
            val item = p.items.single { it.id == l.itemId }
            require(item.occurs(date(l.day)) && time(l.time) in item.times)
            require(l.status in listOf("done", "skipped") && l.recorded > 0)
            if (l.status == "done") Mass.mg(l.actualMg) else require(l.actualMg.isBlank())
        }
        return p
    }
}

suspend fun JournalDb.saveProtocol(p: Protocol) = withTransaction {
    val row = p.row()
    ProtocolCodec.decode(JSONObject(row.document))
    val existing = dao().protocol(p.id)
    if (existing != null) {
        val old = ProtocolCodec.decode(JSONObject(existing.document))
        require(old.logs.all { log -> log in p.logs })
    }
    dao().put(row)
}

suspend fun JournalDb.logProtocol(id: String, log: ProtocolLog) = withTransaction {
    val p = ProtocolCodec.decode(JSONObject(requireNotNull(dao().protocol(id)).document))
    require(p.status == "active" && ProtocolCodec.date(log.day) <= LocalDate.now())
    require(p.logs.none { it.key == log.key })
    saveProtocol(p.copy(logs = p.logs + log))
}

suspend fun JournalDb.deleteEmptyProtocol(id: String) = withTransaction {
    val current = ProtocolCodec.decode(JSONObject(requireNotNull(dao().protocol(id)).document))
    require(current.logs.isEmpty()) { "Protocols with records must be archived" }
    dao().deleteProtocol(id)
}

suspend fun JournalDb.setProtocolArchived(id: String, archived: Boolean) = withTransaction {
    val current = ProtocolCodec.decode(JSONObject(requireNotNull(dao().protocol(id)).document))
    saveProtocol(current.copy(archived = archived, status = if (archived) "completed" else "paused"))
}
