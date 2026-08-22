package it.faccioio.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("task_title") ?: return
        val oldTime = intent.getLongExtra("reminder_time", 0L)
        if (oldTime <= 0L) return

        val tasks = loadTasks(context).toMutableList()
        val index = tasks.indexOfFirst { task ->
            (task.title == title && task.reminderTime == oldTime) ||
                ("È ora di partire: ${task.title}" == title && task.departureTime == oldTime)
        }
        if (index < 0) return

        val task = tasks[index]
        val isDeparture = task.departureTime == oldTime && title.startsWith("È ora di partire:")
        var newTime = calculateSnoozeTime(intent.action)
        task.appointmentTime?.let { appointment ->
            val minimumLead = if (isDeparture) 5L * 60L * 1000L else 30L * 60L * 1000L
            if (newTime >= appointment) {
                if (appointment <= System.currentTimeMillis() + 2L * 60L * 1000L) {
                    Toast.makeText(context, "L’appuntamento è imminente: non posso rinviare l’avviso", Toast.LENGTH_LONG).show()
                    return
                }
                newTime = appointment - minimumLead
            }
        }
        if (newTime <= System.currentTimeMillis()) {
            newTime = System.currentTimeMillis() + 5L * 60L * 1000L
        }

        if (!scheduleReminder(context, title, newTime)) return
        tasks[index] = if (isDeparture) {
            task.copy(departureTime = newTime)
        } else {
            task.copy(reminderTime = newTime)
        }
        saveTasks(context, tasks)
        NotificationManagerCompat.from(context)
            .cancel(intent.getIntExtra("notification_id", title.hashCode()))
        Toast.makeText(
            context,
            "Riprogrammato: ${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(newTime))}",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun calculateSnoozeTime(action: String?): Long {
        val now = Calendar.getInstance()
        return when (action) {
            ACTION_THIS_EVENING -> Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 19)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis
            ACTION_TOMORROW -> Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            else -> now.apply { add(Calendar.HOUR_OF_DAY, 1) }.timeInMillis
        }
    }

    companion object {
        const val ACTION_ONE_HOUR = "it.faccioio.app.SNOOZE_ONE_HOUR"
        const val ACTION_THIS_EVENING = "it.faccioio.app.SNOOZE_THIS_EVENING"
        const val ACTION_TOMORROW = "it.faccioio.app.SNOOZE_TOMORROW"
    }
}
