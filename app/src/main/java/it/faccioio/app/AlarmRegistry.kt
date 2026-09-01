package it.faccioio.app

import android.content.Context

private const val ALARM_REGISTRY_PREFS = "faccio_io_alarm_registry"

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
        .putBoolean("scheduled_alarm_$key", isAlarm)
        .apply()
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
        .remove("scheduled_alarm_$key")
        .putLong("last_delivered_time", time)
        .putString("last_delivered_title", title)
        .putBoolean("last_delivered_alarm", isAlarm)
        .apply()
}

internal fun clearScheduledReminder(context: Context, title: String, time: Long) {
    val key = reminderRequestCode(title, time).toString()
    context.getSharedPreferences(ALARM_REGISTRY_PREFS, Context.MODE_PRIVATE)
        .edit()
        .remove("scheduled_time_$key")
        .remove("scheduled_alarm_$key")
        .apply()
}
