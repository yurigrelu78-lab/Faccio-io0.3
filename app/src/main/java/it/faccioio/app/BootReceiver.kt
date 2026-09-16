package it.faccioio.app

import android.app.AlarmManager
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

        val alarms = futureAutomationAlarms(loadTasksForBoot(context))
        markAlarmRestoreStarted(context, alarms.size, "Broadcast: ${intent.action}")
        alarms.forEach { alarm ->
            if (!scheduleReminder(context, alarm.title, alarm.time, alarm.isAlarm)) {
                return
            }
        }
        if (intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            startAlarmDiagnosticMonitoring(context)
        }
        captureAlarmDiagnosticSnapshot(context, "dopo ripristino ${intent.action}")
    }
}
