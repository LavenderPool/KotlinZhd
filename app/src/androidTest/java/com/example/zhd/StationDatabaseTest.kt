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
    @Test fun existingDatabaseIsMigratedWithoutLosingData() {
        db.close()
        context.deleteDatabase(name)
        val old = context.openOrCreateDatabase(name, 0, null)
        old.execSQL("CREATE TABLE trains (id INTEGER PRIMARY KEY, number TEXT NOT NULL UNIQUE, category TEXT NOT NULL, seats INTEGER NOT NULL CHECK(seats > 0))")
        old.execSQL("CREATE TABLE stations (id INTEGER PRIMARY KEY, name TEXT NOT NULL, city TEXT NOT NULL)")
        old.execSQL("CREATE TABLE employees (id INTEGER PRIMARY KEY, name TEXT NOT NULL, position TEXT NOT NULL)")
        old.execSQL("CREATE TABLE schedules (id INTEGER PRIMARY KEY, train_id INTEGER NOT NULL REFERENCES trains(id) ON DELETE RESTRICT, departure_id INTEGER NOT NULL REFERENCES stations(id) ON DELETE RESTRICT, arrival_id INTEGER NOT NULL REFERENCES stations(id) ON DELETE RESTRICT, departure TEXT NOT NULL, arrival TEXT NOT NULL, price INTEGER NOT NULL, CHECK(departure_id != arrival_id))")
        old.execSQL("CREATE TABLE stops (id INTEGER PRIMARY KEY, schedule_id INTEGER NOT NULL REFERENCES schedules(id) ON DELETE RESTRICT, station_id INTEGER NOT NULL REFERENCES stations(id) ON DELETE RESTRICT, position INTEGER NOT NULL, arrival TEXT NOT NULL, departure TEXT NOT NULL, UNIQUE(schedule_id, position))")
        old.execSQL("CREATE TABLE tickets (id INTEGER PRIMARY KEY, schedule_id INTEGER NOT NULL REFERENCES schedules(id) ON DELETE RESTRICT, employee_id INTEGER NOT NULL REFERENCES employees(id) ON DELETE RESTRICT, passenger TEXT NOT NULL, passport TEXT NOT NULL, seat INTEGER NOT NULL, price INTEGER NOT NULL, status TEXT NOT NULL, owner TEXT NOT NULL DEFAULT 'employee')")
        old.execSQL("CREATE UNIQUE INDEX occupied_seat ON tickets(schedule_id, seat) WHERE status != 'Отменён'")
        old.execSQL("INSERT INTO trains VALUES (1, '101А', 'Скорый', 40)")
        old.execSQL("INSERT INTO stations VALUES (1, 'Вокзал', 'Москва'), (2, 'Вокзал', 'Казань')")
        old.execSQL("INSERT INTO employees VALUES (1, 'Иванов', 'Кассир')")
        old.execSQL("INSERT INTO schedules VALUES (1, 1, 1, 2, '2026-12-01 08:00', '2026-12-01 14:00', 1200)")
        old.execSQL("INSERT INTO tickets VALUES (1, 1, 1, 'Пассажир', '1234', 1, 1200, 'Куплен', 'buyer')")
        old.version = 1
        old.close()

        db = StationDatabase(context, name)
        assertEquals("Пассажир", db.rows("tickets").single()["passenger"])
        assertEquals(39, db.available("1"))
        try { db.delete("trains", "1"); fail("Referenced train was deleted") } catch (_: android.database.sqlite.SQLiteConstraintException) { }
    }
    @Test fun referencedTrainCannotBeDeleted() {
        try { db.delete("trains", "1"); fail("Referenced train was deleted") } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(1, db.rows("trains", "id = ?", arrayOf("1")).size)
    }
}
