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
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
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

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var showSetup by remember {
                        mutableStateOf(!isInitialSetupComplete(this@MainActivity))
                    }
                    if (showSetup) {
                        InitialSetupScreen(
                            onComplete = {
                                markInitialSetupComplete(this@MainActivity)
                                restoreAllFutureAutomations(this@MainActivity)
                                showSetup = false
                            },
                            onClose = if (isInitialSetupComplete(this@MainActivity)) {
                                { showSetup = false }
                            } else null
                        )
                    } else {
                        FaccioIoApp(onOpenSetup = { showSetup = true })
                    }
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
    val departureSafety: String = "Normale",
    val recurrence: String = "Mai",
    val recurrenceIntervalDays: Int = 1,
    val durationMinutes: Int = 30,
    val routineSteps: List<RoutineStep> = emptyList()
)

data class RoutineStep(val title: String, val completed: Boolean = false)

data class ResolvedPlace(
    val address: String,
    val latitude: Double,
    val longitude: Double
)

internal const val TASK_PREFS = "faccio_io_tasks"
internal const val TASKS_KEY = "saved_tasks"
private val TASK_CATEGORIES = listOf("Casa", "Lavoro", "Salute", "Personale")
private val TASK_PRIORITIES = listOf("Bassa", "Media", "Alta")
private val TASK_RECURRENCES = listOf("Mai", "Ogni giorno", "Ogni settimana", "Ogni mese", "Personalizzata")
private val APPOINTMENT_REMINDER_OPTIONS = listOf(
    "24 ore prima",
    "48 ore prima",
    "7 giorni prima",
    "Personalizzato",
    "Nessun promemoria"
)

