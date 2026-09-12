package app.peptides.journal

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Entity(tableName = "peptides")
data class Peptide(@PrimaryKey val id: String = UUID.randomUUID().toString(), val name: String, val aliases: String = "", val source: String = "", val active: Boolean = true,
    @ColumnInfo(defaultValue = "'[]'") val composition: String = "[]")

@Entity(tableName = "records")
data class Entry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val peptideId: String, val peptideName: String, val title: String = "", val notes: String = "",
    val dose: String, val mass: String, val water: String, val capacity: String, val scale: String, val tick: String,
    val created: Long = System.currentTimeMillis(), val updated: Long = System.currentTimeMillis(),
    val concentration: String, val volume: String, val units: String, val doses: String, val remainder: String,
    val formulaVersion: Int = 1,
    @ColumnInfo(defaultValue = "'mg'") val doseUnit: String = "mg",
    @ColumnInfo(defaultValue = "'mg'") val massUnit: String = "mg",
    @ColumnInfo(defaultValue = "'[]'") val composition: String = "[]",
    @ColumnInfo(defaultValue = "-1") val reference: Int = -1
) {
    fun calculation(): Calculation {
        require(doseUnit in listOf("mg", "mcg") && massUnit in listOf("mg", "mcg"))
        val parts = Blend.decode(composition)
        if (parts.isEmpty()) require(reference == -1)
        else require(Blend.basis(parts, reference).compareTo(Mass.mg(mass)) == 0)
        return Calculator.calculate(dose, mass, water, capacity, scale, tick)
    }
}

enum class DataLimit(val maximum: Int, val en: String, val pt: String) {
    CATALOG(5000, "Catalog capacity reached (5,000 peptides).", "Capacidade do catálogo atingida (5.000 peptídeos)."),
    RECORDS(10000, "Journal capacity reached (10,000 records).", "Capacidade do caderno atingida (10.000 registros)."),
    PROTOCOLS(500, "Protocol capacity reached (500 protocols).", "Capacidade atingida (500 protocolos)."),
    BYTES(5 * 1024 * 1024, "Backup exceeds the 5 MiB capacity.", "Backup excede a capacidade de 5 MiB.");

    fun check(size: Int) { if (size > maximum) throw DataCapacityException(this) }
}

class DataCapacityException(val limit: DataLimit) : IllegalArgumentException(limit.en)

@Dao
abstract class JournalDao {
    @Query("SELECT COUNT(*) FROM peptides WHERE id != :id") abstract suspend fun otherPeptides(id: String): Int
    @Query("SELECT COUNT(*) FROM records WHERE id != :id") abstract suspend fun otherEntries(id: String): Int
    @Query("SELECT COUNT(*) FROM protocols WHERE id != :id") abstract suspend fun otherProtocols(id: String): Int
    @Transaction open suspend fun put(peptide: Peptide) {
        DataLimit.CATALOG.check(otherPeptides(peptide.id) + 1)
        insert(peptide)
    }
    @Transaction open suspend fun put(entry: Entry) {
        DataLimit.RECORDS.check(otherEntries(entry.id) + 1)
        insert(entry)
    }
    @Transaction open suspend fun put(protocol: ProtocolRow) {
        DataLimit.PROTOCOLS.check(otherProtocols(protocol.id) + 1)
        insert(protocol)
    }
    @Query("SELECT * FROM protocols ORDER BY id") abstract fun protocols(): Flow<List<ProtocolRow>>
    @Query("SELECT * FROM protocols ORDER BY id") abstract suspend fun allProtocols(): List<ProtocolRow>
    @Query("SELECT * FROM protocols WHERE id = :id") abstract suspend fun protocol(id: String): ProtocolRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE) protected abstract suspend fun insert(protocol: ProtocolRow)
    @Query("DELETE FROM protocols") abstract suspend fun clearProtocols()
    @Query("DELETE FROM protocols WHERE id = :id") abstract suspend fun deleteProtocol(id: String)
    @Query("SELECT * FROM peptides ORDER BY name COLLATE NOCASE") abstract fun peptides(): Flow<List<Peptide>>
    @Query("SELECT * FROM records ORDER BY created DESC") abstract fun entries(): Flow<List<Entry>>
    @Query("SELECT * FROM peptides ORDER BY name") abstract suspend fun allPeptides(): List<Peptide>
    @Query("SELECT * FROM records ORDER BY created DESC") abstract suspend fun allEntries(): List<Entry>
    @Insert(onConflict = OnConflictStrategy.REPLACE) protected abstract suspend fun insert(peptide: Peptide)
    @Insert(onConflict = OnConflictStrategy.REPLACE) protected abstract suspend fun insert(entry: Entry)
    @Query("DELETE FROM records WHERE id = :id") abstract suspend fun delete(id: String)
    @Query("DELETE FROM records") abstract suspend fun clearEntries()
    @Query("DELETE FROM peptides") abstract suspend fun clearPeptides()
}

@Database(entities = [Peptide::class, Entry::class, ProtocolRow::class], version = 3, exportSchema = true)
abstract class JournalDb : RoomDatabase() {
    abstract fun dao(): JournalDao
    companion object {
        @Volatile private var instance: JournalDb? = null
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS protocols (id TEXT NOT NULL PRIMARY KEY, document TEXT NOT NULL)")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE peptides ADD COLUMN composition TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE records ADD COLUMN doseUnit TEXT NOT NULL DEFAULT 'mg'")
                db.execSQL("ALTER TABLE records ADD COLUMN massUnit TEXT NOT NULL DEFAULT 'mg'")
                db.execSQL("ALTER TABLE records ADD COLUMN composition TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE records ADD COLUMN reference INTEGER NOT NULL DEFAULT -1")
            }
        }
        fun get(context: Context): JournalDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, JournalDb::class.java, "journal.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
    suspend fun seed(context: Context) = withTransaction {
        val existing = dao().allPeptides()
            val rows = JSONArray(context.assets.open("peptides.json").bufferedReader().use { it.readText() })
            val defaults = (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                Peptide(row.getString("id"), row.getString("name"), row.optString("aliases"), row.getString("source"))
            }
        CatalogDefaults.missing(existing, defaults).take((DataLimit.CATALOG.maximum - existing.size).coerceAtLeast(0)).forEach { dao().put(it) }
    }
}

