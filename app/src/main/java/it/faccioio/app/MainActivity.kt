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
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    val priority: String = "Media"
)

internal const val TASK_PREFS = "faccio_io_tasks"
internal const val TASKS_KEY = "saved_tasks"
private val TASK_CATEGORIES = listOf("Casa", "Lavoro", "Salute", "Personale")
private val TASK_PRIORITIES = listOf("Bassa", "Media", "Alta")

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
    var categoryFilter by rememberSaveable { mutableStateOf("Tutte") }
    var priorityFilter by rememberSaveable { mutableStateOf("Tutte") }

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

    fun addPendingTask(reminderTime: Long? = null) {
        tasks.add(
            TaskItem(
                title = pendingTask,
                reminderTime = reminderTime,
                category = pendingCategory,
                priority = pendingPriority
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
                    showReminderChoice = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Aggiungi attività")
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

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

    if (showReminderChoice) {
        AlertDialog(
            onDismissRequest = { showReminderChoice = false },
            title = { Text("Vuoi un promemoria?") },
            text = {
                Text(
                    "Puoi aggiungere l’attività subito oppure scegliere data e ora del promemoria."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReminderChoice = false
                        showReminderPicker(context) { selectedTime ->
                            if (selectedTime <= System.currentTimeMillis()) {
                                Toast.makeText(
                                    context,
                                    "Scegli un orario futuro",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else if (
                                scheduleReminder(context, pendingTask, selectedTime)
                            ) {
                                addPendingTask(selectedTime)
                                Toast.makeText(
                                    context,
                                    "Attività e promemoria aggiunti",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                ) {
                    Text("Imposta promemoria")
                }
            },
            dismissButton = {
                TextButton(onClick = { addPendingTask() }) {
                    Text("Senza promemoria")
                }
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                val reminderTime = task.reminderTime
                                val reminderStillActive =
                                    reminderTime != null &&
                                        reminderTime > System.currentTimeMillis()

                                if (
                                    reminderStillActive &&
                                    newTitle != task.title &&
                                    reminderTime != null
                                ) {
                                    if (!scheduleReminder(context, newTitle, reminderTime)) {
                                        return@TextButton
                                    }
                                    cancelReminder(context, task)
                                }

                                tasks[index] = task.copy(
                                    title = newTitle,
                                    category = editedCategory,
                                    priority = editedPriority
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
    val initialCalendar = Calendar.getInstance().apply {
        add(Calendar.MINUTE, 2)
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
                priority = item.optString("priority", "Media")
            )
        }
    } catch (_: Exception) {
        fallback
    }
}

private fun saveTasks(context: Context, tasks: List<TaskItem>) {
    val array = JSONArray()
    tasks.forEach { task ->
        array.put(
            JSONObject().apply {
                put("title", task.title)
                put("completed", task.completed)
                put("reminderTime", task.reminderTime ?: JSONObject.NULL)
                put("category", task.category)
                put("priority", task.priority)
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
