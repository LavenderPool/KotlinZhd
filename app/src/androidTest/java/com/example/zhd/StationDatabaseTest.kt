package com.example.zhd

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StationDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "station-test.db"
    private lateinit var db: StationDatabase
    @Before fun setUp() { context.deleteDatabase(name); db = StationDatabase(context, name) }
    @After fun tearDown() { db.close(); context.deleteDatabase(name) }

    @Test fun sampleDataHasLinkedRecords() {
        sections.forEach { assertTrue("${it.table} has fewer than 10 records", db.rows(it.table).size >= 10) }
        assertEquals(39, db.available("1"))
    }
    @Test fun bookingReservesSeatAndCancellationFreesIt() {
        val ticket = mapOf("schedule_id" to "1", "employee_id" to "1", "passenger" to "Иван Иванов", "passport" to "1234 123456", "seat" to "2", "price" to "1350", "status" to "Бронь")
        db.save(sections.last(), ticket, owner = "buyer")
        assertEquals(38, db.available("1"))
        try { db.save(sections.last(), ticket, owner = "buyer"); fail("Duplicate seat was allowed") } catch (_: IllegalArgumentException) { }
        val saved = db.rows("tickets", "owner = ?", arrayOf("buyer")).single()
        db.save(sections.last(), saved + ("status" to "Отменён"), saved["id"])
        assertEquals(39, db.available("1"))
        db.save(sections.last(), ticket, owner = "buyer")
        db.close()
        db = StationDatabase(context, name)
        assertEquals(2, db.rows("tickets", "owner = ?", arrayOf("buyer")).size)
    }
    @Test fun referencedTrainCannotBeDeleted() {
        try { db.delete("trains", "1"); fail("Referenced train was deleted") } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(1, db.rows("trains", "id = ?", arrayOf("1")).size)
    }
}
