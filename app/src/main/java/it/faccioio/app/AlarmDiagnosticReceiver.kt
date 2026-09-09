package it.faccioio.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmDiagnosticReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reason = when (intent.action) {
            Intent.ACTION_DATE_CHANGED -> "cambio data/mezzanotte"
            Intent.ACTION_TIME_CHANGED -> "ora di sistema modificata"
            Intent.ACTION_TIMEZONE_CHANGED -> "fuso orario modificato"
            Intent.ACTION_POWER_CONNECTED -> "caricatore collegato"
            Intent.ACTION_POWER_DISCONNECTED -> "caricatore scollegato"
            Intent.ACTION_DEVICE_STORAGE_LOW -> "spazio dispositivo insufficiente"
            Intent.ACTION_DEVICE_STORAGE_OK -> "spazio dispositivo ripristinato"
            else -> intent.action ?: "evento sconosciuto"
        }
        captureAlarmDiagnosticSnapshot(context, reason)
    }
}
