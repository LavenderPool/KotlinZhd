package com.example.zhd

import android.database.Cursor
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery

@Entity(tableName = "trains", indices = [Index(value = ["number"], unique = true)])
data class Train(@PrimaryKey val id: Long, val number: String, val category: String, val seats: Int)
@Entity(tableName = "stations")
data class Station(@PrimaryKey val id: Long, val name: String, val city: String)
@Entity(tableName = "employees")
data class Employee(@PrimaryKey val id: Long, val name: String, val position: String)
@Entity(tableName = "schedules", foreignKeys = [
    ForeignKey(entity = Train::class, parentColumns = ["id"], childColumns = ["train_id"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = Station::class, parentColumns = ["id"], childColumns = ["departure_id"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = Station::class, parentColumns = ["id"], childColumns = ["arrival_id"], onDelete = ForeignKey.RESTRICT)
])
data class Schedule(@PrimaryKey val id: Long, @ColumnInfo(name = "train_id") val trainId: Long, @ColumnInfo(name = "departure_id") val departureId: Long, @ColumnInfo(name = "arrival_id") val arrivalId: Long, val departure: String, val arrival: String, val price: Int)
@Entity(tableName = "stops", foreignKeys = [
    ForeignKey(entity = Schedule::class, parentColumns = ["id"], childColumns = ["schedule_id"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = Station::class, parentColumns = ["id"], childColumns = ["station_id"], onDelete = ForeignKey.RESTRICT)
], indices = [Index(value = ["schedule_id", "position"], unique = true)])
data class Stop(@PrimaryKey val id: Long, @ColumnInfo(name = "schedule_id") val scheduleId: Long, @ColumnInfo(name = "station_id") val stationId: Long, val position: Int, val arrival: String, val departure: String)
@Entity(tableName = "tickets", foreignKeys = [
    ForeignKey(entity = Schedule::class, parentColumns = ["id"], childColumns = ["schedule_id"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = Employee::class, parentColumns = ["id"], childColumns = ["employee_id"], onDelete = ForeignKey.RESTRICT)
])
data class Ticket(@PrimaryKey val id: Long, @ColumnInfo(name = "schedule_id") val scheduleId: Long, @ColumnInfo(name = "employee_id") val employeeId: Long, val passenger: String, val passport: String, val seat: Int, val price: Int, val status: String, @ColumnInfo(defaultValue = "'employee'") val owner: String)

@Dao
interface StationDao {
    @RawQuery fun rows(query: SupportSQLiteQuery): Cursor
}

@Database(entities = [Train::class, Station::class, Employee::class, Schedule::class, Stop::class, Ticket::class], version = 2, exportSchema = false)
abstract class StationRoomDatabase : RoomDatabase() {
    abstract fun records(): StationDao

    companion object {
        val callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                StationDatabase.seed(db)
            }
            override fun onOpen(db: SupportSQLiteDatabase) {
                // Room does not model partial indexes; triggers enforce the same rule.
                for (operation in listOf("INSERT", "UPDATE")) {
                    db.execSQL("""CREATE TRIGGER IF NOT EXISTS occupied_seat_${operation.lowercase()}
                        BEFORE $operation ON tickets WHEN NEW.status != 'Отменён' AND EXISTS
                        (SELECT 1 FROM tickets WHERE schedule_id = NEW.schedule_id AND seat = NEW.seat
                         AND status != 'Отменён' AND id != NEW.id)
                        BEGIN SELECT RAISE(ABORT, 'Seat already occupied'); END""")
                }
            }
        }
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val definitions = linkedMapOf(
            "trains" to "id INTEGER NOT NULL PRIMARY KEY, number TEXT NOT NULL, category TEXT NOT NULL, seats INTEGER NOT NULL CHECK(seats > 0)",
            "stations" to "id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, city TEXT NOT NULL",
            "employees" to "id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, position TEXT NOT NULL",
            "schedules" to "id INTEGER NOT NULL PRIMARY KEY, train_id INTEGER NOT NULL REFERENCES trains(id) ON DELETE RESTRICT, departure_id INTEGER NOT NULL REFERENCES stations(id) ON DELETE RESTRICT, arrival_id INTEGER NOT NULL REFERENCES stations(id) ON DELETE RESTRICT, departure TEXT NOT NULL, arrival TEXT NOT NULL, price INTEGER NOT NULL CHECK(price > 0), CHECK(departure_id != arrival_id)",
            "stops" to "id INTEGER NOT NULL PRIMARY KEY, schedule_id INTEGER NOT NULL REFERENCES schedules(id) ON DELETE RESTRICT, station_id INTEGER NOT NULL REFERENCES stations(id) ON DELETE RESTRICT, position INTEGER NOT NULL CHECK(position > 0), arrival TEXT NOT NULL, departure TEXT NOT NULL",
            "tickets" to "id INTEGER NOT NULL PRIMARY KEY, schedule_id INTEGER NOT NULL REFERENCES schedules(id) ON DELETE RESTRICT, employee_id INTEGER NOT NULL REFERENCES employees(id) ON DELETE RESTRICT, passenger TEXT NOT NULL, passport TEXT NOT NULL, seat INTEGER NOT NULL CHECK(seat > 0), price INTEGER NOT NULL CHECK(price > 0), status TEXT NOT NULL CHECK(status IN ('Бронь', 'Куплен', 'Отменён')), owner TEXT NOT NULL DEFAULT 'employee'"
        )
        definitions.forEach { (name, columns) ->
            val newColumns = columns.replace("REFERENCES trains(", "REFERENCES new_trains(")
                .replace("REFERENCES stations(", "REFERENCES new_stations(")
                .replace("REFERENCES employees(", "REFERENCES new_employees(")
                .replace("REFERENCES schedules(", "REFERENCES new_schedules(")
            db.execSQL("CREATE TABLE new_$name ($newColumns)")
            db.execSQL("INSERT INTO new_$name SELECT * FROM $name")
        }
        definitions.keys.reversed().forEach { db.execSQL("DROP TABLE $it") }
        definitions.keys.forEach { db.execSQL("ALTER TABLE new_$it RENAME TO $it") }
        db.execSQL("CREATE UNIQUE INDEX index_trains_number ON trains(number)")
        db.execSQL("CREATE UNIQUE INDEX index_stops_schedule_id_position ON stops(schedule_id, position)")
    }
}
