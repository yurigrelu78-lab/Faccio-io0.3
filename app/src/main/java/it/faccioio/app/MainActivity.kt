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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
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
            var themeMode by remember { mutableStateOf(loadThemeMode(this@MainActivity)) }
            FaccioIoTheme(themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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
                        FaccioIoApp(
                            onOpenSetup = { showSetup = true },
                            themeMode = themeMode,
                            onThemeModeChange = { selectedMode ->
                                saveThemeMode(this@MainActivity, selectedMode)
                                themeMode = selectedMode
                            }
                        )
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
    val alarmEnabled: Boolean = false,
    val category: String = "Personale",
    val priority: String = "Media",
    val appointmentTime: Long? = null,
    val location: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val arrivalReminderId: String? = null,
    val arrivalAlarmEnabled: Boolean = false,
    val departureTime: Long? = null,
    val departureTravelMinutes: Int? = null,
    val departureMarginMinutes: Int? = null,
    val departureTransport: String = "Auto",
    val departureSafety: String = "Normale",
    val recurrence: String = "Mai",
    val recurrenceIntervalDays: Int = 1,
    val recurrenceWeekdays: List<Int> = emptyList(),
    val durationMinutes: Int = 30,
    val routineSteps: List<RoutineStep> = emptyList(),
    val shoppingListEnabled: Boolean = false,
    val shoppingItems: List<ShoppingItem> = emptyList()
)

data class RoutineStep(val title: String, val completed: Boolean = false)

data class ShoppingItem(val title: String, val completed: Boolean = false)

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
    "All’ora esatta",
    "24 ore prima",
    "48 ore prima",
    "7 giorni prima",
    "Personalizzato",
    "Nessun promemoria"
)

