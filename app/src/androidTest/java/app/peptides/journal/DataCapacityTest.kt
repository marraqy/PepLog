package app.peptides.journal

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataCapacityTest {
    @Test fun capacityIsAtomicAndUpdatesRemainPossible() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, JournalDb::class.java).build()
        try {
            val dao = db.dao()
            db.withTransaction {
                repeat(DataLimit.CATALOG.maximum - 1) { dao.put(Peptide("p$it", "Peptide $it")) }
            }
            val results = listOf("last-a", "last-b").map { id -> async {
                runCatching { dao.put(Peptide(id, id)) }
            } }.awaitAll()
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(DataLimit.CATALOG, (results.single { it.isFailure }.exceptionOrNull() as DataCapacityException).limit)
            dao.put(Peptide("p0", "Edited", active = false))
            db.seed(context)
            assertEquals(DataLimit.CATALOG.maximum, dao.allPeptides().size)
            assertEquals("Edited", dao.allPeptides().single { it.id == "p0" }.name)

            val c = Calculator.calculate("1", "2", "1", "1", "100", "1")
            val entry = Entry(id = "e0", peptideId = "p0", peptideName = "Edited", dose = "1", mass = "2", water = "1",
                capacity = "1", scale = "100", tick = "1", concentration = c.concentration.toPlainString(),
                volume = c.volume.toPlainString(), units = c.units.toPlainString(), doses = c.doses.toPlainString(), remainder = c.remainder.toPlainString())
            db.withTransaction { repeat(DataLimit.RECORDS.maximum) { dao.put(entry.copy(id = "e$it")) } }
            val failure = runCatching { dao.put(entry.copy(id = "overflow")) }.exceptionOrNull()
            assertEquals(DataLimit.RECORDS, (failure as DataCapacityException).limit)
            dao.put(entry.copy(title = "Edited"))
            dao.delete("e1")
            dao.put(entry.copy(id = "replacement"))
            assertEquals(DataLimit.RECORDS.maximum, dao.allEntries().size)

            db.withTransaction { repeat(DataLimit.PROTOCOLS.maximum) { dao.put(Protocol(id = "r$it", name = "Protocol $it", start = "2026-09-10").row()) } }
            val protocolFailure = runCatching { dao.put(Protocol(id = "overflow", name = "Overflow", start = "2026-09-10").row()) }.exceptionOrNull()
            assertEquals(DataLimit.PROTOCOLS, (protocolFailure as DataCapacityException).limit)
            dao.put(Protocol(id = "r0", name = "Edited", start = "2026-09-10").row())
            assertEquals(DataLimit.PROTOCOLS.maximum, dao.allProtocols().size)
        } finally { db.close() }
    }
}
