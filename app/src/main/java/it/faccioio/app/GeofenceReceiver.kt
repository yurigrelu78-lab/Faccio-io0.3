package it.faccioio.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val ids = event.triggeringGeofences?.map { it.requestId }.orEmpty()
        val tasks = loadTasks(context).toMutableList()
        ids.forEach { id ->
            val index = tasks.indexOfFirst { it.arrivalReminderId == id }
            if (index >= 0) {
                notifyArrival(context, tasks[index].title, id.hashCode())
                tasks[index] = tasks[index].copy(arrivalReminderId = null)
                removeArrivalGeofence(context, id)
            }
        }
        saveTasks(context, tasks)
    }
}

private fun notifyArrival(context: Context, title: String, id: Int) {
    val channelId = "faccio_io_arrival_v1"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channelId, "Promemoria all’arrivo", NotificationManager.IMPORTANCE_HIGH)
        )
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    NotificationManagerCompat.from(context).notify(id, NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Sei arrivato")
        .setContentText(title).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build())
}
