package it.faccioio.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AgendaWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
            updateAll(context)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    companion object {
        internal fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, AgendaWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach {
                updateWidget(context, manager, it)
            }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val now = System.currentTimeMillis()
            val tasks = loadTasks(context)
            val todayTasks = tasks.filter { task ->
                !task.completed && (
                    task.appointmentTime?.let { sameWidgetDay(it, now) } == true ||
                        task.reminderTime?.let { sameWidgetDay(it, now) } == true ||
                        (task.appointmentTime == null && task.reminderTime == null)
                    )
            }
            val next = tasks
                .filter { !it.completed }
                .mapNotNull { task ->
                    val time = task.appointmentTime ?: task.reminderTime
                    if (time != null && time >= now && sameWidgetDay(time, now)) task to time
                    else null
                }
                .minByOrNull { it.second }

            val views = RemoteViews(context.packageName, R.layout.faccio_io_widget)
            applyWidgetTheme(context, views)
            views.setTextViewText(
                R.id.widget_date,
                SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN)
                    .format(Date(now))
                    .replaceFirstChar { it.uppercase(Locale.ITALIAN) }
            )
            views.setTextViewText(
                R.id.widget_count,
                if (todayTasks.size == 1) "1 attività" else "${todayTasks.size} attività"
            )
            if (next == null) {
                views.setTextViewText(R.id.widget_title, "Nessun altro impegno")
                views.setTextViewText(R.id.widget_time, "Apri l’app per organizzare la giornata")
                views.setTextViewText(R.id.widget_departure, "")
                views.setViewVisibility(R.id.widget_departure, View.GONE)
            } else {
                val (task, time) = next
                views.setTextViewText(R.id.widget_title, task.title)
                views.setTextViewText(
                    R.id.widget_time,
                    "${if (task.appointmentTime != null) "Appuntamento" else "Promemoria"}: ${widgetHour(time)}"
                )
                val departure = task.departureTime?.takeIf { it >= now }
                views.setTextViewText(
                    R.id.widget_departure,
                    departure?.let { "Partenza consigliata  •  ${widgetHour(it)}" }.orEmpty()
                )
                views.setViewVisibility(
                    R.id.widget_departure,
                    if (departure == null) View.GONE else View.VISIBLE
                )
            }

            val openApp = PendingIntent.getActivity(
                context,
                widgetId,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            manager.updateAppWidget(widgetId, views)
        }

        private fun applyWidgetTheme(context: Context, views: RemoteViews) {
            val isDark = when (loadThemeMode(context)) {
                THEME_DARK -> true
                THEME_LIGHT -> false
                else -> context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            }

            val primaryText = Color.parseColor(if (isDark) "#E2E8EE" else "#173A5E")
            val mutedText = Color.parseColor(if (isDark) "#B8C3CC" else "#5E6875")
            val accentText = Color.parseColor(if (isDark) "#66CCCC" else "#188C8C")
            val countText = Color.parseColor(if (isDark) "#B5EEEE" else "#188C8C")
            val departureText = Color.parseColor(if (isDark) "#FFE0A8" else "#173A5E")

            views.setInt(
                R.id.widget_root,
                "setBackgroundResource",
                if (isDark) R.drawable.widget_background_dark else R.drawable.widget_background
            )
            views.setInt(
                R.id.widget_count,
                "setBackgroundResource",
                if (isDark) R.drawable.widget_badge_dark else R.drawable.widget_badge
            )
            views.setInt(
                R.id.widget_departure,
                "setBackgroundResource",
                if (isDark) R.drawable.widget_departure_badge_dark
                else R.drawable.widget_departure_badge
            )
            views.setInt(
                R.id.widget_accent,
                "setBackgroundResource",
                if (isDark) R.drawable.widget_accent_dark else R.drawable.widget_accent
            )

            views.setTextColor(R.id.widget_app_title, primaryText)
            views.setTextColor(R.id.widget_date, mutedText)
            views.setTextColor(R.id.widget_count, countText)
            views.setTextColor(R.id.widget_section_label, accentText)
            views.setTextColor(R.id.widget_title, primaryText)
            views.setTextColor(R.id.widget_time, mutedText)
            views.setTextColor(R.id.widget_departure, departureText)
        }

        private fun sameWidgetDay(first: Long, second: Long): Boolean {
            val a = Calendar.getInstance().apply { timeInMillis = first }
            val b = Calendar.getInstance().apply { timeInMillis = second }
            return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        }

        private fun widgetHour(time: Long): String =
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
    }
}
