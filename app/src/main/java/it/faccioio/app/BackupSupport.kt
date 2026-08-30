package it.faccioio.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

data class BackupPayload(
    val tasks: List<TaskItem>,
    val customTemplates: List<TaskItem>,
    val exportedAt: Long
)

internal fun exportCompleteBackup(context: Context, uri: Uri): Boolean {
    return try {
    val taskArray = JSONArray(serializeTasks(loadTasks(context)))
    val templates = loadCustomRoutineTemplates(context)
    val root = JSONObject().apply {
        put("format", "faccio-io-backup")
        put("version", 1)
        put("exportedAt", System.currentTimeMillis())
        put("tasks", taskArray)
        put("customRoutineTemplates", templatesToJson(templates))
        put(
            "appSettings",
            JSONObject().apply {
                val setup = context.getSharedPreferences("faccio_io_setup", Context.MODE_PRIVATE)
                put("initialSetupComplete", setup.getBoolean("initial_setup_complete", false))
                put("autoStartConfirmed", setup.getBoolean("auto_start_confirmed", false))
            }
        )
    }
    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
        it.write(root.toString(2))
    } ?: return false
        true
    } catch (_: Exception) {
        false
    }
}

internal fun readCompleteBackup(context: Context, uri: Uri): BackupPayload? {
    return try {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return null
        val root = JSONObject(text)
        if (root.optString("format") != "faccio-io-backup" || root.optInt("version") != 1) {
            return null
        }
        val taskArray = root.getJSONArray("tasks")
        val tasks = parseTasks(taskArray.toString(), emptyList())
        if (tasks.size != taskArray.length()) return null
        val templates = templatesFromJson(root.optJSONArray("customRoutineTemplates") ?: JSONArray())
        BackupPayload(tasks, templates, root.optLong("exportedAt", 0L))
    } catch (_: Exception) {
        null
    }
}

internal fun applyCompleteBackup(context: Context, payload: BackupPayload): Boolean = try {
    loadTasks(context).forEach { old ->
        cancelReminder(context, old)
        cancelDepartureReminder(context, old)
        old.arrivalReminderId?.let { removeArrivalGeofence(context, it) }
    }

    saveTasks(context, payload.tasks)
    saveCustomRoutineTemplates(context, payload.customTemplates)

    context.getSharedPreferences("faccio_io_setup", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("initial_setup_complete", false)
        .putBoolean("auto_start_confirmed", false)
        .apply()
    true
} catch (_: Exception) {
    false
}

internal data class AutomationAlarm(
    val title: String,
    val time: Long,
    val isAlarm: Boolean
)

internal fun futureAutomationAlarms(
    tasks: List<TaskItem>,
    now: Long = System.currentTimeMillis()
): List<AutomationAlarm> = tasks
    .filter { !it.completed }
    .flatMap { task ->
        buildList {
            task.reminderTime?.takeIf { it > now }?.let {
                add(AutomationAlarm(task.title, it, task.alarmEnabled))
            }
            task.departureTime?.takeIf { it > now }?.let {
                add(AutomationAlarm("È ora di partire: ${task.title}", it, false))
            }
        }
    }

internal fun restoreAllFutureAutomations(
    context: Context,
    tasks: List<TaskItem> = loadTasks(context)
): Boolean {
    var allScheduled = true
    futureAutomationAlarms(tasks).forEach { alarm ->
        if (!scheduleReminder(context, alarm.title, alarm.time, alarm.isAlarm)) {
            allScheduled = false
        }
    }
    restoreArrivalGeofencesIfAllowed(context, tasks)
    return allScheduled
}

private fun restoreArrivalGeofencesIfAllowed(context: Context, tasks: List<TaskItem>) {
    val foreground = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    if (!foreground || !background) return
    tasks.filter {
        it.arrivalReminderId != null && it.latitude != null && it.longitude != null && !it.completed
    }.forEach { task ->
        registerArrivalGeofence(
            context,
            task.arrivalReminderId!!,
            task.title,
            task.latitude!!,
            task.longitude!!
        )
    }
}

private fun templatesToJson(templates: List<TaskItem>): JSONArray = JSONArray().apply {
    templates.forEach { template ->
        put(
            JSONObject().apply {
                put("title", template.title)
                put("category", template.category)
                put("priority", template.priority)
                put("durationMinutes", template.durationMinutes)
                put("steps", JSONArray().apply { template.routineSteps.forEach { put(it.title) } })
            }
        )
    }
}

private fun templatesFromJson(array: JSONArray): List<TaskItem> =
    List(array.length()) { index ->
        val item = array.getJSONObject(index)
        val steps = item.optJSONArray("steps") ?: JSONArray()
        TaskItem(
            title = item.optString("title"),
            category = item.optString("category", "Personale"),
            priority = item.optString("priority", "Media"),
            durationMinutes = item.optInt("durationMinutes", 15).coerceIn(5, 720),
            routineSteps = List(steps.length()) { RoutineStep(steps.optString(it)) }
                .filter { it.title.isNotBlank() }
        )
    }.filter { it.title.isNotBlank() && it.routineSteps.isNotEmpty() }
