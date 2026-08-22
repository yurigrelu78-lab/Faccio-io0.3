package it.faccioio.app

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createReminderChannel()
        requestNotificationPermission()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FaccioIoApp()
                }
            }
        }
    }

    private fun createReminderChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            "faccio_io_reminders_v2",
            "Promemoria Faccio io",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Promemoria delle attività di Faccio io"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 300, 500)

            val soundUri = android.media.RingtoneManager.getDefaultUri(
                android.media.RingtoneManager.TYPE_NOTIFICATION
            )
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                .build()
            setSound(soundUri, audioAttributes)
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
    }
}

data class TaskItem(
    val title: String,
    val completed: Boolean = false,
    val reminderTime: Long? = null,
    val category: String = "Personale",
    val priority: String = "Media",
    val appointmentTime: Long? = null,
    val location: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val arrivalReminderId: String? = null,
    val departureTime: Long? = null,
    val departureTravelMinutes: Int? = null,
    val departureMarginMinutes: Int? = null,
    val departureTransport: String = "Auto",
    val departureSafety: String = "Normale"
)

data class ResolvedPlace(
    val address: String,
    val latitude: Double,
    val longitude: Double
)

internal const val TASK_PREFS = "faccio_io_tasks"
internal const val TASKS_KEY = "saved_tasks"
private val TASK_CATEGORIES = listOf("Casa", "Lavoro", "Salute", "Personale")
private val TASK_PRIORITIES = listOf("Bassa", "Media", "Alta")
private val APPOINTMENT_REMINDER_OPTIONS = listOf(
    "24 ore prima",
    "48 ore prima",
    "7 giorni prima",
    "Personalizzato",
    "Nessun promemoria"
)

