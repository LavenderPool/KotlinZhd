package com.example.zhd

import android.content.ContentValues
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class Field(val key: String, val label: String, val kind: String = "text", val reference: String? = null)
data class Section(val table: String, val title: String, val fields: List<Field>)
typealias Row = Map<String, String>

val sections = listOf(
    Section("trains", "Поезда", listOf(Field("number", "Номер поезда"), Field("category", "Категория"), Field("seats", "Количество мест", "int"))),
    Section("stations", "Станции", listOf(Field("name", "Название"), Field("city", "Город"))),
    Section("employees", "Сотрудники", listOf(Field("name", "ФИО"), Field("position", "Должность"))),
    Section("schedules", "Расписание", listOf(Field("train_id", "Поезд", reference = "trains"), Field("departure_id", "Станция отправления", reference = "stations"), Field("arrival_id", "Станция прибытия", reference = "stations"), Field("departure", "Отправление (ГГГГ-ММ-ДД ЧЧ:ММ)", "datetime"), Field("arrival", "Прибытие (ГГГГ-ММ-ДД ЧЧ:ММ)", "datetime"), Field("price", "Стоимость, ₽", "int"))),
    Section("stops", "Остановки", listOf(Field("schedule_id", "Рейс", reference = "schedules"), Field("station_id", "Станция", reference = "stations"), Field("position", "Порядок остановки", "int"), Field("arrival", "Прибытие (ГГГГ-ММ-ДД ЧЧ:ММ)", "datetime"), Field("departure", "Отправление (ГГГГ-ММ-ДД ЧЧ:ММ)", "datetime"))),
    Section("tickets", "Билеты", listOf(Field("schedule_id", "Рейс", reference = "schedules"), Field("employee_id", "Сотрудник", reference = "employees"), Field("passenger", "ФИО пассажира"), Field("passport", "Паспорт"), Field("seat", "Место", "int"), Field("price", "Стоимость, ₽", "int"), Field("status", "Статус", "status")))
)

class StationDatabase(context: Context, name: String = "station.db") {
    private val room = Room.databaseBuilder(context.applicationContext, StationRoomDatabase::class.java, name)
        .addMigrations(MIGRATION_1_2)
        .addCallback(StationRoomDatabase.callback)
        .allowMainThreadQueries()
        .build()

    fun close() = room.close()

