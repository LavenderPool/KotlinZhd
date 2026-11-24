package com.example.zhd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.zhd.ui.theme.ZhdTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ZhdTheme { StationApp() } }
    }
}

private val ink = Color(0xFF1D3139)
private val muted = Color(0xFF65777C)
private val accent = Color(0xFF14645E)
private val paper = Color(0xFFF5F7F4)

@Composable
fun StationApp() {
    val context = LocalContext.current
    val db = remember { StationDatabase(context.applicationContext) }
    var role by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("") }
    var section by remember { mutableStateOf<Section?>(null) }
    var editing by remember { mutableStateOf<Row?>(null) }
    var creating by remember { mutableStateOf(false) }
    var trip by remember { mutableStateOf<Row?>(null) }
    var booking by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleteRow by remember { mutableStateOf<Row?>(null) }
    fun back() {
        when {
            booking -> booking = false
            trip != null -> trip = null
            creating -> { creating = false; editing = null }
            section != null -> section = null
            page.isNotEmpty() -> page = ""
            else -> role = ""
        }
    }
    BackHandler(enabled = role.isNotEmpty()) { back() }
    val title = when {
        role.isEmpty() -> ""
        booking -> "Оформление билета"
        trip != null -> "Информация о рейсе"
        creating -> if (editing == null) "Добавить запись" else "Изменить запись"
        section != null -> section!!.title
        page == "search" -> "Поиск билетов"
        page == "mine" -> "Мои билеты"
        role == "employee" -> "Панель сотрудника"
        else -> "Поездки"
    }
    Scaffold(containerColor = paper, topBar = {
        if (role.isNotEmpty()) Column(Modifier.background(Color.White)) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { back() }, contentPadding = PaddingValues(end = 12.dp)) { Text("←", style = MaterialTheme.typography.headlineSmall, color = ink) }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ink)
                    Text(if (role == "employee") "Сотрудник" else "Покупатель", style = MaterialTheme.typography.labelMedium, color = muted)
                }
                TextButton(onClick = { role = ""; page = ""; section = null; trip = null; booking = false; creating = false }) { Text("Выйти") }
            }
            HorizontalDivider(color = Color(0xFFE8EEEA))
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                role.isEmpty() -> RoleScreen { role = it; page = ""; section = null; trip = null; creating = false }
                creating && section != null -> RecordForm(db, section!!, editing, revision, onCancel = { creating = false; editing = null }, onSave = { values ->
                    try { db.save(section!!, values, editing?.get("id")); revision++; creating = false; editing = null } catch (e: Exception) { error = humanError(e) }
                })
                section != null -> EmployeeList(db, section!!, revision, onAdd = { editing = null; creating = true }, onEdit = { editing = it; creating = true }, onDelete = { deleteRow = it })
                booking && trip != null -> BookingForm(db, trip!!, revision, onCancel = { booking = false }, onSave = { values ->
                    try { db.save(sections.last(), values, owner = "buyer"); revision++; booking = false; trip = null; page = "mine" } catch (e: Exception) { error = humanError(e) }
                })
                trip != null -> TripDetails(db, trip!!, revision, onBook = { booking = true })
                page == "search" -> SearchScreen(db, revision, onTrip = { trip = it })
                page == "mine" -> MyTickets(db, revision, onStatus = { row, status ->
                    try { db.save(sections.last(), row + ("status" to status), row["id"]); revision++ } catch (e: Exception) { error = humanError(e) }
                })
                role == "employee" -> EmployeeHome { section = it }
                else -> BuyerHome(onSearch = { page = "search" }, onTickets = { page = "mine" })
            }
        }
    }
    if (error != null) AlertDialog(onDismissRequest = { error = null }, title = { Text("Не удалось сохранить") }, text = { Text(error!!) }, confirmButton = { TextButton(onClick = { error = null }) { Text("Понятно") } })
    if (deleteRow != null && section != null) AlertDialog(onDismissRequest = { deleteRow = null }, title = { Text("Удалить запись?") }, text = { Text("Это действие нельзя отменить. Связанные данные должны быть удалены или изменены заранее.") }, confirmButton = { TextButton(onClick = { try { db.delete(section!!.table, deleteRow!!["id"]!!); revision++ } catch (e: Exception) { error = humanError(e) }; deleteRow = null }) { Text("Удалить") } }, dismissButton = { TextButton(onClick = { deleteRow = null }) { Text("Отмена") } })
}

