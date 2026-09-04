package it.faccioio.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("task_title") ?: "Promemoria"
        val reminderTime = intent.getLongExtra("reminder_time", 0L)
        val isAlarm = intent.getBooleanExtra("is_alarm", false)
        recordSystemDiagnosticEvent(
            context,
            "REMINDER RECEIVER ATTIVATO",
            "titolo=$title; previsto=$reminderTime; tipo=${if (isAlarm) "sveglia" else "avviso"}"
        )
        markReminderDelivered(context, title, reminderTime, isAlarm)
        scheduleNextRecurringAlarmAfterDelivery(context, title, reminderTime)
        val notificationId = (title.hashCode() xor reminderTime.hashCode())

        fun snoozeAction(action: String, label: String): NotificationCompat.Action {
            val snoozeIntent = Intent(context, SnoozeReceiver::class.java).apply {
                this.action = action
                putExtra("task_title", title)
                putExtra("reminder_time", reminderTime)
                putExtra("notification_id", notificationId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                (notificationId * 31) + action.hashCode(),
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return NotificationCompat.Action.Builder(0, label, pendingIntent).build()
        }

        if (isAlarm) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val soundUri = android.media.RingtoneManager.getDefaultUri(
                    android.media.RingtoneManager.TYPE_ALARM
                )
                val attributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .build()
                val channel = android.app.NotificationChannel(
                    AlarmActivity.ALARM_CHANNEL_ID,
                    "Sveglie Faccio io",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Sveglie insistenti associate alle attività"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 800, 400, 800)
                    setSound(soundUri, attributes)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                context.getSystemService(android.app.NotificationManager::class.java)
                    .createNotificationChannel(channel)
            }
            val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
                putExtra("task_title", title)
                putExtra("reminder_time", reminderTime)
                putExtra("notification_id", notificationId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val fullScreenIntent = PendingIntent.getActivity(
                context,
                notificationId,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, AlarmActivity.ALARM_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Sveglia Faccio io")
                .setContentText(title)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenIntent, true)
                .setContentIntent(fullScreenIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .build()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            recordAlarmFailure(
                context, "NOTIFICA SVEGLIA PUBBLICATA", title, reminderTime, true,
                "notificationId=$notificationId; fullScreenIntent=creato"
            )
            return
        }

        val openAppIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "faccio_io_reminders_v2")
    .setSmallIcon(android.R.drawable.ic_dialog_info)
    .setContentTitle("Faccio io")
    .setContentText(title)
    .setPriority(NotificationCompat.PRIORITY_HIGH)
    .setDefaults(NotificationCompat.DEFAULT_ALL)
    .setVibrate(longArrayOf(0, 500, 300, 500))
    .setContentIntent(openAppIntent)
    .addAction(snoozeAction(SnoozeReceiver.ACTION_ONE_HOUR, "Tra un’ora"))
    .addAction(snoozeAction(SnoozeReceiver.ACTION_THIS_EVENING, "Questa sera"))
    .addAction(snoozeAction(SnoozeReceiver.ACTION_TOMORROW, "Domani"))
    .setAutoCancel(true)
    .build()

        NotificationManagerCompat.from(context).notify(
            notificationId,
            notification
        )
        recordAlarmFailure(
            context, "NOTIFICA PROMEMORIA PUBBLICATA", title, reminderTime, false,
            "notificationId=$notificationId"
        )
    }
}