    companion object {
    fun seed(db: SupportSQLiteDatabase) {
        val cities = listOf("Москва", "Санкт-Петербург", "Казань", "Нижний Новгород", "Ярославль", "Владимир", "Тверь", "Рязань", "Самара", "Воронеж", "Пенза", "Тула")
        cities.forEachIndexed { i, city -> db.execSQL("INSERT INTO stations(id,name,city) VALUES(?,?,?)", arrayOf(i + 1, "$city вокзал", city)) }
        for (i in 1..10) {
            db.execSQL("INSERT INTO trains(id,number,category,seats) VALUES(?,?,?,?)", arrayOf(i, "${100 + i}А", if (i % 2 == 0) "Скорый" else "Пассажирский", 40))
            db.execSQL("INSERT INTO employees(id,name,position) VALUES(?,?,?)", arrayOf(i, listOf("Иванов", "Петров", "Сидоров", "Смирнов", "Волков", "Морозов", "Соколов", "Попов", "Козлов", "Новиков")[i - 1] + " Алексей Сергеевич", "Кассир"))
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i); set(Calendar.HOUR_OF_DAY, 8 + i % 6); set(Calendar.MINUTE, 0) }
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
            val departure = fmt.format(cal.time); cal.add(Calendar.HOUR_OF_DAY, 6); val arrival = fmt.format(cal.time)
            val from = if (i % 2 == 0) 2 else 1; val to = i + 2
            db.execSQL("INSERT INTO schedules(id,train_id,departure_id,arrival_id,departure,arrival,price) VALUES(?,?,?,?,?,?,?)", arrayOf(i, i, from, to, departure, arrival, 1200 + i * 150))
            cal.add(Calendar.HOUR_OF_DAY, -3); val stopArrival = fmt.format(cal.time); cal.add(Calendar.MINUTE, 10)
            db.execSQL("INSERT INTO stops(schedule_id,station_id,position,arrival,departure) VALUES(?,?,?,?,?)", arrayOf(i, if (from == 1) 2 else 1, 1, stopArrival, fmt.format(cal.time)))
            db.execSQL("INSERT INTO tickets(schedule_id,employee_id,passenger,passport,seat,price,status,owner) VALUES(?,?,?,?,?,?,?,?)", arrayOf(i, i, "Пассажир $i", "4510 00000$i", 1, 1200 + i * 150, "Куплен", "employee"))
        }
    }
    }

    fun rows(table: String, where: String? = null, args: Array<String>? = null): List<Row> {
        val result = mutableListOf<Row>()
        require(sections.any { it.table == table })
        val query = "SELECT * FROM $table" + (where?.let { " WHERE $it" } ?: "") + " ORDER BY id DESC"
        room.records().rows(SimpleSQLiteQuery(query, args ?: emptyArray())).use { c ->
            while (c.moveToNext()) result.add(c.columnNames.associateWith { c.getString(c.getColumnIndexOrThrow(it)) ?: "" })
        }
        return result
    }
    fun label(table: String, id: String): String {
        val r = rows(table, "id = ?", arrayOf(id)).firstOrNull() ?: return "—"
        return when (table) {
            "trains" -> "${r["number"]} · ${r["category"]}"
            "stations" -> "${r["city"]} · ${r["name"]}"
            "employees" -> r["name"].orEmpty()
            "schedules" -> "${label("stations", r["departure_id"].orEmpty()).substringBefore(" ·")} → ${label("stations", r["arrival_id"].orEmpty()).substringBefore(" ·")} · ${r["departure"]}"
            else -> "#${r["id"]}"
        }
    }
    fun available(scheduleId: String): Int {
        val schedule = rows("schedules", "id = ?", arrayOf(scheduleId)).firstOrNull() ?: return 0
        val train = rows("trains", "id = ?", arrayOf(schedule["train_id"].orEmpty())).firstOrNull() ?: return 0
        return (train["seats"]!!.toInt() - rows("tickets", "schedule_id = ? AND status != 'Отменён'", arrayOf(scheduleId)).size).coerceAtLeast(0)
    }
    fun save(section: Section, values: Map<String, String>, id: String? = null, owner: String? = null) {
        val db = room.openHelper.writableDatabase
        db.beginTransaction()
        try {
            section.fields.forEach { f ->
                val value = values[f.key].orEmpty().trim()
                require(value.isNotEmpty()) { "Заполните поле «${f.label}»" }
                if (f.kind == "int") require((value.toIntOrNull() ?: 0) > 0) { "Поле «${f.label}» должно быть положительным числом" }
                if (f.kind == "datetime") {
                    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply { isLenient = false }
                    val pos = java.text.ParsePosition(0)
                    require(format.parse(value, pos) != null && pos.index == value.length) { "Укажите дату в формате ГГГГ-ММ-ДД ЧЧ:ММ" }
                }
                if (f.reference != null) require(rows(f.reference, "id = ?", arrayOf(value)).isNotEmpty()) { "Выберите значение для «${f.label}»" }
            }
            if (section.table == "schedules") {
                require(values["departure_id"] != values["arrival_id"]) { "Станции отправления и прибытия должны отличаться" }
                require(values["departure"]!! < values["arrival"]!!) { "Прибытие должно быть позже отправления" }
                if (id != null) {
                    val stops = rows("stops", "schedule_id = ?", arrayOf(id))
                    require(stops.all { it["arrival"]!! >= values["departure"]!! && it["departure"]!! <= values["arrival"]!! }) { "Остановки должны находиться в пределах времени рейса" }
                    val train = rows("trains", "id = ?", arrayOf(values["train_id"]!!)).first()
                    val maxSeat = db.query("SELECT MAX(seat) FROM tickets WHERE schedule_id=? AND status != 'Отменён'", arrayOf(id)).use { if (it.moveToFirst()) it.getInt(0) else 0 }
                    require(train["seats"]!!.toInt() >= maxSeat) { "В выбранном поезде нет уже занятых мест" }
                }
            }
            if (section.table == "stops") {
                val trip = rows("schedules", "id = ?", arrayOf(values["schedule_id"]!!)).first()
                require(values["arrival"]!! >= trip["departure"]!! && values["departure"]!! <= trip["arrival"]!! && values["arrival"]!! <= values["departure"]!!) { "Время остановки должно находиться в пределах рейса" }
            }
            if (section.table == "trains" && id != null) {
                val used = db.query("SELECT MAX(t.seat) FROM tickets t JOIN schedules s ON s.id=t.schedule_id WHERE s.train_id=? AND t.status != 'Отменён'", arrayOf(id)).use { if (it.moveToFirst()) it.getInt(0) else 0 }
                require(values["seats"]!!.toInt() >= used) { "Количество мест меньше номера занятого места" }
            }
            if (section.table == "tickets") {
                require(values["status"] in listOf("Бронь", "Куплен", "Отменён")) { "Выберите статус билета" }
                val trip = rows("schedules", "id = ?", arrayOf(values["schedule_id"]!!)).first()
                val train = rows("trains", "id = ?", arrayOf(trip["train_id"]!!)).first()
                require(values["seat"]!!.toInt() <= train["seats"]!!.toInt()) { "Такого места в поезде нет" }
                if (values["status"] != "Отменён") require(rows("tickets", "schedule_id = ? AND seat = ? AND status != 'Отменён' AND id != ?", arrayOf(values["schedule_id"]!!, values["seat"]!!, id ?: "-1")).isEmpty()) { "Это место уже занято" }
            }
            val cv = ContentValues().apply { section.fields.forEach { put(it.key, values[it.key]!!.trim()) }; if (owner != null) put("owner", owner) }
            if (id == null) db.insert(section.table, android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, cv) else db.update(section.table, android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, cv, "id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun delete(table: String, id: String) { room.openHelper.writableDatabase.delete(table, "id = ?", arrayOf(id)) }
}