fun humanError(e: Exception): String = when {
    e is IllegalArgumentException -> e.message ?: "Проверьте введённые данные"
    e.message?.contains("FOREIGN KEY", true) == true -> "Запись связана с другими данными. Сначала измените или удалите связанные записи."
    e.message?.contains("UNIQUE", true) == true -> "Такая запись уже существует или место уже занято."
    else -> "Проверьте данные и попробуйте ещё раз."
}

@Composable fun RoleScreen(onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("ЖД ВОКЗАЛ", color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("Все поездки\nначинаются здесь", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = ink)
        Spacer(Modifier.height(12.dp))
        Text("Выберите, как вы хотите войти в приложение", style = MaterialTheme.typography.bodyLarge, color = muted)
        Spacer(Modifier.height(32.dp))
        MenuCard("Покупатель", "Найти поезд, забронировать или купить билет", "→") { onSelect("buyer") }
        Spacer(Modifier.height(12.dp))
        MenuCard("Сотрудник", "Управлять расписанием и данными вокзала", "→") { onSelect("employee") }
        Spacer(Modifier.height(32.dp))
        Text("Расписание · Билеты · Путешествия", style = MaterialTheme.typography.labelMedium, color = muted)
    }
}

@Composable fun MenuCard(title: String, subtitle: String, trailing: String = "→", onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(), shadowElevation = 1.dp) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ink); Spacer(Modifier.height(5.dp)); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = muted) }
            Spacer(Modifier.width(12.dp)); Text(trailing, color = accent, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable fun BuyerHome(onSearch: () -> Unit, onTickets: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Spacer(Modifier.height(12.dp)); Text("Куда отправимся?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = ink)
        Spacer(Modifier.height(8.dp)); Text("Планируйте поездку и храните билеты в одном месте.", color = muted)
        Spacer(Modifier.height(28.dp)); MenuCard("Найти билеты", "Маршруты, расписание и свободные места", "↗", onSearch)
        Spacer(Modifier.height(12.dp)); MenuCard("Мои билеты", "Бронирования и оформленные поездки", "→", onTickets)
    }
}
@Composable fun EmployeeHome(onSection: (Section) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Управление вокзалом", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ink)
        Spacer(Modifier.height(8.dp)); Text("Выберите раздел для работы с данными.", color = muted); Spacer(Modifier.height(20.dp))
        val descriptions = listOf("Номера, категории и места", "Справочник станций и городов", "Работники вокзала", "Рейсы, время и стоимость", "Промежуточные станции рейсов", "Продажи и бронирования")
        sections.forEachIndexed { index, s -> MenuCard(s.title, descriptions[index]) { onSection(s) }; Spacer(Modifier.height(10.dp)) }
    }
}

@Composable fun EmployeeList(db: StationDatabase, section: Section, revision: Int, onAdd: () -> Unit, onEdit: (Row) -> Unit, onDelete: (Row) -> Unit) {
    var query by remember(section) { mutableStateOf("") }
    val rows = remember(section, revision) { db.rows(section.table) }
    val filtered = rows.filter { row -> row.values.any { it.contains(query, true) } || section.fields.any { it.reference != null && db.label(it.reference, row[it.key].orEmpty()).contains(query, true) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp)); OutlinedTextField(query, { query = it }, label = { Text("Поиск") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp)); Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("+  Добавить") }
        Spacer(Modifier.height(12.dp)); Text("Записей: ${filtered.size}", color = muted, style = MaterialTheme.typography.labelLarge)
        if (filtered.isEmpty()) EmptyState("Записей не найдено")
        else LazyColumn(contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it["id"]!! }) { row ->
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("№ ${row["id"]}", color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        section.fields.forEach { field ->
                            val value = if (field.reference != null) db.label(field.reference, row[field.key].orEmpty()) else row[field.key].orEmpty()
                            Text("${field.label.substringBefore(" (")}: $value", style = MaterialTheme.typography.bodyMedium, color = ink)
                        }
                        Row { TextButton(onClick = { onEdit(row) }) { Text("Изменить") }; TextButton(onClick = { onDelete(row) }) { Text("Удалить") } }
                    }
                }
            }
        }
    }
}