@Composable
fun FaccioIoApp(
    onOpenSetup: () -> Unit = {},
    themeMode: String = THEME_SYSTEM,
    onThemeModeChange: (String) -> Unit = {}
) {
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
    var editedAlarmEnabled by rememberSaveable { mutableStateOf(false) }
    var editedReminderMode by rememberSaveable { mutableStateOf("Nessuno") }
    val editedRoutineSteps = remember { mutableStateListOf<RoutineStep>() }
    var editedRecurrence by rememberSaveable { mutableStateOf("Mai") }
    val editedRecurrenceWeekdays = remember { mutableStateListOf<Int>() }
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
        mutableStateOf("All’ora esatta")
    }
    var customAppointmentReminderTime by rememberSaveable {
        mutableStateOf<Long?>(null)
    }
    var assistantCategory by rememberSaveable { mutableStateOf("Personale") }
    var assistantPriority by rememberSaveable { mutableStateOf("Media") }
    var assistantDuration by rememberSaveable { mutableStateOf("30 minuti") }
    var assistantCustomDuration by rememberSaveable { mutableStateOf("30") }
    var taskReminderMode by rememberSaveable { mutableStateOf("Nessuno") }
    var taskActivityTime by rememberSaveable { mutableStateOf<Long?>(null) }
    var taskReminderTiming by rememberSaveable { mutableStateOf("All’ora esatta") }
    var taskReminderTime by rememberSaveable { mutableStateOf<Long?>(null) }
    var taskAlertType by rememberSaveable { mutableStateOf("Promemoria") }
    var taskArrivalAlertType by rememberSaveable { mutableStateOf("Promemoria") }
    var taskRecurrence by rememberSaveable { mutableStateOf("Mai") }
    val taskRecurrenceWeekdays = remember { mutableStateListOf<Int>() }
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
    var showShoppingSuggestion by rememberSaveable { mutableStateOf(false) }
    var pendingListSuggestionKind by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingHasShoppingList by rememberSaveable { mutableStateOf(false) }
    var assistantListEnabled by rememberSaveable { mutableStateOf(false) }
    var assistantListSuggestionKind by rememberSaveable { mutableStateOf<String?>(null) }
    var shoppingListIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var newShoppingItem by rememberSaveable { mutableStateOf("") }
    val shoppingDraft = remember { mutableStateListOf<ShoppingItem>() }
    var showHelpGuide by rememberSaveable { mutableStateOf(false) }
    var conflictToConfirm by remember { mutableStateOf<ScheduleConflict?>(null) }
    var pendingConflictAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showCustomRoutineEditor by rememberSaveable { mutableStateOf(false) }
    var editingRoutineTemplateIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var customRoutineName by rememberSaveable { mutableStateOf("") }
    var customRoutineCategory by rememberSaveable { mutableStateOf("Personale") }
    var customRoutinePriority by rememberSaveable { mutableStateOf("Media") }
    var customRoutineAppointmentTime by rememberSaveable { mutableStateOf<Long?>(null) }
    var customRoutineReminderMode by rememberSaveable { mutableStateOf("Nessuno") }
    var customRoutineReminderTime by rememberSaveable { mutableStateOf<Long?>(null) }
    var customRoutineAlarmEnabled by rememberSaveable { mutableStateOf(false) }
    var customRoutineRecurrence by rememberSaveable { mutableStateOf("Mai") }
    val customRoutineRecurrenceWeekdays = remember { mutableStateListOf<Int>() }
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

    fun openShoppingList(index: Int) {
        val task = tasks.getOrNull(index) ?: return
        shoppingDraft.clear()
        shoppingDraft.addAll(task.shoppingItems)
        newShoppingItem = ""
        shoppingListIndex = index
    }

    fun addPendingTask(
        reminderTime: Long? = null,
        place: ResolvedPlace? = null,
        arrivalId: String? = null,
        recurrence: String = "Mai",
        recurrenceDays: Int = 1,
        durationMinutes: Int = 30,
        recurrenceWeekdays: List<Int> = emptyList(),
        alarmEnabled: Boolean = false,
        arrivalAlarmEnabled: Boolean = false,
        appointmentTime: Long? = null
    ) {
        tasks.add(
            TaskItem(
                title = pendingTask,
                reminderTime = reminderTime,
                alarmEnabled = alarmEnabled,
                appointmentTime = appointmentTime,
                category = pendingCategory,
                priority = pendingPriority,
                location = place?.address,
                latitude = place?.latitude,
                longitude = place?.longitude,
                arrivalReminderId = arrivalId,
                arrivalAlarmEnabled = arrivalAlarmEnabled,
                recurrence = recurrence,
                recurrenceIntervalDays = recurrenceDays,
                recurrenceWeekdays = recurrenceWeekdays,
                durationMinutes = durationMinutes,
                routineSteps = pendingRoutineSteps,
                shoppingListEnabled = pendingHasShoppingList
            )
        )
        saveTasks(context, tasks)
        if (pendingHasShoppingList) openShoppingList(tasks.lastIndex)
        newTask = ""
        pendingTask = ""
        selectedCategory = "Personale"
        selectedPriority = "Media"
        pendingCategory = "Personale"
        pendingPriority = "Media"
        pendingRoutineSteps = emptyList()
        pendingHasShoppingList = false
        showReminderChoice = false
        taskReminderMode = "Nessuno"
        taskActivityTime = null
        taskReminderTiming = "All’ora esatta"
        taskReminderTime = null
        taskAlertType = "Promemoria"
        taskArrivalAlertType = "Promemoria"
        taskRecurrence = "Mai"
        taskRecurrenceWeekdays.clear()
        taskDuration = "30 minuti"
        taskCustomDuration = "30"
        taskLocationQuery = ""
        taskResolvedPlace = null
        taskPlaceMessage = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Faccio io",
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = FaccioNavy
                )
                Text(
                    text = "Oggi, un passo alla volta",
                    style = MaterialTheme.typography.labelMedium,
                    color = FaccioMutedText
                )
            }
            FilledTonalIconButton(
                onClick = {
                    if (showTaskSearch) {
                        taskSearchQuery = ""
                        showTaskSearch = false
                    } else {
                        mainSection = "Attività"
                        showTaskSearch = true
                    }
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = FaccioNavy
                )
            ) {
                Icon(
                    imageVector = if (showTaskSearch) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (showTaskSearch) "Chiudi ricerca" else "Cerca attività"
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

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
                onOpenShoppingList = { index -> openShoppingList(index) },
                modifier = Modifier.weight(1f)
            )
        } else if (mainSection == "Attività") {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text("Attività", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = FaccioNavy)
        }

        Spacer(modifier = Modifier.height(5.dp))

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

            Spacer(modifier = Modifier.height(6.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
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
                    taskRecurrenceWeekdays.clear()
                    taskDuration = "30 minuti"
                    taskCustomDuration = "30"
                    taskLocationQuery = ""
                    taskResolvedPlace = null
                    pendingHasShoppingList = false
                    pendingListSuggestionKind = suggestedListKind(text)
                    if (pendingListSuggestionKind != null) {
                        showShoppingSuggestion = true
                    } else {
                        showReminderChoice = true
                    }
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

        Button(
            onClick = { showAssistant = true },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = FaccioTeal,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(4.dp))
            Text("Assistente IA")
        }
        }
        }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Le tue attività",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = FaccioNavy
            )
            Text(
                text = "${visibleTasks.size} visibili",
                style = MaterialTheme.typography.labelSmall,
                color = FaccioMutedText
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

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

        Spacer(modifier = Modifier.height(4.dp))

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
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, taskCategoryColor(task.category).copy(alpha = 0.38f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .fillMaxHeight()
                            .background(taskCategoryColor(task.category))
                    )
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).weight(1f)) {
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

                            Icon(
                                imageVector = selectionIcon("Categoria", task.category) ?: Icons.Default.Person,
                                contentDescription = task.category,
                                tint = selectionColor("Categoria", task.category),
                                modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 9.dp).size(21.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = task.title, fontWeight = FontWeight.SemiBold, color = FaccioNavy)
                                Text(
                                    text = "${task.category} · ${formatDuration(task.durationMinutes)} · Priorità ${task.priority}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FaccioMutedText
                                )
                                findScheduleConflict(
                                    tasks = tasks,
                                    proposedStart = task.appointmentTime ?: task.reminderTime,
                                    proposedDurationMinutes = task.durationMinutes,
                                    excludeIndex = index
                                )?.let { conflict ->
                                    Text(
                                        "Sovrapposizione con “${conflict.task.title}” · ${formatHour(conflict.overlapStart)}–${formatHour(conflict.overlapEnd)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
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
                                if (task.shoppingListEnabled) {
                                    val completedItems = task.shoppingItems.count { it.completed }
                                    TextButton(
                                        onClick = { openShoppingList(index) },
                                        contentPadding = PaddingValues(
                                            horizontal = 4.dp,
                                            vertical = 0.dp
                                        )
                                    ) {
                                        Text(
                                            if (task.shoppingItems.isEmpty()) {
                                                "Apri lista"
                                            } else {
                                                "Lista: $completedItems di ${task.shoppingItems.size}"
                                            },
                                            color = FaccioTeal
                                        )
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
                            FilledTonalButton(
                                onClick = {
                                    editingIndex = index
                                    editedTitle = task.title
                                    editedCategory = task.category
                                    editedPriority = task.priority
                                    editedAppointmentTime = task.appointmentTime
                                        ?: task.reminderTime?.takeIf { task.recurrence != "Mai" }
                                    editedReminderTime = task.reminderTime
                                    editedAlarmEnabled = task.alarmEnabled
                                    editedReminderMode = when {
                                        task.reminderTime == null -> "Nessuno"
                                        task.appointmentTime == null && task.recurrence != "Mai" ->
                                            "All’ora esatta"
                                        task.appointmentTime != null &&
                                            task.reminderTime == task.appointmentTime -> "All’ora esatta"
                                        else -> "Personalizzato"
                                    }
                                    editedRoutineSteps.clear()
                                    editedRoutineSteps.addAll(task.routineSteps)
                                    editedRecurrence = task.recurrence
                                    editedRecurrenceWeekdays.clear()
                                    editedRecurrenceWeekdays.addAll(task.recurrenceWeekdays)
                                    editedDuration = durationOption(task.durationMinutes)
                                    editedCustomDuration = task.durationMinutes.toString()
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = FaccioTeal
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Modifica")
                            }
                            FilledTonalButton(
                                onClick = { deletingIndex = index },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = FaccioCoral
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Elimina")
                            }
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
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
                                "Aspetto",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = FaccioNavy
                            )
                            Text(
                                "Scegli il tema dell’app. Sistema segue automaticamente il telefono.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = FaccioMutedText
                            )
                            SelectionMenu(
                                label = "Tema",
                                selectedValue = themeMode,
                                values = THEME_OPTIONS,
                                onValueSelected = onThemeModeChange,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                "Guida e assistenza",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = FaccioNavy
                            )
                            Text(
                                "Scopri rapidamente come utilizzare tutte le funzioni dell’app.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = FaccioMutedText
                            )
                            OutlinedButton(
                                onClick = { showHelpGuide = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = FaccioNavy
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(
                                    horizontal = 14.dp,
                                    vertical = 7.dp
                                )
                            ) { Text("Apri la guida") }
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
        FaccioBottomBar(
            selected = mainSection,
            onSelected = { mainSection = it }
        )
    }

    if (showHelpGuide) {
        AlertDialog(
            onDismissRequest = { showHelpGuide = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
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
                            .size(width = 5.dp, height = 34.dp)
                            .background(FaccioTeal, RoundedCornerShape(50))
                    )
                    Column {
                        Text(
                            "Guida di Faccio io",
                            fontWeight = FontWeight.Bold,
                            color = FaccioNavy
                        )
                        Text(
                            "Tutte le funzioni, spiegate in breve",
                            style = MaterialTheme.typography.bodySmall,
                            color = FaccioMutedText
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GuideTopic(
                        "Attività",
                        "Apri Attività, scrivi cosa devi fare, scegli categoria e priorità, quindi tocca Aggiungi. Puoi modificare, completare o eliminare ogni attività."
                    )
                    GuideTopic(
                        "Promemoria e ripetizioni",
                        "Durante la creazione puoi scegliere data e ora del promemoria, durata e frequenza. Per gli avvisi precisi devono essere abilitate notifiche e sveglie."
                    )
                    GuideTopic(
                        "Assistente IA",
                        "Scrivi o detta una frase completa, per esempio “Domani alle 15 dentista in via Roma 10”. Controlla i dati riconosciuti prima di salvare."
                    )
                    GuideTopic(
                        "Routine",
                        "In Strumenti scegli una routine pronta oppure creane una personale. I passaggi possono essere completati uno alla volta e riutilizzati."
                    )
                    GuideTopic(
                        "Luoghi e partenza",
                        "Associa un indirizzo all’attività. Con la posizione autorizzata, Faccio io può stimare il viaggio e suggerire quando partire."
                    )
                    GuideTopic(
                        "Ricerca e filtri",
                        "Usa la lente per cercare un’attività. Nella sezione Attività puoi filtrare l’elenco per categoria e priorità."
                    )
                    GuideTopic(
                        "Backup",
                        "Da Strumenti puoi esportare attività, promemoria e routine in un file. Ripristina conserva il contenuto del backup e sostituisce i dati presenti."
                    )
                    GuideTopic(
                        "Widget",
                        "Tieni premuto uno spazio libero nella Home, scegli Widget e cerca Faccio io. Il widget mostra attività di oggi e prossimo impegno."
                    )
                    GuideTopic(
                        "Permessi e funzionamento",
                        "In Strumenti apri Configurazione per controllare notifiche, posizione, batteria e attività in background."
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showHelpGuide = false },
                    colors = ButtonDefaults.buttonColors(containerColor = FaccioNavy),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 7.dp)
                ) { Text("Ho capito") }
            }
        )
    }

    if (showAssistant) {
        AlertDialog(
            onDismissRequest = { showAssistant = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
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
                        } else if (parsed.time == null && !parsed.location.isNullOrBlank()) {
                            val listKind = suggestedListKind(
                                "${parsed.title} ${parsed.location.orEmpty()} $assistantText"
                            )
                            pendingTask = parsed.title
                            pendingCategory = suggestAppointmentCategory(parsed.title)
                            pendingPriority = suggestAppointmentPriority(parsed.title)
                            taskReminderMode = "Quando arrivo"
                            taskLocationQuery = parsed.location
                            taskResolvedPlace = null
                            taskPlaceMessage = "Ricerca del luogo in corso…"
                            pendingHasShoppingList = false
                            pendingListSuggestionKind = listKind
                            showAssistant = false
                            if (listKind != null) {
                                showShoppingSuggestion = true
                            } else {
                                showReminderChoice = true
                            }
                            resolvePlace(context, parsed.location) { place ->
                                taskResolvedPlace = place
                                taskPlaceMessage = if (place == null) {
                                    "Luogo non trovato: controlla l’indirizzo e riprova"
                                } else {
                                    "Luogo trovato: ${place.address}"
                                }
                            }
                        } else {
                            assistantListSuggestionKind = suggestedListKind(
                                "${parsed.title} ${parsed.location.orEmpty()} $assistantText"
                            )
                            assistantListEnabled = assistantListSuggestionKind != null
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
            containerColor = MaterialTheme.colorScheme.surface,
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
                                        taskCustomDuration = template.durationMinutes.coerceAtLeast(30).toString()
                                        taskActivityTime = template.appointmentTime
                                        taskReminderTime = template.reminderTime
                                        taskReminderTiming = if (template.reminderTime != null && template.reminderTime == template.appointmentTime) "All’ora esatta" else "Personalizzato"
                                        taskAlertType = if (template.alarmEnabled) "Sveglia" else "Promemoria"
                                        taskRecurrence = template.recurrence
                                        taskRecurrenceWeekdays.clear()
                                        taskRecurrenceWeekdays.addAll(template.recurrenceWeekdays)
                                        taskReminderMode = if (template.appointmentTime != null) "Data e ora" else "Nessuno"
                                        showRoutineTemplates = false
                                        showReminderChoice = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = FaccioNavy),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                                ) { Text("Usa questa routine") }
                                TextButton(
                                    onClick = {
                                        editingRoutineTemplateIndex = null
                                        customRoutineName = "${template.title} personalizzata"
                                        customRoutineCategory = template.category
                                        customRoutinePriority = template.priority
                                        customRoutineAppointmentTime = template.appointmentTime
                                        customRoutineReminderTime = template.reminderTime
                                        customRoutineReminderMode = when {
                                            template.reminderTime == null -> "Nessuno"
                                            template.reminderTime == template.appointmentTime -> "All’ora esatta"
                                            else -> "Personalizzato"
                                        }
                                        customRoutineAlarmEnabled = template.alarmEnabled
                                        customRoutineRecurrence = template.recurrence
                                        customRoutineRecurrenceWeekdays.clear()
                                        customRoutineRecurrenceWeekdays.addAll(template.recurrenceWeekdays)
                                        customRoutineSteps.clear()
                                        customRoutineSteps.addAll(template.routineSteps.map { it.title })
                                        showRoutineTemplates = false
                                        showCustomRoutineEditor = true
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = FaccioTeal)
                                ) { Text("Personalizza") }
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
                                            taskCustomDuration = template.durationMinutes.coerceAtLeast(30).toString()
                                            taskActivityTime = template.appointmentTime
                                            taskReminderTime = template.reminderTime
                                            taskReminderTiming = if (template.reminderTime != null && template.reminderTime == template.appointmentTime) "All’ora esatta" else "Personalizzato"
                                            taskAlertType = if (template.alarmEnabled) "Sveglia" else "Promemoria"
                                            taskRecurrence = template.recurrence
                                            taskRecurrenceWeekdays.clear()
                                            taskRecurrenceWeekdays.addAll(template.recurrenceWeekdays)
                                            taskReminderMode = if (template.appointmentTime != null) "Data e ora" else "Nessuno"
                                            showRoutineTemplates = false
                                            showReminderChoice = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = FaccioNavy),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                                    ) { Text("Usa") }
                                    TextButton(
                                        onClick = {
                                            val index = customRoutineTemplates.indexOf(template)
                                            if (index >= 0) {
                                                editingRoutineTemplateIndex = index
                                                customRoutineName = template.title
                                                customRoutineCategory = template.category
                                                customRoutinePriority = template.priority
                                                customRoutineAppointmentTime = template.appointmentTime
                                                customRoutineReminderTime = template.reminderTime
                                                customRoutineReminderMode = when {
                                                    template.reminderTime == null -> "Nessuno"
                                                    template.reminderTime == template.appointmentTime -> "All’ora esatta"
                                                    else -> "Personalizzato"
                                                }
                                                customRoutineAlarmEnabled = template.alarmEnabled
                                                customRoutineRecurrence = template.recurrence
                                                customRoutineRecurrenceWeekdays.clear()
                                                customRoutineRecurrenceWeekdays.addAll(template.recurrenceWeekdays)
                                                customRoutineSteps.clear()
                                                customRoutineSteps.addAll(
                                                    template.routineSteps.map { it.title }
                                                )
                                                showRoutineTemplates = false
                                                showCustomRoutineEditor = true
                                            }
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = FaccioTeal
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) { Text("Modifica") }
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
                            editingRoutineTemplateIndex = null
                            customRoutineName = ""
                            customRoutineCategory = "Personale"
                            customRoutinePriority = "Media"
                            customRoutineAppointmentTime = null
                            customRoutineReminderMode = "Nessuno"
                            customRoutineReminderTime = null
                            customRoutineAlarmEnabled = false
                            customRoutineRecurrence = "Mai"
                            customRoutineRecurrenceWeekdays.clear()
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
            containerColor = MaterialTheme.colorScheme.surface,
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
                            if (editingRoutineTemplateIndex == null) "Nuova routine" else "Modifica routine",
                            fontWeight = FontWeight.Bold,
                            color = FaccioNavy
                        )
                        Text(
                            if (editingRoutineTemplateIndex == null) {
                                "Costruiscila un passaggio alla volta"
                            } else {
                                "Aggiorna nome, passaggi e ordine"
                            },
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
                    Text("ORARIO E AVVISO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = FaccioTeal)
                    OutlinedButton(
                        onClick = {
                            showDateTimePicker(context, customRoutineAppointmentTime ?: (System.currentTimeMillis() + 60L * 60L * 1000L)) { selectedTime ->
                                customRoutineAppointmentTime = selectedTime
                                if (customRoutineReminderMode == "All’ora esatta") customRoutineReminderTime = selectedTime
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(customRoutineAppointmentTime?.let { "Routine: ${formatReminderTime(it)}" } ?: "Aggiungi data e ora")
                    }
                    SelectionMenu(
                        label = "Quando avvisare",
                        selectedValue = customRoutineReminderMode,
                        values = listOf("Nessuno", "All’ora esatta", "Personalizzato"),
                        onValueSelected = { mode ->
                            customRoutineReminderMode = mode
                            customRoutineReminderTime = when (mode) {
                                "Nessuno" -> null
                                "All’ora esatta" -> customRoutineAppointmentTime
                                else -> customRoutineReminderTime
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (customRoutineReminderMode == "Personalizzato") {
                        OutlinedButton(onClick = { showReminderPicker(context) { customRoutineReminderTime = it } }, modifier = Modifier.fillMaxWidth()) {
                            Text(customRoutineReminderTime?.let { "Avviso: ${formatReminderTime(it)}" } ?: "Scegli data e ora dell’avviso")
                        }
                    }
                    if (customRoutineReminderMode != "Nessuno") {
                        SelectionMenu(
                            label = "Tipo di avviso",
                            selectedValue = if (customRoutineAlarmEnabled) "Sveglia" else "Promemoria",
                            values = listOf("Promemoria", "Sveglia"),
                            onValueSelected = { customRoutineAlarmEnabled = it == "Sveglia" },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    SelectionMenu(
                        label = "Ripetizione",
                        selectedValue = customRoutineRecurrence,
                        values = TASK_RECURRENCES,
                        onValueSelected = { customRoutineRecurrence = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (customRoutineRecurrence == "Personalizzata") {
                        WeekdaySelector(
                            selectedDays = customRoutineRecurrenceWeekdays,
                            onToggle = { day ->
                                if (day in customRoutineRecurrenceWeekdays) customRoutineRecurrenceWeekdays.remove(day)
                                else customRoutineRecurrenceWeekdays.add(day)
                            }
                        )
                    }
                    Text(
                        "PASSAGGI",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = FaccioTeal
                    )
                    customRoutineSteps.forEachIndexed { index, step ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(FaccioCard, RoundedCornerShape(14.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = FaccioNavy
                                    )
                                }
                                OutlinedTextField(
                                    value = step,
                                    onValueChange = { customRoutineSteps[index] = it },
                                    label = { Text("Cosa devi fare?") },
                                    singleLine = false,
                                    minLines = 1,
                                    maxLines = 3,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = FaccioTeal,
                                        focusedLabelColor = FaccioTeal,
                                        cursorColor = FaccioTeal
                                    )
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        if (index > 0) {
                                            val previous = customRoutineSteps[index - 1]
                                            customRoutineSteps[index - 1] = customRoutineSteps[index]
                                            customRoutineSteps[index] = previous
                                        }
                                    },
                                    enabled = index > 0,
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) { Text("Su") }
                                TextButton(
                                    onClick = {
                                        if (index < customRoutineSteps.lastIndex) {
                                            val next = customRoutineSteps[index + 1]
                                            customRoutineSteps[index + 1] = customRoutineSteps[index]
                                            customRoutineSteps[index] = next
                                        }
                                    },
                                    enabled = index < customRoutineSteps.lastIndex,
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) { Text("Giù") }
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
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) { Text("Elimina") }
                            }
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
                        val appointmentTime = customRoutineAppointmentTime
                        val reminderTime = when (customRoutineReminderMode) {
                            "Nessuno" -> null
                            "All’ora esatta" -> appointmentTime
                            else -> customRoutineReminderTime
                        }
                        if (customRoutineRecurrence != "Mai" && appointmentTime == null) {
                            Toast.makeText(context, "La ripetizione richiede data e ora", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        if (customRoutineRecurrence == "Personalizzata" && customRoutineRecurrenceWeekdays.isEmpty()) {
                            Toast.makeText(context, "Seleziona almeno un giorno", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        if (customRoutineReminderMode != "Nessuno" && reminderTime == null) {
                            Toast.makeText(context, "Scegli l’orario dell’avviso", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        val now = System.currentTimeMillis()
                        if (appointmentTime != null && appointmentTime <= now) {
                            Toast.makeText(context, "Data e ora della routine devono essere future", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        if (
                            customRoutineReminderMode == "Personalizzato" &&
                            reminderTime != null &&
                            (reminderTime <= now ||
                                (appointmentTime != null && reminderTime >= appointmentTime))
                        ) {
                            Toast.makeText(context, "L’avviso personalizzato deve essere futuro e precedente alla routine", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        val editingIndex = editingRoutineTemplateIndex
                        val duplicateBuiltIn = routineTemplates().any {
                            it.title.equals(name, ignoreCase = true)
                        }
                        val duplicateCustom = customRoutineTemplates.withIndex().any {
                            it.index != editingIndex &&
                                it.value.title.equals(name, ignoreCase = true)
                        }
                        if (duplicateBuiltIn || duplicateCustom) {
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
                            appointmentTime = appointmentTime,
                            reminderTime = reminderTime,
                            alarmEnabled = customRoutineAlarmEnabled && reminderTime != null,
                            recurrence = customRoutineRecurrence,
                            recurrenceWeekdays = if (customRoutineRecurrence == "Personalizzata") customRoutineRecurrenceWeekdays.toList() else emptyList(),
                            durationMinutes = (steps.size * 5).coerceIn(30, 720),
                            routineSteps = steps.map { RoutineStep(it) }
                        )
                        if (editingIndex != null && editingIndex in customRoutineTemplates.indices) {
                            customRoutineTemplates[editingIndex] = template
                        } else {
                            customRoutineTemplates.add(template)
                        }
                        saveCustomRoutineTemplates(context, customRoutineTemplates)
                        if (editingIndex != null) {
                            editingRoutineTemplateIndex = null
                            showCustomRoutineEditor = false
                            showRoutineTemplates = true
                            Toast.makeText(context, "Routine aggiornata", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val saveRoutineDirectly: () -> Unit = save@ {
                            if (
                                reminderTime != null &&
                                !scheduleReminder(
                                    context,
                                    template.title,
                                    reminderTime,
                                    template.alarmEnabled
                                )
                            ) return@save
                            tasks.add(template)
                            saveTasks(context, tasks)
                            showCustomRoutineEditor = false
                            editingRoutineTemplateIndex = null
                            Toast.makeText(context, "Routine salvata e aggiunta", Toast.LENGTH_SHORT).show()
                        }
                        val conflict = findScheduleConflict(
                            tasks = tasks,
                            proposedStart = appointmentTime ?: reminderTime,
                            proposedDurationMinutes = template.durationMinutes
                        )
                        showCustomRoutineEditor = false
                        if (conflict != null) {
                            conflictToConfirm = conflict
                            pendingConflictAction = saveRoutineDirectly
                        } else {
                            saveRoutineDirectly()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FaccioNavy),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        if (editingRoutineTemplateIndex == null) {
                            "Salva routine"
                        } else {
                            "Salva modifiche"
                        }
                    )
                }
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
        val appointmentTime = appointment.time ?: return@let
        LaunchedEffect(appointment.time, appointment.title) {
            appointmentReminderOption = "All’ora esatta"
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
                    Text("Quando: ${formatReminderTime(appointmentTime)}")
                    Text(
                        "Luogo: ${resolvedPlace?.address ?: appointment.location ?: "non indicato"}"
                    )
                    Text(
                        placeLookupMessage,
                        style = MaterialTheme.typography.bodySmall
                    )
                    assistantListSuggestionKind?.let { kind ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = assistantListEnabled,
                                    onCheckedChange = { assistantListEnabled = it }
                                )
                                Column {
                                    Text(
                                        if (kind == "viaggio") {
                                            "Crea una lista per il viaggio"
                                        } else {
                                            "Crea una lista per la spesa"
                                        },
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        if (kind == "viaggio") {
                                            "Potrai aggiungere e spuntare tutto ciò che devi preparare."
                                        } else {
                                            "Potrai aggiungere e spuntare gli articoli da acquistare."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FaccioMutedText
                                    )
                                }
                            }
                        }
                    }
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
                                estimateDepartureFromCurrentLocation(context, appointmentTime, place.latitude, place.longitude, departureTransport, departureSafety) {
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
                            appointmentTime,
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
                            appointmentTime,
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
                                reminderTime > appointmentTime)
                        ) {
                            Toast.makeText(
                                context,
                                "Il promemoria deve essere futuro e non successivo all’appuntamento",
                                Toast.LENGTH_LONG
                            ).show()
                            return@TextButton
                        }
                        val departure = departureEstimate
                        val durationMinutes = selectedDurationMinutes(
                            assistantDuration,
                            assistantCustomDuration
                        )
                        if (durationMinutes !in 30..720 || durationMinutes % 30 != 0) {
                            Toast.makeText(context, "Scegli una durata da 0,5 a 12 ore in intervalli di mezz’ora", Toast.LENGTH_LONG).show()
                            return@TextButton
                        }
                        if (departure != null && departure.departureTime <= System.currentTimeMillis()) {
                            Toast.makeText(context, "La partenza consigliata è già trascorsa: ricalcola o modifica l’appuntamento", Toast.LENGTH_LONG).show()
                            return@TextButton
                        }
                        val saveAppointment = {
                            var scheduled = true
                            if (departure != null) {
                                scheduled = scheduleReminder(
                                    context,
                                    "È ora di partire: ${appointment.title}",
                                    departure.departureTime
                                )
                            }
                            if (scheduled && reminderTime != null) {
                                scheduled = scheduleReminder(context, appointment.title, reminderTime)
                            }
                            if (scheduled) {
                                tasks.add(
                                    TaskItem(
                                        title = appointment.title,
                                        reminderTime = reminderTime,
                                        category = assistantCategory,
                                        priority = assistantPriority,
                                        appointmentTime = appointmentTime,
                                        location = resolvedPlace?.address ?: appointment.location,
                                        latitude = resolvedPlace?.latitude,
                                        longitude = resolvedPlace?.longitude,
                                        departureTime = departure?.departureTime,
                                        departureTravelMinutes = departure?.travelMinutes,
                                        departureMarginMinutes = departure?.marginMinutes,
                                        departureTransport = departureTransport,
                                        departureSafety = departureSafety,
                                        durationMinutes = durationMinutes,
                                        shoppingListEnabled = assistantListEnabled
                                    )
                                )
                                saveTasks(context, tasks)
                                if (assistantListEnabled) {
                                    openShoppingList(tasks.lastIndex)
                                }
                                assistantText = ""
                                assistantListSuggestionKind = null
                                assistantResult = null
                                Toast.makeText(
                                    context,
                                    if (reminderTime == null) "Appuntamento aggiunto"
                                    else "Appuntamento e promemoria aggiunti",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        val conflict = findScheduleConflict(
                            tasks = tasks,
                            proposedStart = appointmentTime,
                            proposedDurationMinutes = durationMinutes
                        )
                        if (conflict != null) {
                            conflictToConfirm = conflict
                            pendingConflictAction = saveAppointment
                        } else {
                            saveAppointment()
                        }
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

    conflictToConfirm?.let { conflict ->
        AlertDialog(
            onDismissRequest = {
                conflictToConfirm = null
                pendingConflictAction = null
            },
            title = { Text("Orari sovrapposti") },
            text = {
                Text(
                    "Questa attività si sovrappone a “${conflict.task.title}” " +
                        "dalle ${formatHour(conflict.overlapStart)} alle ${formatHour(conflict.overlapEnd)}."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val action = pendingConflictAction
                        conflictToConfirm = null
                        pendingConflictAction = null
                        action?.invoke()
                    }
                ) { Text("Salva comunque") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        conflictToConfirm = null
                        pendingConflictAction = null
                    }
                ) { Text("Modifica orario") }
            }
        )
    }

    if (showShoppingSuggestion) {
        AlertDialog(
            onDismissRequest = {
                pendingHasShoppingList = false
                pendingListSuggestionKind = null
                showShoppingSuggestion = false
                showReminderChoice = true
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    "Vuoi aggiungere una lista?",
                    fontWeight = FontWeight.Bold,
                    color = FaccioNavy
                )
            },
            text = {
                Text(
                    if (pendingListSuggestionKind == "viaggio") {
                        "Sembra un’attività legata a un viaggio. Puoi creare una lista di cose da preparare e spuntarle man mano."
                    } else {
                        "Sembra un’attività legata alla spesa. Puoi creare una lista specifica e spuntare gli articoli mentre acquisti."
                    },
                    color = FaccioMutedText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingHasShoppingList = true
                        pendingListSuggestionKind = null
                        showShoppingSuggestion = false
                        showReminderChoice = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FaccioNavy),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Crea lista") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingHasShoppingList = false
                        pendingListSuggestionKind = null
                        showShoppingSuggestion = false
                        showReminderChoice = true
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = FaccioMutedText)
                ) { Text("Non ora") }
            }
        )
    }

    shoppingListIndex?.let { listIndex ->
        val listTask = tasks.getOrNull(listIndex)
        if (listTask != null) {
            AlertDialog(
                onDismissRequest = { shoppingListIndex = null },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                title = {
                    Column {
                        Text(
                            "Lista",
                            fontWeight = FontWeight.Bold,
                            color = FaccioNavy
                        )
                        Text(
                            listTask.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = FaccioMutedText
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (shoppingDraft.isEmpty()) {
                            Text(
                                "La lista è vuota. Aggiungi il primo elemento.",
                                style = MaterialTheme.typography.bodySmall,
                                color = FaccioMutedText
                            )
                        }
                        shoppingDraft.forEachIndexed { itemIndex, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = item.completed,
                                    onCheckedChange = {
                                        shoppingDraft[itemIndex] =
                                            item.copy(completed = it)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = FaccioTeal
                                    )
                                )
                                Text(
                                    item.title,
                                    modifier = Modifier.weight(1f),
                                    color = if (item.completed) {
                                        FaccioMutedText
                                    } else {
                                        FaccioNavy
                                    }
                                )
                                TextButton(
                                    onClick = { shoppingDraft.removeAt(itemIndex) },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = FaccioCoral
                                    ),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) { Text("×") }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newShoppingItem,
                                onValueChange = { newShoppingItem = it },
                                label = { Text("Nuovo elemento") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = FaccioTeal,
                                    focusedLabelColor = FaccioTeal
                                )
                            )
                            IconButton(
                                onClick = {
                                    val item = newShoppingItem.trim()
                                    if (item.isNotEmpty()) {
                                        shoppingDraft.add(ShoppingItem(item))
                                        newShoppingItem = ""
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Aggiungi elemento",
                                    tint = FaccioTeal
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trailingItem = newShoppingItem.trim()
                            if (trailingItem.isNotEmpty()) {
                                shoppingDraft.add(ShoppingItem(trailingItem))
                                newShoppingItem = ""
                            }
                            tasks[listIndex] = listTask.copy(
                                shoppingItems = shoppingDraft
                                    .filter { it.title.isNotBlank() }
                            )
                            saveTasks(context, tasks)
                            shoppingListIndex = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FaccioNavy
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Salva lista") }
                },
                dismissButton = {
                    TextButton(
                        onClick = { shoppingListIndex = null },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = FaccioMutedText
                        )
                    ) { Text("Annulla") }
                }
            )
        } else {
            shoppingListIndex = null
        }
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
                        Text(
                            "Scegli i giorni",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FaccioNavy
                        )
                        WeekdaySelector(
                            selectedDays = taskRecurrenceWeekdays,
                            onToggle = { day ->
                                if (day in taskRecurrenceWeekdays) {
                                    taskRecurrenceWeekdays.remove(day)
                                } else {
                                    taskRecurrenceWeekdays.add(day)
                                }
                            }
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
                            onClick = {
                                showDateTimePicker(
                                    context,
                                    taskActivityTime ?: System.currentTimeMillis()
                                ) { selectedTime ->
                                    taskActivityTime = selectedTime
                                    if (taskReminderTiming == "All’ora esatta") {
                                        taskReminderTime = null
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                taskActivityTime?.let {
                                    "Attività: ${formatReminderTime(it)}"
                                } ?: "Scegli data e ora dell’attività"
                            )
                        }
                        SelectionMenu(
                            label = "Quando avvisare",
                            selectedValue = taskReminderTiming,
                            values = listOf("All’ora esatta", "Personalizzato"),
                            onValueSelected = {
                                taskReminderTiming = it
                                if (it == "All’ora esatta") taskReminderTime = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (taskReminderTiming == "Personalizzato") {
                            OutlinedButton(
                                onClick = {
                                    showReminderPicker(context) {
                                        taskReminderTime = it
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    taskReminderTime?.let {
                                        "Avviso: ${formatReminderTime(it)}"
                                    } ?: "Scegli data e ora dell’avviso"
                                )
                            }
                        } else {
                            Text(
                                "L’avviso verrà attivato all’inizio dell’attività.",
                                style = MaterialTheme.typography.bodySmall,
                                color = FaccioMutedText
                            )
                        }
                        SelectionMenu(
                            label = "Tipo di avviso",
                            selectedValue = taskAlertType,
                            values = listOf("Promemoria", "Sveglia"),
                            onValueSelected = { taskAlertType = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (taskAlertType == "Sveglia") {
                            Text(
                                "La sveglia continuerà a suonare finché non la spegni o la rimandi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = FaccioMutedText
                            )
                        }
                    }
                    Text("Luogo", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Se non imposti data e ora, il luogo attiva automaticamente “Quando arrivo”.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FaccioMutedText
                    )
                    run {
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
                        if (
                            taskReminderMode == "Quando arrivo" ||
                            taskReminderMode == "Entrambi" ||
                            (taskReminderMode == "Nessuno" && taskLocationQuery.isNotBlank())
                        ) {
                            SelectionMenu(
                                label = "Avviso all’arrivo",
                                selectedValue = taskArrivalAlertType,
                                values = listOf("Promemoria", "Sveglia"),
                                onValueSelected = { taskArrivalAlertType = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (taskArrivalAlertType == "Sveglia") {
                                Text(
                                    "All’arrivo partirà una sveglia con suono e vibrazione.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FaccioMutedText
                                )
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
                        val automaticArrival = taskReminderMode == "Nessuno" && taskLocationQuery.isNotBlank()
                        val needsPlace = taskReminderMode == "Quando arrivo" ||
                            taskReminderMode == "Entrambi" || automaticArrival
                        val recurrenceDays = 1
                        val durationMinutes = selectedDurationMinutes(taskDuration, taskCustomDuration)
                        if (durationMinutes !in 30..720 || durationMinutes % 30 != 0) {
                            Toast.makeText(context, "Scegli una durata da 0,5 a 12 ore in intervalli di mezz’ora", Toast.LENGTH_LONG).show(); return@TextButton
                        }
                        if (taskRecurrence == "Personalizzata" && taskRecurrenceWeekdays.isEmpty()) {
                            Toast.makeText(context, "Seleziona almeno un giorno della settimana", Toast.LENGTH_LONG).show(); return@TextButton
                        }
                        val activityTime = taskActivityTime
                        if (
                            needsTime &&
                            (activityTime == null || activityTime <= System.currentTimeMillis())
                        ) {
                            Toast.makeText(context, "Scegli un orario futuro per l’attività", Toast.LENGTH_LONG).show()
                            return@TextButton
                        }
                        val effectiveReminderTime = when {
                            !needsTime -> null
                            taskReminderTiming == "All’ora esatta" -> activityTime
                            else -> taskReminderTime
                        }
                        if (
                            needsTime &&
                            taskReminderTiming == "Personalizzato" &&
                            (
                                effectiveReminderTime == null ||
                                    effectiveReminderTime <= System.currentTimeMillis() ||
                                    effectiveReminderTime >= activityTime!!
                                )
                        ) {
                            Toast.makeText(
                                context,
                                "L’avviso personalizzato deve essere futuro e precedente all’attività",
                                Toast.LENGTH_LONG
                            ).show()
                            return@TextButton
                        }
                        val place = taskResolvedPlace
                        if (needsPlace && place == null) {
                            Toast.makeText(context, "Cerca e verifica prima il luogo", Toast.LENGTH_LONG).show()
                            return@TextButton
                        }
                        val alarmEnabled = needsTime && taskAlertType == "Sveglia"
                        val arrivalAlarmEnabled = needsPlace && taskArrivalAlertType == "Sveglia"
                        val saveManualTask: () -> Unit = save@ {
                            if (
                                needsTime &&
                                !scheduleReminder(
                                    context,
                                    pendingTask,
                                    effectiveReminderTime!!,
                                    alarmEnabled
                                )
                            ) {
                                return@save
                            }
                            if (needsPlace) {
                                if (!ensureLocationPermissions(context)) return@save
                                val id = "arrival_${System.currentTimeMillis()}_${pendingTask.hashCode()}"
                                registerArrivalGeofence(
                                    context,
                                    id,
                                    pendingTask,
                                    place!!.latitude,
                                    place.longitude
                                ) { ok ->
                                    if (ok) {
                                        addPendingTask(
                                            effectiveReminderTime,
                                            place,
                                            id,
                                            taskRecurrence,
                                            recurrenceDays,
                                            durationMinutes,
                                            taskRecurrenceWeekdays.toList(),
                                            alarmEnabled,
                                            arrivalAlarmEnabled,
                                            activityTime
                                        )
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Attivazione del luogo non riuscita",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            } else {
                                addPendingTask(
                                    reminderTime = effectiveReminderTime,
                                    recurrence = taskRecurrence,
                                    recurrenceDays = recurrenceDays,
                                    durationMinutes = durationMinutes,
                                    recurrenceWeekdays = taskRecurrenceWeekdays.toList(),
                                    alarmEnabled = alarmEnabled,
                                    arrivalAlarmEnabled = false,
                                    appointmentTime = activityTime
                                )
                            }
                        }
                        val conflict = if (needsTime) {
                            findScheduleConflict(
                                tasks = tasks,
                                proposedStart = activityTime,
                                proposedDurationMinutes = durationMinutes
                            )
                        } else null
                        if (conflict != null) {
                            conflictToConfirm = conflict
                            pendingConflictAction = saveManualTask
                        } else {
                            saveManualTask()
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
                        val isPersonalRoutine = task.routineSteps.isNotEmpty() ||
                            task.recurrence != "Mai" || task.reminderTime != null
                        if (isPersonalRoutine || task.appointmentTime != null) {
                            OutlinedButton(
                                onClick = {
                                    showDateTimePicker(
                                        context,
                                        editedAppointmentTime
                                            ?: (System.currentTimeMillis() + 60L * 60L * 1000L)
                                    ) { selectedTime ->
                                        val oldReminderTime = task.reminderTime
                                        val oldLeadTime = if (
                                            oldReminderTime != null && task.appointmentTime != null
                                        ) {
                                            (task.appointmentTime - oldReminderTime)
                                                .takeIf { it > 0L }
                                                ?: 24L * 60L * 60L * 1000L
                                        } else {
                                            null
                                        }
                                        editedAppointmentTime = selectedTime
                                        editedReminderTime = when (editedReminderMode) {
                                            "All’ora esatta" -> selectedTime
                                            "Personalizzato" -> oldLeadTime?.let { selectedTime - it }
                                                ?: editedReminderTime
                                            else -> null
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    editedAppointmentTime?.let {
                                        "Routine: ${formatReminderTime(it)}"
                                    } ?: "Aggiungi data e ora alla routine"
                                )
                            }
                            SelectionMenu(
                                label = "Quando avvisare",
                                selectedValue = editedReminderMode,
                                values = listOf("Nessuno", "All’ora esatta", "Personalizzato"),
                                onValueSelected = { mode ->
                                    editedReminderMode = mode
                                    editedReminderTime = when (mode) {
                                        "Nessuno" -> null
                                        "All’ora esatta" -> editedAppointmentTime
                                        else -> editedReminderTime?.takeIf {
                                            editedAppointmentTime == null || it < editedAppointmentTime!!
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (editedReminderMode == "Personalizzato") {
                                OutlinedButton(
                                    onClick = {
                                        showReminderPicker(context) { editedReminderTime = it }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        editedReminderTime?.let {
                                            "Avviso: ${formatReminderTime(it)}"
                                        } ?: "Scegli data e ora dell’avviso"
                                    )
                                }
                            }
                        }
                        if (editedReminderMode != "Nessuno") {
                            SelectionMenu(
                                label = "Tipo di avviso",
                                selectedValue = if (editedAlarmEnabled) "Sveglia" else "Promemoria",
                                values = listOf("Promemoria", "Sveglia"),
                                onValueSelected = { editedAlarmEnabled = it == "Sveglia" },
                                modifier = Modifier.fillMaxWidth()
                            )
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
                            Text(
                                "Scegli i giorni",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = FaccioNavy
                            )
                            WeekdaySelector(
                                selectedDays = editedRecurrenceWeekdays,
                                onToggle = { day ->
                                    if (day in editedRecurrenceWeekdays) {
                                        editedRecurrenceWeekdays.remove(day)
                                    } else {
                                        editedRecurrenceWeekdays.add(day)
                                    }
                                }
                            )
                        }
                        DurationSelector(
                            selected = editedDuration,
                            customValue = editedCustomDuration,
                            onSelected = { editedDuration = it },
                            onCustomChanged = { editedCustomDuration = it }
                        )
                        if (task.routineSteps.isNotEmpty()) {
                            Text(
                                "PASSAGGI DELLA ROUTINE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = FaccioTeal
                            )
                            editedRoutineSteps.forEachIndexed { stepIndex, step ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(FaccioCard, RoundedCornerShape(12.dp))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    OutlinedTextField(
                                        value = step.title,
                                        onValueChange = {
                                            editedRoutineSteps[stepIndex] = step.copy(title = it)
                                        },
                                        label = { Text("Passaggio ${stepIndex + 1}") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 1,
                                        maxLines = 3
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                if (stepIndex > 0) {
                                                    val previous = editedRoutineSteps[stepIndex - 1]
                                                    editedRoutineSteps[stepIndex - 1] = editedRoutineSteps[stepIndex]
                                                    editedRoutineSteps[stepIndex] = previous
                                                }
                                            },
                                            enabled = stepIndex > 0
                                        ) { Text("Su") }
                                        TextButton(
                                            onClick = {
                                                if (stepIndex < editedRoutineSteps.lastIndex) {
                                                    val next = editedRoutineSteps[stepIndex + 1]
                                                    editedRoutineSteps[stepIndex + 1] = editedRoutineSteps[stepIndex]
                                                    editedRoutineSteps[stepIndex] = next
                                                }
                                            },
                                            enabled = stepIndex < editedRoutineSteps.lastIndex
                                        ) { Text("Giù") }
                                        TextButton(
                                            onClick = {
                                                if (editedRoutineSteps.size > 1) {
                                                    editedRoutineSteps.removeAt(stepIndex)
                                                } else {
                                                    editedRoutineSteps[0] = RoutineStep("")
                                                }
                                            },
                                            colors = ButtonDefaults.textButtonColors(contentColor = FaccioCoral)
                                        ) { Text("Elimina") }
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = { editedRoutineSteps.add(RoutineStep("")) },
                                enabled = editedRoutineSteps.size < 20,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Aggiungi passaggio") }
                        }
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
                                val recurrenceDays = 1
                                val durationMinutes = selectedDurationMinutes(editedDuration, editedCustomDuration)
                                if (durationMinutes !in 30..720 || durationMinutes % 30 != 0) {
                                    Toast.makeText(context, "Scegli una durata da 0,5 a 12 ore in intervalli di mezz’ora", Toast.LENGTH_LONG).show()
                                    return@TextButton
                                }
                                if (editedRecurrence == "Personalizzata" && editedRecurrenceWeekdays.isEmpty()) {
                                    Toast.makeText(context, "Seleziona almeno un giorno della settimana", Toast.LENGTH_LONG).show()
                                    return@TextButton
                                }
                                if (editedRecurrence != "Mai" && editedAppointmentTime == null) {
                                    Toast.makeText(context, "Una ripetizione richiede una data e un orario", Toast.LENGTH_LONG).show()
                                    return@TextButton
                                }
                                val newRoutineSteps = editedRoutineSteps
                                    .map { it.copy(title = it.title.trim()) }
                                    .filter { it.title.isNotBlank() }
                                if (task.routineSteps.isNotEmpty() && newRoutineSteps.isEmpty()) {
                                    Toast.makeText(context, "La routine richiede almeno un passaggio", Toast.LENGTH_LONG).show()
                                    return@TextButton
                                }
                                val now = System.currentTimeMillis()
                                val newAppointmentTime =
                                    editedAppointmentTime ?: task.appointmentTime
                                val newReminderTime = when (editedReminderMode) {
                                    "Nessuno" -> null
                                    "All’ora esatta" -> newAppointmentTime
                                    else -> editedReminderTime
                                }
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
                                    editedReminderMode == "Personalizzato" &&
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

                                val saveEditedTask: () -> Unit = save@ {
                                    val mustReschedule =
                                        newReminderTime != null &&
                                            newReminderTime > now &&
                                            (reminderChanged || titleChanged)
                                    if (
                                        mustReschedule &&
                                        !scheduleReminder(
                                            context,
                                            newTitle,
                                            newReminderTime!!,
                                            editedAlarmEnabled
                                        )
                                    ) return@save
                                    if (
                                        task.reminderTime != null &&
                                        (reminderChanged || titleChanged) &&
                                        task.reminderTime > now
                                    ) cancelReminder(context, task)

                                    tasks[index] = task.copy(
                                        title = newTitle,
                                        category = editedCategory,
                                        priority = editedPriority,
                                        appointmentTime = newAppointmentTime,
                                        reminderTime = newReminderTime,
                                        alarmEnabled = editedAlarmEnabled,
                                        recurrence = editedRecurrence,
                                        recurrenceIntervalDays = recurrenceDays,
                                        recurrenceWeekdays = if (editedRecurrence == "Personalizzata") editedRecurrenceWeekdays.toList() else emptyList(),
                                        durationMinutes = durationMinutes,
                                        routineSteps = if (task.routineSteps.isNotEmpty()) newRoutineSteps else task.routineSteps
                                    )
                                    saveTasks(context, tasks)
                                    editingIndex = null
                                    editedTitle = ""
                                    Toast.makeText(context, "Attività aggiornata", Toast.LENGTH_SHORT).show()
                                }
                                val conflict = findScheduleConflict(
                                    tasks = tasks,
                                    proposedStart = newAppointmentTime ?: newReminderTime,
                                    proposedDurationMinutes = durationMinutes,
                                    excludeIndex = index
                                )
                                if (conflict != null) {
                                    conflictToConfirm = conflict
                                    pendingConflictAction = saveEditedTask
                                } else {
                                    saveEditedTask()
                                }
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

private data class ScheduleConflict(
    val task: TaskItem,
    val overlapStart: Long,
    val overlapEnd: Long
)

private fun findScheduleConflict(
    tasks: List<TaskItem>,
    proposedStart: Long?,
    proposedDurationMinutes: Int,
    excludeIndex: Int? = null
): ScheduleConflict? {
    if (proposedStart == null) return null
    val proposedEnd = proposedStart + proposedDurationMinutes * 60_000L
    return tasks.mapIndexedNotNull { index, task ->
        if (index == excludeIndex || task.completed) return@mapIndexedNotNull null
        val existingStart = task.appointmentTime ?: task.reminderTime
            ?: return@mapIndexedNotNull null
        val existingEnd = existingStart + task.durationMinutes * 60_000L
        val overlapStart = maxOf(proposedStart, existingStart)
        val overlapEnd = minOf(proposedEnd, existingEnd)
        if (overlapStart < overlapEnd) {
            ScheduleConflict(task, overlapStart, overlapEnd)
        } else null
    }.minByOrNull { it.overlapStart }
}

private val FaccioNavy: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface
private val FaccioTeal: Color
    @Composable get() = MaterialTheme.colorScheme.secondary
private val FaccioCoral: Color
    @Composable get() = MaterialTheme.colorScheme.error
private val FaccioAmber: Color
    @Composable get() = MaterialTheme.colorScheme.tertiary
private val FaccioCard: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val FaccioMutedText: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun FaccioBottomBar(
    selected: String,
    onSelected: (String) -> Unit
) {
    val items = listOf(
        Triple("Oggi", Icons.Default.DateRange, { onSelected("Oggi") }),
        Triple("Attività", Icons.Default.CheckCircle, { onSelected("Attività") }),
        Triple("Strumenti", Icons.Default.Build, { onSelected("Strumenti") })
    )
    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        items.forEach { (item, icon, action) ->
            NavigationBarItem(
                selected = selected == item,
                onClick = action,
                icon = { Icon(icon, contentDescription = item) },
                label = { Text(item) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = FaccioTeal,
                    selectedTextColor = FaccioTeal,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = FaccioMutedText,
                    unselectedTextColor = FaccioMutedText
                )
            )
        }
    }
}

private fun suggestedListKind(title: String): String? {
    val text = title.lowercase(Locale.ITALIAN)
    val shoppingKeywords = listOf(
        "spesa",
        "supermercato",
        "comprare",
        "acquisti",
        "alimentari"
    )
    if (shoppingKeywords.any { keyword -> keyword in text }) return "spesa"

    val travelKeywords = listOf(
        "viaggio",
        "weekend",
        "week-end",
        "gita",
        "vacanza",
        "vacanze",
        "partenza",
        "trasferta",
        "campeggio",
        "escursione"
    )
    return if (travelKeywords.any { keyword -> keyword in text }) "viaggio" else null
}

private fun isShoppingTask(title: String): Boolean =
    suggestedListKind(title) == "spesa"

@Composable
private fun taskCategoryColor(category: String): Color = when (category) {
    "Salute" -> FaccioCoral
    "Casa" -> FaccioAmber
    "Lavoro" -> FaccioTeal
    else -> Color(0xFF3978C5)
}

@Composable
private fun GuideTopic(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = FaccioCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = FaccioNavy
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = FaccioMutedText
            )
        }
    }
}

@Composable
private fun TodayAgenda(
    tasks: List<TaskItem>,
    onCompletedChange: (Int, Boolean) -> Unit,
    onStepChange: (Int, Int, Boolean) -> Unit,
    onOpenMap: (TaskItem) -> Unit,
    onAddTask: () -> Unit,
    onOpenShoppingList: (Int) -> Unit,
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
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
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
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        if (routeCandidates.size >= 2) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                    onOpenMap = onOpenMap,
                    onOpenShoppingList = { onOpenShoppingList(entry.index) }
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
                    onOpenMap = onOpenMap,
                    onOpenShoppingList = { onOpenShoppingList(index) }
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
    onOpenMap: (TaskItem) -> Unit,
    onOpenShoppingList: () -> Unit
) {
    val categoryColor = taskCategoryColor(task.category)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNext) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
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
            Icon(
                imageVector = selectionIcon("Categoria", task.category) ?: Icons.Default.Person,
                contentDescription = task.category,
                tint = categoryColor,
                modifier = Modifier.padding(top = 7.dp, end = 9.dp).size(21.dp)
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
                if (task.shoppingListEnabled) {
                    val completedItems = task.shoppingItems.count { it.completed }
                    TextButton(
                        onClick = onOpenShoppingList,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                    ) {
                        Text(
                            if (task.shoppingItems.isEmpty()) {
                                "Apri lista"
                            } else {
                                "Lista: $completedItems di ${task.shoppingItems.size}"
                            },
                            color = FaccioTeal
                        )
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
    30 -> "0,5 ore"
    60 -> "1 ora"
    120 -> "2 ore"
    else -> "Personalizzata"
}

private fun selectedDurationMinutes(option: String, customValue: String): Int = when (option) {
    "0,5 ore" -> 30
    "1 ora" -> 60
    "2 ore" -> 120
    else -> customValue.toIntOrNull() ?: 0
}

private val HALF_HOUR_DURATIONS = (1..24).map { it * 30 }

private fun formatDurationHours(minutes: Int): String {
    val halfHours = minutes.coerceIn(30, 720) / 30
    return when {
        halfHours == 1 -> "0,5 ore"
        halfHours == 2 -> "1 ora"
        halfHours % 2 == 0 -> "${halfHours / 2} ore"
        else -> "${halfHours / 2},5 ore"
    }
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

private val WEEKDAYS = listOf(
    Calendar.MONDAY to "Lun",
    Calendar.TUESDAY to "Mar",
    Calendar.WEDNESDAY to "Mer",
    Calendar.THURSDAY to "Gio",
    Calendar.FRIDAY to "Ven",
    Calendar.SATURDAY to "Sab",
    Calendar.SUNDAY to "Dom"
)

@Composable
private fun WeekdaySelector(
    selectedDays: List<Int>,
    onToggle: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            WEEKDAYS.take(4).forEach { (day, label) ->
                FilterChip(
                    selected = day in selectedDays,
                    onClick = { onToggle(day) },
                    label = { Text(label) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FaccioTeal,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            WEEKDAYS.drop(4).forEach { (day, label) ->
                FilterChip(
                    selected = day in selectedDays,
                    onClick = { onToggle(day) },
                    label = { Text(label) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FaccioTeal,
                        selectedLabelColor = Color.White
                    )
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

private fun weekdayShortName(day: Int): String =
    WEEKDAYS.firstOrNull { it.first == day }?.second ?: ""

private fun weekdayOrder(day: Int): Int =
    WEEKDAYS.indexOfFirst { it.first == day }.let { if (it < 0) Int.MAX_VALUE else it }

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
        values = listOf("0,5 ore", "1 ora", "2 ore", "Personalizzata"),
        onValueSelected = onSelected,
        modifier = Modifier.fillMaxWidth()
    )
    if (selected == "Personalizzata") {
        val currentMinutes = customValue.toIntOrNull()
            ?.let { ((it + 15) / 30 * 30).coerceIn(30, 720) }
            ?: 30
        SelectionMenu(
            label = "Ore",
            selectedValue = formatDurationHours(currentMinutes),
            values = HALF_HOUR_DURATIONS.map(::formatDurationHours),
            onValueSelected = { label ->
                val selectedIndex = HALF_HOUR_DURATIONS
                    .indexOfFirst { formatDurationHours(it) == label }
                if (selectedIndex >= 0) {
                    onCustomChanged(HALF_HOUR_DURATIONS[selectedIndex].toString())
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Da 0,5 a 12 ore, con intervalli di mezz’ora.",
            style = MaterialTheme.typography.bodySmall,
            color = FaccioMutedText
        )
    }
}

private fun recurrenceLabel(task: TaskItem): String = when (task.recurrence) {
    "Personalizzata" -> if (task.recurrenceWeekdays.isEmpty()) {
        "Ripetizione personalizzata"
    } else {
        "Si ripete: ${task.recurrenceWeekdays.sortedBy(::weekdayOrder).joinToString(" · ") { weekdayShortName(it) }}"
    }
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
    next.reminderTime?.let { scheduleReminder(context, next.title, it, next.alarmEnabled) }
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
        routineSteps = task.routineSteps.map { it.copy(completed = false) },
        shoppingItems = task.shoppingItems.map { it.copy(completed = false) }
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
                appointmentTime = if (item.isNull("appointmentTime")) null else item.optLong("appointmentTime"),
                reminderTime = if (item.isNull("reminderTime")) null else item.optLong("reminderTime"),
                alarmEnabled = item.optBoolean("alarmEnabled", false),
                recurrence = item.optString("recurrence", "Mai"),
                recurrenceWeekdays = item.optJSONArray("recurrenceWeekdays")?.let { days ->
                    List(days.length()) { dayIndex -> days.optInt(dayIndex) }
                }.orEmpty(),
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
                put("appointmentTime", template.appointmentTime ?: JSONObject.NULL)
                put("reminderTime", template.reminderTime ?: JSONObject.NULL)
                put("alarmEnabled", template.alarmEnabled)
                put("recurrence", template.recurrence)
                put("recurrenceWeekdays", JSONArray(template.recurrenceWeekdays))
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
            "Personalizzata" -> {
                if (task.recurrenceWeekdays.isEmpty()) {
                    add(Calendar.DAY_OF_YEAR, task.recurrenceIntervalDays.coerceAtLeast(1))
                } else {
                    do {
                        add(Calendar.DAY_OF_YEAR, 1)
                    } while (get(Calendar.DAY_OF_WEEK) !in task.recurrenceWeekdays)
                }
            }
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

    if (option == "All’ora esatta") return appointmentTime

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
    reminderTime: Long,
    isAlarm: Boolean = false
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
        putExtra("is_alarm", isAlarm)
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
    val icon = selectionIcon(label, selectedValue)
    val iconColor = selectionColor(label, selectedValue)

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = FaccioNavy),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            icon?.let {
                Icon(it, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text("$label: $selectedValue", maxLines = 1)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value) },
                    leadingIcon = {
                        selectionIcon(label, value)?.let {
                            Icon(it, contentDescription = null, tint = selectionColor(label, value))
                        }
                    },
                    onClick = {
                        onValueSelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun selectionIcon(label: String, value: String): ImageVector? = when {
    label.contains("Categoria", ignoreCase = true) -> when (value) {
        "Casa" -> Icons.Default.Home
        "Lavoro" -> Icons.Default.Work
        "Salute" -> Icons.Default.Favorite
        "Personale" -> Icons.Default.Person
        else -> null
    }
    label.contains("Priorità", ignoreCase = true) -> Icons.Default.Flag
    else -> null
}

@Composable
private fun selectionColor(label: String, value: String): Color = when {
    label.contains("Categoria", ignoreCase = true) -> when (value) {
        "Casa" -> Color(0xFF3978C5)
        "Lavoro" -> Color(0xFF6E62B5)
        "Salute" -> FaccioCoral
        "Personale" -> FaccioTeal
        else -> FaccioMutedText
    }
    label.contains("Priorità", ignoreCase = true) -> priorityColor(value)
    else -> FaccioMutedText
}

@Composable
private fun CategoryFilterRow(selected: String, onSelected: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(listOf("Tutte") + TASK_CATEGORIES) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelected(category) },
                label = { Text(category) },
                leadingIcon = selectionIcon("Categoria", category)?.let { icon ->
                    { Icon(icon, contentDescription = null, tint = selectionColor("Categoria", category), modifier = Modifier.size(17.dp)) }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun PriorityFilterRow(selected: String, onSelected: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(listOf("Tutte") + TASK_PRIORITIES) { priority ->
            FilterChip(
                selected = selected == priority,
                onClick = { onSelected(priority) },
                label = { Text(priority) },
                leadingIcon = if (priority == "Tutte") null else {
                    { Icon(Icons.Default.Flag, contentDescription = null, tint = priorityColor(priority), modifier = Modifier.size(17.dp)) }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
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
                alarmEnabled = item.optBoolean("alarmEnabled", false),
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
                arrivalAlarmEnabled = item.optBoolean("arrivalAlarmEnabled", false),
                departureTime = if (item.isNull("departureTime")) null else item.optLong("departureTime"),
                departureTravelMinutes = if (item.isNull("departureTravelMinutes")) null else item.optInt("departureTravelMinutes"),
                departureMarginMinutes = if (item.isNull("departureMarginMinutes")) null else item.optInt("departureMarginMinutes"),
                departureTransport = item.optString("departureTransport", "Auto"),
                departureSafety = item.optString("departureSafety", "Normale"),
                recurrence = item.optString("recurrence", "Mai"),
                recurrenceIntervalDays = item.optInt("recurrenceIntervalDays", 1).coerceAtLeast(1),
                recurrenceWeekdays = item.optJSONArray("recurrenceWeekdays")?.let { days ->
                    List(days.length()) { dayIndex -> days.optInt(dayIndex) }
                        .filter { it in Calendar.SUNDAY..Calendar.SATURDAY }
                        .distinct()
                }.orEmpty(),
                durationMinutes = item.optInt("durationMinutes", 30).coerceIn(5, 720),
                routineSteps = item.optJSONArray("routineSteps")?.let { steps ->
                    List(steps.length()) { stepIndex ->
                        val step = steps.getJSONObject(stepIndex)
                        RoutineStep(
                            title = step.optString("title"),
                            completed = step.optBoolean("completed", false)
                        )
                    }.filter { it.title.isNotBlank() }
                }.orEmpty(),
                shoppingListEnabled = item.optBoolean("shoppingListEnabled", false),
                shoppingItems = item.optJSONArray("shoppingItems")?.let { items ->
                    List(items.length()) { itemIndex ->
                        val shoppingItem = items.getJSONObject(itemIndex)
                        ShoppingItem(
                            title = shoppingItem.optString("title"),
                            completed = shoppingItem.optBoolean("completed", false)
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
                put("alarmEnabled", task.alarmEnabled)
                put("category", task.category)
                put("priority", task.priority)
                put("appointmentTime", task.appointmentTime ?: JSONObject.NULL)
                put("location", task.location ?: JSONObject.NULL)
                put("latitude", task.latitude ?: JSONObject.NULL)
                put("longitude", task.longitude ?: JSONObject.NULL)
                put("arrivalReminderId", task.arrivalReminderId ?: JSONObject.NULL)
                put("arrivalAlarmEnabled", task.arrivalAlarmEnabled)
                put("departureTime", task.departureTime ?: JSONObject.NULL)
                put("departureTravelMinutes", task.departureTravelMinutes ?: JSONObject.NULL)
                put("departureMarginMinutes", task.departureMarginMinutes ?: JSONObject.NULL)
                put("departureTransport", task.departureTransport)
                put("departureSafety", task.departureSafety)
                put("recurrence", task.recurrence)
                put("recurrenceIntervalDays", task.recurrenceIntervalDays)
                put("recurrenceWeekdays", JSONArray(task.recurrenceWeekdays))
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
                put("shoppingListEnabled", task.shoppingListEnabled)
                put(
                    "shoppingItems",
                    JSONArray().apply {
                        task.shoppingItems.forEach { item ->
                            put(
                                JSONObject().apply {
                                    put("title", item.title)
                                    put("completed", item.completed)
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
