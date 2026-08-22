package it.faccioio.app
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import java.util.Calendar
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    val channel = NotificationChannel(
    "faccio_io_reminders_v2",
    "Promemoria Faccio io",
    NotificationManager.IMPORTANCE_HIGH
).apply {
    description = "Promemoria delle attività di Faccio io"

    enableVibration(true)
    vibrationPattern = longArrayOf(0, 500, 300, 500)

    val soundUri =
        android.media.RingtoneManager.getDefaultUri(
            android.media.RingtoneManager.TYPE_NOTIFICATION
        )

    val audioAttributes =
        android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
            .build()

    setSound(soundUri, audioAttributes)
    }

    val notificationManager =
        getSystemService(NotificationManager::class.java)

    notificationManager.createNotificationChannel(channel)
        }
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
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FaccioIoApp()
                }
            }
        }
    }
}

data class TaskItem(
    val title: String,
    val completed: Boolean = false,
    val reminderTime: Long? = null
)

@Composable
fun FaccioIoApp() {
    var newTask by remember { mutableStateOf("") }
var reminderTime by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Long?>(null) }
val context = LocalContext.current
    val tasks = remember {
        mutableStateListOf(
            TaskItem("Controllare gli impegni di oggi"),
            TaskItem("Fare una pausa di 10 minuti"),
            TaskItem("Preparare le cose per domani")
        )
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

        Spacer(modifier = Modifier.height(12.dp))
        Button(
    onClick = {
        val calendar = Calendar.getInstance()

        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        calendar.clear()
                        calendar.set(year, month, day, hour, minute, 0)
                        reminderTime = calendar.timeInMillis

                        android.widget.Toast.makeText(
                            context,
                            "Data e ora del promemoria memorizzate",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    },
    modifier = Modifier.fillMaxWidth()
) {
    Text("Imposta promemoria")
}

reminderTime?.let { selectedTime ->
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Promemoria: " +
            java.text.SimpleDateFormat(
                "dd/MM/yyyy HH:mm",
                java.util.Locale.getDefault()
            ).format(java.util.Date(selectedTime)),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
    )
}

Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = addTask@{
                val text = newTask.trim()

                if (text.isEmpty()) {
                    android.widget.Toast.makeText(
                        context,
                        "Scrivi prima il nome dell’attività",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@addTask
                }

                val validatedReminder = reminderTime

                if (validatedReminder == null) {
                    android.widget.Toast.makeText(
                        context,
                        "Imposta prima data e ora del promemoria",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@addTask
                }

                if (validatedReminder <= System.currentTimeMillis()) {
                    android.widget.Toast.makeText(
                        context,
                        "Scegli un orario futuro",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@addTask
                }

                if (text.isNotEmpty()) {

    val selectedReminder = validatedReminder

    if (
        selectedReminder != null &&
        selectedReminder > System.currentTimeMillis()
    ) {
        val intent = Intent(
            context,
            ReminderReceiver::class.java
        ).apply {
            putExtra("task_title", text)
        }

        val requestCode =
            (selectedReminder xor text.hashCode().toLong()).hashCode()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            android.widget.Toast.makeText(
                context,
                "Autorizza sveglie e promemoria, poi riprova",
                android.widget.Toast.LENGTH_LONG
            ).show()

            val permissionIntent = Intent(
                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                android.net.Uri.parse("package:${context.packageName}")
            )

            try {
                context.startActivity(permissionIntent)
            } catch (_: Exception) {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_SETTINGS)
                )
            }

            return@addTask
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                selectedReminder,
                pendingIntent
            )

            android.widget.Toast.makeText(
                context,
                "Promemoria programmato",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } catch (_: SecurityException) {
            android.widget.Toast.makeText(
                context,
                "Autorizza sveglie e promemoria nelle impostazioni",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return@addTask
        }
    }

    tasks.add(
        TaskItem(
            text,
            reminderTime = selectedReminder
        )
    )

    newTask = ""
    reminderTime = null
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

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tasks) { task ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = task.completed,
                        onCheckedChange = { checked ->
                            val index = tasks.indexOf(task)

                            if (index >= 0) {
                                tasks[index] =
                                    task.copy(completed = checked)
                            }
                        }
                    )

                    Text(
                        text = task.title,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
