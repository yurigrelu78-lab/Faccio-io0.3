package it.faccioio.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON" &&
            intent.action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            return
        }

        val now = System.currentTimeMillis()
        loadTasksForBoot(context)
            .filter { task ->
                task.reminderTime != null &&
                    task.reminderTime > now &&
                    !task.completed
            }
            .forEach { task ->
                val reminderTime = task.reminderTime ?: return@forEach
                val reminderIntent =
                    Intent(context, ReminderReceiver::class.java).apply {
                        putExtra("task_title", task.title)
                    }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    reminderRequestCode(task.title, reminderTime),
                    reminderIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
                )

                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminderTime,
                        pendingIntent
                    )
                } catch (_: SecurityException) {
                    return
                }
            }
    }
}