@Composable fun RecordForm(db: StationDatabase, section: Section, row: Row?, revision: Int, onCancel: () -> Unit, onSave: (Map<String, String>) -> Unit) {
    val values = remember(section, row) { mutableStateMapOf<String, String>().apply { section.fields.forEach { put(it.key, row?.get(it.key).orEmpty()) } } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (row == null) "Новая запись" else "Запись № ${row["id"]}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ink)
        section.fields.forEach { field -> FormField(db, field, values, revision) }
        Spacer(Modifier.height(4.dp)); Button(onClick = { onSave(values.toMap()) }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable fun FormField(db: StationDatabase, field: Field, values: SnapshotStateMap<String, String>, revision: Int) {
    if (field.reference != null || field.kind == "status") {
        val options = remember(field, revision) { if (field.reference != null) db.rows(field.reference).map { it["id"]!! to db.label(field.reference, it["id"]!!) } else listOf("Бронь", "Куплен", "Отменён").map { it to it } }
        Picker(field.label, values[field.key].orEmpty(), options) { values[field.key] = it }
    } else OutlinedTextField(value = values[field.key].orEmpty(), onValueChange = { values[field.key] = it }, label = { Text(field.label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = if (field.kind == "int") KeyboardType.Number else KeyboardType.Text), modifier = Modifier.fillMaxWidth())
}
@Composable fun Picker(label: String, value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.labelSmall, color = muted); Text(options.firstOrNull { it.first == value }?.second ?: "Выберите", color = ink) }; Text("▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 300.dp)) {
            if (options.isEmpty()) DropdownMenuItem(text = { Text("Нет доступных записей") }, onClick = { expanded = false })
            options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false }) }
        }
    }
}

@Composable fun SearchScreen(db: StationDatabase, revision: Int, onTrip: (Row) -> Unit) {
    var from by remember { mutableStateOf("") }; var to by remember { mutableStateOf("") }; var date by remember { mutableStateOf("") }
    val stations = remember(revision) { db.rows("stations").map { it["id"]!! to db.label("stations", it["id"]!!) } }
    val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date())
    val trips = remember(revision, from, to, date) { db.rows("schedules").filter { (from.isEmpty() || it["departure_id"] == from) && (to.isEmpty() || it["arrival_id"] == to) && (date.isBlank() || it["departure"].orEmpty().startsWith(date.trim())) && it["departure"].orEmpty() >= now }.sortedBy { it["departure"] } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Найдите свой поезд", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ink) }
        item { Picker("Откуда", from, listOf("" to "Все станции") + stations) { from = it } }
        item { Picker("Куда", to, listOf("" to "Все станции") + stations) { to = it } }
        item { OutlinedTextField(date, { date = it }, label = { Text("Дата (ГГГГ-ММ-ДД)") }, placeholder = { Text("Любая дата") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { Text("Доступные рейсы · ${trips.size}", color = muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp)) }
        if (trips.isEmpty()) item { EmptyState("Рейсов по этим условиям не найдено") }
        items(trips, key = { it["id"]!! }) { trip -> TripCard(db, trip, onClick = { onTrip(trip) }) }
    }
}

@Composable fun TripCard(db: StationDatabase, trip: Row, onClick: () -> Unit) {
    val from = db.label("stations", trip["departure_id"]!!).substringBefore(" ·")
    val to = db.label("stations", trip["arrival_id"]!!).substringBefore(" ·")
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("ПОЕЗД ${db.label("trains", trip["train_id"]!!).substringBefore(" ·")}", color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold); Text("${trip["price"]} ₽", color = ink, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(12.dp)); Text("$from → $to", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ink)
            Spacer(Modifier.height(6.dp)); Text("${trip["departure"]}  —  ${trip["arrival"]}", color = muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp)); Text("Свободно мест: ${db.available(trip["id"]!!)}  ·  Подробнее →", color = accent, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable fun TripDetails(db: StationDatabase, trip: Row, revision: Int, onBook: () -> Unit) {
    val stops = remember(trip, revision) { db.rows("stops", "schedule_id = ?", arrayOf(trip["id"]!!)).sortedBy { it["position"]?.toIntOrNull() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TripCard(db, trip) {}
        Spacer(Modifier.height(24.dp)); Text("Маршрут", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ink)
        Spacer(Modifier.height(12.dp)); RoutePoint(db.label("stations", trip["departure_id"]!!), "Отправление · ${trip["departure"]}")
        stops.forEach { RoutePoint(db.label("stations", it["station_id"]!!), "${it["arrival"]} — ${it["departure"]}") }
        RoutePoint(db.label("stations", trip["arrival_id"]!!), "Прибытие · ${trip["arrival"]}")
        Spacer(Modifier.height(20.dp)); Button(onClick = onBook, enabled = db.available(trip["id"]!!) > 0, modifier = Modifier.fillMaxWidth()) { Text(if (db.available(trip["id"]!!) > 0) "Выбрать место" else "Нет свободных мест") }
    }
}
@Composable fun RoutePoint(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp)) { Text("●", color = accent); Spacer(Modifier.width(14.dp)); Column { Text(title, fontWeight = FontWeight.SemiBold, color = ink); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = muted) } }
}