@Composable
fun FaccioIoApp() {
    val context = LocalContext.current
    var newTask by rememberSaveable { mutableStateOf("") }
    var pendingTask by rememberSaveable { mutableStateOf("") }
    var showReminderChoice by rememberSaveable { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf("Personale") }
    var selectedPriority by rememberSaveable { mutableStateOf("Media") }
    var pendingCategory by rememberSaveable { mutableStateOf("Personale") }
    var pendingPriority by rememberSaveable { mutableStateOf("Media") }
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var deletingIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var editedTitle by rememberSaveable { mutableStateOf("") }
    var editedCategory by rememberSaveable { mutableStateOf("Personale") }
    var editedPriority by rememberSaveable { mutableStateOf("Media") }
    var editedAppointmentTime by rememberSaveable { mutableStateOf<Long?>(null) }
    var editedReminderTime by rememberSaveable { mutableStateOf<Long?>(null) }
    var categoryFilter by rememberSaveable { mutableStateOf("Tutte") }
    var priorityFilter by rememberSaveable { mutableStateOf("Tutte") }
    var showAssistant by rememberSaveable { mutableStateOf(false) }
    var assistantText by rememberSaveable { mutableStateOf("") }
    var assistantResult by remember { mutableStateOf<ParsedAppointment?>(null) }
    var resolvedPlace by remember { mutableStateOf<ResolvedPlace?>(null) }
    var placeLookupMessage by remember { mutableStateOf("") }
    var appointmentReminderOption by rememberSaveable {
        mutableStateOf("24 ore prima")
    }
    var customAppointmentReminderTime by rememberSaveable {
        mutableStateOf<Long?>(null)
    }
    var assistantCategory by rememberSaveable { mutableStateOf("Personale") }
    var assistantPriority by rememberSaveable { mutableStateOf("Media") }
    var taskReminderMode by rememberSaveable { mutableStateOf("Nessuno") }
    var taskReminderTime by rememberSaveable { mutableStateOf<Long?>(null) }
    var taskLocationQuery by rememberSaveable { mutableStateOf("") }
    var taskResolvedPlace by remember { mutableStateOf<ResolvedPlace?>(null) }
    var taskPlaceMessage by rememberSaveable { mutableStateOf("") }
    var departureTransport by rememberSaveable { mutableStateOf("Auto") }
    var departureSafety by rememberSaveable { mutableStateOf("Normale") }
    var departureEstimate by remember { mutableStateOf<DepartureEstimate?>(null) }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spokenText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spokenText.isNullOrBlank()) assistantText = spokenText
    }

    val tasks = remember(context) {
        mutableStateListOf<TaskItem>().apply {
            addAll(loadTasks(context))
        }
    }

    val visibleTasks = tasks.withIndex().filter { indexedTask ->
        val task = indexedTask.value
        (categoryFilter == "Tutte" || task.category == categoryFilter) &&
            (priorityFilter == "Tutte" || task.priority == priorityFilter)
    }

    fun addPendingTask(
        reminderTime: Long? = null,
        place: ResolvedPlace? = null,
        arrivalId: String? = null
    ) {
        tasks.add(
            TaskItem(
                title = pendingTask,
                reminderTime = reminderTime,
                category = pendingCategory,
                priority = pendingPriority,
                location = place?.address,
                latitude = place?.latitude,
                longitude = place?.longitude,
                arrivalReminderId = arrivalId
            )
        )
        saveTasks(context, tasks)
        newTask = ""
        pendingTask = ""
        selectedCategory = "Personale"
        selectedPriority = "Media"
        pendingCategory = "Personale"
        pendingPriority = "Media"
        showReminderChoice = false
        taskReminderMode = "Nessuno"
        taskReminderTime = null
        taskLocationQuery = ""
        taskResolvedPlace = null
        taskPlaceMessage = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "Faccio io",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Un passo alla volta.",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = newTask,
            onValueChange = { newTask = it },
            label = { Text("Cosa devi fare?") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SelectionMenu(
                label = "Categoria",
                selectedValue = selectedCategory,
                values = TASK_CATEGORIES,
                onValueSelected = { selectedCategory = it },
                modifier = Modifier.weight(1f)
            )
            SelectionMenu(
                label = "Priorità",
                selectedValue = selectedPriority,
                values = TASK_PRIORITIES,
                onValueSelected = { selectedPriority = it },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val text = newTask.trim()
                if (text.isEmpty()) {
                    Toast.makeText(
                        context,
                        "Scrivi prima il nome dell’attività",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    pendingTask = text
                    pendingCategory = selectedCategory
                    pendingPriority = selectedPriority
                    taskReminderMode = "Nessuno"
                    taskReminderTime = null
                    taskLocationQuery = ""
                    taskResolvedPlace = null
                    showReminderChoice = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Aggiungi attività")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { showAssistant = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Assistente IA · testo o voce")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Le tue attività",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SelectionMenu(
                label = "Categoria",
                selectedValue = categoryFilter,
                values = listOf("Tutte") + TASK_CATEGORIES,
                onValueSelected = { categoryFilter = it },
                modifier = Modifier.weight(1f)
            )
            SelectionMenu(
                label = "Priorità",
                selectedValue = priorityFilter,
                values = listOf("Tutte") + TASK_PRIORITIES,
                onValueSelected = { priorityFilter = it },
                modifier = Modifier.weight(1f)
            )
        }

        if (categoryFilter != "Tutte" || priorityFilter != "Tutte") {
            TextButton(
                onClick = {
                    categoryFilter = "Tutte"
                    priorityFilter = "Tutte"
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Azzera filtri")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (visibleTasks.isEmpty()) {
                item {
                    Text(
                        text = "Nessuna attività corrisponde ai filtri selezionati.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            items(visibleTasks) { indexedTask ->
                val index = indexedTask.index
                val task = indexedTask.value
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.completed,
                                onCheckedChange = { checked ->
                                    tasks[index] = task.copy(completed = checked)
                                    saveTasks(context, tasks)
                                }
                            )

                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(text = task.title)
                                Text(
                                    text = "${task.category} • Priorità ${task.priority}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = priorityColor(task.priority)
                                )
                                task.reminderTime?.let { selectedTime ->
                                    Text(
                                        text = "Promemoria: ${formatReminderTime(selectedTime)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                task.appointmentTime?.let { appointmentTime ->
                                    Text(
                                        text = "Appuntamento: ${formatReminderTime(appointmentTime)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                task.departureTime?.let { departureTime ->
                                    Text("Partenza consigliata: ${formatReminderTime(departureTime)}", style = MaterialTheme.typography.bodySmall)
                                    Text("Stima: ${task.departureTravelMinutes ?: 0} min + ${task.departureMarginMinutes ?: 0} min di margine", style = MaterialTheme.typography.bodySmall)
                                }
                                task.location?.let { location ->
                                    Text(
                                        text = "Luogo: $location",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (task.location != null) {
                                    TextButton(
                                        onClick = {
                                            openPlaceOnMap(
                                                context,
                                                task.location,
                                                task.latitude,
                                                task.longitude
                                            )
                                        }
                                    ) {
                                        Text("Apri nella mappa")
                                    }
                                }
                                if (task.latitude != null && task.longitude != null) {
                                    if (task.appointmentTime != null) {
                                        TextButton(onClick = {
                                            estimateDepartureFromCurrentLocation(
                                                context, task.appointmentTime, task.latitude, task.longitude,
                                                task.departureTransport, task.departureSafety
                                            ) { estimate ->
                                                if (estimate == null) {
                                                    Toast.makeText(context, "Posizione attuale non disponibile", Toast.LENGTH_LONG).show()
                                                } else {
                                                    cancelDepartureReminder(context, task)
                                                    if (estimate.departureTime > System.currentTimeMillis()) {
                                                        scheduleReminder(context, "È ora di partire: ${task.title}", estimate.departureTime)
                                                    }
                                                    tasks[index] = task.copy(
                                                        departureTime = estimate.departureTime,
                                                        departureTravelMinutes = estimate.travelMinutes,
                                                        departureMarginMinutes = estimate.marginMinutes
                                                    )
                                                    saveTasks(context, tasks)
                                                }
                                            }
                                        }) { Text("Ricalcola partenza") }
                                    }
                                    if (task.arrivalReminderId == null) {
                                        TextButton(onClick = {
                                            if (!ensureLocationPermissions(context)) return@TextButton
                                            val id = arrivalGeofenceId(task)
                                            registerArrivalGeofence(context, id, task.title, task.latitude, task.longitude) { ok ->
                                                if (ok) {
                                                    tasks[index] = task.copy(arrivalReminderId = id)
                                                    saveTasks(context, tasks)
                                                    Toast.makeText(context, "Promemoria all’arrivo attivato", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }) { Text("Avvisami quando arrivo") }
                                    } else {
                                        Text("Promemoria all’arrivo attivo · 200 m", style = MaterialTheme.typography.bodySmall)
                                        TextButton(onClick = {
                                            removeArrivalGeofence(context, task.arrivalReminderId)
                                            tasks[index] = task.copy(arrivalReminderId = null)
                                            saveTasks(context, tasks)
                                        }) { Text("Disattiva arrivo") }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            TextButton(
                                onClick = {
                                    editingIndex = index
                                    editedTitle = task.title
                                    editedCategory = task.category
                                    editedPriority = task.priority
                                    editedAppointmentTime = task.appointmentTime
                                    editedReminderTime = task.reminderTime
                                }
                            ) {
                                Text("Modifica")
                            }
                            TextButton(onClick = { deletingIndex = index }) {
                                Text("Elimina")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAssistant) {
        AlertDialog(
            onDismissRequest = { showAssistant = false },
            title = { Text("Assistente IA") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Scrivi o detta una frase, ad esempio: Domani alle 15 dentista in via Roma 10.")
                    OutlinedTextField(
                        value = assistantText,
                        onValueChange = { assistantText = it },
                        label = { Text("Descrivi l’appuntamento") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Descrivi l’appuntamento")
                            }
                            try {
                                voiceLauncher.launch(intent)
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    "Riconoscimento vocale non disponibile",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Detta appuntamento")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = parseAppointment(assistantText)
                        if (parsed == null) {
                            Toast.makeText(
                                context,
                                "Non ho riconosciuto data, ora o descrizione. Controlla il testo dettato.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            assistantResult = parsed
                            showAssistant = false
                        }
                    }
                ) { Text("Interpreta") }
            },
            dismissButton = {
                TextButton(onClick = { showAssistant = false }) { Text("Annulla") }
            }
        )
    }

    assistantResult?.let { appointment ->
        LaunchedEffect(appointment.time, appointment.title) {
            appointmentReminderOption = "24 ore prima"
            customAppointmentReminderTime = null
            assistantCategory = suggestAppointmentCategory(appointment.title)
            assistantPriority = suggestAppointmentPriority(appointment.title)
            departureEstimate = null
        }

        LaunchedEffect(appointment.location) {
            resolvedPlace = null
            val location = appointment.location
            if (location.isNullOrBlank()) {
                placeLookupMessage = "Nessun luogo indicato"
            } else {
                placeLookupMessage = "Ricerca del luogo in corso…"
                resolvePlace(context, location) { place ->
                    resolvedPlace = place
                    placeLookupMessage = if (place == null) {
                        "Luogo non trovato: puoi comunque salvare l’indirizzo scritto."
                    } else {
                        "Luogo trovato e associato"
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { assistantResult = null },
            title = { Text("Conferma appuntamento") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Attività: ${appointment.title}")
                    Text("Quando: ${formatReminderTime(appointment.time)}")
                    Text(
                        "Luogo: ${resolvedPlace?.address ?: appointment.location ?: "non indicato"}"
                    )
                    Text(
                        placeLookupMessage,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (appointment.location != null) {
                        OutlinedButton(
                            onClick = {
                                openPlaceOnMap(
                                    context,
                                    resolvedPlace?.address ?: appointment.location,
                                    resolvedPlace?.latitude,
                                    resolvedPlace?.longitude
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Controlla sulla mappa")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SelectionMenu(
                            label = "Categoria",
                            selectedValue = assistantCategory,
                            values = TASK_CATEGORIES,
                            onValueSelected = { assistantCategory = it },
                            modifier = Modifier.weight(1f)
                        )
                        SelectionMenu(
                            label = "Priorità",
                            selectedValue = assistantPriority,
                            values = TASK_PRIORITIES,
                            onValueSelected = { assistantPriority = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text("Partenza consigliata", fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectionMenu("Mezzo", departureTransport, listOf("Auto", "A piedi", "Bicicletta"), { departureTransport = it; departureEstimate = null }, Modifier.weight(1f))
                        SelectionMenu("Margine", departureSafety, listOf("Ridotto", "Normale", "Prudente"), { departureSafety = it; departureEstimate = null }, Modifier.weight(1f))
                    }
                    OutlinedButton(
                        onClick = {
                            val place = resolvedPlace
                            if (place == null) {
                                Toast.makeText(context, "Serve un luogo verificato", Toast.LENGTH_LONG).show()
                            } else {
                                estimateDepartureFromCurrentLocation(context, appointment.time, place.latitude, place.longitude, departureTransport, departureSafety) {
                                    departureEstimate = it
                                    if (it == null) Toast.makeText(context, "Concedi la posizione precisa e riprova", Toast.LENGTH_LONG).show()
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()
                    ) { Text("Usa la mia posizione attuale") }
                    departureEstimate?.let { estimate ->
                        Text("Viaggio stimato: ${estimate.travelMinutes} minuti")
                        Text("Margine: ${estimate.marginMinutes} minuti")
                        Text("Partenza consigliata: ${formatReminderTime(estimate.departureTime)}")
                    }
                    SelectionMenu(
                        label = "Promemoria",
                        selectedValue = appointmentReminderOption,
                        values = APPOINTMENT_REMINDER_OPTIONS,
                        onValueSelected = {
                            appointmentReminderOption = it
                            if (it != "Personalizzato") {
                                customAppointmentReminderTime = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (appointmentReminderOption == "Personalizzato") {
                        OutlinedButton(
                            onClick = {
                                showReminderPicker(context) { selectedTime ->
                                    customAppointmentReminderTime = selectedTime
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                customAppointmentReminderTime?.let {
                                    "Promemoria: ${formatReminderTime(it)}"
                                } ?: "Scegli data e ora"
                            )
                        }
                    } else {
                        appointmentReminderTime(
                            appointment.time,
                            appointmentReminderOption,
                            null
                        )?.let { reminderTime ->
                            Text(
                                "Il promemoria arriverà: ${formatReminderTime(reminderTime)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Text("Controlla i dati prima di salvare.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val reminderTime = appointmentReminderTime(
                            appointment.time,
                            appointmentReminderOption,
                            customAppointmentReminderTime
                        )
                        val wantsReminder =
                            appointmentReminderOption != "Nessun promemoria"

                        if (wantsReminder && reminderTime == null) {
                            Toast.makeText(
                                context,
                                "Scegli data e ora del promemoria",
                                Toast.LENGTH_LONG
                            ).show()
                            return@TextButton
                        }
                        if (
                            reminderTime != null &&
                            (reminderTime <= System.currentTimeMillis() ||
                                reminderTime >= appointment.time)
                        ) {
                            Toast.makeText(
                                context,
                                "Il promemoria deve essere futuro e precedente all’appuntamento",
                                Toast.LENGTH_LONG
                            ).show()
                            return@TextButton
                        }
                        val departure = departureEstimate
                        if (departure != null && departure.departureTime > System.currentTimeMillis()) {
                            if (!scheduleReminder(context, "È ora di partire: ${appointment.title}", departure.departureTime)) return@TextButton
                        }
                        if (
                            reminderTime != null &&
                            !scheduleReminder(context, appointment.title, reminderTime)
                        ) {
                            return@TextButton
                        }

                        tasks.add(
                            TaskItem(
                                title = appointment.title,
                                reminderTime = reminderTime,
                                category = assistantCategory,
                                priority = assistantPriority,
                                appointmentTime = appointment.time,
                                location = resolvedPlace?.address ?: appointment.location,
                                latitude = resolvedPlace?.latitude,
                                longitude = resolvedPlace?.longitude
                                ,departureTime = departure?.departureTime,
                                departureTravelMinutes = departure?.travelMinutes,
                                departureMarginMinutes = departure?.marginMinutes,
                                departureTransport = departureTransport,
                                departureSafety = departureSafety
                            )
                        )
                        saveTasks(context, tasks)
                        assistantText = ""
                        assistantResult = null
                        Toast.makeText(
                            context,
                            if (reminderTime == null) {
                                "Appuntamento aggiunto"
                            } else {
                                "Appuntamento e promemoria aggiunti"
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) { Text("Salva appuntamento") }
            },
            dismissButton = {
                TextButton(onClick = { assistantResult = null }) {
                    Text("Annulla")
                }
            }
        )
    }

    if (showReminderChoice) {
        AlertDialog(
            onDismissRequest = { showReminderChoice = false },
            title = { Text("Come vuoi essere avvisato?") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectionMenu(
                        label = "Modalità",
                        selectedValue = taskReminderMode,
                        values = listOf("Nessuno", "Data e ora", "Quando arrivo", "Entrambi"),
                        onValueSelected = { taskReminderMode = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (taskReminderMode == "Data e ora" || taskReminderMode == "Entrambi") {
                        OutlinedButton(
                            onClick = { showReminderPicker(context) { taskReminderTime = it } },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(taskReminderTime?.let { "Promemoria: ${formatReminderTime(it)}" } ?: "Scegli data e ora")
                        }
                    }
                    if (taskReminderMode == "Quando arrivo" || taskReminderMode == "Entrambi") {
                        OutlinedTextField(
                            value = taskLocationQuery,
                            onValueChange = { taskLocationQuery = it; taskResolvedPlace = null },
                            label = { Text("Luogo o indirizzo") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = {
                                if (taskLocationQuery.isBlank()) return@OutlinedButton
                                taskPlaceMessage = "Ricerca in corso…"
                                resolvePlace(context, taskLocationQuery) { place ->
                                    taskResolvedPlace = place
                                    taskPlaceMessage = if (place == null) "Luogo non trovato" else "Luogo trovato: ${place.address}"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cerca luogo") }
                        if (taskPlaceMessage.isNotBlank()) Text(taskPlaceMessage, style = MaterialTheme.typography.bodySmall)
                        taskResolvedPlace?.let { place ->
                            TextButton(onClick = { openPlaceOnMap(context, place.address, place.latitude, place.longitude) }) {
                                Text("Controlla sulla mappa")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val needsTime = taskReminderMode == "Data e ora" || taskReminderMode == "Entrambi"
                        val needsPlace = taskReminderMode == "Quando arrivo" || taskReminderMode == "Entrambi"
                        if (needsTime && (taskReminderTime == null || taskReminderTime!! <= System.currentTimeMillis())) {
                            Toast.makeText(context, "Scegli un orario futuro", Toast.LENGTH_LONG).show(); return@TextButton
                        }
                        val place = taskResolvedPlace
                        if (needsPlace && place == null) {
                            Toast.makeText(context, "Cerca e verifica prima il luogo", Toast.LENGTH_LONG).show(); return@TextButton
                        }
                        if (needsTime && !scheduleReminder(context, pendingTask, taskReminderTime!!)) return@TextButton
                        if (needsPlace) {
                            if (!ensureLocationPermissions(context)) return@TextButton
                            val id = "arrival_${System.currentTimeMillis()}_${pendingTask.hashCode()}"
                            registerArrivalGeofence(context, id, pendingTask, place!!.latitude, place.longitude) { ok ->
                                if (ok) addPendingTask(taskReminderTime, place, id)
                                else Toast.makeText(context, "Attivazione del luogo non riuscita", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            addPendingTask(taskReminderTime)
                        }
                    }
                ) { Text("Salva attività") }
            },
            dismissButton = {
                TextButton(onClick = { showReminderChoice = false }) { Text("Annulla") }
            }
        )
    }

    editingIndex?.let { index ->
        val task = tasks.getOrNull(index)
        if (task != null) {
            AlertDialog(
                onDismissRequest = { editingIndex = null },
                title = { Text("Modifica attività") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editedTitle,
                            onValueChange = { editedTitle = it },
                            label = { Text("Nome attività") },
                            singleLine = true
                        )
                        SelectionMenu(
                            label = "Categoria",
                            selectedValue = editedCategory,
                            values = TASK_CATEGORIES,
                            onValueSelected = { editedCategory = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                        task.appointmentTime?.let { originalAppointmentTime ->
                            OutlinedButton(
                                onClick = {
                                    showDateTimePicker(
                                        context,
                                        editedAppointmentTime ?: originalAppointmentTime
                                    ) { selectedTime ->
                                        val oldReminderTime = task.reminderTime
                                        val oldLeadTime = if (oldReminderTime != null) {
                                            (originalAppointmentTime - oldReminderTime)
                                                .takeIf { it > 0L }
                                                ?: 24L * 60L * 60L * 1000L
                                        } else {
                                            null
                                        }
                                        editedAppointmentTime = selectedTime
                                        editedReminderTime = oldLeadTime?.let {
                                            selectedTime - it
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Appuntamento: ${formatReminderTime(editedAppointmentTime ?: originalAppointmentTime)}"
                                )
                            }
                            editedReminderTime?.let { newReminderTime ->
                                Text(
                                    "Promemoria adattato: ${formatReminderTime(newReminderTime)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        SelectionMenu(
                            label = "Priorità",
                            selectedValue = editedPriority,
                            values = TASK_PRIORITIES,
                            onValueSelected = { editedPriority = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val newTitle = editedTitle.trim()
                            if (newTitle.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    "Il nome non può essere vuoto",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                val now = System.currentTimeMillis()
                                val newAppointmentTime =
                                    editedAppointmentTime ?: task.appointmentTime
                                val newReminderTime = editedReminderTime
                                val appointmentChanged =
                                    newAppointmentTime != task.appointmentTime
                                val reminderChanged =
                                    newReminderTime != task.reminderTime
                                val titleChanged = newTitle != task.title

                                if (
                                    appointmentChanged &&
                                    newAppointmentTime != null &&
                                    newAppointmentTime <= now
                                ) {
                                    Toast.makeText(
                                        context,
                                        "L’appuntamento deve essere nel futuro",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@TextButton
                                }
                                if (
                                    reminderChanged &&
                                    newReminderTime != null &&
                                    (newReminderTime <= now ||
                                        (newAppointmentTime != null &&
                                            newReminderTime >= newAppointmentTime))
                                ) {
                                    Toast.makeText(
                                        context,
                                        "Il nuovo promemoria deve essere futuro e precedente all’appuntamento",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@TextButton
                                }

                                val mustReschedule =
                                    newReminderTime != null &&
                                        newReminderTime > now &&
                                        (reminderChanged || titleChanged)
                                if (mustReschedule) {
                                    if (!scheduleReminder(context, newTitle, newReminderTime)) {
                                        return@TextButton
                                    }
                                }
                                if (
                                    task.reminderTime != null &&
                                    (reminderChanged || titleChanged) &&
                                    task.reminderTime > now
                                ) {
                                    cancelReminder(context, task)
                                }

                                tasks[index] = task.copy(
                                    title = newTitle,
                                    category = editedCategory,
                                    priority = editedPriority,
                                    appointmentTime = newAppointmentTime,
                                    reminderTime = newReminderTime
                                )
                                saveTasks(context, tasks)
                                editingIndex = null
                                editedTitle = ""
                                Toast.makeText(
                                    context,
                                    "Attività aggiornata",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    ) {
                        Text("Salva")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingIndex = null }) {
                        Text("Annulla")
                    }
                }
            )
        } else {
            editingIndex = null
        }
    }

    deletingIndex?.let { index ->
        val task = tasks.getOrNull(index)
        if (task != null) {
            AlertDialog(
                onDismissRequest = { deletingIndex = null },
                title = { Text("Eliminare l’attività?") },
                text = {
                    Text(
                        if (task.reminderTime != null) {
                            "Verrà cancellato anche il promemoria associato."
                        } else {
                            "Questa operazione non può essere annullata."
                        }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            cancelReminder(context, task)
                            cancelDepartureReminder(context, task)
                            task.arrivalReminderId?.let { removeArrivalGeofence(context, it) }
                            tasks.removeAt(index)
                            saveTasks(context, tasks)
                            deletingIndex = null
                            Toast.makeText(
                                context,
                                "Attività eliminata",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Text("Elimina")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingIndex = null }) {
                        Text("Annulla")
                    }
                }
            )
        } else {
            deletingIndex = null
        }
    }
}

private fun showReminderPicker(
    context: Context,
    onSelected: (Long) -> Unit
) {
    val initialTime = Calendar.getInstance().apply {
        add(Calendar.MINUTE, 2)
    }.timeInMillis
    showDateTimePicker(context, initialTime, onSelected)
}

private fun showDateTimePicker(
    context: Context,
    initialTime: Long,
    onSelected: (Long) -> Unit
) {
    val initialCalendar = Calendar.getInstance().apply {
        timeInMillis = initialTime
    }

    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val selectedCalendar = Calendar.getInstance().apply {
                        clear()
                        set(year, month, day, hour, minute, 0)
                    }
                    onSelected(selectedCalendar.timeInMillis)
                },
                initialCalendar.get(Calendar.HOUR_OF_DAY),
                initialCalendar.get(Calendar.MINUTE),
                true
            ).show()
        },
        initialCalendar.get(Calendar.YEAR),
        initialCalendar.get(Calendar.MONTH),
        initialCalendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun appointmentReminderTime(
    appointmentTime: Long,
    option: String,
    customTime: Long?
): Long? {
    if (option == "Nessun promemoria") return null
    if (option == "Personalizzato") return customTime

    return Calendar.getInstance().apply {
        timeInMillis = appointmentTime
        when (option) {
            "48 ore prima" -> add(Calendar.HOUR_OF_DAY, -48)
            "7 giorni prima" -> add(Calendar.DAY_OF_YEAR, -7)
            else -> add(Calendar.HOUR_OF_DAY, -24)
        }
    }.timeInMillis
}

private fun suggestAppointmentCategory(title: String): String {
    val text = title.lowercase(Locale.ITALIAN)
    return when {
        listOf(
            "dentista", "medico", "visita", "farmacia", "ospedale",
            "terapia", "analisi", "palestra", "salute"
        ).any { it in text } -> "Salute"
        listOf(
            "riunione", "cliente", "ufficio", "lavoro", "collega",
            "consegna", "progetto", "responsabile"
        ).any { it in text } -> "Lavoro"
        listOf(
            "spesa", "casa", "bolletta", "pulizia", "manutenzione",
            "idraulico", "elettricista", "tecnico"
        ).any { it in text } -> "Casa"
        else -> "Personale"
    }
}

private fun suggestAppointmentPriority(title: String): String {
    val text = title.lowercase(Locale.ITALIAN)
    return when {
        listOf(
            "urgente", "importante", "scadenza", "subito", "priorità alta"
        ).any { it in text } -> "Alta"
        listOf(
            "facoltativo", "se possibile", "quando posso", "priorità bassa"
        ).any { it in text } -> "Bassa"
        else -> "Media"
    }
}

private fun scheduleReminder(
    context: Context,
    taskTitle: String,
    reminderTime: Long
): Boolean {
    val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        !alarmManager.canScheduleExactAlarms()
    ) {
        Toast.makeText(
            context,
            "Autorizza sveglie e promemoria, poi riprova",
            Toast.LENGTH_LONG
        ).show()

        val permissionIntent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            android.net.Uri.parse("package:${context.packageName}")
        )

        try {
            context.startActivity(permissionIntent)
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
        return false
    }

    val reminderIntent = Intent(context, ReminderReceiver::class.java).apply {
        putExtra("task_title", taskTitle)
    }
    val requestCode = reminderRequestCode(taskTitle, reminderTime)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        reminderIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return try {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminderTime,
            pendingIntent
        )
        true
    } catch (_: SecurityException) {
        Toast.makeText(
            context,
            "Autorizza sveglie e promemoria nelle impostazioni",
            Toast.LENGTH_LONG
        ).show()
        false
    }
}

private fun cancelReminder(context: Context, task: TaskItem) {
    val reminderTime = task.reminderTime ?: return
    val reminderIntent = Intent(context, ReminderReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        reminderRequestCode(task.title, reminderTime),
        reminderIntent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    ) ?: return

    val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.cancel(pendingIntent)
    pendingIntent.cancel()
}

private fun cancelDepartureReminder(context: Context, task: TaskItem) {
    val departureTime = task.departureTime ?: return
    val alarmTitle = "È ora di partire: ${task.title}"
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        reminderRequestCode(alarmTitle, departureTime),
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    ) ?: return
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.cancel(pendingIntent)
    pendingIntent.cancel()
}

internal fun reminderRequestCode(taskTitle: String, reminderTime: Long): Int =
    (reminderTime xor taskTitle.hashCode().toLong()).hashCode()

private fun formatReminderTime(time: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        .format(Date(time))

@Composable
private fun SelectionMenu(
    label: String,
    selectedValue: String,
    values: List<String>,
    onValueSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("$label: $selectedValue")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = {
                        onValueSelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun priorityColor(priority: String) = when (priority) {
    "Alta" -> MaterialTheme.colorScheme.error
    "Bassa" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

internal fun loadTasks(context: Context): List<TaskItem> {
    val preferences = context.getSharedPreferences(TASK_PREFS, Context.MODE_PRIVATE)
    val savedJson = preferences.getString(TASKS_KEY, null)
        ?: return defaultTasks()

    mirrorTasksForBoot(context, savedJson)

    return parseTasks(savedJson)
}

internal fun loadTasksForBoot(context: Context): List<TaskItem> {
    val bootContext = context.createDeviceProtectedStorageContext()
    val savedJson = bootContext
        .getSharedPreferences(TASK_PREFS, Context.MODE_PRIVATE)
        .getString(TASKS_KEY, null)
        ?: return emptyList()

    return parseTasks(savedJson, emptyList())
}

private fun parseTasks(
    savedJson: String,
    fallback: List<TaskItem> = defaultTasks()
): List<TaskItem> {
    return try {
        val array = JSONArray(savedJson)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            TaskItem(
                title = item.getString("title"),
                completed = item.optBoolean("completed", false),
                reminderTime = if (item.isNull("reminderTime")) {
                    null
                } else {
                    item.getLong("reminderTime")
                },
                category = item.optString("category", "Personale"),
                priority = item.optString("priority", "Media"),
                appointmentTime = if (item.isNull("appointmentTime")) {
                    null
                } else {
                    item.optLong("appointmentTime")
                },
                location = if (item.isNull("location")) {
                    null
                } else {
                    item.optString("location").takeIf { it.isNotBlank() }
                },
                latitude = if (item.isNull("latitude")) null else item.optDouble("latitude"),
                longitude = if (item.isNull("longitude")) null else item.optDouble("longitude"),
                arrivalReminderId = if (item.isNull("arrivalReminderId")) null
                    else item.optString("arrivalReminderId").takeIf { it.isNotBlank() },
                departureTime = if (item.isNull("departureTime")) null else item.optLong("departureTime"),
                departureTravelMinutes = if (item.isNull("departureTravelMinutes")) null else item.optInt("departureTravelMinutes"),
                departureMarginMinutes = if (item.isNull("departureMarginMinutes")) null else item.optInt("departureMarginMinutes"),
                departureTransport = item.optString("departureTransport", "Auto"),
                departureSafety = item.optString("departureSafety", "Normale")
            )
        }
    } catch (_: Exception) {
        fallback
    }
}

internal fun saveTasks(context: Context, tasks: List<TaskItem>) {
    val array = JSONArray()
    tasks.forEach { task ->
        array.put(
            JSONObject().apply {
                put("title", task.title)
                put("completed", task.completed)
                put("reminderTime", task.reminderTime ?: JSONObject.NULL)
                put("category", task.category)
                put("priority", task.priority)
                put("appointmentTime", task.appointmentTime ?: JSONObject.NULL)
                put("location", task.location ?: JSONObject.NULL)
                put("latitude", task.latitude ?: JSONObject.NULL)
                put("longitude", task.longitude ?: JSONObject.NULL)
                put("arrivalReminderId", task.arrivalReminderId ?: JSONObject.NULL)
                put("departureTime", task.departureTime ?: JSONObject.NULL)
                put("departureTravelMinutes", task.departureTravelMinutes ?: JSONObject.NULL)
                put("departureMarginMinutes", task.departureMarginMinutes ?: JSONObject.NULL)
                put("departureTransport", task.departureTransport)
                put("departureSafety", task.departureSafety)
            }
        )
    }

    val savedJson = array.toString()

    context.getSharedPreferences(TASK_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(TASKS_KEY, savedJson)
        .apply()

    mirrorTasksForBoot(context, savedJson)
}

private fun mirrorTasksForBoot(context: Context, savedJson: String) {
    context.createDeviceProtectedStorageContext()
        .getSharedPreferences(TASK_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(TASKS_KEY, savedJson)
        .apply()
}

private fun defaultTasks(): List<TaskItem> = listOf(
    TaskItem("Controllare gli impegni di oggi", category = "Personale"),
    TaskItem("Fare una pausa di 10 minuti", category = "Salute"),
    TaskItem("Preparare le cose per domani", category = "Casa")
)

private fun resolvePlace(
    context: Context,
    query: String,
    onResult: (ResolvedPlace?) -> Unit
) {
    if (!Geocoder.isPresent()) {
        onResult(null)
        return
    }

    val geocoder = Geocoder(context, Locale.ITALIAN)
    val searchQuery = if (query.contains("italia", ignoreCase = true)) {
        query
    } else {
        "$query, Italia"
    }
    val deliver: (List<Address>?) -> Unit = { addresses ->
        val address = addresses?.firstOrNull()
        val place = address?.let {
            ResolvedPlace(
                address = it.getAddressLine(0) ?: query,
                latitude = it.latitude,
                longitude = it.longitude
            )
        }
        Handler(Looper.getMainLooper()).post { onResult(place) }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        geocoder.getFromLocationName(
            searchQuery,
            1,
            object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    deliver(addresses)
                }

                override fun onError(errorMessage: String?) {
                    deliver(null)
                }
            }
        )
    } else {
        Thread {
            val addresses = try {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(searchQuery, 1)
            } catch (_: Exception) {
                null
            }
            deliver(addresses)
        }.start()
    }
}

private fun openPlaceOnMap(
    context: Context,
    label: String,
    latitude: Double?,
    longitude: Double?
) {
    val query = if (latitude != null && longitude != null) {
        "${latitude},${longitude}($label)"
    } else {
        label
    }
    val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("geo:0,0?q=${Uri.encode(query)}")
    )
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(
            context,
            "Installa o abilita un’app di mappe",
            Toast.LENGTH_LONG
        ).show()
    }
}
