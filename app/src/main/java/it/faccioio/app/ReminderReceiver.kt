package it.faccioio.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("task_title") ?: "Promemoria"

        val notification = NotificationCompat.Builder(context, "faccio_io_reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Faccio io")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(
            System.currentTimeMillis().toInt(),
            notification
        )
    }
}