@Composable fun BookingForm(db: StationDatabase, trip: Row, revision: Int, onCancel: () -> Unit, onSave: (Map<String, String>) -> Unit) {
    val train = remember(trip) { db.rows("trains", "id = ?", arrayOf(trip["train_id"]!!)).first() }
    val occupied = remember(trip, revision) { db.rows("tickets", "schedule_id = ? AND status != 'Отменён'", arrayOf(trip["id"]!!)).map { it["seat"] }.toSet() }
    val seats = (1..train["seats"]!!.toInt()).filter { it.toString() !in occupied }.map { it.toString() to "Место $it" }
    var passenger by remember { mutableStateOf("") }; var passport by remember { mutableStateOf("") }; var seat by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(db.label("schedules", trip["id"]!!), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ink)
        Text("Стоимость: ${trip["price"]} ₽", color = accent, fontWeight = FontWeight.Bold)
        OutlinedTextField(passenger, { passenger = it }, label = { Text("ФИО пассажира") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(passport, { passport = it }, label = { Text("Паспорт") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Picker("Свободное место", seat, seats) { seat = it }
        Text("Оплата демонстрационная: деньги не списываются.", color = muted, style = MaterialTheme.typography.bodySmall)
        fun values(status: String) = mapOf("schedule_id" to trip["id"]!!, "employee_id" to (db.rows("employees").lastOrNull()?.get("id") ?: ""), "passenger" to passenger, "passport" to passport, "seat" to seat, "price" to trip["price"]!!, "status" to status)
        Button(onClick = { onSave(values("Куплен")) }, modifier = Modifier.fillMaxWidth()) { Text("Купить за ${trip["price"]} ₽") }
        OutlinedButton(onClick = { onSave(values("Бронь")) }, modifier = Modifier.fillMaxWidth()) { Text("Забронировать") }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
    }
}

@Composable fun MyTickets(db: StationDatabase, revision: Int, onStatus: (Row, String) -> Unit) {
    val tickets = remember(revision) { db.rows("tickets", "owner = ?", arrayOf("buyer")) }
    if (tickets.isEmpty()) EmptyState("Здесь появятся ваши билеты и бронирования")
    else LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Ваши поездки", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ink) }
        items(tickets, key = { it["id"]!! }) { ticket ->
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(ticket["status"].orEmpty().uppercase(Locale.ROOT), color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp)); Text(db.label("schedules", ticket["schedule_id"]!!), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ink)
                    Spacer(Modifier.height(8.dp)); Text("${ticket["passenger"]} · Место ${ticket["seat"]}", color = muted); Text("${ticket["price"]} ₽", color = ink, fontWeight = FontWeight.SemiBold)
                    if (ticket["status"] == "Бронь") TextButton(onClick = { onStatus(ticket, "Куплен") }) { Text("Купить · ${ticket["price"]} ₽") }
                    if (ticket["status"] != "Отменён") TextButton(onClick = { onStatus(ticket, "Отменён") }) { Text("Отменить билет") }
                }
            }
        }
    }
}
@Composable fun EmptyState(text: String) { Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) { Text(text, color = muted, style = MaterialTheme.typography.bodyMedium) } }
