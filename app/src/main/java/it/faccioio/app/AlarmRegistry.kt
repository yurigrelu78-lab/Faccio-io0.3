package it.faccioio.app

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ALARM_REGISTRY_PREFS = "faccio_io_alarm_registry"
private const val ALARM_EVENTS_KEY = "alarm_events_v2"
private const val MAX_ALARM_EVENTS = 500
private const val FIELD_SEPARATOR = "\u001f"
private val alarmLogLock = Any()

private fun alarmPrefs(context: Context) = context.createDeviceProtectedStorageContext()
    .getSharedPreferences(ALARM_REGISTRY_PREFS, Context.MODE_PRIVATE)

internal fun diagnosticCaller(): String = Throwable().stackTrace
    .firstOrNull { frame ->
        frame.className.startsWith("it.faccioio.app") &&
            !frame.className.endsWith("AlarmRegistryKt") &&
            frame.methodName !in setOf(
                "scheduleReminder", "cancelReminder", "cancelDepartureReminder", "saveTasks"
            )
    }
    ?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
    ?: "origine sconosciuta"

internal fun markReminderScheduled(
    context: Context,
    title: String,
    time: Long,
    isAlarm: Boolean,
    source: String = diagnosticCaller()
) {
    val key = reminderRequestCode(title, time).toString()
    alarmPrefs(context).edit()
        .putLong("scheduled_time_$key", time)
        .putString("scheduled_title_$key", title)
        .putBoolean("scheduled_alarm_$key", isAlarm)
        .putBoolean("last_pending_$key", true)
        .commit()
    recordAlarmEvent(context, "PROGRAMMATA", title, time, isAlarm, "origine=$source; id=$key")
}

internal fun markReminderDelivered(context: Context, title: String, time: Long, isAlarm: Boolean) {
    val key = reminderRequestCode(title, time).toString()
    alarmPrefs(context).edit()
        .remove("scheduled_time_$key")
        .remove("scheduled_title_$key")
        .remove("scheduled_alarm_$key")
        .putBoolean("last_pending_$key", false)
        .putLong("last_delivered_time", time)
        .putString("last_delivered_title", title)
        .putBoolean("last_delivered_alarm", isAlarm)
        .commit()
    recordAlarmEvent(context, "RICEVUTA DAL SISTEMA", title, time, isAlarm, "ReminderReceiver eseguito; id=$key")
}

internal fun clearScheduledReminder(
    context: Context,
    title: String,
    time: Long,
    isAlarm: Boolean = false,
    source: String = diagnosticCaller(),
    pendingWasPresent: Boolean? = null
) {
    val key = reminderRequestCode(title, time).toString()
    alarmPrefs(context).edit()
        .remove("scheduled_time_$key")
        .remove("scheduled_title_$key")
        .remove("scheduled_alarm_$key")
        .putBoolean("last_pending_$key", false)
        .commit()
    recordAlarmEvent(
        context, "CANCELLAZIONE RICHIESTA", title, time, isAlarm,
        "origine=$source; collegamentoPrima=${pendingWasPresent ?: "non verificato"}; id=$key"
    )
}

internal fun markAlarmRestoreStarted(context: Context, count: Int, reason: String = "Avvio o aggiornamento") {
    recordAlarmEvent(context, "RIPRISTINO ($count)", reason, System.currentTimeMillis(), false, diagnosticEnvironment(context))
}

internal fun recordAlarmFailure(context: Context, action: String, title: String, time: Long, isAlarm: Boolean, detail: String) =
    recordAlarmEvent(context, action, title, time, isAlarm, detail)

internal fun recordSystemDiagnosticEvent(context: Context, action: String, detail: String = "") {
    recordAlarmEvent(
        context, "EVENTO SISTEMA", action, System.currentTimeMillis(), false,
        listOf(detail, diagnosticEnvironment(context)).filter { it.isNotBlank() }.joinToString("; ")
    )
}

internal fun recordTasksSavedDiagnostic(
    context: Context,
    previous: List<TaskItem>,
    updated: List<TaskItem>,
    source: String = diagnosticCaller()
) {
    fun signature(task: TaskItem) = listOf(
        task.reminderTime, task.alarmEnabled, task.departureTime, task.completed,
        task.recurrence, task.recurrenceWeekdays.joinToString(",")
    ).joinToString("|")
    val oldByTitle = previous.associateBy { it.title }
    val newByTitle = updated.associateBy { it.title }
    (oldByTitle.keys + newByTitle.keys).distinct().forEach { title ->
        val old = oldByTitle[title]
        val new = newByTitle[title]
        if ((old?.alarmEnabled == true || old?.departureTime != null ||
                new?.alarmEnabled == true || new?.departureTime != null) &&
            old?.let(::signature) != new?.let(::signature)
        ) {
            val action = when {
                old == null -> "ATTIVITÀ CON ALLARME CREATA"
                new == null -> "ATTIVITÀ CON ALLARME RIMOSSA"
                else -> "DATI ALLARME MODIFICATI"
            }
            val reference = new ?: old!!
            recordAlarmEvent(
                context, action, title, reference.reminderTime ?: reference.departureTime ?: 0L,
                reference.alarmEnabled,
                "origine=$source; prima=${old?.let(::signature)}; dopo=${new?.let(::signature)}"
            )
        }
    }
}

