package it.faccioio.app

import android.app.AlarmManager
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ALARM_REGISTRY_PREFS = "faccio_io_alarm_registry"
private const val ALARM_EVENTS_KEY = "alarm_events"
private const val MAX_ALARM_EVENTS = 40

internal fun markReminderScheduled(
    context: Context,
    title: String,
    time: Long,
    isAlarm: Boolean
) {
    val key = reminderRequestCode(title, time).toString()
    context.getSharedPreferences(ALARM_REGISTRY_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putLong("scheduled_time_$key", time)
        .putString("scheduled_title_$key", title)
        .putBoolean("scheduled_alarm_$key", isAlarm)
        .apply()
    recordAlarmEvent(context, "PROGRAMMATA", title, time, isAlarm)
}

internal fun markReminderDelivered(
    context: Context,
    title: String,
    time: Long,
    isAlarm: Boolean
) {
    val key = reminderRequestCode(title, time).toString()
    context.getSharedPreferences(ALARM_REGISTRY_PREFS, Context.MODE_PRIVATE)
        .edit()
        .remove("scheduled_time_$key")
        .remove("scheduled_title_$key")
        .remove("scheduled_alarm_$key")
        .putLong("last_delivered_time", time)
        .putString("last_delivered_title", title)
        .putBoolean("last_delivered_alarm", isAlarm)
        .apply()
    recordAlarmEvent(context, "CONSEGNATA", title, time, isAlarm)
}

internal fun clearScheduledReminder(context: Context, title: String, time: Long) {
    val key = reminderRequestCode(title, time).toString()
    context.getSharedPreferences(ALARM_REGISTRY_PREFS, Context.MODE_PRIVATE)
        .edit()
        .remove("scheduled_time_$key")
        .remove("scheduled_title_$key")
        .remove("scheduled_alarm_$key")
        .apply()
    recordAlarmEvent(context, "CANCELLATA", title, time, false)
}

internal fun markAlarmRestoreStarted(context: Context, count: Int) {
    recordAlarmEvent(
        context = context,
        action = "RIPRISTINO ($count)",
        title = "Avvio o aggiornamento",
        time = System.currentTimeMillis(),
        isAlarm = false
    )
}

private fun recordAlarmEvent(
    context: Context,
    action: String,
    title: String,
    time: Long,
    isAlarm: Boolean
) {
    val prefs = context.getSharedPreferences(ALARM_REGISTRY_PREFS, Context.MODE_PRIVATE)
    val entry = listOf(
        System.currentTimeMillis().toString(),
        action.replace('|', '/'),
        title.replace('|', '/').replace('\n', ' '),
        time.toString(),
        isAlarm.toString()
    ).joinToString("|")
    val events = prefs.getString(ALARM_EVENTS_KEY, "")
        .orEmpty()
        .lineSequence()
        .filter { it.isNotBlank() }
        .toMutableList()
        .apply { add(entry) }
        .takeLast(MAX_ALARM_EVENTS)
    prefs.edit().putString(ALARM_EVENTS_KEY, events.joinToString("\n")).apply()
}

internal fun alarmDiagnosticReport(
    context: Context,
    tasks: List<TaskItem>,
    now: Long = System.currentTimeMillis()
): String {
    val prefs = context.getSharedPreferences(ALARM_REGISTRY_PREFS, Context.MODE_PRIVATE)
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val nextSystemAlarm = alarmManager.nextAlarmClock?.triggerTime
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ITALIAN)
    fun formatted(time: Long?): String = time?.let { formatter.format(Date(it)) } ?: "nessuna"

    val alarmTasks = tasks
        .filter { it.alarmEnabled && it.reminderTime != null }
        .sortedBy { it.reminderTime }

    return buildString {
        appendLine("Ora: ${formatted(now)}")
        appendLine("Prossima sveglia Android: ${formatted(nextSystemAlarm)}")
        appendLine()
        if (alarmTasks.isEmpty()) {
            appendLine("Nessuna attività salvata come sveglia.")
        } else {
            alarmTasks.forEach { task ->
                val time = task.reminderTime!!
                val key = reminderRequestCode(task.title, time).toString()
                val inRegistry = prefs.getLong("scheduled_time_$key", -1L) == time
                val pending = isReminderPending(context, task.title, time)
                appendLine(task.title)
                appendLine("  Orario: ${formatted(time)}")
                appendLine("  Ripetizione: ${task.recurrence}")
                appendLine("  Completata: ${if (task.completed) "sì" else "no"}")
                appendLine("  Registro app: ${if (inRegistry) "presente" else "assente"}")
                appendLine("  Collegamento Android: ${if (pending) "presente" else "assente"}")
                appendLine("  ID: ${reminderRequestCode(task.title, time)}")
                appendLine()
            }
        }
        appendLine("Ultimi eventi:")
        val events = prefs.getString(ALARM_EVENTS_KEY, "")
            .orEmpty()
            .lineSequence()
            .filter { it.isNotBlank() }
            .toList()
            .takeLast(12)
        if (events.isEmpty()) {
            appendLine("  Nessun evento registrato in questa versione.")
        } else {
            events.asReversed().forEach { raw ->
                val parts = raw.split('|')
                if (parts.size >= 5) {
                    val recordedAt = parts[0].toLongOrNull()
                    val scheduledFor = parts[3].toLongOrNull()
                    val kind = if (parts[4].toBooleanStrictOrNull() == true) "sveglia" else "avviso"
                    appendLine(
                        "  ${formatted(recordedAt)} · ${parts[1]} · ${parts[2]} · " +
                            "${formatted(scheduledFor)} · $kind"
                    )
                }
            }
        }
    }.trim()
}