data class BackupData(val peptides: List<Peptide>, val entries: List<Entry>, val protocols: List<ProtocolRow> = emptyList())
object Backup {
    val MAX_BYTES = DataLimit.BYTES.maximum
    fun encode(data: BackupData): String = JSONObject().put("app", "peptides-journal").put("version", 3)
        .put("protocols", JSONArray().apply { data.protocols.forEach { put(JSONObject(it.document)) } })
        .put("peptides", JSONArray().apply { data.peptides.forEach { p -> put(JSONObject()
            .put("id", p.id).put("name", p.name).put("aliases", p.aliases).put("source", p.source).put("active", p.active).put("composition", JSONArray(p.composition))) } })
        .put("records", JSONArray().apply { data.entries.forEach { e -> put(JSONObject()
            .put("id", e.id).put("peptideId", e.peptideId).put("peptideName", e.peptideName).put("title", e.title).put("notes", e.notes)
            .put("dose", e.dose).put("mass", e.mass).put("water", e.water).put("capacity", e.capacity).put("scale", e.scale).put("tick", e.tick)
            .put("created", e.created).put("updated", e.updated).put("concentration", e.concentration).put("volume", e.volume)
            .put("units", e.units).put("doses", e.doses).put("remainder", e.remainder).put("formulaVersion", e.formulaVersion)
            .put("doseUnit", e.doseUnit).put("massUnit", e.massUnit).put("composition", JSONArray(e.composition)).put("reference", e.reference)) } }).toString(2)

    fun decode(text: String): BackupData {
        DataLimit.BYTES.check(text.toByteArray(Charsets.UTF_8).size)
        val root = JSONObject(text)
        require(root.getString("app") == "peptides-journal" && root.getInt("version") in 1..3)
        val ps = root.getJSONArray("peptides"); val es = root.getJSONArray("records")
        DataLimit.CATALOG.check(ps.length()); DataLimit.RECORDS.check(es.length())
        require(ps.length() > 0)
        fun JSONObject.field(key: String, max: Int = 200): String = getString(key).also { require(it.length <= max) }
        fun JSONObject.id(key: String): String = field(key).also { require(it.isNotBlank()) }
        val peptides = (0 until ps.length()).map { i -> ps.getJSONObject(i).run {
            val composition = if (has("composition")) getJSONArray("composition").toString() else "[]"
            Blend.decode(composition)
            Peptide(id("id"), id("name"), field("aliases"), field("source", 1000), getBoolean("active"), composition)
        } }
        require(peptides.map { it.id }.distinct().size == peptides.size)
        require(peptides.map { it.name.trim().lowercase(java.util.Locale.ROOT) }.distinct().size == peptides.size)
        val entries = (0 until es.length()).map { i -> es.getJSONObject(i).run {
            val e = Entry(id("id"), id("peptideId"), id("peptideName"), field("title"), field("notes", 4000),
                field("dose"), field("mass"), field("water"), field("capacity"), field("scale"), field("tick"),
                getLong("created"), getLong("updated"), field("concentration"), field("volume"), field("units"), field("doses"), field("remainder"), getInt("formulaVersion"),
                if (has("doseUnit")) getString("doseUnit") else "mg", if (has("massUnit")) getString("massUnit") else "mg",
                if (has("composition")) getJSONArray("composition").toString() else "[]", if (has("reference")) getInt("reference") else -1)
            require(e.formulaVersion == 1 && e.created > 0 && e.updated >= e.created)
            require(peptides.any { it.id == e.peptideId })
            val c = e.calculation()
            require(listOf(e.concentration, e.volume, e.units, e.doses, e.remainder).map { it.toBigDecimal() }
                .zip(listOf(c.concentration, c.volume, c.units, c.doses, c.remainder)).all { (a,b) -> a.compareTo(b) == 0 })
            e
        } }
        require(entries.map { it.id }.distinct().size == entries.size)
        val protocols = if (root.getInt("version") == 1) emptyList() else {
            val rows = root.getJSONArray("protocols")
            DataLimit.PROTOCOLS.check(rows.length())
            (0 until rows.length()).map { ProtocolCodec.decode(rows.getJSONObject(it)).row() }
        }
        require(protocols.map { it.id }.distinct().size == protocols.size)
        return BackupData(peptides, entries, protocols)
    }
    fun export(data: BackupData): String {
        DataLimit.CATALOG.check(data.peptides.size)
        DataLimit.RECORDS.check(data.entries.size)
        DataLimit.PROTOCOLS.check(data.protocols.size)
        return encode(data).also { text ->
            decode(text)
            require(data.protocols.all { it.id == JSONObject(it.document).getString("id") })
        }
    }
    suspend fun export(db: JournalDb): String = db.withTransaction { export(BackupData(db.dao().allPeptides(), db.dao().allEntries(), db.dao().allProtocols())) }
    suspend fun restore(db: JournalDb, data: BackupData) = db.withTransaction {
        db.dao().clearProtocols(); db.dao().clearEntries(); db.dao().clearPeptides()
        data.peptides.forEach { db.dao().put(it) }; data.entries.forEach { db.dao().put(it) }
        data.protocols.forEach { db.dao().put(it) }
    }
}