private fun diagnosticEnvironment(context: Context): String {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    val ignoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    return "exact=$exact; idle=${powerManager.isDeviceIdleMode}; risparmio=${powerManager.isPowerSaveMode}; " +
        "batteriaSenzaLimiti=$ignoringBattery; batteria=$battery%; " +
        "nextAlarm=${alarmManager.nextAlarmClock?.triggerTime ?: -1L}"
}

internal fun diagnosticAlarmState(hadPending: Boolean, pending: Boolean): String = when {
    hadPending && !pending -> "SCOMPARSA DA ANDROID"
    pending -> "CONTROLLO PRESENTE"
    else -> "CONTROLLO ASSENTE"
}

private fun sanitize(value: String): String = value.replace(FIELD_SEPARATOR, " ").replace('\n', ' ')

private fun recordAlarmEvent(
    context: Context,
    action: String,
    title: String,
    time: Long,
    isAlarm: Boolean,
    detail: String = ""
) {
    synchronized(alarmLogLock) {
        val prefs = alarmPrefs(context)
        val entry = listOf(
            System.currentTimeMillis().toString(), sanitize(action), sanitize(title),
            time.toString(), isAlarm.toString(), sanitize(detail)
        ).joinToString(FIELD_SEPARATOR)
        val events = prefs.getString(ALARM_EVENTS_KEY, "").orEmpty()
            .lineSequence().filter { it.isNotBlank() }.toMutableList()
            .apply { add(entry) }.takeLast(MAX_ALARM_EVENTS)
        prefs.edit().putString(ALARM_EVENTS_KEY, events.joinToString("\n")).commit()
    }
}

internal fun captureAlarmDiagnosticSnapshot(
    context: Context,
    reason: String,
    tasks: List<TaskItem> = loadTasksForBoot(context),
    now: Long = System.currentTimeMillis()
) {
    recordSystemDiagnosticEvent(context, "CONTROLLO: $reason", "attività=${tasks.size}")
    val prefs = alarmPrefs(context)
    futureAutomationAlarms(tasks, now).forEach { alarm ->
        val key = reminderRequestCode(alarm.title, alarm.time).toString()
        val registered = prefs.getLong("scheduled_time_$key", -1L) == alarm.time
        val pending = isReminderPending(context, alarm.title, alarm.time)
        val hadPending = prefs.getBoolean("last_pending_$key", pending)
        val state = diagnosticAlarmState(hadPending, pending)
        recordAlarmEvent(
            context, state, alarm.title, alarm.time, alarm.isAlarm,
            "motivo=$reason; registro=$registered; collegamento=$pending; id=$key"
        )
        prefs.edit().putBoolean("last_pending_$key", pending).commit()
    }
}

internal fun alarmDiagnosticReport(
    context: Context,
    tasks: List<TaskItem>,
    now: Long = System.currentTimeMillis()
): String {
    captureAlarmDiagnosticSnapshot(context, "apertura diagnostica", tasks, now)
    val prefs = alarmPrefs(context)
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ITALIAN)
    fun formatted(time: Long?): String = time?.takeIf { it >= 0L }?.let { formatter.format(Date(it)) } ?: "nessuna"
    val alarmTasks = tasks.filter { it.alarmEnabled && it.reminderTime != null }.sortedBy { it.reminderTime }

    return buildString {
        appendLine("Ora: ${formatted(now)}")
        appendLine("Prossima sveglia Android: ${formatted(alarmManager.nextAlarmClock?.triggerTime)}")
        appendLine("Ambiente: ${diagnosticEnvironment(context)}")
        appendLine("Versione Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Produttore/modello: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Ora automatica: ${runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME) == 1 }.getOrDefault(false)}")
        appendLine()
        if (alarmTasks.isEmpty()) appendLine("Nessuna attività salvata come sveglia.")
        alarmTasks.forEach { task ->
            val time = task.reminderTime!!
            val key = reminderRequestCode(task.title, time).toString()
            appendLine(task.title)
            appendLine("  Orario: ${formatted(time)}")
            appendLine("  Ripetizione: ${task.recurrence}")
            appendLine("  Completata: ${if (task.completed) "sì" else "no"}")
            appendLine("  Registro app: ${if (prefs.getLong("scheduled_time_$key", -1L) == time) "presente" else "assente"}")
            appendLine("  Collegamento Android: ${if (isReminderPending(context, task.title, time)) "presente" else "assente"}")
            appendLine("  ID: $key")
            appendLine()
        }
        appendLine("Registro cronologico completo (più recente in alto):")
        val events = prefs.getString(ALARM_EVENTS_KEY, "").orEmpty()
            .lineSequence().filter { it.isNotBlank() }.toList()
        if (events.isEmpty()) appendLine("  Nessun evento registrato in questa versione.")
        events.asReversed().forEach { raw ->
            val parts = raw.split(FIELD_SEPARATOR)
            if (parts.size >= 5) {
                val kind = if (parts[4].toBooleanStrictOrNull() == true) "sveglia" else "avviso"
                val detail = parts.getOrNull(5).orEmpty().takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                appendLine("  ${formatted(parts[0].toLongOrNull())} · ${parts[1]} · ${parts[2]} · ${formatted(parts[3].toLongOrNull())} · $kind$detail")
            }
        }
    }.trim()
}