@Composable
fun FaccioIoApp(onOpenSetup: () -> Unit = {}) {
    val context = LocalContext.current
    var newTask by rememberSaveable { mutableStateOf("") }
    var pendingTask by rememberSaveable { mutableStateOf("") }
    var showReminderChoice by rememberSaveable { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf("Personale") }
    var selectedPriority by rememberSaveable { mutableStateOf("Media") }
    var pendingCategory by rememberSaveable { mutableStateOf("Personale") }
    var pendingPriority by rememberSaveable { mutableStateOf("Media") }
    var pendingRoutineSteps by remember { mutableStateOf<List<RoutineStep>>(emptyList()) }
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var deletingIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var editedTitle by rememberSaveable { mutableStateOf("") }
    var editedCategory by rememberSaveable { mutableStateOf("Personale") }
    var editedPriority by rememberSaveable { mutableStateOf("Media") }
    var editedAppointmentTime by rememberSaveable { mutableStateOf<Long?>(null) }
    var editedReminderTime by rememberSaveable { mutableStateOf<Long?>(null) }
    var editedRecurrence by rememberSaveable { mutableStateOf("Mai") }
    var editedRecurrenceDays by rememberSaveable { mutableStateOf("1") }
    var editedDuration by rememberSaveable { mutableStateOf("30 minuti") }
    var editedCustomDuration by rememberSaveable { mutableStateOf("30") }
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
    var assistantDuration by rememberSaveable { mutableStateOf("30 minuti") }
    var assistantCustomDuration by rememberSaveable { mutableStateOf("30") }
    var taskReminderMode by rememberSaveable { mutableStateOf("Nessuno") }
    var taskReminderTime by rememberSaveable { mutableStateOf<Long?>(null) }
    var taskRecurrence by rememberSaveable { mutableStateOf("Mai") }
    var taskRecurrenceDays by rememberSaveable { mutableStateOf("1") }
    var taskDuration by rememberSaveable { mutableStateOf("30 minuti") }
    var taskCustomDuration by rememberSaveable { mutableStateOf("30") }
    var taskLocationQuery by rememberSaveable { mutableStateOf("") }
    var taskResolvedPlace by remember { mutableStateOf<ResolvedPlace?>(null) }
    var taskPlaceMessage by rememberSaveable { mutableStateOf("") }
    var departureTransport by rememberSaveable { mutableStateOf("Auto") }
    var departureSafety by rememberSaveable { mutableStateOf("Normale") }
    var departureEstimate by remember { mutableStateOf<DepartureEstimate?>(null) }
    var mainSection by rememberSaveable { mutableStateOf("Oggi") }
    var showTaskSearch by rememberSaveable { mutableStateOf(false) }
    var taskSearchQuery by rememberSaveable { mutableStateOf("") }
    var showRoutineTemplates by rememberSaveable { mutableStateOf(false) }
    var showCustomRoutineEditor by rememberSaveable { mutableStateOf(false) }
    var customRoutineName by rememberSaveable { mutableStateOf("") }
    var customRoutineCategory by rememberSaveable { mutableStateOf("Personale") }
    var customRoutinePriority by rememberSaveable { mutableStateOf("Media") }
    val customRoutineSteps = remember { mutableStateListOf("") }
    val customRoutineTemplates = remember(context) {
        mutableStateListOf<TaskItem>().apply { addAll(loadCustomRoutineTemplates(context)) }
    }

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
    var pendingBackupRestore by remember { mutableStateOf<BackupPayload?>(null) }
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val ok = exportCompleteBackup(context, uri)
            Toast.makeText(
                context,
                if (ok) "Backup esportato" else "Esportazione non riuscita",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val payload = readCompleteBackup(context, uri)
            if (payload == null) {
                Toast.makeText(context, "Il file non è un backup valido di Faccio io", Toast.LENGTH_LONG).show()
            } else {
                pendingBackupRestore = payload
            }
        }
    }

    val normalizedSearch = taskSearchQuery.trim().lowercase(Locale.ITALIAN)
    val visibleTasks = tasks.withIndex().filter { indexedTask ->
        val task = indexedTask.value
        (categoryFilter == "Tutte" || task.category == categoryFilter) &&
            (priorityFilter == "Tutte" || task.priority == priorityFilter) &&
            (
                normalizedSearch.isEmpty() ||
                    task.title.lowercase(Locale.ITALIAN).contains(normalizedSearch) ||
                    task.location.orEmpty().lowercase(Locale.ITALIAN).contains(normalizedSearch) ||
                    task.category.lowercase(Locale.ITALIAN).contains(normalizedSearch)
                )
    }

    fun addPendingTask(
        reminderTime: Long? = null,
        place: ResolvedPlace? = null,
        arrivalId: String? = null,
        recurrence: String = "Mai",
        recurrenceDays: Int = 1,
        durationMinutes: Int = 30
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
                arrivalReminderId = arrivalId,
                recurrence = recurrence,
                recurrenceIntervalDays = recurrenceDays,
                durationMinutes = durationMinutes,
                routineSteps = pendingRoutineSteps
            )
        )
        saveTasks(context, tasks)
        newTask = ""
        pendingTask = ""
        selectedCategory = "Personale"
        selectedPriority = "Media"
        pendingCategory = "Personale"
        pendingPriority = "Media"
        pendingRoutineSteps = emptyList()
        showReminderChoice = false
        taskReminderMode = "Nessuno"
        taskReminderTime = null
        taskRecurrence = "Mai"
        taskRecurrenceDays = "1"
        taskDuration = "30 minuti"
        taskCustomDuration = "30"
        taskLocationQuery = ""
        taskResolvedPlace = null
        taskPlaceMessage = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Faccio io",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = {
                    if (showTaskSearch) {
                        taskSearchQuery = ""
                        showTaskSearch = false
                    } else {
                        mainSection = "Attività"
                        showTaskSearch = true
                    }
                }
            ) {
                Icon(
                    imageVector = if (showTaskSearch) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (showTaskSearch) "Chiudi ricerca" else "Cerca attività"
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (mainSection == "Oggi") {
                Button(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = FaccioNavy),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("Oggi") }
            } else {
                OutlinedButton(
                    onClick = { mainSection = "Oggi" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FaccioNavy),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("Oggi") }
            }
            if (mainSection == "Attività") {
                Button(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = FaccioNavy),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("Attività") }
            } else {
                OutlinedButton(
                    onClick = { mainSection = "Attività" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FaccioNavy),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("Attività") }
            }
            if (mainSection == "Strumenti") {
                Button(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = FaccioNavy),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("Strumenti") }
            } else {
                OutlinedButton(
                    onClick = { mainSection = "Strumenti" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FaccioNavy),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("Strumenti") }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (mainSection == "Oggi") {
            TodayAgenda(
                tasks = tasks,
                onCompletedChange = { index, completed ->
                    updateTaskCompletion(context, tasks, index, completed)
                },
                onStepChange = { index, stepIndex, completed ->
                    updateRoutineStep(context, tasks, index, stepIndex, completed)
                },
                onOpenMap = { task ->
                    openPlaceOnMap(
                        context,
                        task.location.orEmpty(),
                        task.latitude,
                        task.longitude
                    )
                },
                onAddTask = { mainSection = "Attività" },
                modifier = Modifier.weight(1f)
            )
        } else if (mainSection == "Attività") {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Attività", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = FaccioNavy)
            Text("${visibleTasks.size} visibili", style = MaterialTheme.typography.labelMedium, color = FaccioMutedText)
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (showTaskSearch) {
            OutlinedTextField(
                value = taskSearchQuery,
                onValueChange = { taskSearchQuery = it },
                label = { Text("Cerca attività") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = if (taskSearchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { taskSearchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancella ricerca")
                        }
                    }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FaccioTeal,
                    focusedLabelColor = FaccioTeal,
                    focusedLeadingIconColor = FaccioTeal
                )
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = newTask,
            onValueChange = { newTask = it },
            label = { Text("Cosa devi fare?") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FaccioTeal,
                focusedLabelColor = FaccioTeal
            )
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

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                    pendingRoutineSteps = emptyList()
                    taskReminderMode = "Nessuno"
                    taskReminderTime = null
                    taskRecurrence = "Mai"
                    taskRecurrenceDays = "1"
                    taskDuration = "30 minuti"
                    taskCustomDuration = "30"
                    taskLocationQuery = ""
                    taskResolvedPlace = null
                    showReminderChoice = true
                }
            },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = FaccioNavy),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Aggiungi")
        }

        OutlinedButton(
            onClick = { showAssistant = true },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = FaccioTeal),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text("Assistente IA")
        }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Le tue attività",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = FaccioNavy
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
                        text = if (normalizedSearch.isNotEmpty()) {
                            "Nessuna attività trovata per “${taskSearchQuery.trim()}”."
                        } else {
                            "Nessuna attività corrisponde ai filtri selezionati."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            items(visibleTasks) { indexedTask ->
                val index = indexedTask.index
                val task = indexedTask.value
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Checkbox(
                                checked = task.completed,
                                onCheckedChange = { checked ->
                                    updateTaskCompletion(context, tasks, index, checked)
                                },
                                modifier = Modifier.size(40.dp),
                                colors = CheckboxDefaults.colors(checkedColor = FaccioTeal)
                            )

                            Box(
                                modifier = Modifier
                                    .padding(top = 10.dp, start = 2.dp, end = 9.dp)
                                    .size(9.dp)
                                    .background(taskCategoryColor(task.category), RoundedCornerShape(50))
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = task.title, fontWeight = FontWeight.SemiBold, color = FaccioNavy)
                                Text(
                                    text = "${task.category} · ${formatDuration(task.durationMinutes)} · Priorità ${task.priority}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FaccioMutedText
                                )
                                task.reminderTime?.let { selectedTime ->
                                    Text(
                                        text = "Promemoria: ${formatReminderTime(selectedTime)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FaccioTeal
                                    )
                                }
                                task.appointmentTime?.let { appointmentTime ->
                                    Text(
                                        text = "Appuntamento: ${formatReminderTime(appointmentTime)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FaccioNavy
                                    )
                                }
                                task.departureTime?.let { departureTime ->
                                    Text("Partenza: ${formatReminderTime(departureTime)}", style = MaterialTheme.typography.bodySmall, color = FaccioTeal)
                                    Text("${task.departureTravelMinutes ?: 0} min + ${task.departureMarginMinutes ?: 0} min di margine", style = MaterialTheme.typography.bodySmall, color = FaccioMutedText)
                                }
                                task.location?.let { location ->
                                    Text(
                                        text = location,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FaccioMutedText
                                    )
                                }
                                if (task.recurrence != "Mai") {
                                    Text(
                                        text = recurrenceLabel(task),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                task.routineSteps.forEachIndexed { stepIndex, step ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = step.completed,
                                            onCheckedChange = {
                                                updateRoutineStep(context, tasks, index, stepIndex, it)
                                            }
                                        )
                                        Text(step.title, style = MaterialTheme.typography.bodySmall)
                                    }
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
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Text("Mappa", color = FaccioTeal)
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
                                                    val now = System.currentTimeMillis()
                                                    if (estimate.departureTime <= now) {
                                                        Toast.makeText(context, "L’ora stimata è già trascorsa: parti appena possibile", Toast.LENGTH_LONG).show()
                                                        return@estimateDepartureFromCurrentLocation
                                                    }
                                                    val changed = estimate.departureTime != task.departureTime
                                                    if (changed && !scheduleReminder(context, "È ora di partire: ${task.title}", estimate.departureTime)) {
                                                        return@estimateDepartureFromCurrentLocation
                                                    }
                                                    if (changed) cancelDepartureReminder(context, task)
                                                    tasks[index] = task.copy(
                                                        departureTime = estimate.departureTime,
                                                        departureTravelMinutes = estimate.travelMinutes,
                                                        departureMarginMinutes = estimate.marginMinutes
                                                    )
                                                    saveTasks(context, tasks)
                                                    Toast.makeText(context, "Avviso di partenza aggiornato automaticamente", Toast.LENGTH_SHORT).show()
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
                                    editedRecurrence = task.recurrence
                                    editedRecurrenceDays = task.recurrenceIntervalDays.toString()
                                    editedDuration = durationOption(task.durationMinutes)
                                    editedCustomDuration = task.durationMinutes.toString()
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("Modifica", color = FaccioTeal)
                            }
                            TextButton(
                                onClick = { deletingIndex = index },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("Elimina", color = FaccioCoral)
                            }
                        }
                    }
                }
            }
        }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Strumenti",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = FaccioNavy
                )
                Text(
                    text = "Tutto ciò che ti aiuta a organizzarti e a proteggere i tuoi dati.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FaccioMutedText
                )

                Spacer(modifier = Modifier.height(2.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .fillMaxHeight()
                                .background(FaccioTeal)
                        )
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Routine",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = FaccioNavy
                            )
                            Text(
                                "Scegli una sequenza pronta o crea la tua routine personale.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = FaccioMutedText
                            )
                            Button(
                                onClick = { showRoutineTemplates = true },
                                colors = ButtonDefaults.buttonColors(containerColor = FaccioNavy),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                            ) { Text("Scegli una routine") }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF3978C5))
                        )
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Backup",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = FaccioNavy
                            )
                            Text(
                                "Metti al sicuro attività, promemoria e routine.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = FaccioMutedText
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                        exportBackupLauncher.launch("Faccio-io-backup-$date.json")
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FaccioNavy),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
                                ) { Text("Esporta") }
                                OutlinedButton(
                                    onClick = {
                                        importBackupLauncher.launch(
                                            arrayOf("application/json", "text/plain")
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FaccioNavy),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
                                ) { Text("Ripristina") }
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .fillMaxHeight()
                                .background(FaccioAmber)
                        )
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Configurazione",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = FaccioNavy
                            )
                            Text(
                                "Verifica notifiche, posizione, batteria e attività in background.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = FaccioMutedText
                            )
                            OutlinedButton(
                                onClick = onOpenSetup,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = FaccioNavy),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                            ) { Text("Controlla impostazioni") }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = FaccioCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .fillMaxHeight()
                                .background(FaccioCoral)
                        )
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Widget",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = FaccioNavy
                            )
                            Text(
                                "Aggiungilo dalla schermata Home per vedere subito la prossima attività.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = FaccioMutedText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }

    if (showAssistant) {
        AlertDialog(
            onDismissRequest = { showAssistant = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            tonalElevation = 3.dp,
            titleContentColor = FaccioNavy,
            textContentColor = FaccioMutedText,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 5.dp, height = 28.dp)
                            .background(FaccioTeal, RoundedCornerShape(50))
                    )
                    Column {
                        Text(
                            "Assistente IA",
                            fontWeight = FontWeight.Bold,
                            color = FaccioNavy
                        )
                        Text(
                            "Trasforma una frase in un appuntamento",
                            style = MaterialTheme.typography.bodySmall,
                            color = FaccioMutedText
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Scrivi o detta ciò che devi fare. Per esempio: “Domani alle 15 dentista in via Roma 10”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FaccioMutedText
                    )
                    OutlinedTextField(
                        value = assistantText,
                        onValueChange = { assistantText = it },
                        label = { Text("Cosa devo organizzare?") },
                        placeholder = { Text("Data, ora, attività e luogo") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FaccioTeal,
                            focusedLabelColor = FaccioTeal,
                            cursorColor = FaccioTeal
                        )
                    )
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                                putExtra(
                                    RecognizerIntent.EXTRA_PROMPT,
                                    "Descrivi l’appuntamento"
                                )
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
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = FaccioNavy
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(
                            horizontal = 12.dp,
                            vertical = 7.dp
                        )
                    ) {
                        Text("Detta con la voce")
                    }
                }
            },
            confirmButton = {
                Button(
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
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FaccioNavy),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                ) { Text("Interpreta") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAssistant = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = FaccioMutedText)
                ) { Text("Annulla") }
            }
        )
    }

    pendingBackupRestore?.let { payload ->
        AlertDialog(
            onDismissRequest = { pendingBackupRestore = null },
            title = { Text("Ripristinare il backup?") },
            text = {
                Text(
                    "Verranno sostituite le attività e le routine presenti con ${payload.tasks.size} attività e ${payload.customTemplates.size} modelli del backup. I permessi del telefono dovranno essere verificati nuovamente."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val restored = applyCompleteBackup(context, payload)
                        if (restored) {
                            tasks.clear()
                            tasks.addAll(loadTasks(context))
                            customRoutineTemplates.clear()
                            customRoutineTemplates.addAll(loadCustomRoutineTemplates(context))
                            onOpenSetup()
                            Toast.makeText(context, "Backup ripristinato", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Ripristino non riuscito", Toast.LENGTH_LONG).show()
                        }
                        pendingBackupRestore = null
                    }
                ) { Text("Sostituisci e ripristina") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBackupRestore = null }) { Text("Annulla") }
            }
        )
    }

    if (showRoutineTemplates) {
        AlertDialog(
            onDismissRequest = { showRoutineTemplates = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            tonalElevation = 3.dp,
            titleContentColor = FaccioNavy,
            textContentColor = FaccioMutedText,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 5.dp, height = 32.dp)
                            .background(FaccioTeal, RoundedCornerShape(50))
                    )
                    Column {
                        Text(
                            "Scegli una routine",
                            fontWeight = FontWeight.Bold,
                            color = FaccioNavy
                        )
                        Text(
                            "Una sequenza pronta, un pensiero in meno",
                            style = MaterialTheme.typography.bodySmall,
                            color = FaccioMutedText
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (routineTemplates().isNotEmpty()) {
                        Text(
                            "ROUTINE PRONTE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = FaccioTeal
                        )
                    }
                    routineTemplates().forEach { template ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = FaccioCard),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    template.title,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FaccioNavy
                                )
                                Text(
                                    "${template.routineSteps.size} passaggi • ${formatDuration(template.durationMinutes)} • ${template.category}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FaccioMutedText
                                )
                                Button(
                                    onClick = {
                                        pendingTask = template.title
                                        pendingCategory = template.category
                                        pendingPriority = template.priority
                                        pendingRoutineSteps = template.routineSteps
                                        taskDuration = durationOption(template.durationMinutes)
                                        taskCustomDuration = template.durationMinutes.toString()
                                        showRoutineTemplates = false
                                        showReminderChoice = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = FaccioNavy),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                                ) { Text("Usa questa routine") }
                            }
                        }
                    }

                    if (customRoutineTemplates.isNotEmpty()) {
                        Text(
                            "LE MIE ROUTINE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = FaccioTeal,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    customRoutineTemplates.forEach { template ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F5FF)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    template.title,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FaccioNavy
                                )
                                Text(
                                    "${template.routineSteps.size} passaggi • ${formatDuration(template.durationMinutes)} • ${template.category}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FaccioMutedText
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            pendingTask = template.title
                                            pendingCategory = template.category
                                            pendingPriority = template.priority
                                            pendingRoutineSteps = template.routineSteps
                                            taskDuration = durationOption(template.durationMinutes)
                                            taskCustomDuration = template.durationMinutes.toString()
                                            showRoutineTemplates = false
                                            showReminderChoice = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = FaccioNavy),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                                    ) { Text("Usa") }
                                    TextButton(
                                        onClick = {
                                            customRoutineTemplates.remove(template)
                                            saveCustomRoutineTemplates(
                                                context,
                                                customRoutineTemplates
                                            )
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = FaccioCoral
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) { Text("Elimina") }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            customRoutineName = ""
                            customRoutineCategory = "Personale"
                            customRoutinePriority = "Media"
                            customRoutineSteps.clear()
                            customRoutineSteps.add("")
                            showRoutineTemplates = false
                            showCustomRoutineEditor = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FaccioNavy),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                    ) { Text("Crea una nuova routine") }

                    Text(
                        "Dopo la scelta potrai impostare durata, ripetizione, promemoria e luogo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FaccioMutedText
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showRoutineTemplates = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = FaccioMutedText)
                ) { Text("Chiudi") }
            }
        )
    }

    if (showCustomRoutineEditor) {
        AlertDialog(
            onDismissRequest = { showCustomRoutineEditor = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            tonalElevation = 3.dp,
            titleContentColor = FaccioNavy,
            textContentColor = FaccioMutedText,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 5.dp, height = 32.dp)
                            .background(FaccioTeal, RoundedCornerShape(50))
                    )
                    Column {
                        Text(
                            "Nuova routine",
                            fontWeight = FontWeight.Bold,
                            color = FaccioNavy
                        )
                        Text(
                            "Costruiscila un passaggio alla volta",
                            style = MaterialTheme.typography.bodySmall,
                            color = FaccioMutedText
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = customRoutineName,
                        onValueChange = { customRoutineName = it },
                        label = { Text("Nome della routine") },
                        placeholder = { Text("Es. Prepararmi per il lavoro") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FaccioTeal,
                            focusedLabelColor = FaccioTeal,
                            cursorColor = FaccioTeal
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SelectionMenu(
                            "Categoria",
                            customRoutineCategory,
                            TASK_CATEGORIES,
                            { customRoutineCategory = it },
                            Modifier.weight(1f)
                        )
                        SelectionMenu(
                            "Priorità",
                            customRoutinePriority,
                            TASK_PRIORITIES,
                            { customRoutinePriority = it },
                            Modifier.weight(1f)
                        )
                    }
                    Text(
                        "PASSAGGI",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = FaccioTeal
                    )
                    customRoutineSteps.forEachIndexed { index, step ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(FaccioCard, RoundedCornerShape(50)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = FaccioNavy
                                )
                            }
                            OutlinedTextField(
                                value = step,
                                onValueChange = { customRoutineSteps[index] = it },
                                label = { Text("Cosa devi fare?") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = FaccioTeal,
                                    focusedLabelColor = FaccioTeal,
                                    cursorColor = FaccioTeal
                                )
                            )
                            TextButton(
                                onClick = {
                                    if (customRoutineSteps.size > 1) {
                                        customRoutineSteps.removeAt(index)
                                    } else {
                                        customRoutineSteps[0] = ""
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = FaccioCoral
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text("×") }
                        }
                    }
                    OutlinedButton(
                        onClick = { customRoutineSteps.add("") },
                        enabled = customRoutineSteps.size < 20,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FaccioNavy),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Aggiungi passaggio")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = customRoutineName.trim()
                        val steps = customRoutineSteps
                            .map(String::trim)
                            .filter(String::isNotBlank)
                        if (name.isBlank() || steps.isEmpty()) {
                            Toast.makeText(
                                context,
                                "Inserisci un nome e almeno un passaggio",
                                Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }
                        if ((routineTemplates() + customRoutineTemplates).any {
                                it.title.equals(name, ignoreCase = true)
                            }) {
                            Toast.makeText(
                                context,
                                "Esiste già una routine con questo nome",
                                Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }
                        val template = TaskItem(
                            title = name,
                            category = customRoutineCategory,
                            priority = customRoutinePriority,
                            durationMinutes = (steps.size * 5).coerceAtLeast(10),
                            routineSteps = steps.map { RoutineStep(it) }
                        )
                        customRoutineTemplates.add(template)
                        saveCustomRoutineTemplates(context, customRoutineTemplates)
                        pendingTask = template.title
                        pendingCategory = template.category
                        pendingPriority = template.priority
                        pendingRoutineSteps = template.routineSteps
                        taskDuration = durationOption(template.durationMinutes)
                        taskCustomDuration = template.durationMinutes.toString()
                        showCustomRoutineEditor = false
                        showReminderChoice = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FaccioNavy),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                ) { Text("Salva e configura") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCustomRoutineEditor = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = FaccioMutedText)
                ) { Text("Annulla") }
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
                    DurationSelector(
                        selected = assistantDuration,
                        customValue = assistantCustomDuration,
                        onSelected = { assistantDuration = it },
                        onCustomChanged = { assistantCustomDuration = it }
                    )
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
                        Text(
                            if (estimate.departureTime > System.currentTimeMillis()) {
                                "L’avviso verrà programmato automaticamente al salvataggio."
                            } else {
                                "L’ora stimata è già trascorsa: parti appena possibile."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
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
                        val durationMinutes = selectedDurationMinutes(
                            assistantDuration,
                            assistantCustomDuration
                        )
                        if (durationMinutes !in 5..720) {
                            Toast.makeText(context, "Inserisci una durata da 5 minuti a 12 ore", Toast.LENGTH_LONG).show()
                            return@TextButton
                        }
                        if (departure != null && departure.departureTime <= System.currentTimeMillis()) {
                            Toast.makeText(context, "La partenza consigliata è già trascorsa: ricalcola o modifica l’appuntamento", Toast.LENGTH_LONG).show()
                            return@TextButton
                        }
                        if (departure != null) {
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
                                departureSafety = departureSafety,
                                durationMinutes = durationMinutes
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
                    SelectionMenu(
                        label = "Ripetizione",
                        selectedValue = taskRecurrence,
                        values = TASK_RECURRENCES,
                        onValueSelected = { taskRecurrence = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (taskRecurrence == "Personalizzata") {
                        OutlinedTextField(
                            value = taskRecurrenceDays,
                            onValueChange = { taskRecurrenceDays = it.filter(Char::isDigit).take(3) },
                            label = { Text("Ripeti ogni quanti giorni?") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    DurationSelector(
                        selected = taskDuration,
                        customValue = taskCustomDuration,
                        onSelected = { taskDuration = it },
                        onCustomChanged = { taskCustomDuration = it }
                    )
                    if (
                        taskReminderMode == "Data e ora" ||
                        taskReminderMode == "Entrambi" ||
                        taskRecurrence != "Mai"
                    ) {
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
                        val needsTime = taskReminderMode == "Data e ora" ||
                            taskReminderMode == "Entrambi" || taskRecurrence != "Mai"
                        val needsPlace = taskReminderMode == "Quando arrivo" || taskReminderMode == "Entrambi"
                        val recurrenceDays = taskRecurrenceDays.toIntOrNull() ?: 0
                        val durationMinutes = selectedDurationMinutes(taskDuration, taskCustomDuration)
                        if (durationMinutes !in 5..720) {
                            Toast.makeText(context, "Inserisci una durata da 5 minuti a 12 ore", Toast.LENGTH_LONG).show(); return@TextButton
                        }
                        if (taskRecurrence == "Personalizzata" && recurrenceDays !in 1..365) {
                            Toast.makeText(context, "Inserisci un intervallo da 1 a 365 giorni", Toast.LENGTH_LONG).show(); return@TextButton
                        }
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
                                if (ok) addPendingTask(taskReminderTime, place, id, taskRecurrence, recurrenceDays.coerceAtLeast(1), durationMinutes)
                                else Toast.makeText(context, "Attivazione del luogo non riuscita", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            addPendingTask(taskReminderTime, recurrence = taskRecurrence, recurrenceDays = recurrenceDays.coerceAtLeast(1), durationMinutes = durationMinutes)
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
                        SelectionMenu(
                            label = "Ripetizione",
                            selectedValue = editedRecurrence,
                            values = TASK_RECURRENCES,
                            onValueSelected = { editedRecurrence = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (editedRecurrence == "Personalizzata") {
                            OutlinedTextField(
                                value = editedRecurrenceDays,
                                onValueChange = { editedRecurrenceDays = it.filter(Char::isDigit).take(3) },
                                label = { Text("Ripeti ogni quanti giorni?") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        DurationSelector(
                            selected = editedDuration,
                            customValue = editedCustomDuration,
                            onSelected = { editedDuration = it },
                            onCustomChanged = { editedCustomDuration = it }
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
                                val recurrenceDays = editedRecurrenceDays.toIntOrNull() ?: 0
                                val durationMinutes = selectedDurationMinutes(editedDuration, editedCustomDuration)
                                if (durationMinutes !in 5..720) {
                                    Toast.makeText(context, "Inserisci una durata da 5 minuti a 12 ore", Toast.LENGTH_LONG).show()
                                    return@TextButton
                                }
                                if (editedRecurrence == "Personalizzata" && recurrenceDays !in 1..365) {
                                    Toast.makeText(context, "Inserisci un intervallo da 1 a 365 giorni", Toast.LENGTH_LONG).show()
                                    return@TextButton
                                }
                                if (editedRecurrence != "Mai" && editedReminderTime == null && task.appointmentTime == null) {
                                    Toast.makeText(context, "Una ripetizione richiede una data e un orario", Toast.LENGTH_LONG).show()
                                    return@TextButton
                                }
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
                                    reminderTime = newReminderTime,
                                    recurrence = editedRecurrence,
                                    recurrenceIntervalDays = recurrenceDays.coerceAtLeast(1),
                                    durationMinutes = durationMinutes
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

private data class AgendaEntry(
    val index: Int,
    val task: TaskItem,
    val time: Long
)

private val FaccioNavy = Color(0xFF173A5E)
private val FaccioTeal = Color(0xFF188C8C)
private val FaccioCoral = Color(0xFFE57868)
private val FaccioAmber = Color(0xFFF1AD43)
private val FaccioCard = Color(0xFFF4F7FA)
private val FaccioMutedText = Color(0xFF5E6875)

private fun taskCategoryColor(category: String): Color = when (category) {
    "Salute" -> FaccioCoral
    "Casa" -> FaccioAmber
    "Lavoro" -> FaccioTeal
    else -> Color(0xFF3978C5)
}

@Composable
private fun TodayAgenda(
    tasks: List<TaskItem>,
    onCompletedChange: (Int, Boolean) -> Unit,
    onStepChange: (Int, Int, Boolean) -> Unit,
    onOpenMap: (TaskItem) -> Unit,
    onAddTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var routePlan by remember { mutableStateOf<RoutePlan?>(null) }
    var routeLoading by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val todayCalendar = Calendar.getInstance()
    val scheduled = tasks.mapIndexedNotNull { index, task ->
        val time = task.appointmentTime ?: task.reminderTime
        if (time != null && isSameDay(time, todayCalendar.timeInMillis)) {
            AgendaEntry(index, task, time)
        } else null
    }.sortedBy { it.time }
    val unscheduled = tasks.mapIndexedNotNull { index, task ->
        if (!task.completed && task.appointmentTime == null && task.reminderTime == null) {
            index to task
        } else null
    }.sortedWith(
        compareBy<Pair<Int, TaskItem>> { priorityOrder(it.second.priority) }
            .thenBy { it.first }
    )
    val nextEntry = scheduled.firstOrNull { !it.task.completed && it.time >= now }
    val overlappingIndexes = scheduled.indices.flatMap { firstIndex ->
        ((firstIndex + 1) until scheduled.size).flatMap { secondIndex ->
            val first = scheduled[firstIndex]
            val second = scheduled[secondIndex]
            if (first.time + first.task.durationMinutes * 60_000L > second.time) {
                listOf(first.index, second.index)
            } else emptyList()
        }
    }.toSet()
    val totalMinutes = scheduled.sumOf { it.task.durationMinutes } +
        unscheduled.sumOf { it.second.durationMinutes }
    val todayTasks = scheduled.map { it.task } + unscheduled.map { it.second }
    val completedCount = todayTasks.count { it.completed }
    val progress = if (todayTasks.isEmpty()) 0f else completedCount.toFloat() / todayTasks.size
    val routeCandidates = (scheduled.map { it.task } + unscheduled.map { it.second })
        .distinct()
        .filter { it.latitude != null && it.longitude != null && !it.completed }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN)
                            .format(Date()).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = FaccioNavy
                    )
                    Text("Un passo alla volta.", style = MaterialTheme.typography.bodySmall, color = FaccioMutedText)
                }
                FilledTonalButton(
                    onClick = onAddTask,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = FaccioNavy,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Aggiungi")
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = FaccioCard)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("La tua giornata", fontWeight = FontWeight.Bold, color = FaccioNavy)
                        Text(
                            "$completedCount/${todayTasks.size} completate",
                            style = MaterialTheme.typography.labelMedium,
                            color = FaccioMutedText
                        )
                    }
                    Text(
                        "${todayTasks.size} attività · ${formatDuration(totalMinutes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FaccioMutedText
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = FaccioTeal,
                        trackColor = Color(0xFFDDE5EA)
                    )
                }
            }
        }

        if (routeCandidates.size >= 2) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Giro di oggi", fontWeight = FontWeight.Bold)
                        Text("${routeCandidates.size} tappe con un luogo. L’ordine proposto riduce la distanza dalla posizione attuale.")
                        OutlinedButton(
                            onClick = {
                                routeLoading = true
                                optimizeRouteFromCurrentLocation(context, routeCandidates) {
                                    routePlan = it
                                    routeLoading = false
                                    if (it == null) {
                                        Toast.makeText(context, "Posizione attuale non disponibile", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !routeLoading,
                            modifier = Modifier.align(Alignment.End),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text(if (routeLoading) "Calcolo in corso…" else "Organizza il giro") }
                        routePlan?.let { plan ->
                            Text("Distanza diretta stimata: ${String.format(Locale.ITALIAN, "%.1f", plan.directKilometers)} km")
                            plan.stops.forEachIndexed { index, task ->
                                Text("${index + 1}. ${task.title}")
                            }
                            Button(
                                onClick = { openRouteInGoogleMaps(context, plan) },
                                modifier = Modifier.align(Alignment.End),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) { Text("Apri percorso in Google Maps") }
                            Text(
                                "Google Maps calcolerà strade e tempi reali. Controlla sempre gli orari fissi degli appuntamenti.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        if (scheduled.isNotEmpty()) {
            item { Text("Prossime attività", fontWeight = FontWeight.Bold, color = FaccioNavy) }
            items(scheduled) { entry ->
                AgendaTaskCard(
                    task = entry.task,
                    leadingText = formatHour(entry.time),
                    isNext = entry == nextEntry,
                    hasConflict = entry.index in overlappingIndexes,
                    onStepChange = { stepIndex, completed ->
                        onStepChange(entry.index, stepIndex, completed)
                    },
                    onCompletedChange = { onCompletedChange(entry.index, it) },
                    onOpenMap = onOpenMap
                )
            }
        }

        if (unscheduled.isNotEmpty()) {
            item { Text("Da fare senza orario", fontWeight = FontWeight.Bold, color = FaccioNavy) }
            items(unscheduled) { (index, task) ->
                AgendaTaskCard(
                    task = task,
                    leadingText = task.priority,
                    onStepChange = { stepIndex, completed ->
                        onStepChange(index, stepIndex, completed)
                    },
                    onCompletedChange = { onCompletedChange(index, it) },
                    onOpenMap = onOpenMap
                )
            }
        }

        if (scheduled.isEmpty() && unscheduled.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Nessun impegno per oggi", fontWeight = FontWeight.Bold)
                        Text("Puoi aggiungere una nuova attività dalla sezione Attività.")
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaTaskCard(
    task: TaskItem,
    leadingText: String,
    hasConflict: Boolean = false,
    isNext: Boolean = false,
    onStepChange: (Int, Boolean) -> Unit,
    onCompletedChange: (Boolean) -> Unit,
    onOpenMap: (TaskItem) -> Unit
) {
    val categoryColor = taskCategoryColor(task.category)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNext) Color(0xFFEDF4F8) else Color.White
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = task.completed,
                onCheckedChange = onCompletedChange,
                modifier = Modifier.size(40.dp),
                colors = CheckboxDefaults.colors(checkedColor = FaccioTeal)
            )
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, end = 9.dp)
                    .size(9.dp)
                    .background(categoryColor, RoundedCornerShape(50))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.SemiBold, color = FaccioNavy)
                Text(
                    "${task.category} · ${formatDuration(task.durationMinutes)} · Priorità ${task.priority}",
                    style = MaterialTheme.typography.bodySmall,
                    color = FaccioMutedText
                )
                if (hasConflict) {
                    Text(
                        "Attenzione: questo orario si sovrappone a un altro impegno",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (task.recurrence != "Mai") {
                    Text(recurrenceLabel(task), style = MaterialTheme.typography.bodySmall)
                }
                task.routineSteps.forEachIndexed { stepIndex, step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = step.completed,
                            onCheckedChange = { onStepChange(stepIndex, it) }
                        )
                        Text(step.title, style = MaterialTheme.typography.bodySmall)
                    }
                }
                task.departureTime?.let { Text("Partenza: ${formatHour(it)}", style = MaterialTheme.typography.bodySmall, color = FaccioTeal) }
                task.location?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = FaccioMutedText)
                        Text(it, style = MaterialTheme.typography.bodySmall, color = FaccioMutedText)
                    }
                }
                if (task.location != null) {
                    TextButton(
                        onClick = { onOpenMap(task) },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                    ) { Text("Apri mappa", color = FaccioTeal) }
                }
            }
            Text(
                leadingText,
                fontWeight = FontWeight.Bold,
                color = FaccioNavy,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
            )
        }
    }
}

private fun isSameDay(first: Long, second: Long): Boolean {
    val a = Calendar.getInstance().apply { timeInMillis = first }
    val b = Calendar.getInstance().apply { timeInMillis = second }
    return a.get(Calendar.ERA) == b.get(Calendar.ERA) &&
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

private fun priorityOrder(priority: String): Int = when (priority) {
    "Alta" -> 0
    "Media" -> 1
    else -> 2
}

private fun agendaSummary(
    scheduled: List<AgendaEntry>,
    unscheduledCount: Int,
    totalMinutes: Int
): String {
    val appointments = scheduled.count { it.task.appointmentTime != null }
    val timedTasks = scheduled.size - appointments
    return "$appointments appuntamenti • $timedTasks con orario • $unscheduledCount senza orario\nCarico stimato: ${formatDuration(totalMinutes)}"
}

private fun agendaTimeText(task: TaskItem, time: Long): String =
    if (task.appointmentTime != null) "Appuntamento alle ${formatHour(time)}"
    else "Promemoria alle ${formatHour(time)}"

private fun formatHour(time: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))

private fun durationOption(minutes: Int): String = when (minutes) {
    15 -> "15 minuti"
    30 -> "30 minuti"
    60 -> "1 ora"
    120 -> "2 ore"
    else -> "Personalizzata"
}

private fun selectedDurationMinutes(option: String, customValue: String): Int = when (option) {
    "15 minuti" -> 15
    "30 minuti" -> 30
    "1 ora" -> 60
    "2 ore" -> 120
    else -> customValue.toIntOrNull() ?: 0
}

private fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val remaining = minutes % 60
    return when {
        hours == 0 -> "$remaining min"
        remaining == 0 -> "$hours h"
        else -> "$hours h $remaining min"
    }
}

@Composable
private fun DurationSelector(
    selected: String,
    customValue: String,
    onSelected: (String) -> Unit,
    onCustomChanged: (String) -> Unit
) {
    SelectionMenu(
        label = "Durata",
        selectedValue = selected,
        values = listOf("15 minuti", "30 minuti", "1 ora", "2 ore", "Personalizzata"),
        onValueSelected = onSelected,
        modifier = Modifier.fillMaxWidth()
    )
    if (selected == "Personalizzata") {
        OutlinedTextField(
            value = customValue,
            onValueChange = { onCustomChanged(it.filter(Char::isDigit).take(3)) },
            label = { Text("Durata in minuti (5–720)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun recurrenceLabel(task: TaskItem): String = when (task.recurrence) {
    "Personalizzata" -> "Si ripete ogni ${task.recurrenceIntervalDays} giorni"
    "Mai" -> ""
    else -> "Si ripete: ${task.recurrence.lowercase(Locale.ITALIAN)}"
}

private fun updateTaskCompletion(
    context: Context,
    tasks: MutableList<TaskItem>,
    index: Int,
    completed: Boolean
) {
    val original = tasks.getOrNull(index) ?: return
    val task = if (original.routineSteps.isNotEmpty()) {
        original.copy(
            completed = completed,
            routineSteps = original.routineSteps.map { it.copy(completed = completed) }
        )
    } else original.copy(completed = completed)
    if (!completed || task.recurrence == "Mai") {
        tasks[index] = task
        saveTasks(context, tasks)
        return
    }

    cancelReminder(context, task)
    cancelDepartureReminder(context, task)
    task.arrivalReminderId?.let { removeArrivalGeofence(context, it) }

    val next = nextRecurringOccurrence(task, System.currentTimeMillis())
    next.reminderTime?.let { scheduleReminder(context, next.title, it) }
    next.departureTime?.let {
        scheduleReminder(context, "È ora di partire: ${next.title}", it)
    }
    tasks[index] = next
    saveTasks(context, tasks)
    Toast.makeText(
        context,
        "Completata. Prossima: ${formatReminderTime(next.appointmentTime ?: next.reminderTime!!)}",
        Toast.LENGTH_SHORT
    ).show()
}

private fun nextRecurringOccurrence(task: TaskItem, now: Long): TaskItem {
    var next = task.copy(
        completed = false,
        arrivalReminderId = null,
        routineSteps = task.routineSteps.map { it.copy(completed = false) }
    )
    do {
        next = next.copy(
            reminderTime = next.reminderTime?.let { shiftRecurringTime(it, next) },
            appointmentTime = next.appointmentTime?.let { shiftRecurringTime(it, next) },
            departureTime = next.departureTime?.let { shiftRecurringTime(it, next) }
        )
    } while ((next.appointmentTime ?: next.reminderTime ?: Long.MAX_VALUE) <= now)
    return next
}

private fun updateRoutineStep(
    context: Context,
    tasks: MutableList<TaskItem>,
    taskIndex: Int,
    stepIndex: Int,
    completed: Boolean
) {
    val task = tasks.getOrNull(taskIndex) ?: return
    if (stepIndex !in task.routineSteps.indices) return
    val updatedSteps = task.routineSteps.toMutableList().apply {
        this[stepIndex] = this[stepIndex].copy(completed = completed)
    }
    val allCompleted = updatedSteps.isNotEmpty() && updatedSteps.all { it.completed }
    tasks[taskIndex] = task.copy(routineSteps = updatedSteps)
    if (allCompleted) {
        updateTaskCompletion(context, tasks, taskIndex, true)
    } else {
        tasks[taskIndex] = tasks[taskIndex].copy(completed = false)
        saveTasks(context, tasks)
    }
}

private fun routineTemplates(): List<TaskItem> = listOf(
    TaskItem(
        title = "Uscire di casa",
        category = "Personale",
        durationMinutes = 15,
        routineSteps = listOf(
            RoutineStep("Prendere chiavi e portafoglio"),
            RoutineStep("Controllare telefono e batteria"),
            RoutineStep("Prendere ciò che serve"),
            RoutineStep("Controllare porte e finestre")
        )
    ),
    TaskItem(
        title = "Preparare il lavoro",
        category = "Lavoro",
        durationMinutes = 20,
        routineSteps = listOf(
            RoutineStep("Controllare gli impegni"),
            RoutineStep("Preparare documenti e strumenti"),
            RoutineStep("Controllare il percorso"),
            RoutineStep("Verificare le priorità")
        )
    ),
    TaskItem(
        title = "Preparare una visita medica",
        category = "Salute",
        priority = "Alta",
        durationMinutes = 20,
        routineSteps = listOf(
            RoutineStep("Prendere tessera sanitaria"),
            RoutineStep("Preparare impegnativa e documenti"),
            RoutineStep("Annotare domande e sintomi"),
            RoutineStep("Controllare luogo e orario")
        )
    )
)

private const val ROUTINE_TEMPLATE_PREFS = "faccio_io_routine_templates"
private const val ROUTINE_TEMPLATE_KEY = "custom_templates"

internal fun loadCustomRoutineTemplates(context: Context): List<TaskItem> {
    val saved = context.getSharedPreferences(ROUTINE_TEMPLATE_PREFS, Context.MODE_PRIVATE)
        .getString(ROUTINE_TEMPLATE_KEY, null) ?: return emptyList()
    return try {
        val array = JSONArray(saved)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val steps = item.optJSONArray("steps") ?: JSONArray()
            TaskItem(
                title = item.optString("title"),
                category = item.optString("category", "Personale"),
                priority = item.optString("priority", "Media"),
                durationMinutes = item.optInt("durationMinutes", 15).coerceIn(5, 720),
                routineSteps = List(steps.length()) { stepIndex ->
                    RoutineStep(steps.optString(stepIndex))
                }.filter { it.title.isNotBlank() }
            )
        }.filter { it.title.isNotBlank() && it.routineSteps.isNotEmpty() }
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun saveCustomRoutineTemplates(context: Context, templates: List<TaskItem>) {
    val array = JSONArray()
    templates.forEach { template ->
        array.put(
            JSONObject().apply {
                put("title", template.title)
                put("category", template.category)
                put("priority", template.priority)
                put("durationMinutes", template.durationMinutes)
                put(
                    "steps",
                    JSONArray().apply {
                        template.routineSteps.forEach { put(it.title) }
                    }
                )
            }
        )
    }
    context.getSharedPreferences(ROUTINE_TEMPLATE_PREFS, Context.MODE_PRIVATE)
        .edit().putString(ROUTINE_TEMPLATE_KEY, array.toString()).apply()
}

private fun shiftRecurringTime(time: Long, task: TaskItem): Long =
    Calendar.getInstance().apply {
        timeInMillis = time
        when (task.recurrence) {
            "Ogni giorno" -> add(Calendar.DAY_OF_YEAR, 1)
            "Ogni settimana" -> add(Calendar.WEEK_OF_YEAR, 1)
            "Ogni mese" -> add(Calendar.MONTH, 1)
            "Personalizzata" -> add(Calendar.DAY_OF_YEAR, task.recurrenceIntervalDays.coerceAtLeast(1))
        }
    }.timeInMillis

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

internal fun scheduleReminder(
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
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(permissionIntent)
        } catch (_: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        return false
    }

    val reminderIntent = Intent(context, ReminderReceiver::class.java).apply {
        putExtra("task_title", taskTitle)
        putExtra("reminder_time", reminderTime)
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

internal fun cancelReminder(context: Context, task: TaskItem) {
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

internal fun cancelDepartureReminder(context: Context, task: TaskItem) {
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
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = FaccioNavy),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("$label: $selectedValue", maxLines = 1)
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

internal fun parseTasks(
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
                departureSafety = item.optString("departureSafety", "Normale"),
                recurrence = item.optString("recurrence", "Mai"),
                recurrenceIntervalDays = item.optInt("recurrenceIntervalDays", 1).coerceAtLeast(1),
                durationMinutes = item.optInt("durationMinutes", 30).coerceIn(5, 720),
                routineSteps = item.optJSONArray("routineSteps")?.let { steps ->
                    List(steps.length()) { stepIndex ->
                        val step = steps.getJSONObject(stepIndex)
                        RoutineStep(
                            title = step.optString("title"),
                            completed = step.optBoolean("completed", false)
                        )
                    }.filter { it.title.isNotBlank() }
                }.orEmpty()
            )
        }
    } catch (_: Exception) {
        fallback
    }
}

internal fun saveTasks(context: Context, tasks: List<TaskItem>) {
    val savedJson = serializeTasks(tasks)

    context.getSharedPreferences(TASK_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(TASKS_KEY, savedJson)
        .apply()

    mirrorTasksForBoot(context, savedJson)
    AgendaWidgetProvider.updateAll(context)
}

internal fun serializeTasks(tasks: List<TaskItem>): String {
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
                put("recurrence", task.recurrence)
                put("recurrenceIntervalDays", task.recurrenceIntervalDays)
                put("durationMinutes", task.durationMinutes)
                put(
                    "routineSteps",
                    JSONArray().apply {
                        task.routineSteps.forEach { step ->
                            put(
                                JSONObject().apply {
                                    put("title", step.title)
                                    put("completed", step.completed)
                                }
                            )
                        }
                    }
                )
            }
        )
    }

    return array.toString()
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
